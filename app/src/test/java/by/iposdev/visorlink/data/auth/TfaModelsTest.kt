package org.visorlink.app.data.auth

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.visorlink.app.R
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.UiText
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.screens.auth.TfaTexts

/** Ответ `request2FA`, ошибки по `details.reason` и их тексты (functions/tfa.js, TwoFactorLock.jsx). */
class TfaModelsTest {

    private fun functionsError(code: FirebaseFunctionsException.Code, details: Map<String, Any?>?): FirebaseFunctionsException = mock {
        on { this.code } doReturn code
        on { this.details } doReturn details
        on { message } doReturn "server text"
    }

    // ── request2FA ────────────────────────────────────────────────────────────

    @Test
    fun `fresh code response is parsed as sent`() {
        val info = TfaCodeInfo.parse(
            mapOf("sent" to true, "alreadySent" to false, "method" to "email", "destination" to "a•••e@gmail.com",
                "expiresAt" to 1_791_570_000_000L, "resendAt" to 1_791_569_760_000L),
            requested = TfaMethod.EMAIL, now = 0,
        )
        assertTrue(info.sent)
        assertFalse(info.alreadySent)
        assertEquals(TfaMethod.EMAIL, info.method)
        assertEquals("a•••e@gmail.com", info.destination)
        assertEquals(1_791_570_000_000L, info.expiresAt)
        assertEquals(1_791_569_760_000L, info.resendAt)
    }

    @Test
    fun `already sent keeps the method of the previous code`() {
        // Попросили почту, а живой код уже ушёл в бота меньше минуты назад
        val info = TfaCodeInfo.parse(
            mapOf("sent" to false, "alreadySent" to true, "method" to "bot", "destination" to null, "expiresAt" to 10_000, "resendAt" to 5_000),
            requested = TfaMethod.EMAIL, now = 0,
        )
        assertFalse(info.sent)
        assertTrue(info.alreadySent)
        assertEquals(TfaMethod.BOT, info.method)
        assertNull(info.destination)
    }

    @Test
    fun `missing fields fall back to server constants`() {
        val info = TfaCodeInfo.parse(null, requested = TfaMethod.BOT, now = 1_000)
        assertEquals(TfaMethod.BOT, info.method)
        assertEquals(1_000 + TfaCodeInfo.CODE_TTL_MS, info.expiresAt)
        assertEquals(1_000 + TfaCodeInfo.RESEND_COOLDOWN_MS, info.resendAt)
    }

    // ── Ошибки ────────────────────────────────────────────────────────────────

    @Test
    fun `send failure is read from details reason not from the code`() {
        val err = TfaError.of(functionsError(FirebaseFunctionsException.Code.UNAVAILABLE, mapOf("reason" to "send-failed", "method" to "email")))
        assertEquals(TfaError.SEND_FAILED, err.reason)
        assertEquals(TfaMethod.EMAIL, err.method)
        assertFalse("причина от сервера — не «нет сети»", err.network)
        val text = TfaTexts.sendError(err, TfaMethod.BOT) as UiText.StringResource
        assertEquals(R.string.tfa_lock_send_failed_email, text.resId)
    }

    @Test
    fun `wrong code carries attempts left`() {
        val err = TfaError.of(functionsError(FirebaseFunctionsException.Code.INVALID_ARGUMENT, mapOf("reason" to "wrong", "attemptsLeft" to 3)))
        assertEquals(3, err.attemptsLeft)
        assertFalse(err.needsNewCode)
        val text = TfaTexts.verifyError(err) as UiText.StringResource
        assertEquals(R.string.tfa_lock_wrong, text.resId)
        assertEquals("3", text.args.single())
    }

    @Test
    fun `expired locked and missing need a new code`() {
        listOf("expired" to R.string.tfa_lock_expired, "locked" to R.string.tfa_lock_locked, "missing" to R.string.tfa_lock_missing)
            .forEach { (reason, res) ->
                val err = TfaError.of(functionsError(FirebaseFunctionsException.Code.FAILED_PRECONDITION, mapOf("reason" to reason)))
                assertTrue(reason, err.needsNewCode)
                assertEquals(reason, res, (TfaTexts.verifyError(err) as UiText.StringResource).resId)
            }
    }

    @Test
    fun `unavailable without reason is a network problem`() {
        val err = TfaError.of(functionsError(FirebaseFunctionsException.Code.UNAVAILABLE, null))
        assertTrue(err.network)
        assertEquals(R.string.tfa_lock_network, (TfaTexts.sendError(err, TfaMethod.BOT) as UiText.StringResource).resId)
    }

    @Test
    fun `no email and no telegram have their own texts`() {
        val noEmail = TfaError.of(functionsError(FirebaseFunctionsException.Code.FAILED_PRECONDITION, mapOf("reason" to "no-email")))
        val noTg = TfaError.of(functionsError(FirebaseFunctionsException.Code.FAILED_PRECONDITION, mapOf("reason" to "no-telegram")))
        assertEquals(R.string.tfa_lock_no_email, (TfaTexts.sendError(noEmail, TfaMethod.EMAIL) as UiText.StringResource).resId)
        assertEquals(R.string.tfa_lock_no_telegram, (TfaTexts.sendError(noTg, TfaMethod.TELEGRAM) as UiText.StringResource).resId)
        assertEquals(R.string.tfa_tg_need_link, (TfaTexts.telegramError(noTg) as UiText.StringResource).resId)
    }

    // ── Telegram и мелочи ─────────────────────────────────────────────────────

    @Test
    fun `telegram method is offered only when linked and enabled`() {
        assertEquals(listOf(TfaMethod.BOT, TfaMethod.EMAIL), (null as UserProfile?).tfaMethods())
        assertEquals(listOf(TfaMethod.BOT, TfaMethod.EMAIL), UserProfile(telegramId = 42L, tfaTelegram = false).tfaMethods())
        assertEquals(listOf(TfaMethod.BOT, TfaMethod.EMAIL), UserProfile(tfaTelegram = true).tfaMethods())
        assertEquals(TfaMethod.entries, UserProfile(telegramId = "42", tfaTelegram = true).tfaMethods())
    }

    @Test
    fun `telegram account prefers the username`() {
        assertEquals("@neo", UserProfile(telegramId = 1L, telegramUsername = "neo").telegramAccount)
        assertEquals("ID 1", UserProfile(telegramId = 1L).telegramAccount)
        assertNull(UserProfile().telegramAccount)
    }

    @Test
    fun `timers are formatted as m ss rounding up`() {
        assertEquals("0:42", TfaTexts.mmss(41_100))
        assertEquals("4:51", TfaTexts.mmss(290_400))
        assertEquals("0:00", TfaTexts.mmss(-5))
    }

    @Test
    fun `default google username comes from the email`() {
        assertEquals("john_doe", AuthViewModel.defaultUsername("john.doe@gmail.com"))
        assertEquals("a_b_c", AuthViewModel.defaultUsername("a+b-c@x.org"))
        assertEquals(24, AuthViewModel.defaultUsername("${"x".repeat(40)}@x.org").length)
        assertEquals("", AuthViewModel.defaultUsername(null))
    }
}
