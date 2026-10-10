package org.visorlink.app.ui.screens.auth

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.visorlink.app.data.auth.TfaGate
import org.visorlink.app.data.auth.TfaGateHold
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.AuthState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.FcmManager
import org.visorlink.app.utils.TfaManager

/**
 * Ворота 2FA: «нужен код» — только по ответу сервера; молчание — экран кода с пометкой,
 * а не ложная блокировка; Google-аккаунт без профиля — шаг завершения регистрации.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TfaGateTest {

    private val dispatcher = StandardTestDispatcher()
    private val user: FirebaseUser = mock { on { uid } doReturn "u1" }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        profile: UserProfile?,
        exists: Flow<Boolean> = flowOf(true),
        server: Flow<Boolean> = emptyFlow(),
        cachedPass: Boolean = false,
        hasPassword: Boolean = true,
        tfaManager: TfaManager = mock { on { isTfaPassed(any()) } doReturn cachedPass },
    ): AuthViewModel {
        val auth: AuthRepository = mock {
            on { authState } doReturn MutableStateFlow<AuthState>(AuthState.Verified(user))
            on { getCurrentAuthState() } doReturn AuthState.Verified(user)
            on { currentUser } doReturn user
            on { this.hasPassword } doReturn hasPassword
            on { getAuthTime() } doReturn "1700000000"
            on { sessionAuthorizedFlow(any(), any()) } doReturn server
        }
        val users: UserRepository = mock {
            on { userProfileFlow(any()) } doReturn flowOf(profile)
            on { profileExistsFlow(any()) } doReturn exists
        }
        val fcm: FcmManager = mock()
        return AuthViewModel(auth, users, tfaManager, fcm, TfaGateHold())
    }

    private fun TestScope.settle() { runCurrent() }

    @Test
    fun `2FA off lets the session in`() = runTest(dispatcher) {
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = false))
        settle()
        assertEquals(TfaGate.Off, vm.tfaGate.value)
        assertTrue(vm.isSessionReady.value)
    }

    @Test
    fun `session confirmed earlier on this device passes without the server`() = runTest(dispatcher) {
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = true), cachedPass = true)
        settle()
        assertEquals(TfaGate.Passed, vm.tfaGate.value)
        assertTrue(vm.isSessionReady.value)
    }

    @Test
    fun `server says authorized`() = runTest(dispatcher) {
        val tfa: TfaManager = mock { on { isTfaPassed(any()) } doReturn false }
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = true), server = flowOf(true), tfaManager = tfa)
        settle()
        assertEquals(TfaGate.Passed, vm.tfaGate.value)
        verify(tfa).setTfaPassed("1700000000")
    }

    @Test
    fun `server says no session - code required`() = runTest(dispatcher) {
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = true), server = flowOf(false))
        settle()
        assertEquals(TfaGate.Required(slow = false), vm.tfaGate.value)
        assertTrue(vm.isTfaRequired.value)
        assertFalse(vm.isSessionReady.value)
    }

    @Test
    fun `silent server shows the code screen with a note but keeps listening`() = runTest(dispatcher) {
        val late = flow {
            kotlinx.coroutines.delay(20_000)
            emit(true)
        }
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = true), server = late)
        settle()
        assertEquals(TfaGate.Checking, vm.tfaGate.value)
        advanceTimeBy(TfaGate.SLOW_CHECK_MS + 1); runCurrent()
        assertEquals(TfaGate.Required(slow = true), vm.tfaGate.value)
        // Документ всё-таки пришёл — экран закрывается сам
        advanceTimeBy(10_000); runCurrent()
        assertEquals(TfaGate.Passed, vm.tfaGate.value)
    }

    @Test
    fun `read error is not a silent block`() = runTest(dispatcher) {
        val failing = flow<Boolean> { throw IllegalStateException("PERMISSION_DENIED") }
        val vm = vm(UserProfile(uid = "u1", tfaEnabled = true), server = failing)
        settle()
        assertEquals(TfaGate.Required(slow = true), vm.tfaGate.value)
    }

    @Test
    fun `google account without profile must finish sign-up`() = runTest(dispatcher) {
        val vm = vm(profile = null, exists = flowOf(false), hasPassword = false)
        settle()
        assertTrue(vm.needsGoogleSignup.value)
        assertFalse(vm.isSessionReady.value)
    }

    @Test
    fun `password account without profile is not sent to google sign-up`() = runTest(dispatcher) {
        val vm = vm(profile = null, exists = flowOf(false), hasPassword = true)
        settle()
        assertFalse(vm.needsGoogleSignup.value)
    }

    @Test
    fun `missing profile from offline cache is not an answer`() = runTest(dispatcher) {
        val tfa: TfaManager = mock()
        // profileExistsFlow молчит (только кэш без документа) — регистрацию не предлагаем
        val vm = vm(profile = null, exists = emptyFlow(), hasPassword = false, tfaManager = tfa)
        settle()
        assertFalse(vm.needsGoogleSignup.value)
        assertEquals(TfaGate.Checking, vm.tfaGate.value)
        verify(tfa, never()).setTfaPassed(any())
    }
}
