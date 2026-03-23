package by.iposdev.visorlink.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.URL

suspend fun saveVoiceToDownloads(context: Context, url: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val fileName = "visorlink_voice_${System.currentTimeMillis()}.webm"
            val connection = URL(url).openConnection().also { it.connect() }
            val inputStream = BufferedInputStream(connection.getInputStream())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/webm")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VisorLink")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    inputStream.copyTo(out)
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(dir, fileName)
                file.outputStream().use { out -> inputStream.copyTo(out) }
            }
            inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }