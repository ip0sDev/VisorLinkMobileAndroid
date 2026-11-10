package org.visorlink.app.utils

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.PendingIntent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleDriveAuthManager(
    private val context: Context
) {
    companion object {
        const val TAG = "GoogleDriveAuth"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val PREFS_FILE = "google_drive_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_EXPIRES_AT = "gdrive_expires_at"
        private const val KEY_ACCOUNT_EMAIL = "gdrive_account_email"
        private const val KEY_FOLDER_ID = "gdrive_folder_id"
    }

    // TODO §14.5: SecurePrefs на SecureVault вместо deprecated EncryptedSharedPreferences.
    // Fail-closed: сбой Keystore → токен недоступен, Drive считается отключённым
    // (плейнтекст-fallback с тем же именем файла убран — он оставлял OAuth-токен в открытом виде).
    private val prefs: SecurePrefs = SecurePrefs(context.applicationContext, "gdrive").apply {
        migrateKeysFrom(
            listOf(
                // Файл исторически писался и ESP, и plaintext-fallback под одним именем:
                // для каждого ключа берём первого читателя, который его отдаёт
                SecurePrefs.LegacyFile(PREFS_FILE, encrypted = true),
                SecurePrefs.LegacyFile(PREFS_FILE, encrypted = false)
            ),
            keys = listOf(KEY_ACCESS_TOKEN, KEY_EXPIRES_AT, KEY_ACCOUNT_EMAIL, KEY_FOLDER_ID)
        )
    }

    private val _isConnectedFlow = MutableStateFlow(hasValidConnection())
    val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

    fun getAuthorizationClient(): AuthorizationClient {
        return Identity.getAuthorizationClient(context)
    }

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
    }

    suspend fun authorize(): AuthorizationResult = withContext(Dispatchers.IO) {
        val request = buildAuthorizationRequest()
        getAuthorizationClient().authorize(request).await()
    }

    fun getAuthorizationResultFromIntent(data: Intent?): AuthorizationResult {
        return getAuthorizationClient().getAuthorizationResultFromIntent(data)
    }

    fun isConnected(): Boolean {
        return hasValidConnection()
    }

    fun getAppSignatureSha1(): String {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return "unknown"
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    fun getConnectedEmail(): String? {
        return prefs.getString(KEY_ACCOUNT_EMAIL, null)
    }

    fun getCachedFolderId(): String? {
        return prefs.getString(KEY_FOLDER_ID, null)
    }

    fun setCachedFolderId(folderId: String) {
        prefs.putString(KEY_FOLDER_ID, folderId)
    }

    private fun hasValidConnection(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val email = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        return !token.isNullOrBlank() || !email.isNullOrBlank()
    }

    suspend fun handleAuthorizationResult(authResult: AuthorizationResult): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = authResult.accessToken
            val serverAuthCode = authResult.serverAuthCode
            
            if (token.isNullOrBlank() && serverAuthCode.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Не удалось получить токен доступа Google Drive"))
            }

            val validToken = token ?: serverAuthCode ?: ""
            val expiresIn = 3600_000L // 1 hour default
            val expiresAt = System.currentTimeMillis() + expiresIn - 60_000L

            prefs.putString(KEY_ACCESS_TOKEN, validToken)
            prefs.putLong(KEY_EXPIRES_AT, expiresAt)

            val email = fetchUserEmail(validToken)
            if (!email.isNullOrBlank()) {
                prefs.putString(KEY_ACCOUNT_EMAIL, email)
            }

            _isConnectedFlow.value = true
            Log.i(TAG, "Google Drive connected successfully via AuthorizationClient")
            Result.success(validToken)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process Google Drive authorization: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchUserEmail(accessToken: String): String? {
        return try {
            val url = java.net.URL("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                json.optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch user email: ${e.message}")
            null
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < expiresAt) {
            return@withContext cachedToken
        }

        // Токен истек или отсутствует, пробуем выполнить тихую авторизацию через AuthorizationClient
        return@withContext try {
            val authResult = authorize()
            val newToken = authResult.accessToken
            if (!newToken.isNullOrBlank()) {
                val expiresIn = 3600_000L
                val newExpiresAt = System.currentTimeMillis() + expiresIn - 60_000L

                prefs.putString(KEY_ACCESS_TOKEN, newToken)
                prefs.putLong(KEY_EXPIRES_AT, newExpiresAt)

                _isConnectedFlow.value = true
                newToken
            } else {
                cachedToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh Google Drive access token: ${e.message}", e)
            // Возвращаем существующий cachedToken, если он есть
            cachedToken
        }
    }

    fun disconnect(serverClientId: String = "") {
        try {
            val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            if (!cachedToken.isNullOrBlank()) {
                try {
                    GoogleAuthUtil.clearToken(context, cachedToken)
                } catch (_: Exception) {}
            }
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_EXPIRES_AT)
            prefs.remove(KEY_ACCOUNT_EMAIL)
            prefs.remove(KEY_FOLDER_ID)

            try {
                Identity.getSignInClient(context).signOut()
            } catch (_: Exception) {}

            _isConnectedFlow.value = false
            Log.i(TAG, "Google Drive disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Google Drive", e)
        }
    }
}

/**
 * Медиа хранятся на Google Диске отправителя, а он не подключён (или доступ отозван). Отдельный
 * тип, чтобы очередь отправки не повторяла такую отправку впустую, а чат предложил подключить Диск.
 */
class DriveNotConnectedException : IllegalStateException("Google Drive не подключен. Подключите его в Настройки → Хранилище.")
