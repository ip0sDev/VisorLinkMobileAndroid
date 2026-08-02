package by.iposdev.visorlink.utils

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

object ApkDownloader {

    private const val ACTION_INSTALL_COMPLETE = "by.iposdev.visorlink.INSTALL_COMPLETE"

    /**
     * @param fileName   имя сохраняемого файла (например app-release.apk)
     * @param expectedSha256 Хэш для проверки файла
     * @param onProgress 0f..1f прогресс загрузки и скорость в байтах/сек, -1f = ошибка
     * @param onComplete вызывается когда файл скачан и проверен
     */
    fun downloadAndInstall(
        context: Context,
        url: String,
        fileName: String = "VisorLink_Update.apk",
        expectedSha256: String? = null,
        onProgress: (progress: Float, speedBps: Long) -> Unit = { _, _ -> },
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
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destination))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        var lastDownloaded = 0L
        var lastTime = System.currentTimeMillis()

        val handler = Handler(Looper.getMainLooper())
        val pollRunnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                            val now = System.currentTimeMillis()
                            val dt = now - lastTime
                            val speed = if (dt > 0 && downloaded >= lastDownloaded) {
                                ((downloaded - lastDownloaded) * 1000L) / dt
                            } else 0L

                            lastDownloaded = downloaded
                            lastTime = now

                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            onProgress(progress, speed)
                            handler.postDelayed(this, 300)
                        }
                        DownloadManager.STATUS_FAILED -> onProgress(-1f, 0L)
                        else -> {}
                    }
                } else {
                    cursor?.close()
                }
            }
        }
        handler.postDelayed(pollRunnable, 300)

        // BroadcastReceiver для отслеживания конца загрузки
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
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    success = status == DownloadManager.STATUS_SUCCESSFUL
                    cursor.close()
                }

                if (success) {
                    Thread {
                        val hashValid = verifySha256(destination, expectedSha256)
                        Handler(Looper.getMainLooper()).post {
                            if (hashValid) {
                                onProgress(1f, 0L)
                                onComplete()
                                installApk(ctx, destination)
                            } else {
                                destination.delete()
                                onProgress(-1f, 0L)
                                Toast.makeText(ctx, "Ошибка: Файл поврежден (Checksum mismatch)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                } else {
                    onProgress(-1f, 0L)
                    Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun verifySha256(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            hash.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    // ─── Логика установки ───────────────────────────────────────────────────

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // Для Android 12+ пытаемся обновиться "тихо"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installApkModern(context, apkFile)
        } else {
            // Для Android 11 и ниже используем классический диалог
            installApkLegacy(context, apkFile)
        }
    }

    /**
     * Современный способ (PackageInstaller API).
     * Позволяет обойтись без диалога (USER_ACTION_NOT_REQUIRED), если система это разрешает
     * (обычно со второго обновления, когда приложение уже обновляло само себя).
     */
    private fun installApkModern(context: Context, apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Тот самый флаг для тихого обновления в фоне
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Записываем APK в сессию
            val out = session.openWrite("VisorLink_Update", 0, apkFile.length())
            FileInputStream(apkFile).use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
            out.close()

            // Регистрируем ресивер для обработки статуса установки
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_INSTALL_COMPLETE) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        when (status) {
                            PackageInstaller.STATUS_SUCCESS -> {
                                Log.d("ApkDownloader", "Silent install success. App will restart.")
                                // Приложение будет автоматически убито и перезапущено системой
                            }
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                // Система всё же запросила подтверждение (например, при первой попытке)
                                Log.d("ApkDownloader", "System requires user confirmation dialog")
                                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                }

                                if (confirmationIntent != null) {
                                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(confirmationIntent)
                                }
                            }
                            else -> {
                                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                Log.e("ApkDownloader", "Install failed: $status, $message")
                                Toast.makeText(ctx, "Install failed: $message", Toast.LENGTH_SHORT).show()
                                // Если сломалось, пробуем старый метод
                                installApkLegacy(ctx, apkFile)
                            }
                        }
                        try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_INSTALL_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED // PackageInstaller (система) отправляет нам интент
            )

            // Коммитим сессию
            val intent = Intent(ACTION_INSTALL_COMPLETE).setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

        } catch (e: Exception) {
            Log.e("ApkDownloader", "Modern install failed", e)
            installApkLegacy(context, apkFile)
        }
    }

    /**
     * Классический (старый) способ через вызов интента ACTION_VIEW.
     * Всегда показывает системный диалог "Установить обновление?".
     */
    private fun installApkLegacy(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer", Toast.LENGTH_SHORT).show()
            Log.e("ApkDownloader", "Legacy install failed", e)
        }
    }
}