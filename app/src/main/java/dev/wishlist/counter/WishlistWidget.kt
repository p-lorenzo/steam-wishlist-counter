package dev.wishlist.counter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** RemoteViews keeps the prototype widget independent of Google Play Services. */
class WishlistWidget : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        val result=goAsync()
        CoroutineScope(Dispatchers.IO).launch{try{for(id in ids)update(context,id)}finally{result.finish()}}
    }
    override fun onDeleted(context:Context,ids:IntArray){val prefs=Preferences(context).raw;val editor=prefs.edit();for(id in ids){editor.remove("widget-game-$id");editor.remove("widget-metric-$id")};editor.apply()}
    companion object {
        fun updateAll(context:Context){val manager=AppWidgetManager.getInstance(context);for(id in manager.getAppWidgetIds(ComponentName(context,WishlistWidget::class.java)))update(context,id)}
        fun update(context:Context,id:Int){
            val app=context.applicationContext as WishlistApplication
            val prefs=app.preferences
            val gameId=prefs.raw.getLong("widget-game-$id",-1)
            val game=app.store.games().find{it.id==gameId}
            val reports=game?.let{app.store.reports(it.id)}?:emptyList()
            val latest=reports.lastOrNull()?.date?.let(LocalDate::parse)
            val metric=prefs.raw.getString("widget-metric-$id","adds")
            val balance=if(game!=null&&latest!=null)derivedBalance(game,reports,latest)else null
            val seven=reports.filter{it.date>=utcToday().minusDays(6).toString()&&it.date<=utcToday().toString()}
            val value=if(metric=="balance")balance else seven.takeIf{it.isNotEmpty()}?.sumOf{it.adds}
            val label=if(metric=="balance")"Derived wishlist balance" else "Additions · last 7 days (GMT)"
            val dark=when(prefs.appearance){Appearance.DARK->true;Appearance.LIGHT->false;Appearance.SYSTEM->context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
            val views=RemoteViews(context.packageName,R.layout.wishlist_widget)
            views.setTextViewText(R.id.widget_name,game?.name?:"Choose a monitored game")
            views.setTextViewText(R.id.widget_label,if(game==null)"Open the app to configure"else label)
            views.setTextViewText(R.id.widget_number,value?.let{String.format(Locale.US,"%,d",it)}?:"—")
            val stale=latest!=null&&latest<utcToday().minusDays(1)
            views.setTextViewText(R.id.widget_date,when{game==null->"Game unavailable";latest==null->"No reports yet";metric=="balance"&&balance==null->"Baseline or complete history needed";else->"${if(stale)"Older data · "else ""}$latest GMT${if(latest==utcToday())" · provisional"else ""}${if(metric!="balance"&&seven.size<7)" · partial history"else ""}"})
            // System appearance uses day/night resources so the launcher can re-inflate on a theme change.
            if(prefs.appearance!=Appearance.SYSTEM){
                views.setInt(R.id.widget_root,"setBackgroundResource",if(dark)R.drawable.widget_background_dark else R.drawable.widget_background_light)
                for(view in listOf(R.id.widget_name,R.id.widget_number))views.setTextColor(view,Color.parseColor(if(dark)"#F0EDF7"else"#30283C"))
                for(view in listOf(R.id.widget_label,R.id.widget_date))views.setTextColor(view,Color.parseColor(if(dark)"#BCB7CE"else"#6C6178"))
            }
            val intent=Intent(context,MainActivity::class.java).putExtra("app_id",gameId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            AppWidgetManager.getInstance(context).updateAppWidget(id,views)
        }
    }
}
