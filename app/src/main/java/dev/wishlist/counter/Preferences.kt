package dev.wishlist.counter

import android.content.Context

class Preferences(context: Context) {
    val raw=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    var appearance: Appearance
        get()=runCatching{Appearance.valueOf(raw.getString("appearance","SYSTEM")!!)}.getOrDefault(Appearance.SYSTEM)
        set(value){raw.edit().putString("appearance",value.name).apply()}
    fun rule(id:Long)=AlertRule(raw.getBoolean("alert-$id",false),raw.getLong("threshold-$id",10),raw.getBoolean("quiet-$id",true),raw.getString("quiet-start-$id","22:00")!!,raw.getString("quiet-end-$id","08:00")!!)
    fun saveRule(id:Long,rule:AlertRule){raw.edit().putBoolean("alert-$id",rule.enabled).putLong("threshold-$id",rule.threshold).putBoolean("quiet-$id",rule.quiet).putString("quiet-start-$id",rule.start).putString("quiet-end-$id",rule.end).apply()}
    fun clear(){raw.edit().clear().commit()}
}
