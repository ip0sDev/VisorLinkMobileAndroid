package by.iposdev.visorlink.data.repository

import android.content.Context
import android.util.Log
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.data.model.flags.AppFlags
import by.iposdev.visorlink.data.model.flags.ConfigRequest
import by.iposdev.visorlink.data.model.flags.PairRequest
import by.iposdev.visorlink.data.remote.flags.AegisKeyManager
import by.iposdev.visorlink.data.remote.flags.FlagsApi
import com.auth0.android.jwt.JWT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import retrofit2.HttpException
import java.util.UUID

class FlagsRepository(
    private val context: Context,
    private val api: FlagsApi,
    private val keyManager: AegisKeyManager
) {
    private val prefs = context.getSharedPreferences("visorlink_flags_prefs", Context.MODE_PRIVATE)
    private val backendPrefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    private val _flags = MutableStateFlow(AppFlags())
    val flags: StateFlow<AppFlags> = _flags.asStateFlow()
    val isBackendV2EnabledFlow: Flow<Boolean> = _flags.map { isBackendV2Enabled() }

    fun isManualFallbackActive(): Boolean = backendPrefs.getBoolean("manual_fallback_firebase", false)

    fun isBackendV2Enabled(): Boolean {
        if (isManualFallbackActive()) return false
        return _flags.value.isBackendV2Enabled
    }

    fun isBackendV2EnabledDirect(): Boolean = _flags.value.isBackendV2Enabled

    fun notifyFlagsChanged() {
        _flags.value = _flags.value.copy()
    }

    init {
        loadInitialFlags()
    }

    private fun loadInitialFlags() {
        val isFlipperEnabled = prefs.getBoolean("is_flipper_enabled", false)
        val overridesJson = prefs.getString("local_overrides", null)
        val overrides = try {
            if (overridesJson != null) {
                val map = mutableMapOf<String, Boolean>()
                val json = JSONObject(overridesJson)
                json.keys().forEach { key ->
                    map[key] = json.getBoolean(key)
                }
                map
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap<String, Boolean>()
        }
        _flags.value = _flags.value.copy(
            isFlipperEnabled = isFlipperEnabled,
            localOverrides = overrides
        )
    }

    fun getDeviceId(): String? {
        return prefs.getString("device_id", null)
    }

    suspend fun pairIfNeeded(): String {
        val existingId = getDeviceId()
        val keyExists = keyManager.hasKey()

        if (existingId != null && keyExists) {
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Using existing device_id: $existingId and existing key.")
            return existingId
        }

        if (existingId != null && !keyExists) {
            if (BuildConfig.DEBUG) Log.w("FlagsRepo", "Device ID exists but Key is missing! Clearing ID and re-pairing.")
            prefs.edit().remove("device_id").apply()
        }

        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Starting pairing process...")
        val publicKeyPem = keyManager.generatePublicKeyPem()
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Generated Public Key PEM:\n$publicKeyPem")
        
        val response = api.pair(PairRequest(publicKeyPem))
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Pairing successful, received device_id: ${response.deviceId}")
        
        prefs.edit().putString("device_id", response.deviceId).apply()
        return response.deviceId
    }

    suspend fun fetchFlags() {
        if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Fetching flags...")
        try {
            val deviceId = pairIfNeeded()
            val timestamp = System.currentTimeMillis() / 1000L
            val nonce = UUID.randomUUID().toString()
            
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Signing payload: deviceId=$deviceId, timestamp=$timestamp, nonce=$nonce")
            val signature = keyManager.signPayload(deviceId, timestamp, nonce)
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Generated Signature: $signature")
            
            val response = api.getConfig(
                signature = signature,
                request = ConfigRequest(deviceId, timestamp, nonce)
            )
            
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Config response received. Token: ${response.token}")
            parseAndApplyFlags(response.token)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("FlagsRepo", "Error fetching flags", e)
                if (e is HttpException && e.code() == 401) {
                    Log.w("FlagsRepo", "Received 401. Clearing device_id to force re-pair on next run.")
                    prefs.edit().remove("device_id").apply()
                }
            }
            e.printStackTrace()
        }
    }

    private fun parseAndApplyFlags(token: String) {
        try {
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Parsing JWT Token: $token")
            val jwt = JWT(token)
            
            // Log all claims for debugging
            if (BuildConfig.DEBUG) {
                Log.d("FlagsRepo", "JWT Payload Claims:")
                jwt.claims.forEach { (key, claim) ->
                    Log.d("FlagsRepo", "  $key: ${claim.asString() ?: claim.asBoolean() ?: claim.asInt()}")
                }
            }

            val isDebug = jwt.getClaim("is_aegis_debug_mode").asBoolean() 
                ?: jwt.getClaim("aegis_debug_mode_enabled").asBoolean() 
                ?: false
            val dictUrl = jwt.getClaim("heuristic_dict_url").asString()
            val isTest = jwt.getClaim("test_flag").asBoolean() ?: false

            val allClaimsMap = mutableMapOf<String, Any?>()
            jwt.claims.forEach { (key, claim) ->
                val value = when {
                    claim.asBoolean() != null -> claim.asBoolean()
                    claim.asString() != null -> claim.asString()
                    claim.asInt() != null -> claim.asInt()
                    claim.asDouble() != null -> claim.asDouble()
                    else -> claim.asString()
                }
                allClaimsMap[key] = value
                if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Claim: $key = $value")
            }
            
            if (BuildConfig.DEBUG) Log.d("FlagsRepo", "Applying flags: test_flag=$isTest, is_aegis_debug_mode=$isDebug")
            
            val wasFlipperEnabled = _flags.value.isFlipperEnabled
            val newFlipperEnabled = wasFlipperEnabled || isTest
            if (newFlipperEnabled != wasFlipperEnabled) {
                prefs.edit().putBoolean("is_flipper_enabled", true).apply()
            }

            _flags.value = _flags.value.copy(
                isAegisDebugMode = isDebug,
                heuristicDictUrl = dictUrl,
                testFlag = isTest,
                isFlipperEnabled = newFlipperEnabled,
                serverClaims = allClaimsMap
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error parsing flags JWT", e)
            e.printStackTrace()
        }
    }

    fun toggleFlag(key: String, enabled: Boolean) {
        val currentOverrides = _flags.value.localOverrides.toMutableMap()
        currentOverrides[key] = enabled
        
        val json = JSONObject()
        currentOverrides.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("local_overrides", json.toString()).apply()

        _flags.value = _flags.value.copy(localOverrides = currentOverrides)
    }
}
