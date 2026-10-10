package org.visorlink.app.ui.screens.auth

import org.visorlink.app.R
import org.visorlink.app.data.auth.TfaError
import org.visorlink.app.data.auth.TfaMethod
import org.visorlink.app.ui.UiText

/** Тексты 2FA по `details.reason` (веб `TwoFactorLock.jsx` / `TwoFactorSection.jsx`). */
object TfaTexts {

    /** Ошибка `request2FA`: [method] — каким способом пытались отправить. */
    fun sendError(e: TfaError, method: TfaMethod): UiText = when (e.reason) {
        TfaError.SEND_FAILED -> UiText.StringResource(
            when (e.method ?: method) {
                TfaMethod.BOT -> R.string.tfa_lock_send_failed_bot
                TfaMethod.EMAIL -> R.string.tfa_lock_send_failed_email
                TfaMethod.TELEGRAM -> R.string.tfa_lock_send_failed_telegram
            }
        )
        TfaError.NO_EMAIL -> UiText.StringResource(R.string.tfa_lock_no_email)
        TfaError.NO_TELEGRAM -> UiText.StringResource(R.string.tfa_lock_no_telegram)
        else -> if (e.network) UiText.StringResource(R.string.tfa_lock_network)
        else e.message?.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) }
            ?: UiText.StringResource(R.string.tfa_lock_send_failed)
    }

    /** Ошибка `verify2FA` / `set2FAEnabled`. */
    fun verifyError(e: TfaError): UiText = when (e.reason) {
        TfaError.WRONG -> UiText.StringResource(R.string.tfa_lock_wrong, e.attemptsLeft?.toString() ?: "?")
        TfaError.EXPIRED -> UiText.StringResource(R.string.tfa_lock_expired)
        TfaError.LOCKED -> UiText.StringResource(R.string.tfa_lock_locked)
        TfaError.MISSING -> UiText.StringResource(R.string.tfa_lock_missing)
        else -> if (e.network) UiText.StringResource(R.string.tfa_lock_network)
        else e.message?.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) }
            ?: UiText.StringResource(R.string.tfa_lock_verify_failed)
    }

    /** Ошибка `setTfaTelegram`. */
    fun telegramError(e: TfaError): UiText = when (e.reason) {
        TfaError.NO_TELEGRAM -> UiText.StringResource(R.string.tfa_tg_need_link)
        TfaError.SEND_FAILED -> UiText.StringResource(R.string.tfa_tg_send_failed)
        else -> if (e.network) UiText.StringResource(R.string.tfa_lock_network)
        else e.message?.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) }
            ?: UiText.StringResource(R.string.tfa_tg_failed)
    }

    /** «m:ss» для таймеров. */
    fun mmss(ms: Long): String {
        val s = ((ms.coerceAtLeast(0) + 999) / 1000)
        return "%d:%02d".format(s / 60, s % 60)
    }
}
