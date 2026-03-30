package by.iposdev.visorlink.utils

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
}