package by.iposdev.visorlink.data.repository

import android.content.Context
import android.content.SharedPreferences
import by.iposdev.visorlink.data.remote.chat.BackendConnectivityInterceptor
import okhttp3.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

class BackendFallbackManagerTest {

    private val context: Context = mock()
    private val flagsRepository: FlagsRepository = mock()
    private val prefs: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()

    private var storedFallback = false

    @Before
    fun setup() {
        storedFallback = false
        whenever(context.getSharedPreferences(eq("visorlink_backend_settings"), any())).thenReturn(prefs)
        whenever(prefs.getBoolean(eq("manual_fallback_firebase"), any())).thenAnswer { storedFallback }
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val v = invocation.getArgument<Boolean>(1)
            if (key == "manual_fallback_firebase") storedFallback = v
            editor
        }
        whenever(flagsRepository.isBackendV2EnabledDirect()).thenReturn(true)
    }

    @Test
    fun `does not trigger offer before 6 consecutive failures`() {
        val manager = BackendFallbackManager(context, flagsRepository)

        for (i in 1..5) {
            manager.recordFailure("Failed attempt $i")
            assertEquals(i, manager.consecutiveFailures.value)
            assertFalse(manager.showFallbackPrompt.value)
        }
    }

    @Test
    fun `triggers offer on exactly 6 consecutive failures`() {
        val manager = BackendFallbackManager(context, flagsRepository)

        for (i in 1..6) {
            manager.recordFailure("Failed attempt $i")
        }

        assertEquals(6, manager.consecutiveFailures.value)
        assertTrue(manager.showFallbackPrompt.value)
    }

    @Test
    fun `success resets consecutive failure counter`() {
        val manager = BackendFallbackManager(context, flagsRepository)

        manager.recordFailure("Failure 1")
        manager.recordFailure("Failure 2")
        manager.recordFailure("Failure 3")
        assertEquals(3, manager.consecutiveFailures.value)

        manager.recordSuccess()
        assertEquals(0, manager.consecutiveFailures.value)
        assertFalse(manager.showFallbackPrompt.value)
    }

    @Test
    fun `enableManualFallback activates fallback and notifies flags repository`() {
        val manager = BackendFallbackManager(context, flagsRepository)

        for (i in 1..6) manager.recordFailure("Error")
        assertTrue(manager.showFallbackPrompt.value)

        manager.enableManualFallback()

        assertTrue(manager.manualFallbackActive.value)
        assertTrue(manager.isManualFallbackActive())
        assertFalse(manager.showFallbackPrompt.value)
        assertEquals(0, manager.consecutiveFailures.value)

        verify(flagsRepository).notifyFlagsChanged()
    }

    @Test
    fun `disableManualFallback deactivates fallback and notifies flags repository`() {
        storedFallback = true
        val manager = BackendFallbackManager(context, flagsRepository)
        assertTrue(manager.isManualFallbackActive())

        manager.disableManualFallback()

        assertFalse(manager.manualFallbackActive.value)
        assertFalse(manager.isManualFallbackActive())
        verify(flagsRepository).notifyFlagsChanged()
    }

    @Test
    fun `dismissPrompt hides prompt and resets consecutive failures`() {
        val manager = BackendFallbackManager(context, flagsRepository)
        for (i in 1..6) manager.recordFailure("Error")
        assertTrue(manager.showFallbackPrompt.value)

        manager.dismissPrompt()
        assertFalse(manager.showFallbackPrompt.value)
        assertEquals(0, manager.consecutiveFailures.value)
    }

    @Test
    fun `BackendConnectivityInterceptor records failure on IOException`() {
        val manager = BackendFallbackManager(context, flagsRepository)
        val interceptor = BackendConnectivityInterceptor(manager)

        val chain: Interceptor.Chain = mock()
        val request = Request.Builder().url("https://backend.visorlink.org/test").build()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenThrow(IOException("Network unreachable"))

        try {
            interceptor.intercept(chain)
            fail("Should throw IOException")
        } catch (e: IOException) {
            assertEquals("Network unreachable", e.message)
        }

        assertEquals(1, manager.consecutiveFailures.value)
    }

    @Test
    fun `BackendConnectivityInterceptor records failure on 503 and success on 200`() {
        val manager = BackendFallbackManager(context, flagsRepository)
        val interceptor = BackendConnectivityInterceptor(manager)

        val chain: Interceptor.Chain = mock()
        val request = Request.Builder().url("https://backend.visorlink.org/test").build()
        whenever(chain.request()).thenReturn(request)

        val response503 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("Service Unavailable")
            .body(ResponseBody.create(null, ""))
            .build()
        whenever(chain.proceed(any())).thenReturn(response503)

        interceptor.intercept(chain)
        assertEquals(1, manager.consecutiveFailures.value)

        val response200 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create(null, ""))
            .build()
        whenever(chain.proceed(any())).thenReturn(response200)

        interceptor.intercept(chain)
        assertEquals(0, manager.consecutiveFailures.value)
    }

    @Test
    fun `checkHealth returns true and resets failures when healthy`() = kotlinx.coroutines.test.runTest {
        val api: by.iposdev.visorlink.data.remote.chat.VisorLinkApi = mock()
        whenever(api.getHealth()).thenReturn(by.iposdev.visorlink.data.remote.chat.HealthResponseDto(status = "healthy", database = true, redis = true))

        val manager = BackendFallbackManager(context, flagsRepository) { api }
        manager.recordFailure("Prior error")
        assertEquals(1, manager.consecutiveFailures.value)

        val result = manager.checkHealth()
        assertTrue(result)
        assertEquals(0, manager.consecutiveFailures.value)
    }

    @Test
    fun `checkHealth returns false and records failure on exception`() = kotlinx.coroutines.test.runTest {
        val api: by.iposdev.visorlink.data.remote.chat.VisorLinkApi = mock()
        whenever(api.getHealth()).thenThrow(RuntimeException("Health check down"))

        val manager = BackendFallbackManager(context, flagsRepository) { api }
        val result = manager.checkHealth()
        assertFalse(result)
        assertEquals(1, manager.consecutiveFailures.value)
    }
}
