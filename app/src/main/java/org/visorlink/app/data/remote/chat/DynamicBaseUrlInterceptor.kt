package org.visorlink.app.data.remote.chat

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val prefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("use_custom_backend", false)
        val customUrlStr = prefs.getString("custom_backend_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
        
        var request = chain.request()
        
        // We redirect if custom backend is enabled
        if (isEnabled) {
            val formattedUrl = if (customUrlStr.startsWith("http")) customUrlStr else "http://$customUrlStr"
            val newBaseUrl = formattedUrl.toHttpUrlOrNull()
            
            // Validate the scheme and host to prevent SSRF
            if (newBaseUrl != null && 
                (newBaseUrl.scheme == "http" || newBaseUrl.scheme == "https") &&
                isValidBackendHost(newBaseUrl.host)) {
                
                val newUrl = request.url.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
        }
        
        return chain.proceed(request)
    }

    private fun isValidBackendHost(host: String): Boolean {
        // Prevent resolving local/metadata endpoints for SSRF mitigation
        if (host == "169.254.169.254" || host == "metadata.google.internal") return false
        
        // Ensure no local network exploitation outside of the allowed simulator IPs
        if (host == "localhost" || host == "127.0.0.1") return false
        
        // For production, you'd typically want a strict whitelist here
        // e.g. return host.endsWith(".visorlink.com") || host == "10.0.2.2"
        return true
    }
}
