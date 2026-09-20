package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CrossoutNewsItem(
    val title: String,
    val url: String,
    val imageUrl: String?,
    val tag: String?,
    val date: String?,
    val excerpt: String?
) {
    fun toJson() = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("imageUrl", imageUrl ?: JSONObject.NULL)
        put("tag", tag ?: JSONObject.NULL)
        put("date", date ?: JSONObject.NULL)
        put("excerpt", excerpt ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject) = CrossoutNewsItem(
            title = json.getString("title"),
            url = json.getString("url"),
            imageUrl = if (json.isNull("imageUrl")) null else json.getString("imageUrl"),
            tag = if (json.isNull("tag")) null else json.getString("tag"),
            date = if (json.isNull("date")) null else json.getString("date"),
            excerpt = if (json.isNull("excerpt")) null else json.getString("excerpt")
        )
    }
}

object CrossoutNewsService {
    private const val URL = "https://crossout.net/ru/news"
    private const val PREFS_NAME = "decoy_news_cache"
    private const val KEY_CACHE = "news_json"
    private const val KEY_TIME = "cache_time"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 минут

    suspend fun loadCached(context: Context): List<CrossoutNewsItem> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHE, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            List(arr.length()) { CrossoutNewsItem.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun isCacheFresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_TIME, 0L)
        (System.currentTimeMillis() - time) < CACHE_TTL_MS
    }

    suspend fun fetchFresh(context: Context): List<CrossoutNewsItem>? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0 Safari/537.36")

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val items = extractItems(html)

            if (items.isNotEmpty()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val arr = JSONArray()
                items.forEach { arr.put(it.toJson()) }
                prefs.edit()
                    .putString(KEY_CACHE, arr.toString())
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .apply()
                items
            } else null
        } catch (e: Exception) {
            Log.e("DecoyNews", "Fetch error: ${e.message}")
            null
        }
    }

    private fun extractItems(html: String): List<CrossoutNewsItem> {
        val result = mutableListOf<CrossoutNewsItem>()
        val seen = mutableSetOf<String>()

        // Регулярка для поиска блоков новостей
        val blockRegex = Regex("<a[^>]*href=\"(/ru/news/\\d+[^\"]*)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<h[23][^>]*>(.*?)</h[23]>", RegexOption.DOT_MATCHES_ALL)
        val imgRegex = Regex("<img[^>]*src=\"([^\"]+)\"")
        val pRegex = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val dateRegex = Regex("(\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+\\d{4})")

        blockRegex.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            val url = if (href.startsWith("http")) href else "https://crossout.net$href"
            if (!seen.add(url)) return@forEach

            val innerHtml = match.groupValues[2]

            val rawTitle = titleRegex.find(innerHtml)?.groupValues?.get(1) ?: return@forEach
            val title = rawTitle.replace(Regex("<[^>]*>"), "").trim()
            if (title.isEmpty()) return@forEach

            val imageUrl = imgRegex.find(innerHtml)?.groupValues?.get(1)

            val fullText = innerHtml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            val date = dateRegex.find(fullText)?.groupValues?.get(1)

            var tag: String? = null
            if (date != null) {
                val beforeDate = fullText.substringBefore(date).trim()
                if (beforeDate.isNotEmpty() && beforeDate.length < 30) tag = beforeDate
            }

            val rawExcerpt = pRegex.find(innerHtml)?.groupValues?.get(1)
            val excerpt = rawExcerpt?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotEmpty() && it != title }

            result.add(CrossoutNewsItem(title, url, imageUrl, tag, date, excerpt))
        }
        return result
    }
}