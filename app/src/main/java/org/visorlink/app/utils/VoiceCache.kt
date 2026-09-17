package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "VoiceCache"

/**
 * Скачивает голосовое сообщение в постоянный кэш (cacheDir/voice_cache/)
 * и возвращает путь к локальному файлу.
 * Если файл уже скачан — возвращает кэшированный.
 */
object VoiceCache {

    fun getCachedPath(context: Context, url: String): File? {
        if (url.startsWith("/") || url.startsWith("file:")) {
            val local = File(url.removePrefix("file://").removePrefix("file:"))
            if (local.exists() && local.length() > 0) return local
        }
        val file = cacheFile(context, url)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun getOrDownload(context: Context, url: String): File =
        withContext(Dispatchers.IO) {
            if (url.startsWith("/") || url.startsWith("file:")) {
                val local = File(url.removePrefix("file://").removePrefix("file:"))
                if (local.exists() && local.length() > 0) return@withContext local
            }

            val file = cacheFile(context, url)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Cache hit: ${file.name}")
                file.setLastModified(System.currentTimeMillis()) // LRU touch
                return@withContext file
            }

            Log.d(TAG, "Downloading voice: $url")
            val dir = file.parentFile!!
            if (!dir.exists()) dir.mkdirs()

            val tmp = File(dir, "${file.name}.tmp")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout    = 30_000
                conn.instanceFollowRedirects = true
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    throw IOException("HTTP error ${conn.responseCode}: ${conn.responseMessage}")
                }

                FileOutputStream(tmp).use { out ->
                    conn.inputStream.use { it.copyTo(out) }
                }
                conn.disconnect()

                tmp.renameTo(file)
                Log.d(TAG, "Downloaded: ${file.name} (${file.length() / 1024}KB)")
                file
            } catch (e: Exception) {
                tmp.delete()
                Log.e(TAG, "Download failed for $url", e)
                throw e
            }
        }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, CACHE_DIR_VOICE)
        // Отсекаем query-параметры (?token=...), чтобы смена токена не приводила к промаху мимо кэша
        val cleanUrl = url.substringBefore('?')
        val idOrHash = cleanUrl.substringAfter("/f/", "").substringAfter("/p/", "").takeIf { it.isNotEmpty() && !it.contains('/') }
            ?: cleanUrl.hashCode().toString()
        val ext = cleanUrl.substringAfterLast('.', "webm").take(5).filter { it.isLetterOrDigit() }.ifEmpty { "webm" }
        return File(dir, "voice_${idOrHash}.${ext}")
    }
}