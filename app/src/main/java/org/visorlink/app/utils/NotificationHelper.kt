package org.visorlink.app.utils

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
import org.visorlink.app.MainActivity
import org.visorlink.app.R

object NotificationHelper {

    private const val CHANNEL_MESSAGES    = "messages"
    private const val CHANNEL_MESSAGES_NAME = "Messages"
    private const val CHANNEL_DIARY       = "diary_reminders"
    private const val CHANNEL_DIARY_NAME  = "Diary Reminders"
    private const val TAG = "NotificationHelper"
    private const val GROUP_KEY = "org.visorlink.app.MESSAGES"
    private const val SUMMARY_ID = 9999

    fun createChannels(context: Context) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Messages Channel
        val msgChannel = NotificationChannel(
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

        // 2. Diary Reminders Channel
        val diaryChannel = NotificationChannel(
            CHANNEL_DIARY,
            CHANNEL_DIARY_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily diary entry reminders"
        }

        val existing = manager.getNotificationChannel(CHANNEL_MESSAGES)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
            manager.deleteNotificationChannel(CHANNEL_MESSAGES)
            Log.d(TAG, "Old low-priority channel removed")
        }

        manager.createNotificationChannel(msgChannel)
        manager.createNotificationChannel(diaryChannel)
    }

    fun getChatNotificationId(chatId: String): Int {
        return 10000 + (chatId.hashCode() and 0x7FFFFFFF) % 80000
    }

    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messagePreview: String,
        senderUid: String? = null,
        notificationId: Int = getChatNotificationId(chatId)
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

        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChatId", chatId)
            if (!senderUid.isNullOrBlank()) {
                putExtra("senderUid", senderUid)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Группировка уведомлений (Inbox Style) ──
        val prefs = getFcmPrefs(context)
        val historyKey = "unread_msgs_$chatId"
        val historyStr = prefs.getString(historyKey, "") ?: ""
        val history = if (historyStr.isEmpty()) mutableListOf() else historyStr.split("|||").toMutableList()

        history.add(messagePreview)
        if (history.size > 7) {
            history.removeAt(0)
        }
        prefs.edit().putString(historyKey, history.joinToString("|||")).apply()

        val activeChatsPrefs = context.getSharedPreferences("fcm_active_chats", Context.MODE_PRIVATE)
        activeChatsPrefs.edit().putBoolean(chatId, true).apply()

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(senderName)

        if (history.size > 1) {
            inboxStyle.setSummaryText("+${history.size} новых")
        }
        history.forEach { inboxStyle.addLine(it) }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Само уведомление чата
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY) // ГРУППИРОВКА
            .setVibrate(longArrayOf(0, 150, 80, 150))
            .setSound(soundUri)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        // Сводное уведомление для группы (чтобы Android корректно группировал чаты в шторке)
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val summaryPendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VisorLink")
            .setContentText("Новые сообщения")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Новые сообщения"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, notification)
            notify(SUMMARY_ID, summaryNotification)
        }
    }

    @Volatile
    private var cachedFcmPrefs: android.content.SharedPreferences? = null

    private fun getFcmPrefs(context: Context): android.content.SharedPreferences {
        cachedFcmPrefs?.let { return it }
        return synchronized(this) {
            cachedFcmPrefs ?: run {
                val prefs = try {
                    val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    androidx.security.crypto.EncryptedSharedPreferences.create(
                        context.applicationContext,
                        "fcm_prefs_encrypted",
                        masterKey,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (_: Exception) {
                    context.applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                }
                cachedFcmPrefs = prefs
                prefs
            }
        }
    }

    fun getUnreadCount(context: Context, chatId: String): Int {
        return try {
            val prefs = getFcmPrefs(context)
            val historyStr = prefs.getString("unread_msgs_$chatId", "") ?: ""
            if (historyStr.isEmpty()) 0 else historyStr.split("|||").size
        } catch (_: Exception) {
            0
        }
    }

    fun clearNotification(context: Context, chatId: String) {
        try {
            val prefs = getFcmPrefs(context)
            prefs.edit().remove("unread_msgs_$chatId").apply()

            val activeChatsPrefs = context.getSharedPreferences("fcm_active_chats", Context.MODE_PRIVATE)
            activeChatsPrefs.edit().remove(chatId).apply()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(getChatNotificationId(chatId))
            notificationManager.cancel(chatId.hashCode())

            val remainingChats = activeChatsPrefs.all.keys
            if (remainingChats.isEmpty()) {
                notificationManager.cancel(SUMMARY_ID)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear notification", e)
        }
    }

    fun clearAllNotifications(context: Context) {
        try {
            val prefs = getFcmPrefs(context)
            prefs.edit().clear().apply()

            val activeChatsPrefs = context.getSharedPreferences("fcm_active_chats", Context.MODE_PRIVATE)
            activeChatsPrefs.edit().clear().apply()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all notifications", e)
        }
    }
}