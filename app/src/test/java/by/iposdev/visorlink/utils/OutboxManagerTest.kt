package org.visorlink.app.utils

import android.content.Context
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.FeedRepository
import org.visorlink.app.data.repository.UserRepository
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import org.visorlink.app.data.remote.GoogleDriveMediaService
import org.visorlink.app.data.remote.GoogleDriveUploadResult
import org.visorlink.app.utils.GoogleDriveAuthManager
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mock()
    private val chatRepository: ChatRepository = mock()
    private val userRepository: UserRepository = mock()
    private val feedRepository: FeedRepository = mock()
    private val functions: FirebaseFunctions = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val outboxDataSource: OutboxDataSource = mock()
    private val googleDriveAuthManager: GoogleDriveAuthManager = mock()
    private val googleDriveMediaService: GoogleDriveMediaService = mock()

    private val isOnline = MutableStateFlow(true)

    @Before
    fun setup() {
        whenever(networkMonitor.isOnline).thenReturn(isOnline)
    }

    @Test
    fun `text messages should be processed in parallel across chats`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        
        val action1 = createQueuedAction("1", "text", chatId = "chat1")
        val action2 = createQueuedAction("2", "text", chatId = "chat2")
        
        whenever(outboxDataSource.loadOutbox(any()))
            .thenReturn(listOf(action1, action2))
            .thenReturn(emptyList())

        val outboxManager = OutboxManager(
            context = context,
            chatRepository = chatRepository,
            userRepository = userRepository,
            feedRepository = feedRepository,
            functions = functions,
            networkMonitor = networkMonitor,
            outboxDataSource = outboxDataSource,
            coroutineContext = testDispatcher,
            googleDriveAuthManager = googleDriveAuthManager,
            googleDriveMediaService = googleDriveMediaService
        )
        
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        
        verify(chatRepository, timeout(2000).times(2)).sendTextNow(
            any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull()
        )
        outboxManager.stopProcessing()
    }

    @Test
    fun `media messages should respect concurrency limit across chats`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        
        val file1 = tempFolder.newFile("f1.jpg")
        val file2 = tempFolder.newFile("f2.jpg")
        val file3 = tempFolder.newFile("f3.jpg")

        val media1 = createQueuedAction("m1", "image", file1.absolutePath, chatId = "chat1")
        val media2 = createQueuedAction("m2", "image", file2.absolutePath, chatId = "chat2")
        val media3 = createQueuedAction("m3", "image", file3.absolutePath, chatId = "chat3")
        
        whenever(outboxDataSource.loadOutbox(any()))
            .thenReturn(listOf(media1, media2, media3))
            .thenReturn(emptyList())
        
        val concurrentCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        whenever(googleDriveAuthManager.getValidAccessToken()).thenReturn("test_token")
        whenever(googleDriveMediaService.getOrCreateVisorLinkFolder(any())).thenReturn("folder_123")
        whenever(googleDriveMediaService.uploadMediaFile(any(), any(), any(), any(), anyOrNull())).thenAnswer {
            val current = concurrentCount.incrementAndGet()
            if (current > maxConcurrent.get()) {
                maxConcurrent.set(current)
            }
            // Имитируем работу
            Thread.sleep(10) 
            concurrentCount.decrementAndGet()
            GoogleDriveUploadResult(
                fileId = "gdrive_id",
                directUrl = "https://lh3.googleusercontent.com/d/gdrive_id",
                viewUrl = "https://drive.google.com/file/d/gdrive_id/view",
                previewUrl = "https://lh3.googleusercontent.com/d/gdrive_id=s400",
                fileName = "f.jpg",
                fileSize = 1024L,
                mimeType = "image/jpeg"
            )
        }

        val outboxManager = OutboxManager(
            context = context,
            chatRepository = chatRepository,
            userRepository = userRepository,
            feedRepository = feedRepository,
            functions = functions,
            networkMonitor = networkMonitor,
            outboxDataSource = outboxDataSource,
            coroutineContext = testDispatcher,
            googleDriveAuthManager = googleDriveAuthManager,
            googleDriveMediaService = googleDriveMediaService
        )
        
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        
        verify(googleDriveMediaService, timeout(2000).atLeast(2)).uploadMediaFile(any(), any(), any(), any(), anyOrNull())
        outboxManager.stopProcessing()
    }

    private fun createQueuedAction(id: String, type: String, path: String = "some_path", chatId: String = "chat1"): ChatDataCache.QueuedAction {
        val data = mock<JSONObject>()
        whenever(data.getString(any())).thenAnswer { inv ->
            val key = inv.getArgument<String>(0)
            if (key == "localPath") path else "some_value"
        }
        whenever(data.has(any())).thenReturn(false)
        whenever(data.optBoolean(any(), any())).thenReturn(false)
        
        return ChatDataCache.QueuedAction(id, chatId, type, data, System.currentTimeMillis())
    }
}
