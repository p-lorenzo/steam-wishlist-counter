package dev.wishlist.counter

import android.app.PendingIntent
import android.content.ComponentName
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetConfigurationActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val pinMode=intent.getBooleanExtra("pin_mode",false)
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID&&!pinMode){finish();return}
        val app=application as WishlistApplication
        setContent {
            FocusTheme(app.preferences.appearance){
                var games by remember{mutableStateOf<List<Game>>(emptyList())}
                var selected by remember{mutableStateOf<Long?>(null)}
                var metric by remember{mutableStateOf(intent.getStringExtra("selected_metric")?:"adds")}
                var loaded by remember{mutableStateOf(false)}
                LaunchedEffect(Unit){games=withContext(Dispatchers.IO){app.store.games()};selected=intent.getLongExtra("selected_app_id",-1).takeIf{id->games.any{it.id==id}}?:games.firstOrNull()?.id;loaded=true}
                Surface(Modifier.fillMaxSize()){
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
                        Text("Choose your widget",style=MaterialTheme.typography.headlineMedium)
                        Text("Select the game and number to show on your home screen.")
                        if(loaded&&games.isEmpty())Text("Add a game in Wishlist Counter first, then add this widget again.")
                        games.forEach{game->OutlinedButton(onClick={selected=game.id},modifier=Modifier.fillMaxWidth()){RadioButton(selected==game.id,onClick=null);Spacer(Modifier.width(8.dp));Text(game.name)}}
                        HorizontalDivider()
                        listOf("adds" to "Last 7 days’ additions","balance" to "Derived wishlist balance").forEach{(value,label)->OutlinedButton(onClick={metric=value},modifier=Modifier.fillMaxWidth()){RadioButton(metric==value,onClick=null);Spacer(Modifier.width(8.dp));Text(label)}}
                        Text("A derived balance needs a dated baseline and complete history. Report dates stay visible in the widget.",style=MaterialTheme.typography.bodySmall)
                        Button(enabled=selected!=null,onClick={
                            if(pinMode){
                                val callback=Intent(this@WidgetConfigurationActivity,WidgetPinnedReceiver::class.java).putExtra("selected_app_id",selected!!).putExtra("selected_metric",metric)
                                val success=PendingIntent.getBroadcast(this@WidgetConfigurationActivity,System.nanoTime().hashCode(),callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                                AppWidgetManager.getInstance(this@WidgetConfigurationActivity).requestPinAppWidget(ComponentName(this@WidgetConfigurationActivity,WishlistWidget::class.java),null,success)
                            }else{
                                app.preferences.raw.edit().putLong("widget-game-$widgetId",selected!!).putString("widget-metric-$widgetId",metric).commit()
                                WishlistWidget.update(this@WidgetConfigurationActivity,widgetId)
                                setResult(Activity.RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId))
                            }
                            finish()
                        },modifier=Modifier.fillMaxWidth()){Text("Add widget")}
                        TextButton(onClick={finish()},modifier=Modifier.fillMaxWidth()){Text("Cancel")}
                    }
                }
            }
        }
    }
}
