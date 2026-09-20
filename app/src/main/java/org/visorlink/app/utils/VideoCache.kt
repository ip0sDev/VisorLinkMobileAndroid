package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

private const val TAG = "VideoCache"

object VideoCache {

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "video_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hashUrl(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun cacheFile(context: Context, url: String): File {
        val hash = hashUrl(url)
        return File(getCacheDir(context), "video_$hash.mp4")
    }

    fun getCachedPath(context: Context, url: String): File? {
        if (url.startsWith("/") || url.startsWith("file:")) {
            val local = File(url.removePrefix("file://").removePrefix("file:"))
            if (local.exists() && local.length() > 0) return local
        }
        val file = cacheFile(context, url)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun saveToCache(context: Context, url: String, sourceFile: File) = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0) return@withContext
        try {
            val destFile = cacheFile(context, url)
            if (destFile.exists() && destFile.length() == sourceFile.length()) return@withContext
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Saved video to cache: ${destFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache video locally", e)
        }
    }

    fun extractGoogleDriveFileId(url: String): String? {
        if (url.isBlank()) return null
        return when {
            url.contains("googleusercontent.com/d/") -> {
                url.substringAfter("googleusercontent.com/d/").substringBefore("=").substringBefore("/").substringBefore("?")
            }
            url.contains("drive.google.com/file/d/") -> {
                url.substringAfter("drive.google.com/file/d/").substringBefore("/").substringBefore("?")
            }
            url.contains("id=") -> {
                url.substringAfter("id=").substringBefore("&")
            }
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    fun getStreamableVideoUrl(rawUrl: String, driveFileId: String? = null): String {
        if (rawUrl.isBlank()) return rawUrl
        val fileId = driveFileId?.ifBlank { null } ?: extractGoogleDriveFileId(rawUrl)
        if (fileId != null) {
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }
        return rawUrl
    }

    suspend fun saveVideoToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cached = getCachedPath(context, url)
            val sourceFile = if (cached != null && cached.exists()) {
                cached
            } else {
                val dest = cacheFile(context, url)
                val streamUrl = getStreamableVideoUrl(url)
                val conn = java.net.URL(streamUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                if (conn.responseCode in 200..299) {
                    FileOutputStream(dest).use { out -> conn.inputStream.use { it.copyTo(out) } }
                    dest
                } else null
            } ?: return@withContext false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "video_${System.currentTimeMillis()}.mp4")
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/VisorLink")
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { it.copyTo(out) }
                }
                values.clear()
                values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val moviesDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "VisorLink")
                if (!moviesDir.exists()) moviesDir.mkdirs()
                val target = File(moviesDir, "video_${System.currentTimeMillis()}.mp4")
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(target).use { input.copyTo(it) }
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video to gallery", e)
            false
        }
    }
}
