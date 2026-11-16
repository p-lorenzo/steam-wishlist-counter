package dev.wishlist.counter

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** In-app pinning needs an explicit success callback; the host skips normal configuration. */
class WidgetPinnedReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager=AppWidgetManager.getInstance(context)
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID||manager.getAppWidgetInfo(id)?.provider!=ComponentName(context,WishlistWidget::class.java))return
        val gameId=intent.getLongExtra("selected_app_id",-1)
        val metric=intent.getStringExtra("selected_metric")?.takeIf{it in listOf("adds","balance")}?:"adds"
        val result=goAsync()
        CoroutineScope(Dispatchers.IO).launch{
            try{
                val app=context.applicationContext as WishlistApplication
                if(app.store.games().none{it.id==gameId})return@launch
                app.preferences.raw.edit().putLong("widget-game-$id",gameId).putString("widget-metric-$id",metric).commit()
                WishlistWidget.update(context,id)
            }finally{result.finish()}
        }
    }
}
