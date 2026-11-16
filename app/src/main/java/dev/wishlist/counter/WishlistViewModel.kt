package dev.wishlist.counter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class UiState(
    val games: List<Game> = emptyList(),
    val reports: Map<Long, List<DailyReport>> = emptyMap(),
    val salesReports: Map<Long, List<SalesReport>> = emptyMap(),
    val community: Map<Long, CommunitySnapshot?> = emptyMap(),
    val selectedId: Long? = null,
    val appearance: Appearance = Appearance.SYSTEM,
    val hasKey: Boolean = false,
    val busy: Boolean = false,
    val status: String = "",
    val lastSync: Map<Long, String?> = emptyMap(),
    val ready: Boolean = false,
)

class WishlistViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as WishlistApplication
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()

    init {
        reload()
    }

    fun select(id: Long) {
        mutable.update { it.copy(selectedId = id) }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        val games = app.store.games()
        mutable.update { current ->
            current.copy(
                games = games,
                reports = games.associate { it.id to app.store.reports(it.id) },
                salesReports = games.associate { it.id to app.store.salesReports(it.id) },
                community = games.associate { it.id to app.store.community(it.id) },
                selectedId = current.selectedId?.takeIf { id -> games.any { it.id == id } } ?: games.firstOrNull()?.id,
                appearance = app.preferences.appearance,
                hasKey = app.key.read() != null,
                lastSync = games.associate { it.id to app.store.lastSync(it.id) },
                ready = true,
            )
        }
    }

    private fun operation(block: suspend () -> String) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, status = "Working…") }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                load()
                mutable.update { it.copy(status = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SteamFailure) {
                load()
                mutable.update { it.copy(status = e.message ?: "Steam request failed.") }
            } catch (_: Exception) {
                load()
                mutable.update { it.copy(status = "Could not complete this action. Your saved data is still available.") }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun addGame(id: Long, fallbackName: String) = operation {
        if (app.store.games().any { it.id == id }) return@operation "That AppID is already monitored."
        var metadata: Game? = null
        try {
            metadata = app.client.metadata(id)
        } catch (_: SteamFailure) {
            /* Private or unavailable store pages can be added manually. */
        }
        val game = metadata ?: Game(id, fallbackName.trim().ifBlank { "App $id" })
        app.store.saveGame(game)
        runCatching { app.store.saveCommunity(app.client.communitySnapshot(id)) }
        mutable.update { it.copy(selectedId = id) }
        WishlistWidget.updateAll(app)
        if (metadata == null) "Game added with manual details. The public store page was unavailable."
        else "${game.name} added from Steam. Sync to load private wishlist & sales reports."
    }

    fun refreshMetadata(game: Game) = operation {
        runCatching { app.store.saveCommunity(app.client.communitySnapshot(game.id)) }
        val metadata = app.client.metadata(game.id) ?: return@operation "Community stats refreshed; no public store metadata available."
        app.store.saveGame(metadata.copy(baseline = game.baseline, baselineDate = game.baselineDate))
        WishlistWidget.updateAll(app)
        "Store metadata & community stats refreshed."
    }

    fun refreshCommunity(game: Game) = operation {
        val snapshot = app.client.communitySnapshot(game.id)
        app.store.saveCommunity(snapshot)
        "Store & community metrics updated."
    }

    fun saveKey(value: String) = operation {
        app.sync.mutex.lock()
        try {
            app.key.save(value)
        } finally {
            app.sync.mutex.unlock()
        }
        "Financial key saved on this device."
    }

    fun removeKey() = operation {
        app.sync.mutex.lock()
        try {
            app.key.clear()
        } finally {
            app.sync.mutex.unlock()
        }
        "Financial key removed. Cached reports remain available."
    }

    fun sync(from: LocalDate) = operation {
        app.sync.sync(from) { progress -> mutable.update { it.copy(status = progress) } }
    }

    fun setAppearance(value: Appearance) {
        app.preferences.appearance = value
        mutable.update { it.copy(appearance = value) }
        viewModelScope.launch(Dispatchers.IO) { WishlistWidget.updateAll(app) }
    }

    fun saveBaseline(game: Game, count: Long?, date: LocalDate?) = operation {
        app.store.saveGame(game.copy(baseline = count, baselineDate = date?.toString()))
        WishlistWidget.updateAll(app)
        if (count == null) "Baseline removed. Using cumulative cached reports."
        else "Baseline saved. Daily balances are now anchored to this count."
    }

    fun removeGame(game: Game) = operation {
        app.sync.mutex.lock()
        try {
            app.store.removeGame(game.id)
        } finally {
            app.sync.mutex.unlock()
        }
        WishlistWidget.updateAll(app)
        "${game.name} removed from this device."
    }

    fun clearAll() = operation {
        app.sync.mutex.lock()
        try {
            app.key.clear()
            app.store.clear()
            app.preferences.clear()
        } finally {
            app.sync.mutex.unlock()
        }
        app.scheduleSync()
        WishlistWidget.updateAll(app)
        "All local games, reports, preferences, and credentials deleted."
    }

    fun setRule(game: Game, rule: AlertRule) = operation {
        app.preferences.saveRule(game.id, rule)
        if (!rule.enabled) app.store.consumePending(game.id, app.store.pending(game.id))
        "Notification preferences saved for ${game.name}."
    }
}
