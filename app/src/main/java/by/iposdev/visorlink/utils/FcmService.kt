package by.iposdev.visorlink.utils

import android.Manifest
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
import org.koin.android.ext.android.inject

class FcmService : FirebaseMessagingService() {

    private val userRepository: by.iposdev.visorlink.data.repository.UserRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token received: $token")

        // Сохраняем только если пользователь авторизован
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d("FCM", "User not logged in, skipping token save")
            return
        }

        scope.launch {
            try {
                userRepository.saveFcmToken(token)
                Log.d("FCM", "New token saved")
            } catch (e: Exception) {
                Log.e("FCM", "Failed to save new token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received: ${message.data}")

        // Проверяем разрешение
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

        val chatId = message.data["chatId"] ?: run {
            Log.w("FCM", "No chatId in data payload")
            return
        }

        // Используем notification payload если есть, иначе data payload
        val title = message.notification?.title
            ?: message.data["senderName"]
            ?: "New message"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new message"

        Log.d("FCM", "Showing notification: $title - $body")

        val isCurrentChat = ActiveChatTracker.activeChatId == chatId
        if (!isCurrentChat) {
            NotificationHelper.showMessageNotification(
                context = applicationContext,
                chatId = chatId,
                senderName = title,
                messagePreview = body
            )
        } else {
            Log.d("FCM", "Suppressed notification: chat $chatId is currently open")
        }
    }
}