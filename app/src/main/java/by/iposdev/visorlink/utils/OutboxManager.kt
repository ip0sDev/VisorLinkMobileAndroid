package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import by.iposdev.visorlink.data.model.ReplyData
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.data.repository.FeedRepository
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File

private const val TAG = "OutboxManager"

class OutboxManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processingJob: Job? = null

    init {
        scope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                if (online) {
                    startProcessing()
                } else {
                    stopProcessing()
                }
            }
        }
        scope.launch {
            ChatDataCache.outboxSignal.collect {
                if (networkMonitor.isOnline.value) startProcessing()
            }
        }
    }

    private fun startProcessing() {
        if (processingJob?.isActive == true) return
        processingJob = scope.launch {
            while (isActive) {
                val queued = ChatDataCache.loadOutbox(context).filter { it.status == 0 }
                if (queued.isEmpty()) {
                    delay(3000)
                    continue
                }

                for (action in queued) {
                    if (!networkMonitor.isOnline.value) break
                    
                    try {
                        processAction(action)
                        ChatDataCache.updateOutboxStatus(context, action.id, 1) // Mark as sent, but don't delete yet
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process outbox action ${action.id}", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    private fun stopProcessing() {
        processingJob?.cancel()
    }

    private suspend fun processAction(action: ChatDataCache.QueuedAction) {
        val data = action.data
        val replyTo = if (data.has("replyTo")) {
            val r = data.getJSONObject("replyTo")
            ReplyData(
                id = r.getString("id"),
                type = r.getString("type"),
                text = r.optString("text"),
                url = r.optString("url"),
                senderUsername = r.getString("senderUsername")
            )
        } else null

        when (action.type) {
            "text" -> {
                chatRepository.sendTextNow(
                    id = action.id,
                    chatId = action.chatId,
                    text = data.getString("text"),
                    senderUsername = data.getString("senderUsername"),
                    replyTo = replyTo
                )
            }
            "image" -> {
                val localPath = data.getString("localPath")
                val isSpoiler = data.optBoolean("isSpoiler", false)
                val file = File(localPath)
                if (file.exists()) {
                    val mediaId = CdnService.uploadFile(file, "image/jpeg")
                    chatRepository.sendImageNow(
                        id = action.id,
                        chatId = action.chatId,
                        mediaId = mediaId,
                        fileName = file.name,
                        senderUsername = data.getString("senderUsername"),
                        replyTo = replyTo,
                        isSpoiler = isSpoiler
                    )
                    file.delete()
                }
            }
            "voice" -> {
                val localPath = data.getString("localPath")
                val duration = data.getInt("duration")
                val file = File(localPath)
                if (file.exists()) {
                    val mediaId = CdnService.uploadFile(file, "audio/webm")
                    chatRepository.sendVoiceNow(
                        id = action.id,
                        chatId = action.chatId,
                        mediaId = mediaId,
                        durationSec = duration,
                        senderUsername = data.getString("senderUsername"),
                        replyTo = replyTo
                    )
                    file.delete()
                }
            }
            "sticker" -> {
                chatRepository.sendStickerNow(
                    id = action.id,
                    chatId = action.chatId,
                    stickerId = data.getString("stickerId"),
                    url = data.getString("url"),
                    packId = data.getString("packId"),
                    packName = data.getString("packName"),
                    packEmoji = data.getString("packEmoji"),
                    senderUsername = data.getString("senderUsername"),
                    replyTo = replyTo
                )
            }
            "like" -> {
                val messageId = data.getString("messageId")
                val isLiked = data.getBoolean("isLiked")
                functions.getHttpsCallable("toggleLike")
                    .call(mapOf("chatId" to action.chatId, "messageId" to messageId, "isLiked" to isLiked))
                    .await()
            }
        }
    }
}
