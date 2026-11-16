package dev.wishlist.counter

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class ChartPoint(val date: LocalDate, val value: Long)
data class ChartConnection(val from: ChartPoint, val to: ChartPoint, val spansMissingDays: Boolean)

/** A sparse series stays connected, but an unavailable day is never represented as zero. */
fun chartConnections(points: List<ChartPoint>): List<ChartConnection> =
    points.zipWithNext().map { (from, to) ->
        ChartConnection(from, to, from.date.plusDays(1) != to.date)
    }

/** Preserve the selected interval after the first known addition; do not retrim every quiet week. */
fun trimmedChartStart(start: LocalDate, end: LocalDate, history: List<DailyReport>): LocalDate {
    val firstAddition = history.filter { it.adds > 0 }.minOfOrNull { LocalDate.parse(it.date) } ?: return start
    return if (firstAddition <= end) maxOf(start, firstAddition) else start
}

/** The cursor snaps to a calendar day, including missing days, rather than inventing a nearby value. */
fun chartDateAtFraction(start: LocalDate, end: LocalDate, fraction: Float): LocalDate {
    val span = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
    val position = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    return start.plusDays((span * position.toDouble()).roundToLong())
}

fun chartSelection(reports: List<DailyReport>, date: LocalDate): DailyReport? = reports.find { it.date == date.toString() }
