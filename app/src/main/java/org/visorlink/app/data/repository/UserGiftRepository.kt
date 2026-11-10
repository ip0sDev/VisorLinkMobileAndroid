package org.visorlink.app.data.repository

import org.visorlink.app.data.remote.EconomyCaller
import org.visorlink.app.data.remote.EconomyException

/**
 * Подарки от пользователей за Bits (веб `src/services/giftService.ts`): callable `userGift` —
 * `send | redeem | cancel`. Цены и наполнение решает сервер; здесь — для отображения в UI.
 */
object UserGifts {
    val proPrices = mapOf("pro_week" to 300, "pro_month" to 1000)
    val postcardPrices = mapOf("spark" to 20, "aurora" to 35, "supernova" to 50)
    const val NOTE_MAX = 140

    val kindTitles = mapOf(
        "pro" to "VisorLink PRO",
        "pro_week" to "PRO на 7 дней",
        "pro_month" to "PRO на 30 дней",
        "skin" to "ID-карта",
        "postcard" to "Открытка"
    )

    val designTitles = mapOf(
        "spark" to "Искры",
        "aurora" to "Сияние",
        "supernova" to "Сверхновая"
    )
}

data class UserGiftSendResult(val giftId: String, val messageId: String)

class UserGiftRepository {
    /** Отправить подарок в личный чат: `kind` — `pro_week | pro_month | skin | postcard`. */
    suspend fun send(
        chatId: String,
        kind: String,
        design: String? = null,
        skinId: String? = null,
        note: String = ""
    ): UserGiftSendResult {
        val payload = buildMap<String, Any?> {
            put("action", "send")
            put("chatId", chatId)
            put("kind", kind)
            if (design != null) put("design", design)
            if (skinId != null) put("skinId", skinId)
            if (note.trim().isNotEmpty()) put("note", note.trim())
        }
        val res = EconomyCaller.callEconomy("userGift", payload)
        return UserGiftSendResult(
            giftId = res["giftId"] as? String ?: "",
            messageId = res["messageId"] as? String ?: ""
        )
    }

    /** Открыть (только получатель). */
    suspend fun redeem(giftId: String) {
        EconomyCaller.callEconomy("userGift", mapOf("action" to "redeem", "giftId" to giftId))
    }

    /** Отозвать (только отправитель, пока не открыт): Bits / скин возвращаются. */
    suspend fun cancel(giftId: String) {
        EconomyCaller.callEconomy("userGift", mapOf("action" to "cancel", "giftId" to giftId))
    }
}

/** Текст ошибки подарка (веб `giftErrorText`, ключи `gift.err.*`); защита от ферм — те же причины, что у обмена. */
fun giftErrorText(error: Throwable): String {
    if (error !is EconomyException) return error.message?.takeIf { it.isNotBlank() } ?: "Не удалось. Попробуйте ещё раз."
    val wait = EconomyCaller.formatWait(error.waitMs)
    return when (error.reason) {
        "not-dm" -> "Подарки — только в личных чатах."
        "bad-recipient" -> "Этому собеседнику подарок отправить нельзя."
        "recipient-eternal" -> "У собеседника бесконечный PRO."
        "not-enough-bits" -> "Не хватает Bits."
        "equipped" -> "Надетый скин подарить нельзя."
        "already-listed" -> "Скин выставлен на обмен — сначала снимите его."
        "no-gift" -> "Подарок не найден."
        "not-recipient" -> "Этот подарок не вам."
        "not-sender" -> "Отозвать может только отправитель."
        "redeemed" -> "Подарок уже открыт."
        "cancelled" -> "Подарок отозван."
        "no-card" -> "Чтобы принять ID-карту, сначала получите свою."
        "no-slot" -> "В инвентаре нет свободного слота."
        "single" -> "Эта карта существует в одном экземпляре и не передаётся."
        "pro-card" -> "PRO-карту нельзя продать, обменять или подарить."
        "peer-not-ready" -> "Собеседнику пока недоступны подарки."
        "trust-age" -> "Дарить PRO и ID-карты можно через $wait: аккаунту нужно 30 дней."
        "trust-activity" -> "Дарить PRO и ID-карты можно после ещё ${error.daysLeft} дн. общения."
        "skin-hold" -> "Этот скин можно будет подарить через $wait."
        "invite-link" -> "Между пригласившим и приглашённым подарки откроются через $wait."
        "gift-limit" -> "Не больше 3 подарков за 30 дней на получателя. Следующий — через $wait."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Не удалось. Попробуйте ещё раз."
    }
}
