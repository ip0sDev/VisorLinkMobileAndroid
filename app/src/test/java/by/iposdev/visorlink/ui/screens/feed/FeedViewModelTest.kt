package org.visorlink.app.ui.screens.feed

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.CuratedChannel
import org.visorlink.app.data.model.FEED_LIKE_EMOJI
import org.visorlink.app.data.model.FeedPost
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.FeedRepository
import org.visorlink.app.data.repository.FeedSubscriptions

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val channel = Chat(id = "c1", type = "channel", name = "Новости")
    private val subscriptions = MutableStateFlow<FeedSubscriptions?>(null)

    private lateinit var feedRepository: FeedRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var auth: FirebaseAuth

    private fun post(id: String, likedBy: List<String> = emptyList(), sender: String = "author") = FeedPost(
        channel,
        Message(
            id = id, senderId = sender, text = "пост", createdAt = Timestamp(100, 0),
            reactions = if (likedBy.isEmpty()) emptyList() else listOf(mapOf("emoji" to FEED_LIKE_EMOJI, "uids" to likedBy, "count" to likedBy.size.toLong())),
        ),
    )

    private fun emit(vararg posts: FeedPost, channels: List<Chat> = listOf(channel)) {
        subscriptions.value = FeedSubscriptions(channels, posts.toList())
    }

    private fun vm(): FeedViewModel = FeedViewModel(feedRepository, chatRepository, auth)

    private val FeedViewModel.first get() = uiState.value.posts.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        feedRepository = mock {
            on { subscriptionsFlow(any()) } doReturn subscriptions.filterNotNull()
        }
        chatRepository = mock()
        val user = mock<FirebaseUser> { on { uid } doReturn "me" }
        auth = mock { on { currentUser } doReturn user }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Лайки ─────────────────────────────────────────────────────────────────

    @Test
    fun `like shows immediately and sends the wanted state`() = runTest {
        whenever(feedRepository.setLike(any(), any(), any())).thenReturn(true)
        val vm = vm()
        emit(post("m1", likedBy = listOf("a")))

        vm.toggleLike(vm.first)

        assertTrue(vm.first.isLikedBy("me"))
        assertEquals(2, vm.first.likeCount)
        verify(feedRepository).setLike("c1", "m1", true)
    }

    @Test
    fun `failed like is rolled back and reported`() = runTest {
        whenever(feedRepository.setLike(any(), any(), any())).thenThrow(RuntimeException("offline"))
        val vm = vm()
        val events = mutableListOf<FeedEvent>()
        backgroundScope.launch(testDispatcher) { vm.events.collect { events += it } }
        emit(post("m1"))

        vm.toggleLike(vm.first)

        assertFalse(vm.first.isLikedBy("me"))
        assertEquals(0, vm.first.likeCount)
        assertEquals(listOf<FeedEvent>(FeedEvent.LikeFailed), events)
    }

    @Test
    fun `double tap while a request is in flight never sends two likes`() = runTest {
        val firstCall = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Boolean>()
        feedRepository.stub {
            onBlocking { setLike(any(), any(), any()) } doSuspendableAnswer { inv ->
                val liked = inv.getArgument<Boolean>(2)
                sent += liked
                if (sent.size == 1) firstCall.await() else liked
            }
        }
        val vm = vm()
        emit(post("m1"))

        vm.toggleLike(vm.first) // лайк — запрос висит
        vm.toggleLike(vm.first) // передумал
        vm.toggleLike(vm.first) // и снова лайк
        assertEquals(listOf(true), sent)
        assertTrue(vm.first.isLikedBy("me"))

        firstCall.complete(true)
        advanceUntilIdle()
        // Итог совпал с уже отправленным — второго запроса нет
        assertEquals(listOf(true), sent)
        assertEquals(1, vm.first.likeCount)
    }

    @Test
    fun `last tap wins after the in-flight request`() = runTest {
        val firstCall = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Boolean>()
        feedRepository.stub {
            onBlocking { setLike(any(), any(), any()) } doSuspendableAnswer { inv ->
                val liked = inv.getArgument<Boolean>(2)
                sent += liked
                if (sent.size == 1) firstCall.await() else liked
            }
        }
        val vm = vm()
        emit(post("m1"))

        vm.toggleLike(vm.first)
        vm.toggleLike(vm.first)
        firstCall.complete(true)
        advanceUntilIdle()

        assertEquals(listOf(true, false), sent)
        assertFalse(vm.first.isLikedBy("me"))
        assertEquals(0, vm.first.likeCount)
    }

    @Test
    fun `optimistic like survives until the server snapshot catches up`() = runTest {
        whenever(feedRepository.setLike(any(), any(), any())).thenReturn(true)
        val vm = vm()
        emit(post("m1"))

        vm.toggleLike(vm.first)
        // Снимок ещё без моего лайка (пришёл раньше записи) — кнопка не мигает назад
        emit(post("m1"))
        assertTrue(vm.first.isLikedBy("me"))

        emit(post("m1", likedBy = listOf("me")))
        assertEquals(1, vm.first.likeCount)
        // Намерение снято: дальше правда — у сервера (например, сняли лайк с другого устройства)
        emit(post("m1"))
        assertFalse(vm.first.isLikedBy("me"))
    }

    @Test
    fun `like is ignored when the channel disables reactions`() = runTest {
        val vm = vm()
        val locked = post("m1").let { it.copy(channel = it.channel.copy(settings = it.channel.settings.copy(allowReactions = false))) }
        emit(locked)

        vm.toggleLike(vm.first)

        verify(feedRepository, never()).setLike(any(), any(), any())
    }

    // ── Просмотры ────────────────────────────────────────────────────────────

    @Test
    fun `views are sent once per post, grouped by channel, without own posts`() = runTest {
        val vm = vm()
        val other = Chat(id = "c2", type = "channel")
        emit(post("m1"), post("m2"), post("mine", sender = "me"), FeedPost(other, Message(id = "x", senderId = "author")))

        vm.onPostsSeen(setOf("c1/m1", "c1/m2", "c1/mine", "c2/x", "header"))
        vm.onPostsSeen(setOf("c1/m1", "c1/m2"))

        verify(feedRepository).recordViews("c1", listOf("m1", "m2"))
        verify(feedRepository).recordViews("c2", listOf("x"))
        verify(feedRepository, times(2)).recordViews(any(), any())
    }

    @Test
    fun `failed view is retried next time the post is seen`() = runTest {
        whenever(feedRepository.recordViews(any(), any())).thenThrow(RuntimeException("offline")).thenReturn(Unit)
        val vm = vm()
        emit(post("m1"))

        vm.onPostsSeen(setOf("c1/m1"))
        vm.onPostsSeen(setOf("c1/m1"))
        vm.onPostsSeen(setOf("c1/m1"))

        verify(feedRepository, times(2)).recordViews("c1", listOf("m1"))
    }

    // ── Вкладки и каталог ────────────────────────────────────────────────────

    @Test
    fun `without subscriptions the catalog opens by itself`() = runTest {
        whenever(feedRepository.curatedChannels()).thenReturn(listOf(CuratedChannel("c9", "Каталог")))
        val vm = vm()

        emit(channels = emptyList())

        assertEquals(FeedTab.CURATED, vm.uiState.value.tab)
        assertEquals(listOf("c9"), vm.uiState.value.curated.map { it.id })
    }

    @Test
    fun `a tab chosen by hand is kept`() = runTest {
        val vm = vm()
        vm.selectTab(FeedTab.SUBSCRIPTIONS)

        emit(channels = emptyList())

        assertEquals(FeedTab.SUBSCRIPTIONS, vm.uiState.value.tab)
    }

    @Test
    fun `join marks the channel subscribed`() = runTest {
        whenever(chatRepository.joinChannel(any(), anyOrNull())).thenReturn(true)
        val vm = vm()
        emit(post("m1"))

        vm.join(CuratedChannel("c9", "Каталог", tag = "news"))

        verify(chatRepository).joinChannel("c9", "news")
        assertTrue("c9" in vm.uiState.value.subscribedIds)
        assertTrue(vm.uiState.value.joining.isEmpty())
    }
}
