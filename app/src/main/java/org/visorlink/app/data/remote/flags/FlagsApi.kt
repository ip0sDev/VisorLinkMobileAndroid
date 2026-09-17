package org.visorlink.app.data.remote.flags

import org.visorlink.app.data.model.flags.ConfigRequest
import org.visorlink.app.data.model.flags.ConfigResponse
import org.visorlink.app.data.model.flags.PairRequest
import org.visorlink.app.data.model.flags.PairResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface FlagsApi {

    @POST("api/v1/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    @POST("api/v1/config")
    suspend fun getConfig(
        @Header("X-Aegis-Signature") signature: String,
        @Body request: ConfigRequest
    ): ConfigResponse
}
