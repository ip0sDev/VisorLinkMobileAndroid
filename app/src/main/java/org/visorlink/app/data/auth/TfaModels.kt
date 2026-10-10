package org.visorlink.app.data.auth

import com.google.firebase.functions.FirebaseFunctionsException
import org.visorlink.app.data.model.UserProfile

/** Куда слать код 2FA (`request2FA {method}`). Telegram — запасной способ, только если включён. */
enum class TfaMethod(val id: String) {
    BOT("bot"),
    EMAIL("email"),
    TELEGRAM("telegram");

    companion object {
        fun of(id: String?): TfaMethod? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Ответ `request2FA` (functions/tfa.js). Вызов идемпотентен: если живой код отправлен недавно,
 * новый не шлётся — приходит `sent = false, alreadySent = true` со сроком и способом прежнего кода.
 *
 * @property method куда код ушёл на самом деле (при [alreadySent] — способ прежнего кода).
 * @property destination почта — маска «a•••e@gmail.com», Telegram — «@ник», бот — `null`.
 * @property expiresAt мс; код живёт [CODE_TTL_MS].
 * @property resendAt раньше этого времени тем же способом новый код не отправится.
 */
data class TfaCodeInfo(
    val sent: Boolean,
    val alreadySent: Boolean,
    val method: TfaMethod,
    val destination: String?,
    val expiresAt: Long,
    val resendAt: Long,
) {
    companion object {
        const val CODE_TTL_MS = 5 * 60_000L
        const val RESEND_COOLDOWN_MS = 60_000L
        const val SWITCH_COOLDOWN_MS = 15_000L
        const val MAX_ATTEMPTS = 5

        /** Разбор ответа callable; недостающие поля — по константам сервера от [now]. */
        fun parse(data: Any?, requested: TfaMethod, now: Long): TfaCodeInfo {
            val m = data as? Map<*, *> ?: emptyMap<Any, Any>()
            fun long(key: String) = (m[key] as? Number)?.toLong()
            val alreadySent = m["alreadySent"] == true
            return TfaCodeInfo(
                sent = m["sent"] as? Boolean ?: !alreadySent,
                alreadySent = alreadySent,
                method = TfaMethod.of(m["method"] as? String) ?: requested,
                destination = (m["destination"] as? String)?.takeIf { it.isNotBlank() },
                expiresAt = long("expiresAt") ?: (now + CODE_TTL_MS),
                resendAt = long("resendAt") ?: (now + RESEND_COOLDOWN_MS),
            )
        }
    }
}

/**
 * Ошибка callable'а 2FA. Разбирается по `details.reason`, а не по коду ошибки: например, сбой
 * доставки теперь `unavailable` + `send-failed` (раньше был `internal`).
 */
data class TfaError(
    /** wrong | expired | locked | missing | send-failed | no-email | no-telegram | … */
    val reason: String?,
    val attemptsLeft: Int?,
    /** Для `send-failed` — каким способом не дошло. */
    val method: TfaMethod?,
    /** Нет связи с сервером (сеть, таймаут) — без причины от сервера. */
    val network: Boolean,
    val message: String?,
) {
    /** После этих ошибок код больше не годится — «Подтвердить» ждёт нового кода. */
    val needsNewCode: Boolean get() = reason in NEW_CODE_REASONS

    companion object {
        const val WRONG = "wrong"
        const val EXPIRED = "expired"
        const val LOCKED = "locked"
        const val MISSING = "missing"
        const val SEND_FAILED = "send-failed"
        const val NO_EMAIL = "no-email"
        const val NO_TELEGRAM = "no-telegram"
        private val NEW_CODE_REASONS = setOf(EXPIRED, LOCKED, MISSING)

        fun of(t: Throwable): TfaError {
            val fe = t as? FirebaseFunctionsException
            val details = fe?.details as? Map<*, *>
            val reason = details?.get("reason") as? String
            val network = fe == null && isNetworkError(t) ||
                reason == null && fe?.code in NETWORK_CODES
            return TfaError(
                reason = reason,
                attemptsLeft = (details?.get("attemptsLeft") as? Number)?.toInt(),
                method = TfaMethod.of(details?.get("method") as? String),
                network = network,
                message = t.message,
            )
        }

        private val NETWORK_CODES = setOf(
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL,
        )

        private fun isNetworkError(t: Throwable): Boolean =
            t is java.io.IOException || t.cause is java.io.IOException ||
                t.javaClass.simpleName.contains("Network", ignoreCase = true)
    }
}

/** Коды можно получать в Telegram: Telegram привязан и этот способ включён (`tfaTelegram`). */
fun UserProfile.telegramCodesReady(): Boolean = hasTelegram && tfaTelegram == true

/** Способы, которые показывает экран кода: Telegram — только если он готов. */
fun UserProfile?.tfaMethods(): List<TfaMethod> =
    if (this?.telegramCodesReady() == true) TfaMethod.entries else listOf(TfaMethod.BOT, TfaMethod.EMAIL)
