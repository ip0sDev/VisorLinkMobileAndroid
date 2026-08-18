package by.iposdev.visorlink.data.remote.chat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import okhttp3.*

class ChatWebSocketClient(private val client: OkHttpClient) {

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val _incomingMessages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    suspend fun connect(baseUrl: String) {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            ?: return 

        val wsUrl = if (baseUrl.startsWith("http")) {
            baseUrl.replace("http", "ws")
        } else {
            "ws://$baseUrl"
        }
        
        val url = "$wsUrl/ws?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ChatWS", "🟢 WebSocket Connected!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, MessageDto::class.java)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {
                    Log.e("ChatWS", "Ошибка парсинга сообщения: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatWS", "🔴 WebSocket Closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ChatWS", "🔴 WebSocket Failed: ${t.message}", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
