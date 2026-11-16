package dev.wishlist.counter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class SteamFailure(message: String, val retryable: Boolean = false) : IOException(message)

data class WishlistResponse(
    val report: DailyReport?,
    val minimumDate: String?,
    val countries: List<BreakdownEntry> = emptyList(),
    val languages: List<BreakdownEntry> = emptyList(),
)

data class ChangedDatesResponse(
    val dates: List<String>,
    val highwatermark: Long,
)

/** Public metadata and private financial reporting have separate hosts and credentials. */
class SteamClient {
    private fun request(url: String, financialKey: String? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        if (financialKey != null) {
            check(URL(url).host == "partner.steam-api.com")
            connection.setRequestProperty("x-webapi-key", financialKey)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw SteamFailure(
                    when (code) {
                        401, 403 -> "Steam rejected access. Check the Financial API key and AppID permissions."
                        429 -> "Steam is rate limiting requests. Try again later."
                        else -> "Steam returned HTTP $code. Try again later."
                    },
                    code == 429 || code >= 500,
                )
            }
            val body = connection.inputStream.bufferedReader().use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    output.append(buffer, 0, n)
                    if (output.length > 4_000_000) throw SteamFailure("Steam returned an unexpectedly large response.")
                }
                output.toString()
            }
            return try {
                JSONObject(body)
            } catch (_: Exception) {
                throw SteamFailure("Steam returned an unreadable response.")
            }
        } catch (e: SteamFailure) {
            throw e
        } catch (_: IOException) {
            throw SteamFailure("Could not reach Steam. Cached data is still available.", true)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun metadata(id: Long): Game? = withContext(Dispatchers.IO) {
        val result = request("https://store.steampowered.com/api/appdetails?appids=$id&l=english").optJSONObject(id.toString()) ?: return@withContext null
        if (!result.optBoolean("success")) return@withContext null
        val data = result.optJSONObject("data") ?: return@withContext null
        if (data.optLong("steam_appid") != id) return@withContext null
        Game(
            id = id,
            name = data.optString("name").ifBlank { "App $id" },
            description = data.optString("short_description"),
            header = safeImage(data.optString("header_image")),
            website = safeLink(data.optString("website")),
        )
    }

    suspend fun wishlist(id: Long, date: LocalDate, key: String): WishlistResponse = withContext(Dispatchers.IO) {
        val response = request(
            "https://partner.steam-api.com/IPartnerFinancialsService/GetAppWishlistReporting/v001/?appid=$id&date=$date",
            key,
        ).optJSONObject("response") ?: throw SteamFailure("Steam returned no reporting response. Check your key permissions.")
        val minimum = response.optString("app_min_date").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it.replace('/', '-')).toString() }.getOrNull()
        }
        val summary = response.optJSONObject("wishlist_summary") ?: return@withContext WishlistResponse(null, minimum)
        val reportedDate = response.optString("date").replace('/', '-')
        if (response.optLong("appid") != id || reportedDate != date.toString()) {
            throw SteamFailure("Steam returned a report for a different game or date.")
        }
        fun count(obj: JSONObject, name: String, required: Boolean = true): Long = try {
            if (!required && !obj.has(name)) 0L
            else obj.getLong(name).also { if (it < 0) throw IllegalArgumentException() }
        } catch (_: Exception) {
            if (required) throw SteamFailure("Steam returned incomplete wishlist counts.") else 0L
        }
        val report = DailyReport(
            appId = id,
            date = date.toString(),
            adds = count(summary, "wishlist_adds"),
            deletes = count(summary, "wishlist_deletes"),
            purchases = count(summary, "wishlist_purchases"),
            gifts = count(summary, "wishlist_gifts"),
            windowsAdds = count(summary, "wishlist_adds_windows", required = false),
            macAdds = count(summary, "wishlist_adds_mac", required = false),
            linuxAdds = count(summary, "wishlist_adds_linux", required = false),
        )
        val countries = parseBreakdowns(response.optJSONArray("country_summary"), codeKey = "country_code", labelKey = "country_name")
        val languages = parseBreakdowns(response.optJSONArray("language_summary"), codeKey = "language_name", labelKey = "language_name")
        WishlistResponse(report, minimum, countries, languages)
    }

    private fun parseBreakdowns(array: JSONArray?, codeKey: String, labelKey: String): List<BreakdownEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val code = item.optString(codeKey).trim()
                if (code.isBlank()) continue
                val label = item.optString(labelKey).trim().ifBlank { code }
                val actions = item.optJSONObject("summary_actions") ?: item
                val adds = actions.optLong("wishlist_adds", 0L).coerceAtLeast(0L)
                val deletes = actions.optLong("wishlist_deletes", 0L).coerceAtLeast(0L)
                val purchases = actions.optLong("wishlist_purchases", 0L).coerceAtLeast(0L)
                val gifts = actions.optLong("wishlist_gifts", 0L).coerceAtLeast(0L)
                val net = adds - deletes - purchases - gifts
                if (adds > 0 || net != 0L) {
                    add(BreakdownEntry(code = code, label = label, adds = adds, net = net))
                }
            }
        }
    }

    suspend fun changedSalesDates(highwatermark: Long, key: String): ChangedDatesResponse = withContext(Dispatchers.IO) {
        val response = request(
            "https://partner.steam-api.com/IPartnerFinancialsService/GetChangedDatesForPartner/v001/?highwatermark=$highwatermark",
            key,
        ).optJSONObject("response") ?: return@withContext ChangedDatesResponse(emptyList(), highwatermark)
        val datesArray = response.optJSONArray("dates")
        val dates = buildList {
            if (datesArray != null) {
                for (i in 0 until datesArray.length()) {
                    val normalized = datesArray.optString(i).replace('/', '-').trim()
                    if (runCatching { LocalDate.parse(normalized) }.isSuccess) add(normalized)
                }
            }
        }
        val nextHwm = response.optString("result_highwatermark").toLongOrNull() ?: response.optLong("result_highwatermark", highwatermark)
        ChangedDatesResponse(dates, nextHwm)
    }

    suspend fun detailedSalesForDate(date: String, monitoredAppIds: Set<Long>, key: String): List<SalesReport> = withContext(Dispatchers.IO) {
        if (monitoredAppIds.isEmpty()) return@withContext emptyList()
        data class MutableAgg(
            var grossUnits: Long = 0,
            var returnedUnits: Long = 0,
            var netUnits: Long = 0,
            var activatedKeys: Long = 0,
            var grossMicros: Long = 0,
            var returnedMicros: Long = 0,
            var netMicros: Long = 0,
        )
        val byApp = mutableMapOf<Long, MutableAgg>()
        var highwatermarkId = 0L
        var pages = 0
        while (pages < 20) {
            pages++
            val response = request(
                "https://partner.steam-api.com/IPartnerFinancialsService/GetDetailedSales/v001/?date=$date&highwatermark_id=$highwatermarkId",
                key,
            ).optJSONObject("response") ?: break
            val results = response.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val row = results.optJSONObject(i) ?: continue
                    val primaryAppId = row.optLong("primary_appid", 0L)
                    val directAppId = row.optLong("appid", 0L)
                    val appId = when {
                        primaryAppId in monitoredAppIds -> primaryAppId
                        directAppId in monitoredAppIds -> directAppId
                        else -> continue
                    }
                    val agg = byApp.getOrPut(appId) { MutableAgg() }
                    val grossU = row.optLong("gross_units_sold", 0L).coerceAtLeast(0L)
                    val retU = abs(row.optLong("gross_units_returned", 0L))
                    val netU = if (row.has("net_units_sold")) row.optLong("net_units_sold", 0L) else (grossU - retU)
                    val actKeys = row.optLong("gross_units_activated", 0L).coerceAtLeast(0L)
                    agg.grossUnits += grossU
                    agg.returnedUnits += retU
                    agg.netUnits += netU
                    agg.activatedKeys += actKeys
                    agg.grossMicros += parseUsdMicros(row.optString("gross_sales_usd"))
                    agg.returnedMicros += abs(parseUsdMicros(row.optString("gross_returns_usd")))
                    agg.netMicros += parseUsdMicros(row.optString("net_sales_usd"))
                }
            }
            val maxId = response.optString("max_id").toLongOrNull() ?: response.optLong("max_id", highwatermarkId)
            if (maxId <= highwatermarkId) break
            highwatermarkId = maxId
        }
        monitoredAppIds.mapNotNull { appId ->
            val agg = byApp[appId] ?: return@mapNotNull null
            SalesReport(
                appId = appId,
                date = date,
                grossUnits = agg.grossUnits,
                returnedUnits = agg.returnedUnits,
                netUnits = agg.netUnits,
                activatedKeys = agg.activatedKeys,
                grossMicrosUsd = agg.grossMicros,
                returnedMicrosUsd = agg.returnedMicros,
                netMicrosUsd = agg.netMicros,
            )
        }
    }

    suspend fun communitySnapshot(id: Long): CommunitySnapshot = withContext(Dispatchers.IO) {
        val players = runCatching {
            val resp = request("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$id").optJSONObject("response")
            if (resp != null && resp.optInt("result") == 1) resp.optLong("player_count", 0L) else null
        }.getOrNull()

        val steamSummary = runCatching {
            request("https://store.steampowered.com/appreviews/$id?json=1&language=all&purchase_type=steam&num_per_page=0")
                .optJSONObject("query_summary")
        }.getOrNull()

        val allReviewsJson = runCatching {
            request("https://store.steampowered.com/appreviews/$id?json=1&language=all&purchase_type=all&filter=recent&num_per_page=10")
        }.getOrNull()
        val allSummary = allReviewsJson?.optJSONObject("query_summary")

        val reviewsList = buildList {
            val arr = allReviewsJson?.optJSONArray("reviews")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    val author = r.optJSONObject("author")
                    val playtimeMinutes = author?.optLong("playtime_at_review", 0L)?.takeIf { it > 0 }
                        ?: author?.optLong("playtime_forever", 0L) ?: 0L
                    val text = r.optString("review").trim()
                    if (text.isNotEmpty()) {
                        add(
                            ReviewItem(
                                id = r.optString("recommendationid", i.toString()),
                                votedUp = r.optBoolean("voted_up", true),
                                steamPurchase = r.optBoolean("steam_purchase", true),
                                playtimeHours = (playtimeMinutes / 6.0).toLong() / 10.0,
                                timestamp = r.optLong("timestamp_created", 0L),
                                language = r.optString("language", "english"),
                                text = text.take(400),
                            )
                        )
                    }
                }
            }
        }

        val storeDetails = runCatching {
            request("https://store.steampowered.com/api/appdetails?appids=$id&l=english")
                .optJSONObject(id.toString())
                ?.optJSONObject("data")
        }.getOrNull()
        val priceOverview = storeDetails?.optJSONObject("price_overview")
        val priceFormatted = priceOverview?.optString("final_formatted").orEmpty()
        val discountPercent = priceOverview?.optInt("discount_percent", 0) ?: 0
        val releaseDate = storeDetails?.optJSONObject("release_date")?.optString("date").orEmpty()

        val achievements = runCatching {
            val arr = request("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=$id")
                .optJSONObject("achievementpercentages")
                ?.optJSONArray("achievements")
            buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val name = a.optString("name").trim()
                        val pct = a.optDouble("percent", 0.0)
                        if (name.isNotEmpty() && pct.isFinite()) {
                            add(AchievementStat(name = name, percent = pct.coerceIn(0.0, 100.0)))
                        }
                    }
                }
            }.sortedByDescending { it.percent }
        }.getOrDefault(emptyList())

        val steamPos = steamSummary?.optLong("total_positive", 0L) ?: 0L
        val steamNeg = steamSummary?.optLong("total_negative", 0L) ?: 0L
        val allPos = (allSummary?.optLong("total_positive", 0L) ?: 0L).coerceAtLeast(steamPos)
        val allNeg = (allSummary?.optLong("total_negative", 0L) ?: 0L).coerceAtLeast(steamNeg)
        val scoreDesc = steamSummary?.optString("review_score_desc")?.takeIf { it.isNotBlank() }
            ?: allSummary?.optString("review_score_desc").orEmpty()

        CommunitySnapshot(
            appId = id,
            currentPlayers = players,
            scoreDesc = scoreDesc,
            steamPositive = steamPos,
            steamNegative = steamNeg,
            allPositive = allPos,
            allNegative = allNeg,
            priceFormatted = priceFormatted,
            discountPercent = discountPercent,
            releaseDate = releaseDate,
            reviews = reviewsList,
            achievements = achievements.take(12),
            updatedAt = Instant.now().toString(),
        )
    }

    companion object {
        fun safeLink(value: String): String = runCatching {
            val uri = java.net.URI(value)
            if (uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) value else ""
        }.getOrDefault("")

        fun safeImage(value: String): String = runCatching {
            val url = URL(value)
            if (url.protocol == "https" && (url.host.endsWith(".steamstatic.com") || url.host.endsWith(".steamcontent.com"))) value else ""
        }.getOrDefault("")
    }
}
