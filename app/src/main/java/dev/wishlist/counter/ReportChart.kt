package dev.wishlist.counter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

private val ChartDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportChart(reports: List<DailyReport>, start: LocalDate, end: LocalDate, game: Game, allReports: List<DailyReport>) {
    var metric by remember(game.id) { mutableStateOf("adds") }
    var selectedDate by remember(game.id, metric, start, end) { mutableStateOf<LocalDate?>(null) }
    val canBalance = game.baseline != null || allReports.isNotEmpty()
    val effectiveMetric = if (metric == "balance" && !canBalance) "adds" else metric
    val chartStart = trimmedChartStart(start, end, allReports)
    val visibleReports = reports.filter { it.date >= chartStart.toString() }
    val expectedDays = ChronoUnit.DAYS.between(chartStart, end) + 1
    val availableDays = visibleReports.size.toLong()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val hintText = buildString {
        append("• Tap or slide horizontally on the chart to inspect any day.")
        if (availableDays < expectedDays) {
            append("\n• $availableDays of $expectedDays reporting days available. Days without a dot have no Steam report.")
        } else {
            append("\n• All $expectedDays reporting days in this interval are available.")
        }
        if (chartStart > start) {
            append("\n• Days before the first recorded wishlist ($chartStart) are hidden.")
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("adds" to "Additions", "net" to "Net change", "balance" to "Balance").forEach { (value, label) ->
            FilterChip(
                selected = effectiveMetric == value,
                onClick = { metric = value },
                enabled = value != "balance" || canBalance,
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            )
        }
    }

    val points = visibleReports.mapNotNull { report ->
        val value = when (effectiveMetric) {
            "balance" -> derivedBalance(game, allReports, LocalDate.parse(report.date))
            "net" -> report.net
            else -> report.adds
        }
        value?.let { ChartPoint(LocalDate.parse(report.date), it) }
    }
    if (points.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            Text("No complete data for this chart. Sync this interval or set a baseline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val connections = chartConnections(points)
    val minimum = if (effectiveMetric == "balance") floor(points.minOf { it.value }.toDouble()) else minOf(0L, points.minOf { it.value }).toDouble()
    val maximum = maxOf(minimum + 1, points.maxOf { it.value }.toDouble())
    val colors = MaterialTheme.colorScheme
    val color = colors.primary
    val grid = colors.outlineVariant
    val tooltipBackground = colors.primaryContainer
    val tooltipColor = colors.onPrimaryContainer
    val surface = colors.surface
    val days = ChronoUnit.DAYS.between(chartStart, end)
    val measurer = rememberTextMeasurer()
    val selectedReport = selectedDate?.let { chartSelection(visibleReports, it) }
    val selectedPoint = points.find { it.date == selectedDate }
    val selectedBalance = if (effectiveMetric == "balance" && selectedDate != null) derivedBalance(game, allReports, selectedDate!!) else null
    val tooltip = selectedDate?.let { date ->
        val detail = when {
            effectiveMetric == "balance" && selectedReport == null && selectedBalance != null -> "Balance: ${number(selectedBalance)} · No report available"
            selectedReport == null -> "No report available"
            effectiveMetric == "adds" -> "${number(selectedReport.adds)} wishlist additions"
            effectiveMetric == "net" -> "Net: ${number(selectedReport.net)} · Added: ${number(selectedReport.adds)}"
            selectedPoint == null -> "Balance unavailable · Added: ${number(selectedReport.adds)}"
            else -> "Balance: ${number(selectedPoint.value)} · Added: ${number(selectedReport.adds)}"
        }
        "${date.format(ChartDateFormat)} · GMT\n$detail"
    }
    val description = "${if (effectiveMetric == "balance") "Derived balance" else if (effectiveMetric == "net") "Daily net change" else "Daily additions"}, $chartStart through $end. ${points.size} available days. Tap or slide to inspect a day."
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier.weight(1f).height(210.dp)
                .semantics {
                    contentDescription = tooltip ?: description
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(
                        CustomAccessibilityAction("Previous day") { selectedDate = (selectedDate ?: end).minusDays(1).coerceIn(chartStart, end); true },
                        CustomAccessibilityAction("Next day") { selectedDate = (selectedDate ?: chartStart).plusDays(1).coerceIn(chartStart, end); true },
                    )
                }
                .pointerInput(chartStart, end, effectiveMetric) {
                    detectTapGestures { offset -> selectedDate = chartDateAtFraction(chartStart, end, offset.x / size.width.coerceAtLeast(1)) }
                }
                .pointerInput(chartStart, end, effectiveMetric) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> selectedDate = chartDateAtFraction(chartStart, end, offset.x / size.width.coerceAtLeast(1)) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            selectedDate = chartDateAtFraction(chartStart, end, change.position.x / size.width.coerceAtLeast(1))
                        },
                    )
                }
        ) {
            val top = 56.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val graphHeight = bottom - top
            fun x(date: LocalDate) = if (days == 0L) size.width / 2 else ChronoUnit.DAYS.between(chartStart, date).toFloat() / days * size.width
            fun y(value: Long) = bottom - ((value - minimum) / (maximum - minimum) * graphHeight).toFloat()
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for (i in 0..2) {
                val yy = top + graphHeight * i / 2
                drawLine(grid, Offset(0f, yy), Offset(size.width, yy), pathEffect = dash)
            }
            connections.forEach { connection ->
                drawLine(
                    color = color,
                    start = Offset(x(connection.from.date), y(connection.from.value)),
                    end = Offset(x(connection.to.date), y(connection.to.value)),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point -> drawCircle(color, 3.dp.toPx(), Offset(x(point.date), y(point.value))) }
            selectedDate?.let { date ->
                drawLine(color.copy(alpha = .6f), Offset(x(date), top), Offset(x(date), bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                selectedPoint?.let { point ->
                    drawCircle(surface, 7.dp.toPx(), Offset(x(point.date), y(point.value)))
                    drawCircle(color, 5.dp.toPx(), Offset(x(point.date), y(point.value)))
                }
                tooltip?.let { text ->
                    val padding = 9.dp.toPx()
                    val layout = measurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(color = tooltipColor, fontSize = 12.sp),
                        maxLines = 3,
                        constraints = Constraints(maxWidth = (size.width - padding * 2).toInt().coerceAtLeast(1)),
                    )
                    val width = (layout.size.width + padding * 2).coerceAtMost(size.width)
                    val left = (x(date) - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
                    drawRoundRect(tooltipBackground, Offset(left, 0f), Size(width, layout.size.height + padding * 2), CornerRadius(8.dp.toPx()))
                    drawText(layout, topLeft = Offset(left + padding, padding))
                }
            }
        }
        Column(Modifier.padding(top = 56.dp).height(146.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(maximum, (minimum + maximum) / 2, minimum).forEach { value ->
                val label = if (value % 1.0 == 0.0) number(value.toLong()) else String.format(Locale.US, "%,.1f", value)
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chartStart.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
            tooltip = {
                RichTooltip(title = { Text("Chart info") }) {
                    Text(hintText, style = MaterialTheme.typography.bodySmall)
                }
            },
            state = tooltipState,
        ) {
            TextButton(
                onClick = { scope.launch { tooltipState.show() } },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Chart info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Chart info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(end.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
