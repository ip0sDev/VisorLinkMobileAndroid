package org.visorlink.app.data.repository

import android.app.Application
import android.content.Context
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.visorlink.app.data.model.flags.ConfigResponse
import org.visorlink.app.data.model.flags.PairResponse
import org.visorlink.app.data.remote.flags.AegisKeyManager
import org.visorlink.app.data.remote.flags.FlagsApi
import retrofit2.HttpException
import retrofit2.Response
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FlagsRepositoryTest {

    private lateinit var context: Context
    private val keyManager = mock<AegisKeyManager> {
        on { hasKey() } doReturn true
        on { generatePublicKeyPem() } doReturn "PEM"
        on { signPayload(any(), any(), any()) } doReturn "sig"
    }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    /** JWT без подписи: клиент подпись не проверяет, ему нужны только claims. */
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        return "$header.${enc.encodeToString(payload.toByteArray())}.sig"
    }

    private val prefs get() = context.getSharedPreferences("visorlink_flags_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs.edit().clear().putString("device_id", "old").commit()
    }

    @Test
    fun `401 drops the device id, re-pairs and retries once`() = runTest {
        val api = mock<FlagsApi> {
            onBlocking { getConfig(any(), any()) }
                .thenThrow(http(401))
                .thenReturn(ConfigResponse(jwt("""{"test_flag":true}""")))
            onBlocking { pair(any()) } doReturn PairResponse("new")
        }
        val repo = FlagsRepository(context, api, keyManager)

        assertTrue(repo.fetchFlags())
        verify(api, times(1)).pair(any())
        verify(api).getConfig(any(), argThat { deviceId == "new" })
        assertEquals("new", prefs.getString("device_id", null))
        assertTrue(repo.flags.value.testFlag)
    }

    @Test
    fun `a second 401 gives up instead of looping`() = runTest {
        val api = mock<FlagsApi> {
            onBlocking { getConfig(any(), any()) }.thenThrow(http(401))
            onBlocking { pair(any()) } doReturn PairResponse("new")
        }
        val repo = FlagsRepository(context, api, keyManager)

        assertFalse(repo.fetchFlags())
        verify(api, times(1)).pair(any())
        verify(api, times(2)).getConfig(any(), any())
    }

    @Test
    fun `other server errors keep the device id`() = runTest {
        val api = mock<FlagsApi> {
            onBlocking { getConfig(any(), any()) }.thenThrow(http(500))
        }
        val repo = FlagsRepository(context, api, keyManager)

        assertFalse(repo.fetchFlags())
        verify(api, never()).pair(any())
        assertEquals("old", prefs.getString("device_id", null))
    }

    @Test
    fun `cached claims survive a failed fetch`() = runTest {
        prefs.edit().putString("cached_server_claims", """{"service_mode_enabled":true}""").commit()
        val api = mock<FlagsApi> {
            onBlocking { getConfig(any(), any()) }.thenThrow(http(500))
        }
        val repo = FlagsRepository(context, api, keyManager)

        assertFalse(repo.fetchFlags())
        assertTrue("без сети действует последнее полученное значение", repo.flags.value.serviceMode)
    }

    @Test
    fun `toggleFlag in debug persists true and enables flag without server`() {
        val api = mock<FlagsApi>()
        val repo = FlagsRepository(context, api, keyManager)

        // Изначально флаг выключен (сервер ничего не присылал)
        assertFalse(repo.flags.value.isEnabled(org.visorlink.app.data.model.flags.AppFlags.PROFILE_NAVBAR))

        // Включаем в debug
        repo.toggleFlag(org.visorlink.app.data.model.flags.AppFlags.PROFILE_NAVBAR, true)
        assertTrue(repo.flags.value.isEnabled(org.visorlink.app.data.model.flags.AppFlags.PROFILE_NAVBAR))

        // Выключаем обратно
        repo.toggleFlag(org.visorlink.app.data.model.flags.AppFlags.PROFILE_NAVBAR, false)
        assertFalse(repo.flags.value.isEnabled(org.visorlink.app.data.model.flags.AppFlags.PROFILE_NAVBAR))
    }

    @Test
    fun `all app flags are present in flippableKeys in debug`() {
        val api = mock<FlagsApi>()
        val repo = FlagsRepository(context, api, keyManager)

        val keys = repo.flags.value.flippableKeys
        assertTrue(keys.containsAll(org.visorlink.app.data.model.flags.AppFlags.KNOWN_FLAGS))
    }
}
