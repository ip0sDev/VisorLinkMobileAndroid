package by.iposdev.visorlink.data.remote.chat

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
        
        // We only redirect if custom backend is enabled and the request is going to our "placeholder" host
        // Our placeholder in AppModule is http://10.0.2.2:8080
        if (isEnabled && request.url.host == "10.0.2.2" && request.url.port == 8080) {
            val formattedUrl = if (customUrlStr.startsWith("http")) customUrlStr else "http://$customUrlStr"
            formattedUrl.toHttpUrlOrNull()?.let { newBaseUrl ->
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
}
