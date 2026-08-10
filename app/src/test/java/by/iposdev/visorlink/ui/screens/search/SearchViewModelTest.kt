package by.iposdev.visorlink.ui.screens.search

import by.iposdev.visorlink.data.model.TagSearchResult
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseUser: FirebaseUser
    private lateinit var viewModel: SearchViewModel

    private val me = UserProfile(uid = "me", username = "me_user", displayName = "Me")
    private val other = UserProfile(uid = "other", username = "alice", displayName = "Alice")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        userRepository = mock {
            on { currentUserFlow() } doReturn flowOf(me)
        }
        chatRepository = mock()
        firebaseUser = mock { on { uid } doReturn "me" }
        auth = mock { on { currentUser } doReturn firebaseUser }

        viewModel = SearchViewModel(userRepository, chatRepository, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── onQueryChange ─────────────────────────────────────────────────────────

    @Test
    fun `onQueryChange trims and removes @ prefix`() {
        viewModel.onQueryChange("@alice")
        assertEquals("alice", viewModel.uiState.value.query)
    }

    @Test
    fun `onQueryChange clears previous results`() = runTest {
        whenever(userRepository.findUserByUsername(any())).thenReturn(other)
        whenever(chatRepository.findByTag(any())).thenReturn(TagSearchResult(found = false))
        viewModel.search()
        advanceUntilIdle()

        viewModel.onQueryChange("new query")
        assertNull(viewModel.uiState.value.userResult)
        assertNull(viewModel.uiState.value.chatResult)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.notFound)
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    fun `search does nothing when query is empty`() = runTest {
        // Устанавливаем пустой запрос
        viewModel.onQueryChange("")

        // Запускаем поиск
        viewModel.search()
        runCurrent() // или advanceUntilIdle()

        // Проверяем, что методы поиска НЕ вызывались
        verify(userRepository, never()).findUserByUsername(any())
        verify(chatRepository, never()).findByTag(any())

        // А currentUserFlow() игнорируем, он нам тут не мешает
    }

    @Test
    fun `search finds user and sets userResult`() = runTest {
        whenever(userRepository.findUserByUsername("alice")).thenReturn(other)
        whenever(chatRepository.findByTag("alice")).thenReturn(TagSearchResult(found = false))

        viewModel.onQueryChange("alice")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(other, viewModel.uiState.value.userResult)
        assertNull(viewModel.uiState.value.chatResult)
        assertFalse(viewModel.uiState.value.notFound)
    }

    @Test
    fun `search finds channel and sets chatResult`() = runTest {
        val channel = TagSearchResult(found = true, chatId = "c1", name = "Dev", tag = "dev", type = "channel")
        whenever(userRepository.findUserByUsername(any())).thenReturn(null)
        whenever(chatRepository.findByTag("dev")).thenReturn(channel)

        viewModel.onQueryChange("dev")
        viewModel.search()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userResult)
        assertEquals(channel, viewModel.uiState.value.chatResult)
    }

    @Test
    fun `search sets notFound when nothing found`() = runTest {
        whenever(userRepository.findUserByUsername(any())).thenReturn(null)
        whenever(chatRepository.findByTag(any())).thenReturn(TagSearchResult(found = false))

        viewModel.onQueryChange("nobody")
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.notFound)
        assertNull(viewModel.uiState.value.userResult)
        assertNull(viewModel.uiState.value.chatResult)
    }

    @Test
    fun `search sets notFound when exception occurs`() = runTest {
        viewModel.onQueryChange("error_user")
        whenever(userRepository.findUserByUsername(any())).thenThrow(RuntimeException("Network Error"))
        whenever(chatRepository.findByTag(any())).thenThrow(RuntimeException("Network Error"))

        viewModel.search()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.notFound)
        assertNull(state.error) // Ошибку мы не показываем пользователю напрямую
    }

    @Test
    fun `search filters out self from results`() = runTest {
        // me.uid == auth.currentUser.uid
        whenever(userRepository.findUserByUsername("me_user")).thenReturn(me)
        whenever(chatRepository.findByTag(any())).thenReturn(TagSearchResult(found = false))

        viewModel.onQueryChange("me_user")
        viewModel.search()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userResult)
        assertTrue(viewModel.uiState.value.notFound)
        assertEquals("That's you!", viewModel.uiState.value.error)
    }

    // ── initSearch ────────────────────────────────────────────────────────────

    @Test
    fun `initSearch runs search when initial query is non-blank`() = runTest {
        whenever(userRepository.findUserByUsername(any())).thenReturn(null)
        whenever(chatRepository.findByTag(any())).thenReturn(TagSearchResult(found = false))

        viewModel.initSearch("alice")
        advanceUntilIdle()

        assertEquals("alice", viewModel.uiState.value.query)
        verify(userRepository).findUserByUsername("alice")
    }

    @Test
    fun `initSearch does nothing when initial query is blank`() = runTest {
        viewModel.initSearch(null)
        advanceUntilIdle()

        verify(userRepository, never()).findUserByUsername(any())
    }

    @Test
    fun `initSearch does not override existing query`() = runTest {
        viewModel.onQueryChange("existing")
        viewModel.initSearch("new")
        advanceUntilIdle()

        // query should stay "existing" — initSearch is no-op if query already set
        assertEquals("existing", viewModel.uiState.value.query)
    }
}