package by.iposdev.visorlink.data.model.flags

import com.google.gson.annotations.SerializedName

data class PairRequest(
    @SerializedName("public_key_pem") val publicKey: String
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
    val allClaims: Map<String, Any?> = emptyMap()
)
