package dev.wishlist.counter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ShortDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@Composable
fun MetricWidgetCard(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun WishlistMovementsWidget(filtered: List<DailyReport>) {
    var mode by rememberSaveable { mutableStateOf("count") }
    val adds = filtered.sumOf { it.adds }
    val deletes = filtered.sumOf { it.deletes }
    val purchases = filtered.sumOf { it.purchases }
    val gifts = filtered.sumOf { it.gifts }
    val net = filtered.sumOf { it.net }
    val conversions = purchases + gifts
    val base = maxOf(1L, adds)

    MetricWidgetCard(
        title = "Movements & conversions",
        subtitle = "Entries, exits, purchases & gifts from wishlist",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == "count", onClick = { mode = "count" }, label = { Text("#", style = MaterialTheme.typography.labelSmall) })
                FilterChip(selected = mode == "pct", onClick = { mode = "pct" }, label = { Text("%", style = MaterialTheme.typography.labelSmall) })
            }
        },
    ) {
        if (filtered.isEmpty()) {
            Text("No wishlist reports in the selected interval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@MetricWidgetCard
        }
        fun fmt(v: Long): String = if (mode == "count") number(v) else String.format(Locale.US, "%.1f%%", v * 100.0 / base)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Added", number(adds))
            KpiStat("Removed", fmt(deletes))
            KpiStat("Net change", (if (net > 0) "+" else "") + number(net))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Purchased", fmt(purchases))
            KpiStat("Gifted", fmt(gifts))
            KpiStat("Converted", String.format(Locale.US, "%.1f%%", conversions * 100.0 / base))
        }
    }
}

@Composable
fun WishlistGeoLanguageWidget(
    store: LocalStore,
    gameId: Long,
    start: LocalDate,
    end: LocalDate,
    reportsVersion: Int,
) {
    var dimension by rememberSaveable { mutableStateOf("country") }
    var metric by rememberSaveable { mutableStateOf("adds") }
    val items by produceState(initialValue = emptyList<BreakdownEntry>(), gameId, dimension, start, end, reportsVersion) {
        value = withContext(Dispatchers.IO) {
            store.breakdowns(gameId, dimension, start.toString(), end.toString())
        }
    }
    MetricWidgetCard(
        title = "Geographic & language reach",
        subtitle = "Top regions & client languages in selected interval",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = metric == "adds",
                    onClick = { metric = "adds" },
                    label = { Text("Adds", style = MaterialTheme.typography.labelSmall) },
                )
                FilterChip(
                    selected = metric == "net",
                    onClick = { metric = "net" },
                    label = { Text("Net", style = MaterialTheme.typography.labelSmall) },
                )
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("country" to "Countries", "language" to "Languages").forEach { (value, label) ->
                FilterChip(
                    selected = dimension == value,
                    onClick = { dimension = value },
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                )
            }
        }
        val sorted = remember(items, metric) {
            if (metric == "net") items.sortedByDescending { it.net } else items.sortedByDescending { it.adds }
        }.take(6)
        if (sorted.isEmpty()) {
            Text(
                "Tap Sync to load country & language breakdowns from Steamworks for this interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricWidgetCard
        }
        val total = sorted.sumOf { (if (metric == "net") it.net else it.adds).coerceAtLeast(0L) }.coerceAtLeast(1L)
        val maxVal = sorted.maxOf { (if (metric == "net") it.net else it.adds).coerceAtLeast(1L) }.toFloat()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sorted.forEach { item ->
                val v = if (metric == "net") item.net else item.adds
                val pct = (v.coerceAtLeast(0L) * 100.0 / total)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            item.label.replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${number(v)} (${String.format(Locale.US, "%.0f%%", pct)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (v.coerceAtLeast(0L) / maxVal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun WishlistPlatformWidget(filtered: List<DailyReport>) {
    var showPct by rememberSaveable { mutableStateOf(true) }
    val win = filtered.sumOf { it.windowsAdds }
    val mac = filtered.sumOf { it.macAdds }
    val linux = filtered.sumOf { it.linuxAdds }
    val sumOs = win + mac + linux

    MetricWidgetCard(
        title = "Platform & Steam Deck interest",
        subtitle = "Wishlist additions by user browsing OS",
        action = {
            FilterChip(
                selected = showPct,
                onClick = { showPct = !showPct },
                label = { Text(if (showPct) "% Share" else "Count", style = MaterialTheme.typography.labelSmall) },
            )
        },
    ) {
        if (sumOs == 0L) {
            Text(
                "No platform-specific wishlist additions recorded in this interval (or tap Sync to refresh).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricWidgetCard
        }
        val platforms = listOf(
            "Windows" to win,
            "Linux / SteamOS (Deck)" to linux,
            "macOS" to mac,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            platforms.forEach { (name, count) ->
                val ratio = count.toFloat() / sumOs.coerceAtLeast(1L)
                val valueText = if (showPct) String.format(Locale.US, "%.1f%% (%s)", ratio * 100f, number(count)) else number(count)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodySmall)
                        Text(valueText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun SalesTrendWidget(
    filteredSales: List<SalesReport>,
    allSales: List<SalesReport>,
    start: LocalDate,
    end: LocalDate,
) {
    var metric by rememberSaveable { mutableStateOf("net_usd") }
    var selectedDate by remember(metric, start, end) { mutableStateOf<LocalDate?>(null) }

    MetricWidgetCard(
        title = "Sales & revenue trend",
        subtitle = "Daily partner financials ($ USD & units)",
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "net_usd" to "Net $",
                "gross_usd" to "Gross $",
                "units" to "Units",
                "cum_usd" to "Total $",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = metric == value,
                    onClick = { metric = value },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                )
            }
        }

        if (filteredSales.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(
                    "No sales transactions reported in this interval. For unreleased games or quiet intervals, figures will appear automatically once sales occur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@MetricWidgetCard
        }

        val points = remember(filteredSales, allSales, metric) {
            filteredSales.map { s ->
                val d = LocalDate.parse(s.date)
                val v = when (metric) {
                    "gross_usd" -> s.grossMicrosUsd
                    "units" -> s.netUnits
                    "cum_usd" -> allSales.filter { it.date <= s.date }.sumOf { it.netMicrosUsd }
                    else -> s.netMicrosUsd
                }
                ChartPoint(d, v)
            }
        }
        val connections = chartConnections(points)
        val minimum = minOf(0L, points.minOf { it.value }).toDouble()
        val maximum = maxOf(minimum + 1.0, points.maxOf { it.value }.toDouble())
        val colors = MaterialTheme.colorScheme
        val color = colors.primary
        val grid = colors.outlineVariant
        val tooltipBackground = colors.primaryContainer
        val tooltipColor = colors.onPrimaryContainer
        val surface = colors.surface
        val chartStart = points.first().date.let { if (it > start) it else start }
        val days = ChronoUnit.DAYS.between(chartStart, end)
        val measurer = rememberTextMeasurer()
        val selectedSale = selectedDate?.let { d -> filteredSales.find { it.date == d.toString() } }
        val selectedPoint = points.find { it.date == selectedDate }
        val isUsd = metric != "units"

        fun fmtVal(v: Long): String = if (isUsd) formatUsd(v) else "${number(v)} units"

        val tooltip = selectedDate?.let { date ->
            val detail = when {
                selectedSale == null -> "No sales transactions"
                selectedPoint != null -> "${fmtVal(selectedPoint.value)} · ${number(selectedSale.netUnits)} net units"
                else -> fmtVal(selectedSale.netMicrosUsd)
            }
            "${date.format(ShortDateFormat)}\n$detail"
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(
                Modifier.weight(1f).height(200.dp)
                    .pointerInput(chartStart, end, metric) {
                        detectTapGestures { offset -> selectedDate = chartDateAtFraction(chartStart, end, offset.x / size.width.coerceAtLeast(1)) }
                    }
                    .pointerInput(chartStart, end, metric) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> selectedDate = chartDateAtFraction(chartStart, end, offset.x / size.width.coerceAtLeast(1)) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                selectedDate = chartDateAtFraction(chartStart, end, change.position.x / size.width.coerceAtLeast(1))
                            },
                        )
                    },
            ) {
                val top = 54.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val graphHeight = bottom - top
                fun x(date: LocalDate) = if (days <= 0L) size.width / 2 else ChronoUnit.DAYS.between(chartStart, date).toFloat() / days * size.width
                fun y(value: Long) = bottom - ((value - minimum) / (maximum - minimum) * graphHeight).toFloat()
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                for (i in 0..2) {
                    val yy = top + graphHeight * i / 2
                    drawLine(grid, Offset(0f, yy), Offset(size.width, yy), pathEffect = dash)
                }
                connections.forEach { c ->
                    drawLine(color, Offset(x(c.from.date), y(c.from.value)), Offset(x(c.to.date), y(c.to.value)), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEach { p -> drawCircle(color, 3.dp.toPx(), Offset(x(p.date), y(p.value))) }
                selectedDate?.let { date ->
                    drawLine(color.copy(alpha = .6f), Offset(x(date), top), Offset(x(date), bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    selectedPoint?.let { p ->
                        drawCircle(surface, 7.dp.toPx(), Offset(x(p.date), y(p.value)))
                        drawCircle(color, 5.dp.toPx(), Offset(x(p.date), y(p.value)))
                    }
                    tooltip?.let { text ->
                        val pad = 8.dp.toPx()
                        val layout = measurer.measure(AnnotatedString(text), style = TextStyle(color = tooltipColor, fontSize = 12.sp), maxLines = 3, constraints = Constraints(maxWidth = (size.width - pad * 2).toInt().coerceAtLeast(1)))
                        val w = (layout.size.width + pad * 2).coerceAtMost(size.width)
                        val left = (x(date) - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                        drawRoundRect(tooltipBackground, Offset(left, 0f), Size(w, layout.size.height + pad * 2), CornerRadius(8.dp.toPx()))
                        drawText(layout, topLeft = Offset(left + pad, pad))
                    }
                }
            }
            Column(Modifier.padding(top = 54.dp).height(138.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maximum, (minimum + maximum) / 2, minimum).forEach { v ->
                    val label = if (isUsd) formatUsd(v.toLong()) else number(v.toLong())
                    Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(chartStart.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(end.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SalesRevenueKpiWidget(filteredSales: List<SalesReport>, intervalDays: Long) {
    var mode by rememberSaveable { mutableStateOf("total") }
    val grossMicros = filteredSales.sumOf { it.grossMicrosUsd }
    val netMicros = filteredSales.sumOf { it.netMicrosUsd }
    val grossUnits = filteredSales.sumOf { it.grossUnits }
    val netUnits = filteredSales.sumOf { it.netUnits }
    val divisor = if (mode == "avg") intervalDays.coerceAtLeast(1L) else 1L
    val aspMicros = if (netUnits > 0) netMicros / netUnits else 0L

    MetricWidgetCard(
        title = "Revenue & unit economics",
        subtitle = "Steam store & microtransaction summary (USD)",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == "total", onClick = { mode = "total" }, label = { Text("Total", style = MaterialTheme.typography.labelSmall) })
                FilterChip(selected = mode == "avg", onClick = { mode = "avg" }, label = { Text("/ Day", style = MaterialTheme.typography.labelSmall) })
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Net revenue", formatUsd(netMicros / divisor))
            KpiStat("Gross sales", formatUsd(grossMicros / divisor))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Net units", number(netUnits / divisor))
            KpiStat("Gross units", number(grossUnits / divisor))
            KpiStat("Avg net / unit", if (netUnits > 0) formatUsd(aspMicros) else "—")
        }
    }
}

@Composable
fun RefundsMonitorWidget(filteredSales: List<SalesReport>) {
    var mode by rememberSaveable { mutableStateOf("units") }
    val grossUnits = filteredSales.sumOf { it.grossUnits }
    val returnedUnits = filteredSales.sumOf { it.returnedUnits }
    val grossMicros = filteredSales.sumOf { it.grossMicrosUsd }
    val returnedMicros = filteredSales.sumOf { it.returnedMicrosUsd }
    val unitRate = refundRatePercent(grossUnits, returnedUnits)
    val usdRate = if (grossMicros > 0) returnedMicros * 100.0 / grossMicros else 0.0
    val activeRate = if (mode == "units") unitRate else usdRate

    MetricWidgetCard(
        title = "Refunds & return rate",
        subtitle = "Returned units & refunded USD in interval",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == "units", onClick = { mode = "units" }, label = { Text("Units", style = MaterialTheme.typography.labelSmall) })
                FilterChip(selected = mode == "usd", onClick = { mode = "usd" }, label = { Text("USD $", style = MaterialTheme.typography.labelSmall) })
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            KpiStat(
                label = if (mode == "units") "Returned units" else "Refunded USD",
                value = if (mode == "units") number(returnedUnits) else formatUsd(returnedMicros),
            )
            KpiStat(
                label = "Refund rate",
                value = String.format(Locale.US, "%.1f%%", activeRate),
            )
            Surface(
                color = if (activeRate >= 10.0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (grossUnits == 0L) "No sales" else if (activeRate >= 10.0) "Elevated" else "Healthy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (activeRate >= 10.0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun KeysAndActivationsWidget(filteredSales: List<SalesReport>) {
    val activatedKeys = filteredSales.sumOf { it.activatedKeys }
    val storeUnits = filteredSales.sumOf { it.netUnits }
    val total = (activatedKeys + storeUnits).coerceAtLeast(1L)

    MetricWidgetCard(
        title = "CD-Key & retail activations",
        subtitle = "External key redemptions vs direct Steam purchases",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Keys activated", number(activatedKeys))
            KpiStat("Steam net units", number(storeUnits))
            KpiStat("Key share", if (activatedKeys + storeUnits > 0) String.format(Locale.US, "%.1f%%", activatedKeys * 100.0 / total) else "0%")
        }
    }
}

@Composable
fun LiveStoreStatusWidget(
    snapshot: CommunitySnapshot?,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    MetricWidgetCard(
        title = "Live players & store status",
        subtitle = "Real-time CCU & active store pricing",
        action = {
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh store & community")
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("In-game now (CCU)", snapshot?.currentPlayers?.let(::number) ?: "0")
            KpiStat(
                "Store price",
                snapshot?.priceFormatted?.ifBlank { "Unreleased / Free" } ?: "—",
            )
            KpiStat(
                "Active discount",
                if ((snapshot?.discountPercent ?: 0) > 0) "-${snapshot?.discountPercent}%" else "None",
            )
        }
        if (!snapshot?.releaseDate.isNullOrBlank()) {
            Text(
                "Store release date: ${snapshot?.releaseDate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ReviewMilestonesWidget(snapshot: CommunitySnapshot?) {
    var scopeFilter by rememberSaveable { mutableStateOf("steam") }
    val pos = if (scopeFilter == "steam") (snapshot?.steamPositive ?: 0L) else (snapshot?.allPositive ?: 0L)
    val neg = if (scopeFilter == "steam") (snapshot?.steamNegative ?: 0L) else (snapshot?.allNegative ?: 0L)
    val total = pos + neg
    val pct = if (total > 0) pos * 100.0 / total else 0.0
    val steamTotal = snapshot?.steamTotal ?: 0L
    val target = nextReviewMilestone(steamTotal)
    val milestoneProgress = (steamTotal.toFloat() / target.coerceAtLeast(1L)).coerceIn(0f, 1f)

    MetricWidgetCard(
        title = "Reviews & visibility milestones",
        subtitle = "Only Steam-purchased reviews count toward algorithm tiers",
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = scopeFilter == "steam",
                onClick = { scopeFilter = "steam" },
                label = {
                    Text(
                        "Steam (${number(steamTotal)})",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            )
            FilterChip(
                selected = scopeFilter == "all",
                onClick = { scopeFilter = "all" },
                label = {
                    Text(
                        "All (${number(snapshot?.allTotal ?: 0L)})",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiStat("Rating", snapshot?.scoreDesc?.ifBlank { "No rating yet" } ?: "—")
            KpiStat("Positive %", if (total > 0) String.format(Locale.US, "%.1f%%", pct) else "—")
            KpiStat("+ / -", "${number(pos)} / ${number(neg)}")
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Next Steam visibility tier: ${number(target)} paid reviews",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${number(steamTotal)} / ${number(target)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { milestoneProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Text(
                when {
                    steamTotal < 10 -> "${10 - steamTotal} more Steam-purchased reviews needed to unlock a public Review Score."
                    steamTotal < 50 -> "10-review tier unlocked! ${50 - steamTotal} more to reach the 50-review tier."
                    steamTotal < 500 -> "50-review tier unlocked! ${500 - steamTotal} more to reach the 500-review tier (Overwhelmingly Positive eligible)."
                    else -> "All major Steam review count tiers (10 / 50 / 500) unlocked."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RecentReviewsWidget(snapshot: CommunitySnapshot?) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val allReviews = snapshot?.reviews.orEmpty()
    val filtered = remember(allReviews, filter) {
        when (filter) {
            "pos" -> allReviews.filter { it.votedUp }
            "neg" -> allReviews.filter { !it.votedUp }
            else -> allReviews
        }
    }

    MetricWidgetCard(
        title = "Recent player reviews",
        subtitle = "Latest reviews with playtime at review",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "pos" to "Positive", "neg" to "Negative").forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                )
            }
        }

        if (filtered.isEmpty()) {
            Text(
                "No matching player reviews available yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricWidgetCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filtered.take(5).forEach { review ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (review.votedUp) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbDown,
                                    contentDescription = null,
                                    tint = if (review.votedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    if (review.votedUp) "Recommended" else "Not Recommended",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "${review.playtimeHours}h · ${if (review.steamPurchase) "Steam" else "Key"} · ${review.language}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            review.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (review.timestamp > 0) {
                            val dateStr = runCatching {
                                Instant.ofEpochSecond(review.timestamp).atZone(ZoneOffset.UTC).toLocalDate().format(ShortDateFormat)
                            }.getOrDefault("")
                            if (dateStr.isNotEmpty()) {
                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementsFunnelWidget(snapshot: CommunitySnapshot?) {
    var order by rememberSaveable { mutableStateOf("top") }
    val achievements = snapshot?.achievements.orEmpty()
    val sorted = remember(achievements, order) {
        if (order == "rarest") achievements.sortedBy { it.percent } else achievements.sortedByDescending { it.percent }
    }.take(6)

    MetricWidgetCard(
        title = "Player progression funnel",
        subtitle = "Global achievement unlock percentages",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = order == "top", onClick = { order = "top" }, label = { Text("Top", style = MaterialTheme.typography.labelSmall) })
                FilterChip(selected = order == "rarest", onClick = { order = "rarest" }, label = { Text("Rarest", style = MaterialTheme.typography.labelSmall) })
            }
        },
    ) {
        if (sorted.isEmpty()) {
            Text(
                "No public Steam achievements found for this AppID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricWidgetCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sorted.forEach { ach ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ach.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f%%", ach.percent), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(
                        progress = { (ach.percent.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun KpiStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
