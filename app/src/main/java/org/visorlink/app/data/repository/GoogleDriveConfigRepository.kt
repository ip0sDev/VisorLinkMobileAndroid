package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

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
}
