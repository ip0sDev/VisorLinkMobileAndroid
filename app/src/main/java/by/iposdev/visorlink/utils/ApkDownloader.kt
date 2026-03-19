package by.iposdev.visorlink.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

object ApkDownloader {

    fun downloadAndInstall(context: Context, url: String) {
        Toast.makeText(context, "Загрузка обновления...", Toast.LENGTH_SHORT).show()

        val fileName = "VisorLink_Update.apk"
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // Удаляем старый установочный файл, если он остался
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Обновление VisorLink")
            setDescription("Загрузка новой версии...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destination))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // Слушаем завершение загрузки
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (e: Exception) { /* ignored */ }

                    installApk(ctxt, destination)
                }
            }
        }

        // Для Android 13+ флаг RECEIVER_EXPORTED обязателен
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // Получаем безопасный content:// URI через FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка запуска установки", Toast.LENGTH_SHORT).show()
        }
    }
}