package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.visorlink.app.BuildConfig

/**
 * Singleton that checks whether the current client can obtain a valid
 * Firebase App Check token. A missing / rejected token means the build
 * is unofficial, modified, or running on an untrusted device.
 *
 * Policy:
 * 1. Pre-setup: attestation is performed before initial setup/onboarding.
 * 2. Normal launches: silent background check with forceRefresh = false
 *    (Firebase SDK reuses cached token, refreshes only if expired).
 * 3. Token refresh (forceRefresh = true): ONLY on app update (versionCode changed),
 *    explicit retry by user, or after logout.
 * 4. Warning dialog shown at most once per app version in normal flow.
 */
object AppCheckManager {

    private const val TAG = "AppCheck"
    private const val PREFS_NAME = "visorlink_appcheck"
    private const val KEY_INITIAL_ATTESTATION_DONE = "initial_attestation_done"
    private const val KEY_LAST_ATTESTED_VERSION = "last_attested_version"
    private const val KEY_WARNING_SHOWN_VERSION = "warning_shown_version"
    private const val KEY_VERIFIED_CLIENT_UID = "verified_client_uid"
    private const val KEY_VERIFIED_CLIENT_VERSION = "verified_client_version"

    sealed class State {
        /** Token obtained — client is legitimate. */
        object Valid : State()

        /** Token request failed — client is unofficial / modified. */
        data class Invalid(val reason: String) : State()

        /** In progress / checking. */
        object Checking : State()

        /** Not yet checked. */
        object Idle : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun isInitialAttestationDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INITIAL_ATTESTATION_DONE, false)
    }

    fun setInitialAttestationDone(context: Context, done: Boolean = true) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INITIAL_ATTESTATION_DONE, done)
            .putInt(KEY_LAST_ATTESTED_VERSION, BuildConfig.VERSION_CODE)
            .apply()
    }

    fun isAppUpdated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_LAST_ATTESTED_VERSION, -1)
        return lastVersion != -1 && lastVersion != BuildConfig.VERSION_CODE
    }

    fun shouldShowWarningForCurrentVersion(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shownVersion = prefs.getInt(KEY_WARNING_SHOWN_VERSION, -1)
        return shownVersion != BuildConfig.VERSION_CODE
    }

    fun markWarningShownForCurrentVersion(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WARNING_SHOWN_VERSION, BuildConfig.VERSION_CODE)
            .apply()
    }

    fun onLogout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VERIFIED_CLIENT_UID)
            .remove(KEY_VERIFIED_CLIENT_VERSION)
            .apply()
    }

    /**
     * Request App Check token.
     * [forceRefresh] should only be true on app update, explicit retry, or after logout.
     * Normal launches use forceRefresh = false so Firebase App Check uses the cached token
     * and only talks to network if the token has expired.
     */
    suspend fun validate(context: Context? = null, forceRefresh: Boolean = false) {
        _state.value = State.Checking
        try {
            val result = Firebase.appCheck
                .getAppCheckToken(forceRefresh)
                .await()
            Log.d(TAG, "App Check token obtained (len=${result.token.length})")
            _state.value = State.Valid

            if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt(KEY_LAST_ATTESTED_VERSION, BuildConfig.VERSION_CODE)
                    .putInt(KEY_WARNING_SHOWN_VERSION, -1) // Сброс предупреждения при успехе
                    .apply()
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown App Check error"
            Log.e(TAG, "App Check failed: $msg", e)
            _state.value = State.Invalid(msg)
        }
    }

    /**
     * Проверка клиента через Cloud Functions `verifyOfficialClient` (только для авторизованных пользователей).
     * Вызывается последовательно после успешного подтверждения App Check.
     */
    suspend fun verifyOfficialClientIfNeeded(context: Context, functions: FirebaseFunctions) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val verifiedUid = prefs.getString(KEY_VERIFIED_CLIENT_UID, null)
        val verifiedVersion = prefs.getInt(KEY_VERIFIED_CLIENT_VERSION, -1)

        if (verifiedUid == user.uid && verifiedVersion == BuildConfig.VERSION_CODE) {
            // Уже верифицирован для текущего пользователя и текущей версии
            return
        }

        try {
            Log.d(TAG, "Calling verifyOfficialClient Cloud Function...")
            functions.getHttpsCallable("verifyOfficialClient")
                .call()
                .await()
            Log.d(TAG, "verifyOfficialClient succeeded")
            prefs.edit()
                .putString(KEY_VERIFIED_CLIENT_UID, user.uid)
                .putInt(KEY_VERIFIED_CLIENT_VERSION, BuildConfig.VERSION_CODE)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "verifyOfficialClient failed: ${e.message}")
        }
    }

    /**
     * Получить или сгенерировать фиксированный Debug Secret для Firebase App Check.
     * Сохраняется в стандартном файле SharedPreferences Firebase Debug Provider.
     * Только debug-сборки: секрет не должен попадать в release-процесс/бинарник.
     */
    fun getOrCreateDebugSecret(context: Context): String {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "getOrCreateDebugSecret called outside DEBUG build — ignored")
            return ""
        }
        val fixedSecret = "5fe72dba-b295-4e3d-b164-4883567fd9f4"
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val persistenceKey = app.persistenceKey
            context.getSharedPreferences(
                "com.google.firebase.appcheck.debug.store.$persistenceKey",
                Context.MODE_PRIVATE
            ).edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", fixedSecret)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write debug secret using persistenceKey: ${e.message}")
        }

        try {
            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("com.google.firebase.appcheck.debug")) {
                        val nameWithoutXml = file.name.removeSuffix(".xml")
                        context.getSharedPreferences(nameWithoutXml, Context.MODE_PRIVATE)
                            .edit()
                            .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", fixedSecret)
                            .apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan debug preferences: ${e.message}")
        }

        val prefNames = listOf(
            "com.google.firebase.appcheck.debug.DebugAppCheckProvider",
            "com.google.firebase.appcheck.debug.DebugAppCheckProvider_[DEFAULT]",
            "com.google.firebase.appcheck.debug.DebugAppCheckProvider_org.visorlink.app",
            "com.google.firebase.appcheck.debug.DebugAppCheckProvider_18511020064"
        )
        for (name in prefNames) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", fixedSecret)
                .apply()
        }
        return fixedSecret
    }
}
