package by.iposdev.visorlink.data.model.flags

import com.google.gson.annotations.SerializedName

data class PairRequest(
    @SerializedName("public_key_pem") val publicKey: String,
    @SerializedName("install_id") val installId: String? = null
)

data class PairResponse(
    @SerializedName("device_id") val deviceId: String
)

data class ConfigRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("nonce") val nonce: String
)

data class ConfigResponse(
    @SerializedName("config_jwt") val token: String
)

data class AppFlags(
    val isAegisDebugMode: Boolean = false,
    val heuristicDictUrl: String? = null,
    val testFlag: Boolean = false,
    val isFlipperEnabled: Boolean = false,
    val serverClaims: Map<String, Any?> = emptyMap(),
    val localOverrides: Map<String, Boolean> = emptyMap()
) {
    fun isEnabled(key: String): Boolean {
        // Special handling for Aegis aliases
        if (key == "is_aegis_debug_mode" || key == "aegis_debug_mode_enabled") {
            val aegisOverride = localOverrides["aegis_debug_mode_enabled"] ?: localOverrides["is_aegis_debug_mode"]
            if (aegisOverride != null) return aegisOverride
            return isAegisDebugMode
        }

        val override = localOverrides[key]
        if (override != null) return override
        
        return when (key) {
            "test_flag" -> testFlag
            "backend_v2_enabled" -> (serverClaims["backend_v2_enabled"] as? Boolean) ?: false
            else -> serverClaims[key] as? Boolean ?: false
        }
    }

    val isBackendV2Enabled: Boolean get() = isEnabled("backend_v2_enabled")
}

