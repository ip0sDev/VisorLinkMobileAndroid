package org.visorlink.app.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
            val dir = file.parentFile ?: context.cacheDir
            if (!dir.exists()) dir.mkdirs()

            val tmp = File(dir, "${file.name}.tmp")
            try {
                var currentUrl = url
                var redirectCount = 0
                var conn: HttpURLConnection? = null
                var code = 0

                while (redirectCount < 5) {
                    conn = URL(currentUrl).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 30_000
                    conn.connect()
                    code = conn.responseCode

                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        val location = conn.getHeaderField("Location")
                        if (!location.isNullOrBlank()) {
                            currentUrl = location
                            redirectCount++
                            conn.disconnect()
                            continue
                        }
                    }
                    break
                }

                if (conn != null && code == HttpURLConnection.HTTP_OK) {
                    FileOutputStream(tmp).use { out ->
                        conn.inputStream.use { it.copyTo(out) }
                    }
                    tmp.renameTo(file)
                    Log.d(TAG, "Downloaded: ${file.name}")
                    file
                } else {
                    throw Exception("HTTP $code")
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
        val cleanUrl = url.substringBefore('?')
        val ext = cleanUrl.substringAfterLast('.', "jpg").take(5).filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
        return File(dir, "img_$hash.$ext")
    }

    suspend fun saveImageToGallery(context: Context, url: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val cached = getCachedPath(context, url)
                val model = cached ?: url
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context).data(model).allowHardware(false).build()
                val result = loader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap } ?: return@withContext false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "visorlink_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VisorLink")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, "visorlink_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream) }
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}
