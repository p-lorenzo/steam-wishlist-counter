package dev.wishlist.counter

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun utcToday(): LocalDate = LocalDate.now(ZoneOffset.UTC)

data class Game(
    val id: Long,
    val name: String,
    val description: String = "",
    val header: String = "",
    val website: String = "",
    val baseline: Long? = null,
    val baselineDate: String? = null,
) {
    val storeUrl get() = "https://store.steampowered.com/app/$id/"
    val steamDbUrl get() = "https://steamdb.info/app/$id/"
}

data class DailyReport(
    val appId: Long,
    val date: String,
    val adds: Long,
    val deletes: Long,
    val purchases: Long,
    val gifts: Long,
    val windowsAdds: Long = 0,
    val macAdds: Long = 0,
    val linuxAdds: Long = 0,
) {
    val net get() = adds - deletes - purchases - gifts
    val conversions get() = purchases + gifts
}

data class BreakdownEntry(
    val code: String,
    val label: String,
    val adds: Long,
    val net: Long,
)

data class SalesReport(
    val appId: Long,
    val date: String,
    val grossUnits: Long,
    val returnedUnits: Long,
    val netUnits: Long,
    val activatedKeys: Long,
    val grossMicrosUsd: Long,
    val returnedMicrosUsd: Long,
    val netMicrosUsd: Long,
) {
    val grossUsd get() = grossMicrosUsd / 10_000.0
    val returnedUsd get() = returnedMicrosUsd / 10_000.0
    val netUsd get() = netMicrosUsd / 10_000.0
}

data class ReviewItem(
    val id: String,
    val votedUp: Boolean,
    val steamPurchase: Boolean,
    val playtimeHours: Double,
    val timestamp: Long,
    val language: String,
    val text: String,
)

data class AchievementStat(
    val name: String,
    val percent: Double,
)

data class CommunitySnapshot(
    val appId: Long,
    val currentPlayers: Long? = null,
    val scoreDesc: String = "",
    val steamPositive: Long = 0,
    val steamNegative: Long = 0,
    val allPositive: Long = 0,
    val allNegative: Long = 0,
    val priceFormatted: String = "",
    val discountPercent: Int = 0,
    val releaseDate: String = "",
    val reviews: List<ReviewItem> = emptyList(),
    val achievements: List<AchievementStat> = emptyList(),
    val updatedAt: String = "",
) {
    val steamTotal get() = steamPositive + steamNegative
    val allTotal get() = allPositive + allNegative
    val keyTotal get() = (allTotal - steamTotal).coerceAtLeast(0)
    val steamPositivePercent get() = if (steamTotal > 0) steamPositive * 100.0 / steamTotal else 0.0
    val allPositivePercent get() = if (allTotal > 0) allPositive * 100.0 / allTotal else 0.0
}

enum class Appearance { SYSTEM, LIGHT, DARK }
data class AlertRule(val enabled: Boolean = false, val threshold: Long = 10, val quiet: Boolean = true, val start: String = "22:00", val end: String = "08:00")

/**
 * Derives the cumulative wishlist balance at the end of [through] (GMT).
 * - With a manual baseline on [Game.baselineDate], calculates forwards (adding subsequent net deltas)
 *   and backwards (subtracting subsequent net deltas up to the baseline date).
 * - Without a manual baseline, accumulates net deltas from 0 starting at the earliest cached report.
 * - Quiet days where Steam omits `wishlist_summary` carry a net delta of 0.
 */
fun derivedBalance(game: Game, reports: List<DailyReport>, through: LocalDate): Long? {
    val byDate = reports.associateBy { LocalDate.parse(it.date) }
    val baseline = game.baseline
    val anchor = game.baselineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val total = if (baseline != null && anchor != null) {
        when {
            through == anchor -> baseline
            through > anchor -> baseline + byDate.entries.filter { (d, _) -> d > anchor && d <= through }.sumOf { it.value.net }
            else -> baseline - byDate.entries.filter { (d, _) -> d > through && d <= anchor }.sumOf { it.value.net }
        }
    } else {
        val earliest = byDate.keys.minOrNull() ?: return null
        if (through < earliest) return null
        byDate.entries.filter { (d, _) -> d <= through }.sumOf { it.value.net }
    }
    return total.takeIf { it >= 0 }
}

/** Converts Steam decimal USD strings (up to 4 decimal places, e.g. "311.7201") into 1e-4 USD integer units. */
fun parseUsdMicros(raw: String?): Long {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return 0L
    return runCatching { (trimmed.toDouble() * 10_000.0).roundToLong() }.getOrDefault(0L)
}

fun formatUsd(micros: Long): String = String.format(Locale.US, "$%,.2f", micros / 10_000.0)

fun refundRatePercent(grossUnits: Long, returnedUnits: Long): Double =
    if (grossUnits > 0) (abs(returnedUnits).toDouble() * 100.0 / grossUnits) else 0.0

fun nextReviewMilestone(steamReviews: Long): Long = when {
    steamReviews < 10L -> 10L
    steamReviews < 50L -> 50L
    steamReviews < 500L -> 500L
    else -> 1_000L
}

fun inQuietHours(rule: AlertRule, now: LocalTime): Boolean {
    if (!rule.quiet) return false
    val start = LocalTime.parse(rule.start)
    val end = LocalTime.parse(rule.end)
    return when {
        start == end -> false
        start < end -> now >= start && now < end
        else -> now >= start || now < end
    }
}

/** Never notify twice when a provisional count decreases and later recovers. */
fun unseenAdditions(previousHigh: Long, current: Long): Long = (current - previousHigh).coerceAtLeast(0)

fun validAppId(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 1..4_294_967_295L }
