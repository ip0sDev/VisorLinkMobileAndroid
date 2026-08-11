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
import retrofit2.HttpException
import java.util.UUID

class FlagsRepository(
    private val context: Context,
    private val api: FlagsApi,
    private val keyManager: AegisKeyManager
) {
    private val prefs = context.getSharedPreferences("visorlink_flags_prefs", Context.MODE_PRIVATE)

    private val _flags = MutableStateFlow(AppFlags())
    val flags: StateFlow<AppFlags> = _flags.asStateFlow()

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
            
            _flags.value = AppFlags(
                isAegisDebugMode = isDebug,
                heuristicDictUrl = dictUrl,
                testFlag = isTest,
                allClaims = allClaimsMap
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("FlagsRepo", "Error parsing flags JWT", e)
            e.printStackTrace()
        }
    }
}
