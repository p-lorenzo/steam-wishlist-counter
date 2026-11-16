package dev.wishlist.counter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

class SyncEngine(private val app: WishlistApplication) {
    val mutex = Mutex()

    suspend fun sync(from: LocalDate = utcToday().minusDays(29), onProgress: (String) -> Unit = {}): String = mutex.withLock {
        val games = app.store.games()
        if (games.isEmpty()) return@withLock "Add a game first."

        // 1. Refresh public Store & Community snapshot (CCU, Reviews, Achievements, Price) for all games
        for (game in games) {
            onProgress("${game.name} · Store & reviews")
            runCatching {
                val snapshot = app.client.communitySnapshot(game.id)
                app.store.saveCommunity(snapshot)
            }
        }

        val key = app.key.read() ?: return@withLock "Store & community refreshed. Add your Financial API key in Settings for wishlists & sales."
        val today = utcToday()
        var requested = 0
        var updated = 0

        // 2. Sync Wishlist reports + Country/Language/OS breakdowns
        for (game in games) {
            val initialized = app.store.initialized(game.id)
            val cached = app.store.reports(game.id).associateBy { it.date }
            var minimum = app.store.minDate(game.id)?.let(LocalDate::parse)
            var date = today
            val start = maxOf(from, today.minusDays(365))
            while (date >= start && requested < 90) {
                if (minimum != null && date < minimum) break
                val existing = cached[date.toString()]
                val missingBreakdowns = existing != null && existing.adds > 0 &&
                    existing.windowsAdds == 0L && existing.macAdds == 0L && existing.linuxAdds == 0L &&
                    !app.store.hasBreakdownsForDate(game.id, date.toString())
                if (date >= today.minusDays(1) || existing == null || missingBreakdowns) {
                    onProgress("${game.name} · Wishlists $date")
                    val result = app.client.wishlist(game.id, date, key)
                    requested++
                    result.minimumDate?.let { app.store.setMinDate(game.id, it); minimum = LocalDate.parse(it) }
                    result.report?.let {
                        app.store.saveReport(it, initialized && date >= today.minusDays(1))
                        app.store.saveBreakdowns(game.id, date.toString(), "country", result.countries)
                        app.store.saveBreakdowns(game.id, date.toString(), "language", result.languages)
                        updated++
                    }
                    delay(180)
                }
                date = date.minusDays(1)
            }
            val hasClosedReport = app.store.reports(game.id).any { it.date == today.minusDays(1).toString() }
            app.store.finishSync(game.id, hasClosedReport)
            deliverAlert(game)
        }

        // 3. Sync Sales, Refunds & Revenue ($) via GetChangedDatesForPartner + GetDetailedSales
        runCatching {
            val monitoredIds = games.map { it.id }.toSet()
            val prevHwm = app.preferences.raw.getLong("sales_hwm", 0L)
            onProgress("Checking partner sales dates…")
            val changed = app.client.changedSalesDates(prevHwm, key)
            val cutoff = today.minusDays(365).toString()
            val recentDates = changed.dates.filter { it >= cutoff }.sortedDescending().take(45)
            for (salesDate in recentDates) {
                onProgress("Sales & revenue · $salesDate")
                val sales = app.client.detailedSalesForDate(salesDate, monitoredIds, key)
                sales.forEach { app.store.saveSalesReport(it); updated++ }
                delay(150)
            }
            if (recentDates.size == changed.dates.filter { it >= cutoff }.size && changed.highwatermark > prevHwm) {
                app.preferences.raw.edit().putLong("sales_hwm", changed.highwatermark).apply()
            }
        }

        WishlistWidget.updateAll(app)
        if (requested >= 90) "$updated reports saved. Sync again to continue historical import."
        else "Wishlists, sales & community data synchronized ($updated updates)."
    }

    fun deliverAlert(game: Game) {
        val count = app.store.pending(game.id)
        val rule = app.preferences.rule(game.id)
        if (!rule.enabled) {
            if (count > 0) app.store.consumePending(game.id, count)
            return
        }
        if (count < rule.threshold || inQuietHours(rule, LocalTime.now())) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("wishlist-updates", "Wishlist updates", NotificationManager.IMPORTANCE_DEFAULT))
        val channel = manager.getNotificationChannel("wishlist-updates")
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return
        val intent = Intent(app, MainActivity::class.java).putExtra("app_id", game.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(app, game.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val date = app.store.reports(game.id).lastOrNull()?.date ?: "unknown"
        manager.notify(
            game.id.hashCode(),
            NotificationCompat.Builder(app, "wishlist-updates").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${game.name}: +$count wishlist additions")
                .setContentText("New additions detected on sync. Latest GMT report: $date.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$count new additions detected since your previous alert. Latest GMT report: $date. Steam reports are not real-time."))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build(),
        )
        app.store.consumePending(game.id, count)
    }
}
