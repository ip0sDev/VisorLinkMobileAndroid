package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Менеджер управления жизненным циклом FCM-токенов:
 * - Настройка токена строго ПОСЛЕ двухфакторной аутентификации (2FA)
 * - Автоматический отзыв токена при логауте / сбросе приложения
 */
class FcmManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository
) {
    /**
     * Регистрирует токен в облаке.
     * Вызывается ТОЛЬКО после успешного подтверждения 2FA (или если 2FA не включена).
     */
    suspend fun syncTokenAfter2FA() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "No authenticated user, skipping FCM setup")
            return@withContext
        }

        try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Configuring FCM token post-2FA for $uid: $token")
            userRepository.saveFcmToken(token)
            Log.d(TAG, "FCM token successfully registered post-2FA")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token post-2FA", e)
        }
    }

    /**
     * Автоматически отзывает FCM-токен при сбросе приложения / выходе из учетной записи:
     * 1. Удаляет токен из профиля пользователя в Firestore
     * 2. Вызывает deleteToken() в FirebaseMessaging для аннулирования токена на серверах FCM
     */
    suspend fun revokeToken() = withContext(Dispatchers.IO) {
        try {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Removing FCM token from user profile...")
                userRepository.removeFcmToken(token)
            }
            FirebaseMessaging.getInstance().deleteToken().await()
            Log.d(TAG, "FCM token revoked and invalidated on FirebaseMessaging")
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking FCM token", e)
        }
    }

    companion object {
        private const val TAG = "FcmManager"
    }
}
