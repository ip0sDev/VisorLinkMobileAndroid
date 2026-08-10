package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ImageCache"

/**
 * Custom image cache for robust offline support.
 */
object ImageCache {

    fun getCachedPath(context: Context, url: String): File? {
        val file = cacheFile(context, url)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun getOrDownload(context: Context, url: String): File =
        withContext(Dispatchers.IO) {
            val file = cacheFile(context, url)
            if (file.exists() && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis())
                return@withContext file
            }

            Log.d(TAG, "Downloading image: $url")
            val dir = file.parentFile!!
            if (!dir.exists()) dir.mkdirs()

            val tmp = File(dir, "${file.name}.tmp")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout    = 30_000
                conn.connect()

                if (conn.responseCode == 200) {
                    FileOutputStream(tmp).use { out ->
                        conn.inputStream.use { it.copyTo(out) }
                    }
                    tmp.renameTo(file)
                    Log.d(TAG, "Downloaded: ${file.name}")
                    file
                } else {
                    throw Exception("HTTP ${conn.responseCode}")
                }
            } catch (e: Exception) {
                tmp.delete()
                Log.e(TAG, "Download failed for $url", e)
                throw e
            }
        }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, CACHE_DIR_CUSTOM_IMAGES)
        val hash = url.hashCode().toString()
        val ext = url.substringAfterLast('.', "jpg").take(5).filter { it.isLetterOrDigit() }
        return File(dir, "img_$hash.$ext")
    }
}
