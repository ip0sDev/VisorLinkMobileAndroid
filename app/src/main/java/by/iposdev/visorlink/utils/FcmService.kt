package by.iposdev.visorlink.utils

import android.util.Log
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
        Log.d("FCM", "New token: $token")
        scope.launch {
            try { userRepository.saveFcmToken(token) }
            catch (e: Exception) { Log.e("FCM", "Failed to save token", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Уведомления из foreground — показываем вручную
        val chatId = message.data["chatId"] ?: return
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: return
        NotificationHelper.showMessageNotification(
            context = applicationContext,
            chatId = chatId,
            senderName = title,
            messagePreview = body
        )
    }
}