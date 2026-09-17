package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import org.visorlink.app.data.remote.chat.VisorLinkApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackendFallbackManager(
    private val context: Context,
    private val flagsRepository: FlagsRepository,
    private val apiProvider: (() -> VisorLinkApi)? = null
) {
    private val prefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    private val _manualFallbackActive = MutableStateFlow(
        prefs.getBoolean("manual_fallback_firebase", false)
    )
    val manualFallbackActive: StateFlow<Boolean> = _manualFallbackActive.asStateFlow()

    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    private val _showFallbackPrompt = MutableStateFlow(false)
    val showFallbackPrompt: StateFlow<Boolean> = _showFallbackPrompt.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isManualFallbackActive(): Boolean = _manualFallbackActive.value

    @Synchronized
    fun recordSuccess() {
        if (_consecutiveFailures.value > 0) {
            _consecutiveFailures.value = 0
        }
    }

    @Synchronized
    fun recordFailure(reason: String? = null) {
        // If user already switched to manual fallback, do not track further
        if (_manualFallbackActive.value) return
        // Only track if backend v2 is actually enabled in configuration
        if (!flagsRepository.isBackendV2EnabledDirect()) return

        val next = _consecutiveFailures.value + 1
        _consecutiveFailures.value = next
        Log.w(TAG, "Consecutive backend connection failure #$next: $reason")

        if (next >= 6) {
            _showFallbackPrompt.value = true
        }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        val api = try { apiProvider?.invoke() } catch (_: Exception) { null }
        if (api == null) return@withContext true
        try {
            val res = api.getHealth()
            if (res.status == "healthy") {
                recordSuccess()
                true
            } else {
                recordFailure("Unhealthy status: ${res.status}")
                false
            }
        } catch (e: Exception) {
            recordFailure("Health check unreachable: ${e.message}")
            false
        }
    }

    fun enableManualFallback() {
        Log.i(TAG, "Enabling manual fallback to Firebase")
        prefs.edit().putBoolean("manual_fallback_firebase", true).apply()
        _manualFallbackActive.value = true
        _showFallbackPrompt.value = false
        _consecutiveFailures.value = 0
        flagsRepository.notifyFlagsChanged()
    }

    fun disableManualFallback() {
        Log.i(TAG, "Disabling manual fallback; attempting Backend v2")
        prefs.edit().putBoolean("manual_fallback_firebase", false).apply()
        _manualFallbackActive.value = false
        _showFallbackPrompt.value = false
        _consecutiveFailures.value = 0
        flagsRepository.notifyFlagsChanged()
    }

    fun dismissPrompt() {
        _showFallbackPrompt.value = false
        _consecutiveFailures.value = 0
    }

    companion object {
        private const val TAG = "BackendFallbackManager"
    }
}
