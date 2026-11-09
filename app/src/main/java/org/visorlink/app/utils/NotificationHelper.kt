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
    private const val CHANNEL_CK          = "ck_handshake"
    private const val CHANNEL_CK_NAME     = "Key delivery approvals"
    private const val TAG = "NotificationHelper"
    private const val GROUP_KEY = "org.visorlink.app.MESSAGES"
    private const val SUMMARY_ID = 9999

    /** Фиксированный id уведомления хендшейка (не пересекается с чатами 10000+ и сводкой). */
    private const val CK_NOTIFICATION_ID = 9901

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

        // 3. E2EE-хендшейк «ключ контента на новый браузер» (TODO §14.3): хедс-ап одобрения.
        // Отдельный канал — пользователь управляет им независимо от сообщений чатов.
        val ckChannel = NotificationChannel(
            CHANNEL_CK,
            CHANNEL_CK_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Approve delivering your encryption key to a new browser"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val existing = manager.getNotificationChannel(CHANNEL_MESSAGES)
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
            manager.deleteNotificationChannel(CHANNEL_MESSAGES)
            Log.d(TAG, "Old low-priority channel removed")
        }

        manager.createNotificationChannel(msgChannel)
        manager.createNotificationChannel(diaryChannel)
        manager.createNotificationChannel(ckChannel)
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
        prefs.putString(historyKey, history.joinToString("|||"))

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

    /**
     * Хедс-ап «Запрос ключа шифрования» (E2EE-хендшейк, TODO §14.3): тап открывает
     * MainActivity с extra ck_request_id — там Compose-диалог одобрения со сверкой кода.
     * В пейлоаде только id заявки и имя браузера — никаких ключей. Фиксированный id:
     * повторные запросы заменяют уведомление, а не копят шторку.
     */
    fun showCkHandshakeNotification(context: Context, requestId: String, client: String) {
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
            putExtra("ck_request_id", requestId)
            if (client.isNotBlank()) putExtra("ck_client", client)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CK_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (client.isBlank()) {
            "Браузер запрашивает ключ шифрования «Избранного»"
        } else {
            "Браузер $client запрашивает ключ шифрования «Избранного»"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Запрос ключа шифрования")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 150, 80, 150))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            with(NotificationManagerCompat.from(context)) { notify(CK_NOTIFICATION_ID, notification) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show ck_handshake notification: ${e.message}")
        }
    }

    @Volatile
    private var cachedFcmPrefs: SecurePrefs? = null

    /**
     * Превью сообщений — чувствительные данные: SecurePrefs на SecureVault (TODO §14.5).
     * Fail-closed, плейнтекст-fallback убран; legacy переносится однократно.
     */
    private fun getFcmPrefs(context: Context): SecurePrefs {
        cachedFcmPrefs?.let { return it }
        return synchronized(this) {
            cachedFcmPrefs ?: SecurePrefs(context.applicationContext, "fcm_prefs").apply {
                migrateAllFrom(
                    listOf(
                        SecurePrefs.LegacyFile("fcm_prefs_encrypted", encrypted = true),
                        SecurePrefs.LegacyFile("fcm_prefs", encrypted = false)
                    )
                )
            }.also { cachedFcmPrefs = it }
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
            prefs.remove("unread_msgs_$chatId")

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
            prefs.clear()

            val activeChatsPrefs = context.getSharedPreferences("fcm_active_chats", Context.MODE_PRIVATE)
            activeChatsPrefs.edit().clear().apply()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all notifications", e)
        }
    }
}