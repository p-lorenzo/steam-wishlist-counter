package dev.wishlist.counter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) : SQLiteOpenHelper(context, "wishlist.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL,header TEXT NOT NULL,website TEXT NOT NULL,baseline INTEGER,baseline_date TEXT)")
        db.execSQL("CREATE TABLE reports(app_id INTEGER NOT NULL,date TEXT NOT NULL,adds INTEGER NOT NULL,deletes INTEGER NOT NULL,purchases INTEGER NOT NULL,gifts INTEGER NOT NULL,win_adds INTEGER NOT NULL DEFAULT 0,mac_adds INTEGER NOT NULL DEFAULT 0,linux_adds INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(app_id,date))")
        db.execSQL("CREATE TABLE seen(app_id INTEGER NOT NULL,date TEXT NOT NULL,high INTEGER NOT NULL,PRIMARY KEY(app_id,date))")
        db.execSQL("CREATE TABLE sync_state(app_id INTEGER PRIMARY KEY,initialized INTEGER NOT NULL DEFAULT 0,pending INTEGER NOT NULL DEFAULT 0,min_date TEXT,last_sync TEXT)")
        createV2Tables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            for (col in listOf("win_adds", "mac_adds", "linux_adds")) {
                runCatching { db.execSQL("ALTER TABLE reports ADD COLUMN $col INTEGER NOT NULL DEFAULT 0") }
            }
            createV2Tables(db)
        }
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wishlist_breakdowns(app_id INTEGER NOT NULL,date TEXT NOT NULL,kind TEXT NOT NULL,code TEXT NOT NULL,label TEXT NOT NULL,adds INTEGER NOT NULL,net INTEGER NOT NULL,PRIMARY KEY(app_id,date,kind,code))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sales_reports(app_id INTEGER NOT NULL,date TEXT NOT NULL,gross_units INTEGER NOT NULL,returned_units INTEGER NOT NULL,net_units INTEGER NOT NULL,activated_keys INTEGER NOT NULL,gross_micros_usd INTEGER NOT NULL,returned_micros_usd INTEGER NOT NULL,net_micros_usd INTEGER NOT NULL,PRIMARY KEY(app_id,date))")
        db.execSQL("CREATE TABLE IF NOT EXISTS community_cache(app_id INTEGER PRIMARY KEY,json TEXT NOT NULL)")
    }

    @Synchronized fun games(): List<Game> = readableDatabase.rawQuery("SELECT * FROM games ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(Game(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), if (c.isNull(5)) null else c.getLong(5), c.getString(6))) }
    }

    @Synchronized fun saveGame(game: Game) {
        writableDatabase.insertWithOnConflict("games", null, ContentValues().apply {
            put("id", game.id); put("name", game.name); put("description", game.description); put("header", game.header); put("website", game.website)
            if (game.baseline == null) putNull("baseline") else put("baseline", game.baseline)
            put("baseline_date", game.baselineDate)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun reports(id: Long): List<DailyReport> = readableDatabase.rawQuery(
        "SELECT app_id,date,adds,deletes,purchases,gifts,win_adds,mac_adds,linux_adds FROM reports WHERE app_id=? ORDER BY date",
        arrayOf(id.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(DailyReport(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6), c.getLong(7), c.getLong(8)))
            }
        }
    }

    @Synchronized fun hasBreakdownsForDate(id: Long, date: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM wishlist_breakdowns WHERE app_id=? AND date=? LIMIT 1",
        arrayOf(id.toString(), date),
    ).use { it.moveToFirst() }

    @Synchronized fun saveBreakdowns(appId: Long, date: String, kind: String, entries: List<BreakdownEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("wishlist_breakdowns", "app_id=? AND date=? AND kind=?", arrayOf(appId.toString(), date, kind))
            for (entry in entries) {
                db.insertWithOnConflict("wishlist_breakdowns", null, ContentValues().apply {
                    put("app_id", appId); put("date", date); put("kind", kind)
                    put("code", entry.code); put("label", entry.label)
                    put("adds", entry.adds); put("net", entry.net)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized fun breakdowns(appId: Long, kind: String, start: String, end: String): List<BreakdownEntry> = readableDatabase.rawQuery(
        "SELECT code,MAX(label),SUM(adds),SUM(net) FROM wishlist_breakdowns WHERE app_id=? AND kind=? AND date>=? AND date<=? GROUP BY code ORDER BY SUM(adds) DESC, SUM(net) DESC",
        arrayOf(appId.toString(), kind, start, end),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(BreakdownEntry(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3)))
            }
        }
    }

    @Synchronized fun saveSalesReport(report: SalesReport) {
        writableDatabase.insertWithOnConflict("sales_reports", null, ContentValues().apply {
            put("app_id", report.appId); put("date", report.date)
            put("gross_units", report.grossUnits); put("returned_units", report.returnedUnits)
            put("net_units", report.netUnits); put("activated_keys", report.activatedKeys)
            put("gross_micros_usd", report.grossMicrosUsd); put("returned_micros_usd", report.returnedMicrosUsd)
            put("net_micros_usd", report.netMicrosUsd)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun salesReports(id: Long): List<SalesReport> = readableDatabase.rawQuery(
        "SELECT app_id,date,gross_units,returned_units,net_units,activated_keys,gross_micros_usd,returned_micros_usd,net_micros_usd FROM sales_reports WHERE app_id=? ORDER BY date",
        arrayOf(id.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(SalesReport(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6), c.getLong(7), c.getLong(8)))
            }
        }
    }

    @Synchronized fun saveCommunity(snapshot: CommunitySnapshot) {
        val json = JSONObject().apply {
            put("appId", snapshot.appId)
            if (snapshot.currentPlayers != null) put("currentPlayers", snapshot.currentPlayers)
            put("scoreDesc", snapshot.scoreDesc)
            put("steamPositive", snapshot.steamPositive)
            put("steamNegative", snapshot.steamNegative)
            put("allPositive", snapshot.allPositive)
            put("allNegative", snapshot.allNegative)
            put("priceFormatted", snapshot.priceFormatted)
            put("discountPercent", snapshot.discountPercent)
            put("releaseDate", snapshot.releaseDate)
            put("updatedAt", snapshot.updatedAt)
            put("reviews", JSONArray().apply {
                snapshot.reviews.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id); put("votedUp", r.votedUp); put("steamPurchase", r.steamPurchase)
                        put("playtimeHours", r.playtimeHours); put("timestamp", r.timestamp)
                        put("language", r.language); put("text", r.text)
                    })
                }
            })
            put("achievements", JSONArray().apply {
                snapshot.achievements.forEach { a ->
                    put(JSONObject().apply { put("name", a.name); put("percent", a.percent) })
                }
            })
        }
        writableDatabase.insertWithOnConflict("community_cache", null, ContentValues().apply {
            put("app_id", snapshot.appId)
            put("json", json.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun community(id: Long): CommunitySnapshot? = readableDatabase.rawQuery(
        "SELECT json FROM community_cache WHERE app_id=?",
        arrayOf(id.toString()),
    ).use { c ->
        if (!c.moveToFirst()) return null
        return runCatching {
            val obj = JSONObject(c.getString(0))
            val reviewsArr = obj.optJSONArray("reviews")
            val reviews = buildList {
                if (reviewsArr != null) {
                    for (i in 0 until reviewsArr.length()) {
                        val r = reviewsArr.optJSONObject(i) ?: continue
                        add(ReviewItem(
                            id = r.optString("id"),
                            votedUp = r.optBoolean("votedUp", true),
                            steamPurchase = r.optBoolean("steamPurchase", true),
                            playtimeHours = r.optDouble("playtimeHours", 0.0),
                            timestamp = r.optLong("timestamp", 0L),
                            language = r.optString("language", "english"),
                            text = r.optString("text"),
                        ))
                    }
                }
            }
            val achArr = obj.optJSONArray("achievements")
            val achievements = buildList {
                if (achArr != null) {
                    for (i in 0 until achArr.length()) {
                        val a = achArr.optJSONObject(i) ?: continue
                        add(AchievementStat(a.optString("name"), a.optDouble("percent", 0.0)))
                    }
                }
            }
            CommunitySnapshot(
                appId = id,
                currentPlayers = if (obj.has("currentPlayers")) obj.optLong("currentPlayers") else null,
                scoreDesc = obj.optString("scoreDesc"),
                steamPositive = obj.optLong("steamPositive", 0L),
                steamNegative = obj.optLong("steamNegative", 0L),
                allPositive = obj.optLong("allPositive", 0L),
                allNegative = obj.optLong("allNegative", 0L),
                priceFormatted = obj.optString("priceFormatted"),
                discountPercent = obj.optInt("discountPercent", 0),
                releaseDate = obj.optString("releaseDate"),
                reviews = reviews,
                achievements = achievements,
                updatedAt = obj.optString("updatedAt"),
            )
        }.getOrNull()
    }

    @Synchronized fun initialized(id: Long): Boolean = readableDatabase.rawQuery("SELECT initialized FROM sync_state WHERE app_id=?", arrayOf(id.toString())).use { it.moveToFirst() && it.getInt(0) == 1 }
    @Synchronized fun minDate(id: Long): String? = readableDatabase.rawQuery("SELECT min_date FROM sync_state WHERE app_id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun lastSync(id: Long): String? = readableDatabase.rawQuery("SELECT last_sync FROM sync_state WHERE app_id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun setMinDate(id: Long, date: String) { ensureState(id); writableDatabase.execSQL("UPDATE sync_state SET min_date=? WHERE app_id=?", arrayOf<Any>(date, id)) }
    private fun ensureState(id: Long) { writableDatabase.execSQL("INSERT OR IGNORE INTO sync_state(app_id) VALUES(?)", arrayOf(id)) }

    /** Report, notification watermark, and pending additions commit atomically. */
    @Synchronized fun saveReport(report: DailyReport, allowAlert: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensureState(report.appId)
            val oldHigh = db.rawQuery("SELECT high FROM seen WHERE app_id=? AND date=?", arrayOf(report.appId.toString(), report.date)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            db.insertWithOnConflict("reports", null, ContentValues().apply {
                put("app_id", report.appId); put("date", report.date)
                put("adds", report.adds); put("deletes", report.deletes)
                put("purchases", report.purchases); put("gifts", report.gifts)
                put("win_adds", report.windowsAdds); put("mac_adds", report.macAdds); put("linux_adds", report.linuxAdds)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL("INSERT OR REPLACE INTO seen(app_id,date,high) VALUES(?,?,?)", arrayOf<Any>(report.appId, report.date, maxOf(oldHigh, report.adds)))
            if (allowAlert) db.execSQL("UPDATE sync_state SET pending=pending+? WHERE app_id=?", arrayOf(unseenAdditions(oldHigh, report.adds), report.appId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized fun finishSync(id: Long, initialize: Boolean) {
        ensureState(id)
        writableDatabase.execSQL("UPDATE sync_state SET initialized=MAX(initialized,?),last_sync=? WHERE app_id=?", arrayOf<Any>(if (initialize) 1 else 0, java.time.Instant.now().toString(), id))
    }

    @Synchronized fun pending(id: Long): Long = readableDatabase.rawQuery("SELECT pending FROM sync_state WHERE app_id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    @Synchronized fun consumePending(id: Long, amount: Long) { writableDatabase.execSQL("UPDATE sync_state SET pending=MAX(0,pending-?) WHERE app_id=?", arrayOf(amount, id)) }
    @Synchronized fun removeGame(id: Long) {
        val db = writableDatabase; db.beginTransaction()
        try {
            db.delete("games", "id=?", arrayOf(id.toString()))
            for (table in listOf("reports", "seen", "sync_state", "wishlist_breakdowns", "sales_reports", "community_cache")) {
                db.delete(table, "app_id=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized fun clear() {
        val db = writableDatabase; db.beginTransaction()
        try {
            for (table in listOf("games", "reports", "seen", "sync_state", "wishlist_breakdowns", "sales_reports", "community_cache")) {
                db.delete(table, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
