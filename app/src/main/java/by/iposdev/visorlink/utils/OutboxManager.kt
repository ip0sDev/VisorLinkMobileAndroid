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

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

private const val TAG = "OutboxManager"
private const val MAX_RETRIES = 5
private const val MEDIA_CONCURRENCY = 2

interface OutboxDataSource {
    suspend fun loadOutbox(context: Context): List<ChatDataCache.QueuedAction>
    suspend fun updateStatus(context: Context, id: String, status: Int)
    suspend fun updateRetry(context: Context, id: String, retryCount: Int, error: String?)
}

class ChatDataOutboxSource : OutboxDataSource {
    override suspend fun loadOutbox(context: Context) = ChatDataCache.loadOutbox(context)
    override suspend fun updateStatus(context: Context, id: String, status: Int) {
        ChatDataCache.updateOutboxStatus(context, id, status)
    }
    override suspend fun updateRetry(context: Context, id: String, retryCount: Int, error: String?) {
        ChatDataCache.updateOutboxRetry(context, id, retryCount, error)
    }
}

interface CdnUploader {
    suspend fun uploadFile(file: File, mimeType: String): String
}

class DefaultCdnUploader : CdnUploader {
    override suspend fun uploadFile(file: File, mimeType: String): String = CdnService.uploadFile(file, mimeType)
}

class OutboxManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
    private val outboxDataSource: OutboxDataSource = ChatDataOutboxSource(),
    private val cdnUploader: CdnUploader = DefaultCdnUploader(),
    coroutineContext: CoroutineContext = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var processingJob: Job? = null
    
    private val inFlightIds = ConcurrentHashMap.newKeySet<String>()
    private val mediaSemaphore = Semaphore(MEDIA_CONCURRENCY)

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
                val now = System.currentTimeMillis()
                val queued = outboxDataSource.loadOutbox(context)
                    .filter { action ->
                        action.status == 0 && 
                        !inFlightIds.contains(action.id) &&
                        action.retryCount < MAX_RETRIES &&
                        (now - action.lastAttempt) > (action.retryCount * 10000L)
                    }

                if (queued.isEmpty()) break // Останавливаем цикл, если очередь пуста. 
                                            // Он перезапустится по сигналу outboxSignal или при смене сети.
                
                for (action in queued) {
                    if (!networkMonitor.isOnline.value) break
                    
                    inFlightIds.add(action.id)
                    launch {
                        try {
                            withTimeout(120_000) { 
                                if (action.type == "image" || action.type == "voice" || action.type == "video") {
                                    mediaSemaphore.withPermit { processAction(action) }
                                } else {
                                    processAction(action)
                                }
                            }
                            outboxDataSource.updateStatus(context, action.id, 1)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process outbox action ${action.id}", e)
                            val nextRetry = action.retryCount + 1
                            outboxDataSource.updateRetry(context, action.id, nextRetry, e.message)
                            if (nextRetry >= MAX_RETRIES) {
                                outboxDataSource.updateStatus(context, action.id, 2)
                            }
                        } finally {
                            inFlightIds.remove(action.id)
                        }
                    }
                }
                delay(2000) 
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
        inFlightIds.clear()
        scope.cancel() // Cancel the whole scope to stop any background launches
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
                withTimeout(20_000) {
                    chatRepository.sendTextNow(
                        id = action.id,
                        chatId = action.chatId,
                        text = data.getString("text"),
                        senderUsername = data.getString("senderUsername"),
                        replyTo = replyTo
                    )
                }
            }
            "image" -> {
                val localPath = data.getString("localPath")
                val isSpoiler = data.optBoolean("isSpoiler", false)
                val file = File(localPath)
                if (file.exists()) {
                    val mediaId = cdnUploader.uploadFile(file, "image/jpeg")
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
                    val mediaId = cdnUploader.uploadFile(file, "audio/webm")
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
            "video" -> {
                val localPath = data.getString("localPath")
                val file = File(localPath)
                if (file.exists()) {
                    val mediaId = cdnUploader.uploadFile(file, "video/mp4")
                    chatRepository.sendVideoNow(
                        id = action.id,
                        chatId = action.chatId,
                        mediaId = mediaId,
                        fileName = file.name,
                        senderUsername = data.getString("senderUsername"),
                        replyTo = replyTo
                    )
                    file.delete()
                }
            }
            "sticker" -> {
                withTimeout(20_000) {
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
            }
            "like" -> {
                withTimeout(15_000) {
                    val messageId = data.getString("messageId")
                    val isLiked = data.getBoolean("isLiked")
                    functions.getHttpsCallable("toggleLike")
                        .call(mapOf("chatId" to action.chatId, "messageId" to messageId, "isLiked" to isLiked))
                        .await()
                }
            }
        }
    }
}
