package by.iposdev.visorlink.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import by.iposdev.visorlink.MainActivity
import by.iposdev.visorlink.R

object NotificationHelper {

    private const val CHANNEL_MESSAGES    = "messages"
    private const val CHANNEL_MESSAGES_NAME = "Messages"
    private const val TAG = "NotificationHelper"

    fun createChannels(context: Context) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            CHANNEL_MESSAGES_NAME,
            // IMPORTANCE_HIGH — единственный уровень, который даёт heads-up (всплывающий баннер)
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description          = "New message notifications"
            enableVibration(true)
            vibrationPattern     = longArrayOf(0, 150, 80, 150)
            enableLights(true)
            setSound(soundUri, audioAttrs)
            // Блокируем режим «не беспокоить» только для этого канала
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Если канал уже существует с другими настройками — удалить и пересоздать.
        // (Изменить importance существующего канала нельзя без удаления.)
        val existing = manager.getNotificationChannel(CHANNEL_MESSAGES)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
            manager.deleteNotificationChannel(CHANNEL_MESSAGES)
            Log.d(TAG, "Old low-priority channel removed")
        }

        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: importance=${channel.importance}")
    }

    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messagePreview: String,
        notificationId: Int = chatId.hashCode()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChatId", chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            // Замени R.drawable.ic_notification на свою иконку из drawable
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messagePreview))
            // MAX на уровне NotificationCompat (совместимость со старыми API < 26)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 150, 80, 150))
            .setSound(soundUri)
            // Heads-up (всплывающий баннер) — требует IMPORTANCE_HIGH на канале
            .setFullScreenIntent(pendingIntent, /* highPriority = */ true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        Log.d(TAG, "Notification shown for chat $chatId from $senderName")
    }
}