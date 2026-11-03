package org.visorlink.app.data.repository

import androidx.compose.ui.graphics.Color
import org.visorlink.app.data.remote.EconomyCaller
import org.visorlink.app.data.remote.EconomyException
import org.visorlink.app.ui.theme.ProAccent

/**
 * Эмодзи-статус и цвет ника (веб `src/utils/cosmetics.js`; сервер — `functions/cosmeticsLogic.js`, цены и
 * палитра — как там). Пишет только сервер (callable `cosmetics`); показ — при активном PRO владельца или
 * купленной навсегда вещи (`UserProfile.shownEmojiStatus` / `shownNameColor`).
 */
object CosmeticsRepository {
    val unlockPrices: Map<String, Int> = mapOf("emojiStatus" to 300, "nameColor" to 500)

    /** Палитра цвета ника — та же, что у цвета профиля (`PROFILE_ACCENTS`). */
    val nameColors: List<String> = listOf(
        "#35C7E8", "#3B9EFF", "#6366F1", "#8C6BFF", "#D946EF", "#F472B6", "#FF4D6A",
        "#FF8A3D", "#F5B83D", "#A3E635", "#34D399", "#2DD4BF", "#94A3B8"
    )

    val quickEmojis: List<String> = listOf(
        "🔥", "✨", "💜", "🌙", "⚡", "🎧", "🎮", "☕", "🦊", "🐾", "🤖", "🌈", "🍀", "🚀", "💤", "🏳️‍🌈"
    )

    val itemTitles: Map<String, String> = mapOf(
        "emojiStatus" to "Эмодзи-статус",
        "nameColor" to "Цвет ника"
    )

    /**
     * Цвет ника под тему смотрящего (контраст ≥ 4.5 к фону, как `readableAccent`); `null` — не из палитры.
     */
    fun readableNameColor(hex: String?, light: Boolean): Color? {
        if (hex == null) return null
        val upper = hex.uppercase()
        if (!nameColors.contains(upper)) return null
        return try {
            ProAccent.readable(ProAccent.parse(upper), light = light)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Поставить или снять (`null`) эмодзи-статус и / или цвет ника. Ошибки: `bad-emoji`, `bad-color`, `locked`.
     */
    suspend fun setCosmetics(
        emojiStatus: String? = KEEP_STRING,
        nameColor: String? = KEEP_STRING
    ) {
        val data = mutableMapOf<String, Any?>("action" to "set")
        if (emojiStatus !== KEEP_STRING) {
            data["emojiStatus"] = emojiStatus
        }
        if (nameColor !== KEEP_STRING) {
            data["nameColor"] = nameColor
        }
        EconomyCaller.callEconomy("cosmetics", data)
    }

    /**
     * Купить навсегда: `emojiStatus` — 300, `nameColor` — 500 Bits. Ошибки: `owned`, `not-enough-bits`.
     */
    suspend fun unlock(item: String) {
        EconomyCaller.callEconomy("cosmetics", mapOf("action" to "unlock", "item" to item))
    }

    const val KEEP_STRING: String = "__KEEP__"

    fun errorText(e: Throwable): String {
        val reason = (e as? EconomyException)?.reason
        return when (reason) {
            "not-enough-bits" -> "Не хватает Bits."
            "bad-emoji" -> "Нужен ровно один эмодзи."
            "bad-color" -> "Такого цвета нет в палитре."
            "locked" -> "Доступно с PRO или после покупки."
            "owned" -> "Уже куплено."
            else -> {
                val msg = (e as? EconomyException)?.message ?: e.message
                if (!msg.isNullOrBlank()) msg else "Не удалось. Попробуйте ещё раз."
            }
        }
    }
}
