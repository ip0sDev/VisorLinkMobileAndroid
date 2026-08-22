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
    suspend fun updateProgress(context: Context, id: String, progress: Float)
}

class ChatDataOutboxSource : OutboxDataSource {
    override suspend fun loadOutbox(context: Context) = ChatDataCache.loadOutbox(context)
    override suspend fun updateStatus(context: Context, id: String, status: Int) {
        ChatDataCache.updateOutboxStatus(context, id, status)
    }
    override suspend fun updateRetry(context: Context, id: String, retryCount: Int, error: String?) {
        ChatDataCache.updateOutboxRetry(context, id, retryCount, error)
    }
    override suspend fun updateProgress(context: Context, id: String, progress: Float) {
        ChatDataCache.updateOutboxProgress(context, id, progress)
    }
}

interface CdnUploader {
    suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): String
}

class DefaultCdnUploader : CdnUploader {
    override suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): String =
        CdnService.uploadFile(file, mimeType, onProgress = onProgress)
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
    private val lastProgressUpdate = ConcurrentHashMap<String, Long>()
    private val mediaSemaphore = Semaphore(MEDIA_CONCURRENCY)

    private suspend fun updateProgressThrottled(actionId: String, progress: Float) {
        val now = System.currentTimeMillis()
        val last = lastProgressUpdate[actionId] ?: 0L
        if (now - last > 150) { // Ограничиваем частоту обновлений до ~7 FPS
            lastProgressUpdate[actionId] = now
            outboxDataSource.updateProgress(context, actionId, progress)
        }
    }

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
                val allQueued = outboxDataSource.loadOutbox(context)
                
                val toProcess = allQueued.filter { action ->
                    action.status == 0 &&
                            !inFlightIds.contains(action.id) &&
                            action.retryCount < MAX_RETRIES &&
                            (now - action.lastAttempt) > (action.retryCount * 10000L)
                }

                if (toProcess.isEmpty()) {
                    // Если нечего делать и ничего не летит — можем поспать подольше или выйти
                    if (inFlightIds.isEmpty()) {
                        // Выходим из цикла, он перезапустится по сигналу
                        break
                    }
                    delay(2000)
                    continue
                }

                // Группируем по чатам для последовательной отправки внутри каждого чата
                val groups = toProcess.groupBy { it.chatId }

                for ((_, actions) in groups) {
                    launch {
                        for (action in actions) {
                            if (!networkMonitor.isOnline.value || !isActive) break
                            // Двойная проверка, так как другой цикл мог подхватить (хотя groupBy это исключает)
                            if (inFlightIds.contains(action.id)) continue

                            inFlightIds.add(action.id)
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
                                val nextRetry = action.retryCount + 1
                                outboxDataSource.updateRetry(context, action.id, nextRetry, e.message)
                                if (nextRetry >= MAX_RETRIES) {
                                    outboxDataSource.updateStatus(context, action.id, 2)
                                }
                                // Прекращаем обработку очереди этого чата при ошибке, чтобы сохранить порядок сообщений
                                break
                            } finally {
                                inFlightIds.remove(action.id)
                            }
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
        val replyTo = if (data.has("replyTo") && !data.isNull("replyTo")) {
            try {
                val r = data.getJSONObject("replyTo")
                ReplyData(
                    id = r.getString("id"),
                    type = r.getString("type"),
                    text = if (r.isNull("text")) null else r.optString("text"),
                    url = if (r.isNull("url")) null else r.optString("url"),
                    senderUsername = r.getString("senderUsername")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse replyTo for action ${action.id}", e)
                null
            }
        } else null

        try {
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
                        val mediaId = cdnUploader.uploadFile(file, "image/jpeg") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
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
                        lastProgressUpdate.remove(action.id)
                    }
                }
                "voice" -> {
                    val localPath = data.getString("localPath")
                    val duration = data.getInt("duration")
                    val file = File(localPath)
                    if (file.exists()) {
                        val mediaId = cdnUploader.uploadFile(file, "audio/webm") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendVoiceNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = mediaId,
                            durationSec = duration,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo
                        )
                        file.delete()
                        lastProgressUpdate.remove(action.id)
                    }
                }
                "video" -> {
                    val localPath = data.getString("localPath")
                    val file = File(localPath)
                    if (file.exists()) {
                        val mediaId = cdnUploader.uploadFile(file, "video/mp4") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendVideoNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = mediaId,
                            fileName = file.name,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo
                        )
                        file.delete()
                        lastProgressUpdate.remove(action.id)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error processing action ${action.id} of type ${action.type}", e)
            throw e
        }
    }
}
