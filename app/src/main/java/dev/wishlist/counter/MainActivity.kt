package dev.wishlist.counter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

fun number(value: Long) = String.format(Locale.US, "%,d", value)

class MainActivity : ComponentActivity() {
    private lateinit var model: WishlistViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[WishlistViewModel::class.java]
        intent.getLongExtra("app_id", -1).takeIf { it > 0 }?.let(model::select)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            FocusTheme(state.appearance) {
                val dark = MaterialTheme.colorScheme.background == Color(0xFF201F2B)
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                WishlistApp(model, state)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getLongExtra("app_id", -1).takeIf { it > 0 }?.let(model::select)
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistApp(model: WishlistViewModel, state: UiState) {
    var screen by rememberSaveable { mutableStateOf("games") }
    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0: Wishlists, 1: Sales & Revenue, 2: Store & Community
    var range by rememberSaveable { mutableStateOf("30") }
    var customStart by rememberSaveable { mutableStateOf(utcToday().minusDays(29).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(utcToday().toString()) }
    var clearDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val game = state.games.find { it.id == state.selectedId }
    val start = if (range == "custom") runCatching { LocalDate.parse(customStart) }.getOrNull() else utcToday().minusDays(range.toLong() - 1)
    val end = if (range == "custom") runCatching { LocalDate.parse(customEnd) }.getOrNull() else utcToday()
    val validRange = start != null && end != null && start <= end && end <= utcToday() && ChronoUnit.DAYS.between(start, end) <= 365
    val reports = game?.let { state.reports[it.id] }.orEmpty()
    val salesReports = game?.let { state.salesReports[it.id] }.orEmpty()
    val community = game?.let { state.community[it.id] }
    val filtered = if (validRange) reports.filter { it.date >= start.toString() && it.date <= end.toString() } else emptyList()
    val filteredSales = if (validRange) salesReports.filter { it.date >= start.toString() && it.date <= end.toString() } else emptyList()

    // Trigger background community refresh if missing when viewing a game
    LaunchedEffect(game?.id) {
        if (game != null && state.community[game.id] == null && !state.busy) {
            model.refreshCommunity(game)
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = buildString {
                if (activeTab == 1) {
                    append("appid,date_pt,gross_units,returned_units,net_units,activated_keys,gross_usd,returned_usd,net_usd\n")
                    filteredSales.forEach {
                        append("${it.appId},${it.date},${it.grossUnits},${it.returnedUnits},${it.netUnits},${it.activatedKeys},${it.grossUsd},${it.returnedUsd},${it.netUsd}\n")
                    }
                } else {
                    append("appid,date_gmt,additions,removals,purchases,gifts,net_change,win_adds,mac_adds,linux_adds\n")
                    filtered.forEach {
                        append("${it.appId},${it.date},${it.adds},${it.deletes},${it.purchases},${it.gifts},${it.net},${it.windowsAdds},${it.macAdds},${it.linuxAdds}\n")
                    }
                }
            }
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("No output") }.isSuccess
            }
            snackbar.showSnackbar(if (saved) "Report exported." else "Could not save this export. Try another destination.")
        }
    }

    LaunchedEffect(state.status, state.busy) {
        if (state.status.isNotBlank() && !state.busy) snackbar.showSnackbar(state.status)
    }
    BackHandler(screen != "games") { screen = "games" }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Delete all local data?") },
            text = { Text("This removes your key, monitored games, reports, and preferences from this device.") },
            confirmButton = { TextButton(onClick = { clearDialog = false; model.clearAll(); screen = "games" }) { Text("Delete all data") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
    if (removeDialog && game != null) {
        AlertDialog(
            onDismissRequest = { removeDialog = false },
            title = { Text("Remove ${game.name}?") },
            text = { Text("Its cached reports and baseline will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { removeDialog = false; model.removeGame(game); screen = "games" }) { Text("Remove game") } },
            dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            "games" -> "Your games"
                            "alerts" -> "Notifications"
                            "widget" -> "Home widget"
                            "add" -> "Add a game"
                            "info" -> "Game details"
                            else -> "Settings"
                        }
                    )
                },
                navigationIcon = {
                    if (screen in listOf("add", "info")) {
                        IconButton(onClick = { screen = "games" }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to games")
                        }
                    }
                },
                actions = {
                    if (screen == "games") {
                        IconButton(onClick = { screen = "add" }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Add, "Add game")
                        }
                        IconButton(
                            onClick = { if (validRange) model.sync(start!!) },
                            enabled = !state.busy && state.games.isNotEmpty() && validRange,
                        ) {
                            Icon(Icons.Outlined.Sync, "Synchronize selected interval")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (screen !in listOf("add", "info")) {
                NavigationBar {
                    listOf(
                        Triple("games", "Games", Icons.Outlined.SportsEsports),
                        Triple("alerts", "Alerts", Icons.Outlined.Notifications),
                        Triple("widget", "Widget", Icons.Outlined.Widgets),
                        Triple("settings", "Settings", Icons.Outlined.Settings),
                    ).forEach { (value, label, glyph) ->
                        NavigationBarItem(
                            selected = screen == value,
                            onClick = { screen = value },
                            icon = { Icon(glyph, label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize()) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    state.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            if (!state.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            when (screen) {
                "add" -> AddGameForm(state.busy) { id, name -> model.addGame(id, name); screen = "games" }
                "settings" -> SettingsScreen(model, state, onClear = { clearDialog = true })
                "info" -> game?.let {
                    GameInfo(
                        it,
                        state.busy,
                        onRefresh = { model.refreshMetadata(it) },
                        onBaseline = { count, date -> model.saveBaseline(it, count, date) },
                        onRemove = { removeDialog = true },
                    )
                }
                else -> if (game == null) {
                    EmptyGames(state.hasKey, onAdd = { screen = "add" }, onKey = { screen = "settings" })
                } else {
                    if (state.games.size > 1) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.games.forEach { item ->
                                FilterChip(
                                    selected = item.id == game.id,
                                    onClick = { model.select(item.id) },
                                    label = { Text(item.name) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                    when (screen) {
                        "games" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            GameHero(game, community, onInfo = { screen = "info" })

                            // 3-View Switcher: Wishlists · Sales & Revenue · Store & Community
                            PrimaryTabRow(selectedTabIndex = activeTab) {
                                listOf(
                                    "Wishlists",
                                    "Sales & $",
                                    "Community",
                                ).forEachIndexed { index, title ->
                                    Tab(
                                        selected = activeTab == index,
                                        onClick = { activeTab = index },
                                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }

                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (!state.hasKey && activeTab != 2) {
                                    OutlinedCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Connect your Steamworks reports", style = MaterialTheme.typography.titleSmall)
                                            Text("Add a Financial API key to load private wishlist & sales data.", style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = { screen = "settings" }) { Text("Add financial key") }
                                        }
                                    }
                                }

                                // Generalized Date Interval Selector (shared across Wishlists and Sales & Revenue)
                                if (activeTab in 0..1) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    ) {
                                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "Date interval",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (validRange) {
                                                    Text(
                                                        "$start → $end",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                listOf("7" to "7d", "30" to "30d", "90" to "90d", "custom" to "Custom").forEach { (value, label) ->
                                                    FilterChip(
                                                        selected = range == value,
                                                        onClick = { range = value },
                                                        label = {
                                                            Text(
                                                                label,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                maxLines = 1,
                                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        },
                                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                                    )
                                                }
                                            }
                                            if (range == "custom") {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        customStart,
                                                        { customStart = it },
                                                        label = { Text("From · YYYY-MM-DD") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    OutlinedTextField(
                                                        customEnd,
                                                        { customEnd = it },
                                                        label = { Text("Through · YYYY-MM-DD") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            }
                                            if (!validRange) {
                                                Text(
                                                    "Use valid dates, up to 366 days, ending no later than today.",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (validRange) {
                                    val intervalDays = ChronoUnit.DAYS.between(start!!, end!!) + 1
                                    when (activeTab) {
                                        0 -> {
                                            // VIEW 1: WISHLISTS
                                            val latest = reports.lastOrNull()
                                            val balance = latest?.let { derivedBalance(game, reports, LocalDate.parse(it.date)) }
                                            MetricWidgetCard(
                                                title = if (balance != null) "Derived wishlist balance" else "Additions · selected interval",
                                                subtitle = latest?.let { "Report through ${it.date} GMT${if (it.date == utcToday().toString()) " · provisional" else ""}" } ?: "No wishlist reports yet",
                                            ) {
                                                Text(
                                                    if (balance != null) number(balance) else if (filtered.isNotEmpty()) number(filtered.sumOf { it.adds }) else "—",
                                                    style = MaterialTheme.typography.displayLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    when {
                                                        game.baseline != null -> "Anchored to baseline of ${number(game.baseline)} on ${game.baselineDate} GMT."
                                                        balance != null -> "Cumulative net total from cached reports. Set today’s count in Game details to anchor earlier history."
                                                        else -> "Sync reports or add a baseline in Game details to derive a total."
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                ReportChart(filtered, start, end, game, reports)
                                            }

                                            WishlistMovementsWidget(filtered)
                                            WishlistGeoLanguageWidget(
                                                store = model.app.store,
                                                gameId = game.id,
                                                start = start,
                                                end = end,
                                                reportsVersion = reports.size,
                                            )
                                            WishlistPlatformWidget(filtered)

                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { model.sync(start) },
                                                    enabled = !state.busy,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(Icons.Outlined.Sync, null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Sync")
                                                }
                                                OutlinedButton(
                                                    onClick = { export.launch("wishlist-${game.id}-$start-$end.csv") },
                                                    enabled = filtered.isNotEmpty(),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text("Export CSV")
                                                }
                                            }
                                        }

                                        1 -> {
                                            // VIEW 2: SALES & REVENUE
                                            SalesTrendWidget(filteredSales, salesReports, start, end)
                                            SalesRevenueKpiWidget(filteredSales, intervalDays)
                                            RefundsMonitorWidget(filteredSales)
                                            KeysAndActivationsWidget(filteredSales)

                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { model.sync(start) },
                                                    enabled = !state.busy,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(Icons.Outlined.Sync, null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Sync sales")
                                                }
                                                OutlinedButton(
                                                    onClick = { export.launch("sales-${game.id}-$start-$end.csv") },
                                                    enabled = filteredSales.isNotEmpty(),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text("Export CSV")
                                                }
                                            }
                                        }

                                        else -> {
                                            // VIEW 3: STORE & COMMUNITY
                                            LiveStoreStatusWidget(
                                                snapshot = community,
                                                busy = state.busy,
                                                onRefresh = { model.refreshCommunity(game) },
                                            )
                                            ReviewMilestonesWidget(community)
                                            RecentReviewsWidget(community)
                                            AchievementsFunnelWidget(community)
                                        }
                                    }
                                }

                                state.lastSync[game.id]?.let {
                                    Text(
                                        "Last successful sync: $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { screen = "info" }) {
                                    Text("Game details, links & baseline")
                                    Icon(Icons.Outlined.ChevronRight, null)
                                }
                            }
                        }
                        "alerts" -> AlertsScreen(model, game)
                        "widget" -> WidgetScreen(model, game, reports, state.appearance)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHero(game: Game, community: CommunitySnapshot?, onInfo: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(185.dp).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onInfo),
    ) {
        AsyncImage(
            model = game.header,
            contentDescription = "${game.name} store artwork",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6111B20)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                game.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val metaParts = buildList {
                add("AppID ${game.id}")
                if (community?.currentPlayers != null && community.currentPlayers > 0) {
                    add("${number(community.currentPlayers)} in-game")
                }
                if (!community?.priceFormatted.isNullOrBlank()) {
                    val disc = if (community!!.discountPercent > 0) " (-${community.discountPercent}%)" else ""
                    add("${community.priceFormatted}$disc")
                }
                add("game details")
            }
            Text(metaParts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Color(0xFFE4E8EB))
        }
    }
}

@Composable
private fun EmptyGames(hasKey: Boolean, onAdd: () -> Unit, onKey: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.SportsEsports, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Your next launch starts here.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Text("Add a Steam AppID. Public store details load automatically; your Financial API key unlocks private wishlist & sales reports.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add your first game") }
        if (!hasKey) TextButton(onClick = onKey) { Text("Set up financial key") }
    }
}

@Composable
private fun AddGameForm(busy: Boolean, onAdd: (Long, String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Start with an AppID.", style = MaterialTheme.typography.headlineMedium)
        Text("We’ll fetch the name, artwork, description, reviews, and website directly from the public Steam store.")
        OutlinedTextField(
            id,
            { id = it.filter(Char::isDigit).take(10) },
            label = { Text("Steam AppID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = id.isNotBlank() && validAppId(id) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Find it in store.steampowered.com/app/123456/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Fallback name · optional") }, modifier = Modifier.fillMaxWidth())
        Text("Unpublished or unavailable store pages can still be monitored using a manual name. No SteamDB scraping is used.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { validAppId(id)?.let { onAdd(it, name) } }, enabled = validAppId(id) != null && !busy, modifier = Modifier.fillMaxWidth()) { Text("Add game") }
    }
}
