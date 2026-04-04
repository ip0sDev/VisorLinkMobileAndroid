package by.iposdev.visorlink.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

object ApkDownloader {

    /**
     * @param fileName   имя сохраняемого файла (например app-beta.apk)
     * @param onProgress 0f..1f прогресс загрузки, -1f = ошибка
     * @param onComplete вызывается когда файл скачан и запускается установщик
     */
    fun downloadAndInstall(
        context: Context,
        url: String,
        fileName: String = "VisorLink_Update.apk",
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val destination = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("VisorLink Update")
            setDescription("Downloading new version...")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationUri(Uri.fromFile(destination))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // ── Опрос прогресса каждые 300 мс ───────────────────────────────
        val handler = Handler(Looper.getMainLooper())
        val pollRunnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING -> {
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            onProgress(progress)
                            handler.postDelayed(this, 300)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            onProgress(-1f)
                        }
                        // SUCCESS / PAUSED — ничего, ждём BroadcastReceiver
                        else -> {}
                    }
                } else {
                    cursor?.close()
                }
            }
        }
        handler.postDelayed(pollRunnable, 300)

        // ── BroadcastReceiver — завершение ────────────────────────────────
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                handler.removeCallbacks(pollRunnable)
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                var success = false
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    success = status == DownloadManager.STATUS_SUCCESSFUL
                    cursor.close()
                }

                if (success) {
                    onProgress(1f)
                    onComplete()
                    installApk(ctx, destination)
                } else {
                    onProgress(-1f)
                    Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
            Toast.makeText(context, "Failed to launch installer", Toast.LENGTH_SHORT).show()
        }
    }
}