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
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description          = "New message notifications"
            enableVibration(true)
            vibrationPattern     = longArrayOf(0, 150, 80, 150)
            enableLights(true)
            setSound(soundUri, audioAttrs)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existing = manager.getNotificationChannel(CHANNEL_MESSAGES)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
            manager.deleteNotificationChannel(CHANNEL_MESSAGES)
            Log.d(TAG, "Old low-priority channel removed")
        }

        manager.createNotificationChannel(channel)
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

        // ── Группировка уведомлений (Inbox Style) ──
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val historyKey = "unread_msgs_$chatId"
        val historyStr = prefs.getString(historyKey, "") ?: ""
        val history = if (historyStr.isEmpty()) mutableListOf() else historyStr.split("|||").toMutableList()

        history.add(messagePreview)
        if (history.size > 7) {
            history.removeAt(0)
        }
        prefs.edit().putString(historyKey, history.joinToString("|||")).apply()

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(senderName)

        if (history.size > 1) {
            inboxStyle.setSummaryText("+${history.size} новых")
        }
        history.forEach { inboxStyle.addLine(it) }
        // ──────────────────────────────────────────

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 150, 80, 150))
            .setSound(soundUri)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun clearNotification(context: Context, chatId: String) {
        try {
            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("unread_msgs_$chatId").apply()
            NotificationManagerCompat.from(context).cancel(chatId.hashCode())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear notification", e)
        }
    }
}