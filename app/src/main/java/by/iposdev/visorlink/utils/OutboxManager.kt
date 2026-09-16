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
import by.iposdev.visorlink.data.remote.GoogleDriveService

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
    suspend fun retryOutbox(context: Context, id: String)
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
    override suspend fun retryOutbox(context: Context, id: String) {
        ChatDataCache.retryOutbox(context, id)
    }
}

interface CdnUploader {
    suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): String
    suspend fun uploadFileWithDetails(file: File, mimeType: String, onProgress: (Float) -> Unit): CdnUploadResult =
        CdnService.uploadFileWithDetails(file, mimeType, onProgress = onProgress)
}

class GoogleDriveCdnUploader(
    private val driveService: GoogleDriveService?,
    private val authManager: GoogleDriveAuthManager?
) : CdnUploader {
    override suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): String {
        if (driveService == null || authManager == null) {
            return CdnService.uploadFile(file, mimeType, onProgress = onProgress)
        }
        val token = authManager.getValidAccessToken()
            ?: throw IllegalStateException("Google Drive не подключен. Перейдите в Настройки -> Хранилище.")
        val folderId = driveService.getOrCreateVisorLinkFolder(token)
        val result = driveService.uploadMedia(token, folderId, file, mimeType, onProgress)
        return result.fileId
    }

    override suspend fun uploadFileWithDetails(file: File, mimeType: String, onProgress: (Float) -> Unit): CdnUploadResult {
        if (driveService == null || authManager == null) {
            return CdnService.uploadFileWithDetails(file, mimeType, onProgress = onProgress)
        }
        val token = authManager.getValidAccessToken()
            ?: throw IllegalStateException("Google Drive не подключен. Перейдите в Настройки -> Хранилище.")
        val folderId = driveService.getOrCreateVisorLinkFolder(token)
        val result = driveService.uploadMedia(token, folderId, file, mimeType, onProgress)
        return CdnUploadResult(
            mediaId = result.fileId,
            thumbUrl = result.thumbnailUrl,
            size = result.fileSize
        )
    }
}

class DefaultCdnUploader : CdnUploader {
    override suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): String =
        CdnService.uploadFile(file, mimeType, onProgress = onProgress)

    override suspend fun uploadFileWithDetails(file: File, mimeType: String, onProgress: (Float) -> Unit): CdnUploadResult =
        CdnService.uploadFileWithDetails(file, mimeType, onProgress = onProgress)
}

class OutboxManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
    private val outboxDataSource: OutboxDataSource = ChatDataOutboxSource(),
    private val cdnUploader: CdnUploader? = null,
    coroutineContext: CoroutineContext = Dispatchers.IO,
    private val fallbackManager: by.iposdev.visorlink.data.repository.BackendFallbackManager? = null,
    private val driveService: by.iposdev.visorlink.data.remote.GoogleDriveService? = null,
    private val driveAuthManager: by.iposdev.visorlink.utils.GoogleDriveAuthManager? = null
) {
    private val uploader: CdnUploader = cdnUploader ?: GoogleDriveCdnUploader(driveService, driveAuthManager)
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var processingJob: Job? = null
    
    private val inFlightIds = ConcurrentHashMap.newKeySet<String>()
    private val inFlightChatIds = ConcurrentHashMap.newKeySet<String>()
    private val lastProgressUpdate = ConcurrentHashMap<String, Long>()
    private val mediaSemaphore = Semaphore(MEDIA_CONCURRENCY)
    @Volatile
    private var lastHealthCheckTimestamp: Long = 0L

    companion object {
        private val inFlightJobs = ConcurrentHashMap<String, Job>()
        @Volatile
        private var instance: OutboxManager? = null

        fun cancelInFlight(actionId: String): Boolean {
            val job = inFlightJobs.remove(actionId)
            job?.cancel()
            return job != null
        }

        fun retry(actionId: String) {
            instance?.let { mgr ->
                mgr.scope.launch { mgr.retryAction(actionId) }
            }
        }
    }

    suspend fun retryAction(actionId: String) {
        outboxDataSource.retryOutbox(context, actionId)
        if (networkMonitor.isOnline.value) startProcessing()
    }

    private suspend fun updateProgressThrottled(actionId: String, progress: Float) {
        val now = System.currentTimeMillis()
        val last = lastProgressUpdate[actionId] ?: 0L
        if (now - last > 150) { // Ограничиваем частоту обновлений до ~7 FPS
            lastProgressUpdate[actionId] = now
            outboxDataSource.updateProgress(context, actionId, progress)
        }
    }

    init {
        instance = this
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
                            (now - action.lastAttempt) >= (action.retryCount * 5000L)
                }

                if (toProcess.isEmpty()) {
                    if (inFlightIds.isEmpty() && inFlightChatIds.isEmpty()) {
                        break
                    }
                    delay(1500)
                    continue
                }

                // Группируем по чатам для строго последовательной отправки внутри каждого чата
                val groups = toProcess.groupBy { it.chatId }

                for ((chatId, actions) in groups) {
                    // Если чат уже обрабатывается активной корутиной — не запускаем параллельную отправку
                    if (!inFlightChatIds.add(chatId)) continue

                    launch {
                        try {
                            for (action in actions) {
                                if (!networkMonitor.isOnline.value || !isActive) break
                                if (inFlightIds.contains(action.id)) continue

                                inFlightIds.add(action.id)
                                val job = coroutineContext[Job]
                                if (job != null) inFlightJobs[action.id] = job
                                try {
                                    if (action.type == "image" || action.type == "voice" || action.type == "video") {
                                        mediaSemaphore.withPermit {
                                            withTimeout(120_000) {
                                                processAction(action)
                                            }
                                        }
                                    } else {
                                        withTimeout(60_000) {
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
                                    // Прекращаем обработку очереди этого чата при ошибке, чтобы сохранить строгий порядок сообщений (FIFO)
                                    break
                                } finally {
                                    inFlightJobs.remove(action.id)
                                    inFlightIds.remove(action.id)
                                }
                            }
                        } finally {
                            inFlightChatIds.remove(chatId)
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
        processingJob = null
        inFlightJobs.values.forEach { it.cancel() }
        inFlightJobs.clear()
        inFlightIds.clear()
        inFlightChatIds.clear()
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
        val topicId = if (data.has("topicId") && !data.isNull("topicId")) data.getString("topicId") else null
        val nextSeq = if (data.has("nextSeq") && !data.isNull("nextSeq")) data.optLong("nextSeq") else null
        val isDirect = data.optBoolean("isDirect", false)
        val otherUserId = if (data.has("otherUserId") && !data.isNull("otherUserId")) data.optString("otherUserId") else null

        try {
            // Идемпотентность: если это повторная попытка, проверяем, не было ли сообщение уже сохранено в Firestore
            if (action.retryCount > 0) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val existing = db.collection("chats").document(action.chatId)
                        .collection("messages").document(action.id).get().await()
                    if (existing.exists()) {
                        Log.d(TAG, "Message ${action.id} already committed in Firestore in earlier attempt, skipping duplicate send")
                        val localPath = data.optString("localPath")
                        if (localPath.isNotEmpty()) {
                            try { File(localPath).delete() } catch (_: Exception) {}
                        }
                        return
                    }
                } catch (_: Exception) {
                    // Игнорируем сетевые ошибки при проверке
                }
            }

            when (action.type) {
                "text" -> {
                    withTimeout(20_000) {
                        chatRepository.sendTextNow(
                            id = action.id,
                            chatId = action.chatId,
                            text = data.getString("text"),
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId
                        )
                    }
                }
                "image" -> {
                    val localPath = data.getString("localPath")
                    val isSpoiler = data.optBoolean("isSpoiler", false)
                    val file = File(localPath)
                    if (file.exists()) {
                        val mediaId = uploader.uploadFile(file, "image/jpeg") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendImageNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = mediaId,
                            fileName = file.name,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo,
                            isSpoiler = isSpoiler,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId
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
                        val mediaId = uploader.uploadFile(file, "audio/webm") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendVoiceNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = mediaId,
                            durationSec = duration,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId
                        )
                        file.delete()
                        lastProgressUpdate.remove(action.id)
                    }
                }
                "audio" -> {
                    val localPath = data.getString("localPath")
                    val title = data.optString("title", "")
                    val performer = data.optString("performer", "")
                    val duration = data.optInt("duration", 0)
                    val coverLocalPath = data.optString("coverLocalPath", "")
                    val file = File(localPath)
                    if (file.exists()) {
                        var coverMediaId: String? = null
                        if (coverLocalPath.isNotBlank()) {
                            val coverFile = File(coverLocalPath)
                            if (coverFile.exists()) {
                                try {
                                    coverMediaId = uploader.uploadFile(coverFile, "image/jpeg") {}
                                    coverFile.delete()
                                } catch (_: Exception) {}
                            }
                        }
                        val mediaId = uploader.uploadFile(file, "audio/mpeg") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendAudioNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = mediaId,
                            fileName = file.name,
                            fileSize = file.length(),
                            title = title,
                            performer = performer,
                            durationSec = duration,
                            coverMediaId = coverMediaId,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId
                        )
                        file.delete()
                        lastProgressUpdate.remove(action.id)
                    }
                }
                "video" -> {
                    val localPath = data.getString("localPath")
                    val file = File(localPath)
                    if (file.exists()) {
                        val uploadResult = uploader.uploadFileWithDetails(file, "video/mp4") { progress ->
                            scope.launch { updateProgressThrottled(action.id, progress) }
                        }
                        chatRepository.sendVideoNow(
                            id = action.id,
                            chatId = action.chatId,
                            mediaId = uploadResult.mediaId,
                            fileName = file.name,
                            senderUsername = data.getString("senderUsername"),
                            replyTo = replyTo,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId,
                            duration = uploadResult.duration,
                            width = uploadResult.width,
                            height = uploadResult.height,
                            thumbUrl = uploadResult.thumbUrl
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
                            replyTo = replyTo,
                            topicId = topicId,
                            nextSeq = nextSeq,
                            isDirect = isDirect,
                            otherUserId = otherUserId
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
            val now = System.currentTimeMillis()
            if (fallbackManager != null && (now - lastHealthCheckTimestamp > 30_000L)) {
                lastHealthCheckTimestamp = now
                scope.launch {
                    fallbackManager.checkHealth()
                }
            }
            throw e
        }
    }
}
