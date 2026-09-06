package by.iposdev.visorlink.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject

class FcmService : FirebaseMessagingService() {

    private val userRepository: by.iposdev.visorlink.data.repository.UserRepository by inject()
    private val tfaManager: TfaManager by inject()
    private val authRepository: by.iposdev.visorlink.data.repository.AuthRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token received: $token")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d("FCM", "User not logged in, skipping token save")
            return
        }

        scope.launch {
            try {
                val authTime = authRepository.getAuthTime()
                val isTfaPassed = authTime != null && tfaManager.isTfaPassed(authTime)
                val profile = userRepository.getUserProfile(uid)
                if (profile == null || !profile.tfaEnabled || isTfaPassed) {
                    userRepository.saveFcmToken(token)
                    Log.d("FCM", "New token saved: $token")
                } else {
                    Log.d("FCM", "2FA pending: deferring token registration")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Failed to save new token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received: ${message.data}")

        val chatId = message.data["chatId"] ?: run {
            Log.w("FCM", "No chatId in data payload")
            return
        }

        val type = message.data["type"]
        if (type == "read" || type == "clear_notification") {
            NotificationHelper.clearNotification(applicationContext, chatId)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("FCM", "No notification permission, skipping")
                return
            }
        }

        // Проверка общих настроек уведомлений из SharedPreferences
        val prefs = applicationContext.getSharedPreferences("visorlink_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) {
            Log.d("FCM", "Notifications are disabled in app settings")
            return
        }

        val title = message.notification?.title
            ?: message.data["senderName"]
            ?: message.data["title"]
            ?: "New message"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: "You have a new message"

        val senderUid = message.data["senderUid"]
            ?: message.data["senderId"]
            ?: message.data["fromUid"]

        // Если чат открыт на экране прямо сейчас и приложение на переднем плане — скрываем уведомление
        val isCurrentChat = ActiveChatTracker.isChatActive(chatId)
        if (isCurrentChat) {
            Log.d("FCM", "Suppressed notification: chat $chatId is currently open in foreground")
            return
        }

        // Синхронный показ уведомления во избежание сброса фонового сервиса операционной системой
        NotificationHelper.showMessageNotification(
            context = applicationContext,
            chatId = chatId,
            senderName = title,
            messagePreview = body,
            senderUid = senderUid
        )
    }
}