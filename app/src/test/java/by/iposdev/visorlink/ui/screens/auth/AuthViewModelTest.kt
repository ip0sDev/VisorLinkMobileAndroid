package by.iposdev.visorlink.ui.screens.auth

import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock {
            on { authState } doReturn MutableStateFlow(AuthState.NoSession)
        }
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login with blank email sets error`() {
        viewModel.login("", "password123")
        assertEquals("Please fill in all fields", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login with blank password sets error`() {
        viewModel.login("user@example.com", "")
        assertEquals("Please fill in all fields", viewModel.uiState.value.error)
    }

    @Test
    fun `login success sets success state`() = runTest {
        whenever(authRepository.login(any(), any())).thenReturn(Unit)

        viewModel.login("user@example.com", "password123")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.success)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login failure sets error message`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenThrow(RuntimeException("Wrong password"))

        viewModel.login("user@example.com", "wrongpass")
        advanceUntilIdle()

        assertEquals("Wrong password", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.success)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `login trims email whitespace`() = runTest {
        whenever(authRepository.login(any(), any())).thenReturn(Unit)

        viewModel.login("  user@example.com  ", "password123")
        advanceUntilIdle()

        verify(authRepository).login("user@example.com", "password123")
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    fun `register with blank username sets error`() {
        viewModel.register("user@example.com", "password123", "")
        assertEquals("Please fill in all fields", viewModel.uiState.value.error)
    }

    @Test
    fun `register success sets success state`() = runTest {
        whenever(authRepository.register(any(), any(), any())).thenReturn(Unit)

        viewModel.register("user@example.com", "password123", "alice")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.success)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `register failure sets error`() = runTest {
        whenever(authRepository.register(any(), any(), any()))
            .thenThrow(RuntimeException("Email already in use"))

        viewModel.register("user@example.com", "password123", "alice")
        advanceUntilIdle()

        assertEquals("Email already in use", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.success)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun `clearError removes error without touching other fields`() = runTest {
        whenever(authRepository.login(any(), any()))
            .thenThrow(RuntimeException("fail"))
        viewModel.login("u@u.com", "pass")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
        // success must remain unchanged (false)
        assertFalse(viewModel.uiState.value.success)
    }

    // ── email verification ────────────────────────────────────────────────────

    @Test
    fun `startVerificationPolling sets isPolling to true`() {
        viewModel.startVerificationPolling()
        assertTrue(viewModel.verifyState.value.isPolling)
    }

    @Test
    fun `stopVerificationPolling sets isPolling to false`() {
        viewModel.startVerificationPolling()
        viewModel.stopVerificationPolling()
        assertFalse(viewModel.verifyState.value.isPolling)
    }

    @Test
    fun `startVerificationPolling does not launch duplicate jobs`() {
        viewModel.startVerificationPolling()
        viewModel.startVerificationPolling() // second call should be no-op
        assertTrue(viewModel.verifyState.value.isPolling)
    }

    @Test
    fun `resendVerificationEmail does nothing when cooldown active`() = runTest {
        // 1. Успешная отправка письма
        whenever(authRepository.resendVerificationEmail()).thenReturn(Unit)

        // 2. Вызываем метод в первый раз
        viewModel.resendVerificationEmail()

        // ВАЖНО: Используем runCurrent() вместо advanceUntilIdle()
        // Это позволит выполнить код до первого delay(1000) внутри startResendCooldown()
        runCurrent()

        // 3. Проверяем, что таймер запустился (cooldown стал 60)
        val state = viewModel.verifyState.value
        assertTrue("Expected cooldown > 0, was ${state.resendCooldown}", state.resendCooldown > 0)

        // 4. Пытаемся отправить письмо еще раз, пока кулдаун активен
        viewModel.resendVerificationEmail()
        runCurrent()

        // 5. Проверяем, что репозиторий был вызван только ОДИН раз
        verify(authRepository, times(1)).resendVerificationEmail()
    }

    @Test
    fun `resendVerificationEmail sets error on failure`() = runTest {
        whenever(authRepository.resendVerificationEmail())
            .thenThrow(RuntimeException("Too many requests"))

        viewModel.resendVerificationEmail()
        advanceUntilIdle()

        assertEquals("Too many requests", viewModel.verifyState.value.error)
        assertFalse(viewModel.verifyState.value.resendLoading)
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout stops polling and calls repository logout`() {
        viewModel.startVerificationPolling()
        viewModel.logout()

        assertFalse(viewModel.verifyState.value.isPolling)
        verify(authRepository).logout()
    }
}