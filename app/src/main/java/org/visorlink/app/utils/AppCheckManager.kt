package org.visorlink.app.utils

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Singleton that checks whether the current client can obtain a valid
 * Firebase App Check token.  A missing / rejected token means the build
 * is unofficial, modified, or running on an untrusted device.
 *
 * Usage
 * -----
 *   AppCheckManager.validate()         // call once at startup
 *   AppCheckManager.state.value        // observe anywhere
 */
object AppCheckManager {

    private const val TAG = "AppCheck"

    sealed class State {
        /** Token obtained — client is legitimate. */
        object Valid : State()

        /** Token request failed — client is unofficial / modified. */
        data class Invalid(val reason: String) : State()

        /** Not yet checked. */
        object Idle : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Request a fresh App Check token. Call from a coroutine scope
     * (e.g. inside Application.onCreate via a GlobalScope launch, or
     * from the Activity before the NavGraph is composed).
     *
     * [forceRefresh] – set true the first time to bypass any cached token
     * so we get an authoritative result immediately.
     */
    suspend fun validate(forceRefresh: Boolean = true) {
        _state.value = State.Idle
        try {
            val result = Firebase.appCheck
                .getAppCheckToken(forceRefresh)
                .await()
            Log.d(TAG, "App Check token obtained (len=${result.token.length})")
            _state.value = State.Valid
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown App Check error"
            Log.e(TAG, "App Check failed: $msg", e)
            _state.value = State.Invalid(msg)
        }
    }

    /**
     * Получить или сгенерировать фиксированный Debug Secret для Firebase App Check.
     * Сохраняется в стандартном файле SharedPreferences Firebase Debug Provider.
     */
    fun getOrCreateDebugSecret(context: android.content.Context): String {
        val fixedSecret = "5fe72dba-b295-4e3d-b164-4883567fd9f4"
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val persistenceKey = app.persistenceKey
            context.getSharedPreferences(
                "com.google.firebase.appcheck.debug.store.$persistenceKey",
                android.content.Context.MODE_PRIVATE
            ).edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", fixedSecret)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write debug secret using persistenceKey: ${e.message}")
        }

        // Также обновляем все файлы preferences, связанные с debug App Check
        try {
            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("com.google.firebase.appcheck.debug")) {
                        val nameWithoutXml = file.name.removeSuffix(".xml")
                        context.getSharedPreferences(nameWithoutXml, android.content.Context.MODE_PRIVATE)
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
            context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", fixedSecret)
                .apply()
        }
        return fixedSecret
    }
}