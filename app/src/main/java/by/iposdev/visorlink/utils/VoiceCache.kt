package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
        val file = cacheFile(context, url)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun getOrDownload(context: Context, url: String): File =
        withContext(Dispatchers.IO) {
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
                conn.connectTimeout = 10_000
                conn.readTimeout    = 30_000
                conn.connect()

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
        // Используем хэш URL как имя файла + расширение из URL
        val ext = url.substringAfterLast('.', "webm").take(5).filter { it.isLetterOrDigit() }
        return File(dir, "voice_${url.hashCode()}.${ext}")
    }
}