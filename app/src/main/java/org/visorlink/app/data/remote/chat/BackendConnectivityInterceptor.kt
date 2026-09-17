package org.visorlink.app.data.remote.chat

import org.visorlink.app.data.repository.BackendFallbackManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class BackendConnectivityInterceptor(
    private val fallbackManager: BackendFallbackManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        try {
            val response = chain.proceed(request)
            if (response.code in 502..504) {
                fallbackManager.recordFailure("HTTP ${response.code}")
            } else {
                fallbackManager.recordSuccess()
            }
            return response
        } catch (e: IOException) {
            fallbackManager.recordFailure("${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
