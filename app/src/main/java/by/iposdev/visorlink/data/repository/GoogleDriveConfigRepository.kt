package by.iposdev.visorlink.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Репозиторий для динамического получения Google OAuth Client ID через Cloud Function,
 * избегая утечки ключей в сборках клиента (Секция 2.2 ANDROID_GOOGLE_DRIVE_AND_STORAGE_SPEC).
 */
class GoogleDriveConfigRepository(
    private val functions: FirebaseFunctions
) {
    private var cachedClientId: String? = null

    suspend fun getGoogleClientId(): String {
        cachedClientId?.let { return it }

        return try {
            val result = functions
                .getHttpsCallable("getGoogleDriveConfig")
                .call()
                .await()

            val data = result.data as? Map<*, *>
            val clientId = data?.get("clientId") as? String ?: ""
            if (clientId.isNotBlank()) {
                cachedClientId = clientId
            }
            clientId
        } catch (e: Exception) {
            Log.e("GoogleDriveConfig", "Failed to fetch Google Client ID", e)
            ""
        }
    }

    suspend fun getGoogleDriveClientId(): String = getGoogleClientId()

    /**
     * Позволяет установить Client ID вручную для тестов или локальной разработки.
     */
    fun setCachedClientId(clientId: String) {
        cachedClientId = clientId
    }
}
