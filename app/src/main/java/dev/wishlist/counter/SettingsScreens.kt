package dev.wishlist.counter

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime

@Composable fun SettingsScreen(model:WishlistViewModel,state:UiState,onClear:()->Unit){
    var key by remember{mutableStateOf("")}
    var background by remember{mutableStateOf(model.app.preferences.raw.getBoolean("background",true))}
    var hours by remember{mutableStateOf(model.app.preferences.raw.getLong("interval",6))}
    var removeKey by remember{mutableStateOf(false)}
    if(removeKey)AlertDialog(onDismissRequest={removeKey=false},title={Text("Remove financial key?")},text={Text("Reporting synchronization will stop. Existing cached reports will remain available.")},confirmButton={TextButton(onClick={removeKey=false;model.removeKey()}){Text("Remove key")}},dismissButton={TextButton(onClick={removeKey=false}){Text("Cancel")}})
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text("Make it yours.",style=MaterialTheme.typography.headlineMedium)
        Text("Appearance",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Appearance.entries.forEach{appearance->FilterChip(selected=state.appearance==appearance,onClick={model.setAppearance(appearance)},label={Text(appearance.name.lowercase().replaceFirstChar(Char::titlecase))},modifier=Modifier.weight(1f).heightIn(min=48.dp))}}
        Text("System follows your device. Light and dark override it for the app and widgets.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Steamworks Financial API key",style=MaterialTheme.typography.titleMedium)
        Text(if(state.hasKey)"A key is saved on this device."else"No financial key saved.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(key,{key=it.trim()},label={Text(if(state.hasKey)"Replacement key"else"Financial API key")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={model.saveKey(key);key=""},enabled=(key.isNotBlank()&&key.length<=512&&key.all{it.code in 33..126})&&!state.busy,modifier=Modifier.fillMaxWidth()){Text("Save key securely")}
        Text("Use the key from your Steamworks Financial API Group. It can read financial data for the entire partner account. It is encrypted on this device and sent only to partner.steam-api.com.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.hasKey)TextButton(onClick={removeKey=true},enabled=!state.busy){Text("Remove saved key")}
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Background sync",style=MaterialTheme.typography.titleSmall);Text("Android schedules the actual run time.",style=MaterialTheme.typography.bodySmall)};Switch(checked=background,onCheckedChange={background=it;model.app.preferences.raw.edit().putBoolean("background",it).apply();model.app.scheduleSync()})}
        Text("Sync interval",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(1L,6L,12L,24L).forEach{value->FilterChip(selected=hours==value,onClick={hours=value;model.app.preferences.raw.edit().putLong("interval",value).apply();model.app.scheduleSync()},label={Text("${value}h")},modifier=Modifier.weight(1f).heightIn(min=48.dp))}}
        Text("Steam refreshes reports at least daily. More frequent checks do not make individual wishlist events real-time. Historical import is capped at 90 requests per run and resumes from cached days.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Your data",style=MaterialTheme.typography.titleMedium)
        Text("Games, reports, alert rules, and theme preferences stay on this device. Backups are disabled. No analytics or Google Play Services are used.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=onClear,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Delete all local data")}
        Text("Wishlist Counter · personal prototype 0.1.1\nPublic Steam metadata is best-effort. Direct mobile use of financial credentials still needs clarification with Valve before public distribution.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun AlertsScreen(model:WishlistViewModel,game:Game){
    var rule by remember(game.id){mutableStateOf(model.app.preferences.rule(game.id))}
    var message by remember{mutableStateOf("")}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->message=if(granted)"Notifications allowed."else"Notifications are disabled by Android. You can allow them in system settings."}
    val validTimes=!rule.quiet||runCatching{LocalTime.parse(rule.start);LocalTime.parse(rule.end)}.isSuccess
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text("Your rules. Your rhythm.",style=MaterialTheme.typography.headlineMedium)
        Text("${game.name} · notifications when new additions are detected on synchronization.")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Wishlist alerts",modifier=Modifier.weight(1f));Switch(rule.enabled,{rule=rule.copy(enabled=it)})}
        Text("Notify after new additions",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(1L,10L,25L,100L).forEach{count->FilterChip(selected=rule.threshold==count,onClick={rule=rule.copy(threshold=count)},label={Text("$count")},modifier=Modifier.weight(1f).heightIn(min=48.dp))}}
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Quiet hours");Text("Uses the device’s local time zone.",style=MaterialTheme.typography.bodySmall)};Switch(rule.quiet,{rule=rule.copy(quiet=it)})}
        if(rule.quiet){OutlinedTextField(rule.start,{rule=rule.copy(start=it)},label={Text("From · HH:mm")},singleLine=true,isError=!validTimes,modifier=Modifier.fillMaxWidth());OutlinedTextField(rule.end,{rule=rule.copy(end=it)},label={Text("Until · HH:mm")},singleLine=true,isError=!validTimes,modifier=Modifier.fillMaxWidth());Text("Equal start and end times disable the quiet interval.",style=MaterialTheme.typography.bodySmall)}
        Button(onClick={model.setRule(game,rule);if(Build.VERSION.SDK_INT>=33&&rule.enabled)permission.launch(Manifest.permission.POST_NOTIFICATIONS)},enabled=validTimes,modifier=Modifier.fillMaxWidth()){Text("Save for ${game.name}")}
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
        Text("The first history import never triggers old alerts. Repeated syncs do not count the same additions again. Alerts held by quiet hours are delivered on a later synchronization.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun WidgetScreen(model:WishlistViewModel,game:Game,reports:List<DailyReport>,appearance:Appearance){
    val context=LocalContext.current
    var balanceMetric by rememberSaveable{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    val latest=reports.lastOrNull()?.date
    val balance=latest?.let{derivedBalance(game,reports,LocalDate.parse(it))}
    val recent=reports.filter{it.date>=utcToday().minusDays(6).toString()}
    val count=if(balanceMetric)balance else recent.takeIf{it.isNotEmpty()}?.sumOf{it.adds}
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Text("A little window into your game.",style=MaterialTheme.typography.headlineMedium)
        Text("${game.name} · preview follows your appearance setting.")
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(game.name,style=MaterialTheme.typography.titleMedium)
                Text(if(balanceMetric)"Derived wishlist balance"else"Additions · last 7 days",style=MaterialTheme.typography.bodySmall)
                Text(count?.let(::number)?:"—",style=MaterialTheme.typography.displayMedium)
                Text(latest?.let{"$it GMT${if(it==utcToday().toString())" · provisional"else""}"}?:"No reports yet",style=MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!balanceMetric,{balanceMetric=false},label={Text("7-day additions")},modifier=Modifier.heightIn(min=48.dp));FilterChip(balanceMetric,{balanceMetric=true},label={Text("Derived balance")},modifier=Modifier.heightIn(min=48.dp))}
        Text("Choose the game and metric separately for each home-screen widget when adding it. A missing baseline or reporting gap shows an unknown balance, never an invented count.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={val manager=AppWidgetManager.getInstance(context);message=if(manager.isRequestPinAppWidgetSupported){context.startActivity(Intent(context,WidgetConfigurationActivity::class.java).putExtra("pin_mode",true).putExtra("selected_app_id",game.id).putExtra("selected_metric",if(balanceMetric)"balance"else"adds"));"Choose the game and metric, then confirm on your launcher."}else"Long-press your home screen, open Widgets, and choose Wishlist Counter."},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add home-screen widget")}
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun GameInfo(game:Game,busy:Boolean,onRefresh:()->Unit,onBaseline:(Long?,LocalDate?)->Unit,onRemove:()->Unit){
    val context=LocalContext.current
    var count by rememberSaveable(game.id){mutableStateOf(game.baseline?.toString().orEmpty())}
    var date by rememberSaveable(game.id){mutableStateOf(game.baselineDate?:utcToday().toString())}
    val parsedDate=runCatching{LocalDate.parse(date)}.getOrNull()
    val valid=count.toLongOrNull()?.let{it>=0}==true&&parsedDate!=null&&parsedDate<=utcToday()
    fun open(url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text(game.name,style=MaterialTheme.typography.headlineMedium)
        Text("AppID ${game.id}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(game.description.isNotBlank())Text(android.text.Html.fromHtml(game.description,android.text.Html.FROM_HTML_MODE_LEGACY).toString())else Text("No public description is available. The game may not have a published store page.")
        OutlinedButton(onClick={open(game.storeUrl)},modifier=Modifier.fillMaxWidth()){Text("Open Steam store");Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Outlined.OpenInNew,null)}
        if(game.website.isNotBlank())OutlinedButton(onClick={open(game.website)},modifier=Modifier.fillMaxWidth()){Text("Open game website");Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Outlined.OpenInNew,null)}
        TextButton(onClick={open(game.steamDbUrl)}){Text("View on SteamDB");Icon(Icons.AutoMirrored.Outlined.OpenInNew,null)}
        TextButton(onClick=onRefresh,enabled=!busy){Text("Refresh metadata from Steam")}
        HorizontalDivider()
        Text("Wishlist baseline",style=MaterialTheme.typography.titleMedium)
        Text("Enter your current outstanding wishlist count from Steamworks (or a count from any GMT date) to reconstruct earlier and later daily totals. Without a baseline, the total is accumulated from your earliest cached report.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(count,{count=it.filter(Char::isDigit).take(15)},label={Text("Outstanding wishlist count")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(date,{date=it},label={Text("Reference date · YYYY-MM-DD (GMT)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={onBaseline(count.toLong(),parsedDate)},enabled=valid&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save dated baseline")}
        if(game.baseline!=null)TextButton(onClick={count="";onBaseline(null,null)},enabled=!busy){Text("Remove baseline")}
        Text("The API does not document a direct outstanding balance. Past dates before the baseline are reconstructed by subtracting subsequent net changes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        OutlinedButton(onClick=onRemove,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Remove this game")}
    }
}
