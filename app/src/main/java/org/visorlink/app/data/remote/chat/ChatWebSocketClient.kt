package org.visorlink.app.data.remote.chat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ChatWS"

class ChatWebSocketClient(
    private val client: OkHttpClient
) {

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val activeChannels = ConcurrentHashMap.newKeySet<String>()
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentBaseUrl: String = "https://backend.visorlink.org"
    private var isExplicitDisconnect = false

    private val _incomingMessages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _updatedMessages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val updatedMessages = _updatedMessages.asSharedFlow()

    private val _deletedMessages = MutableSharedFlow<DeleteMessagePayload>(extraBufferCapacity = 64)
    val deletedMessages = _deletedMessages.asSharedFlow()

    private val _updatedChats = MutableSharedFlow<ChatDto>(extraBufferCapacity = 64)
    val updatedChats = _updatedChats.asSharedFlow()

    private val _typingEvents = MutableSharedFlow<TypingPayload>(extraBufferCapacity = 64)
    val typingEvents = _typingEvents.asSharedFlow()

    private val _presenceEvents = MutableSharedFlow<PresencePayload>(extraBufferCapacity = 64)
    val presenceEvents = _presenceEvents.asSharedFlow()

    suspend fun connect(baseUrl: String = "https://backend.visorlink.org", forceRefreshToken: Boolean = false) {
        currentBaseUrl = baseUrl
        isExplicitDisconnect = false

        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(forceRefreshToken)?.await()?.token
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch token: ${e.message}")
            null
        }

        if (token == null) {
            Log.e(TAG, "❌ Cannot connect: Token is null")
            return
        }

        val wsUrl = when {
            baseUrl.startsWith("https://") -> baseUrl.replace("https://", "wss://")
            baseUrl.startsWith("http://") -> baseUrl.replace("http://", "ws://")
            baseUrl.startsWith("ws://") || baseUrl.startsWith("wss://") -> baseUrl
            else -> "wss://$baseUrl"
        }.trimEnd('/')

        val url = "$wsUrl/ws?token=$token"
        Log.d(TAG, "🚀 Connecting to WebSocket: $wsUrl/ws?token=REDACTED")
        val request = Request.Builder().url(url).build()

        webSocket?.close(1000, "Reconnecting")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "🟢 WebSocket Connected! Code: ${response.code}")
                // Resubscribe to all active channels
                activeChannels.forEach { ch ->
                    sendSubscribe(ch)
                }
                startPing()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "🔴 WebSocket Closed: $reason ($code)")
                stopPing()
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.e(TAG, "🔴 WebSocket Failure: ${t.message}, HTTP code: $code")
                stopPing()
                val isAuthError = code == 401 || code == 403
                scheduleReconnect(forceRefreshToken = isAuthError)
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val event = gson.fromJson(text, WsEvent::class.java)
            if (event.type == "pong") return

            val dataStr = if (event.data?.isJsonPrimitive == true) {
                event.data.asString
            } else {
                event.data?.toString() ?: ""
            }

            when (event.type) {
                "message_new" -> {
                    val msg = gson.fromJson(dataStr, MessageDto::class.java)
                    if (msg != null) _incomingMessages.tryEmit(msg)
                }
                "message_update" -> {
                    val msg = gson.fromJson(dataStr, MessageDto::class.java)
                    if (msg != null) _updatedMessages.tryEmit(msg)
                }
                "message_delete" -> {
                    val del = gson.fromJson(dataStr, DeleteMessagePayload::class.java)
                    if (del != null) _deletedMessages.tryEmit(del)
                }
                "chat_update" -> {
                    val chat = gson.fromJson(dataStr, ChatDto::class.java)
                    if (chat != null) _updatedChats.tryEmit(chat)
                }
                "typing" -> {
                    val typing = gson.fromJson(dataStr, TypingPayload::class.java)
                    if (typing != null) _typingEvents.tryEmit(typing)
                }
                "presence" -> {
                    val presence = gson.fromJson(dataStr, PresencePayload::class.java)
                    if (presence != null) _presenceEvents.tryEmit(presence)
                }
                else -> {
                    // Also attempt raw fallback parsing if server sent bare MessageDto without envelope
                    try {
                        val msg = gson.fromJson(text, MessageDto::class.java)
                        if (msg?.id != null && msg.senderId != null) {
                            _incomingMessages.tryEmit(msg)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing WS event: ${e.message}", e)
        }
    }

    fun subscribe(channel: String) {
        activeChannels.add(channel)
        sendSubscribe(channel)
    }

    fun unsubscribe(channel: String) {
        activeChannels.remove(channel)
        sendUnsubscribe(channel)
    }

    fun subscribeChat(chatId: String) = subscribe("chat:$chatId")
    fun unsubscribeChat(chatId: String) = unsubscribe("chat:$chatId")
    fun subscribeUser(uid: String) = subscribe("user:$uid")
    fun unsubscribeUser(uid: String) = unsubscribe("user:$uid")

    private fun sendSubscribe(channel: String) {
        val payload = JsonObject().apply {
            addProperty("type", "subscribe")
            addProperty("channel", channel)
        }
        sendText(payload.toString())
    }

    private fun sendUnsubscribe(channel: String) {
        val payload = JsonObject().apply {
            addProperty("type", "unsubscribe")
            addProperty("channel", channel)
        }
        sendText(payload.toString())
    }

    fun sendTyping(chatId: String) {
        val payload = JsonObject().apply {
            addProperty("type", "typing")
            addProperty("chatId", chatId)
        }
        sendText(payload.toString())
    }

    fun sendStopTyping(chatId: String) {
        val payload = JsonObject().apply {
            addProperty("type", "stop_typing")
            addProperty("chatId", chatId)
        }
        sendText(payload.toString())
    }

    private fun sendText(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(15_000)
                sendText("""{"type":"ping"}""")
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect(forceRefreshToken: Boolean = false) {
        if (isExplicitDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3_000)
            if (!isExplicitDisconnect && isActive) {
                Log.d(TAG, "🔄 Attempting WebSocket reconnect (forceRefresh=$forceRefreshToken)...")
                connect(currentBaseUrl, forceRefreshToken = forceRefreshToken)
            }
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        stopPing()
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
        activeChannels.clear()
        scope.cancel()
    }
}

