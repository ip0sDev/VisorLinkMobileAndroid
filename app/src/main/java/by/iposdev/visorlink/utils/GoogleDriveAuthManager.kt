package by.iposdev.visorlink.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleDriveAuthState(
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val folderId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Менеджер аутентификации Google Drive со строгим скоупом drive.file
 * (Секция 2 ANDROID_GOOGLE_DRIVE_AND_STORAGE_SPEC).
 */
class GoogleDriveAuthManager(
    private val context: Context
) {
    companion object {
        const val TAG = "GoogleDriveAuth"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

        private const val PREFS_NAME = "visorlink_gdrive_auth"
        private const val KEY_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_EXPIRES_AT = "gdrive_token_expires_at"
        private const val KEY_FOLDER_ID = "gdrive_folder_id"
        private const val KEY_ACCOUNT_EMAIL = "gdrive_account_email"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val preferences: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences failed, falling back to standard prefs", e)
            context.applicationContext.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _state = MutableStateFlow(
        GoogleDriveAuthState(
            isConnected = preferences.getString(KEY_ACCOUNT_EMAIL, null) != null,
            accountEmail = preferences.getString(KEY_ACCOUNT_EMAIL, null),
            folderId = preferences.getString(KEY_FOLDER_ID, null)
        )
    )
    val state: StateFlow<GoogleDriveAuthState> = _state.asStateFlow()

    fun isDriveConnected(): Boolean {
        return preferences.getString(KEY_ACCOUNT_EMAIL, null) != null
    }

    fun getAccountEmail(): String? {
        return preferences.getString(KEY_ACCOUNT_EMAIL, null)
    }

    fun getCachedFolderId(): String? {
        return preferences.getString(KEY_FOLDER_ID, null)
    }

    fun saveCachedFolderId(folderId: String) {
        preferences.edit().putString(KEY_FOLDER_ID, folderId).apply()
        _state.update { it.copy(folderId = folderId) }
    }

    fun getGoogleSignInOptions(serverClientId: String): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))

        if (serverClientId.isNotBlank()) {
            builder.requestServerAuthCode(serverClientId)
        }
        return builder.build()
    }

    fun getGoogleSignInClient(serverClientId: String): GoogleSignInClient {
        val gso = getGoogleSignInOptions(serverClientId)
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInClient(serverClientId: String): GoogleSignInClient = getGoogleSignInClient(serverClientId)

    suspend fun handleSignInResult(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.update { it.copy(isLoading = true, error = null) }
            val email = account.email ?: throw IllegalStateException("Google Account email is null")

            // Получаем токен доступа для скоупа drive.file
            val androidAccount = account.account ?: throw IllegalStateException("Android Account is null")
            val token = GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
            
            // Сохраняем в зашифрованное хранилище: токен и 1 час жизни (за вычетом запаса в 60 сек)
            val expiresAt = System.currentTimeMillis() + 3600_000L - 60_000L
            preferences.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .putString(KEY_ACCOUNT_EMAIL, email)
                .apply()

            _state.update {
                it.copy(
                    isConnected = true,
                    accountEmail = email,
                    isLoading = false,
                    error = null
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Google Sign-In result", e)
            _state.update { it.copy(isLoading = false, error = e.message) }
            Result.failure(e)
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val email = preferences.getString(KEY_ACCOUNT_EMAIL, null) ?: return@withContext null
            val cachedToken = preferences.getString(KEY_ACCESS_TOKEN, null)
            val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)

            if (!cachedToken.isNullOrEmpty() && System.currentTimeMillis() < expiresAt) {
                return@withContext cachedToken
            }

            // Токен истек — обновляем через GoogleSignIn / GoogleAuthUtil
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            val androidAccount = lastAccount?.account ?: android.accounts.Account(email, "com.google")

            if (cachedToken != null) {
                try {
                    GoogleAuthUtil.clearToken(context, cachedToken)
                } catch (_: Exception) {}
            }

            val newToken = GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
            val newExpiresAt = System.currentTimeMillis() + 3600_000L - 60_000L

            preferences.edit()
                .putString(KEY_ACCESS_TOKEN, newToken)
                .putLong(KEY_EXPIRES_AT, newExpiresAt)
                .apply()

            newToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh Google Drive access token", e)
            null
        }
    }

    suspend fun disconnect(serverClientId: String = "") = withContext(Dispatchers.IO) {
        try {
            val token = preferences.getString(KEY_ACCESS_TOKEN, null)
            if (!token.isNullOrEmpty()) {
                try {
                    GoogleAuthUtil.clearToken(context, token)
                } catch (_: Exception) {}
            }

            val client = getGoogleSignInClient(serverClientId)
            try {
                client.signOut().await()
                client.revokeAccess().await()
            } catch (_: Exception) {}

            preferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_FOLDER_ID)
                .remove(KEY_ACCOUNT_EMAIL)
                .apply()

            _state.update {
                GoogleDriveAuthState(
                    isConnected = false,
                    accountEmail = null,
                    folderId = null,
                    isLoading = false,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Google Drive", e)
        }
    }
}
