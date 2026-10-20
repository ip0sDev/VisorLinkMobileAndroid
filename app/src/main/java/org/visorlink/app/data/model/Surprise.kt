package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import org.visorlink.app.data.idcard.IdSkinLook
import java.util.Date

/**
 * Слайд «вашей переписки в цифрах». Всё уже посчитано и сформулировано скриптом
 * (`scripts/birthday-surprise.cjs` в веб-репо): клиент ничего не считает, а только рисует.
 * Числовой [value] («1 284», «312 дней») при показе набегает от нуля, если не выключен [countUp]
 * (дата «17 марта» набегать не должна).
 */
data class SurpriseSlide(
    val kicker: String? = null,
    val value: String? = null,
    val text: String? = null,
    val caption: String? = null,
    val imageUrl: String? = null,
    val countUp: Boolean = true,
)

data class SurpriseWish(val name: String, val text: String)

/**
 * Сюрприз для одного пользователя: `surprises/{uid}`. Пишет только админ-скрипт, сам
 * пользователь может отметить лишь `openedAt` / `finishedAt`. Показывается один раз
 * (ключ — [id]) в окне [showFrom]..[showUntil].
 */
data class Surprise(
    val id: String,
    val active: Boolean = true,
    val showFrom: Long? = null,
    val showUntil: Long? = null,
    val fromName: String? = null,
    val title: String? = null,
    val message: String? = null,
    val slides: List<SurpriseSlide> = emptyList(),
    val wishes: List<SurpriseWish> = emptyList(),
    val giftTitle: String? = null,
    val giftText: String? = null,
    /** Досмотрен до конца (на любом устройстве). */
    val finished: Boolean = false,
    /**
     * В конце ждёт подарок (`gift`, выдаёт callable `claimSurpriseGift`). Что внутри, клиент
     * не знает до вскрытия — чтобы не проболтаться раньше времени.
     */
    val hasGift: Boolean = false,
) {
    fun isOpenAt(now: Long): Boolean =
        active && (showFrom == null || now >= showFrom) && (showUntil == null || now < showUntil)

    /** Сколько ждать до начала окна; null — уже началось, закончилось или выключено. */
    fun startsIn(now: Long): Long? {
        if (!active || showFrom == null || now >= showFrom) return null
        if (showUntil != null && showUntil <= showFrom) return null
        return showFrom - now
    }

    companion object {
        fun parse(data: Map<String, Any?>?): Surprise? {
            if (data == null) return null
            val id = data.str("id") ?: return null
            return Surprise(
                id = id,
                active = data["active"] as? Boolean ?: true,
                showFrom = millis(data["showFrom"]),
                showUntil = millis(data["showUntil"]),
                fromName = data.str("fromName")?.removePrefix("@"),
                title = data.str("title"),
                message = data.str("message"),
                slides = (data["slides"] as? List<*>).orEmpty().mapNotNull { raw ->
                    val m = raw as? Map<*, *> ?: return@mapNotNull null
                    SurpriseSlide(
                        kicker = m.str("kicker"),
                        value = m.str("value"),
                        text = m.str("text"),
                        caption = m.str("caption"),
                        imageUrl = m.str("imageUrl")?.takeIf { it.startsWith("https://") },
                        countUp = m["countUp"] as? Boolean ?: true,
                    ).takeIf { it.value != null || it.text != null }
                },
                wishes = (data["wishes"] as? List<*>).orEmpty().mapNotNull { raw ->
                    val m = raw as? Map<*, *> ?: return@mapNotNull null
                    val text = m.str("text") ?: return@mapNotNull null
                    SurpriseWish(name = m.str("name") ?: "", text = text)
                },
                giftTitle = data.str("giftTitle"),
                giftText = data.str("giftText"),
                finished = data["finishedAt"] != null,
                hasGift = data["gift"] != null || data["claim"] != null,
            )
        }

        private fun Map<*, *>.str(key: String): String? = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        private fun millis(v: Any?): Long? = when (v) {
            is Timestamp -> v.toDate().time
            is Date -> v.time
            is Number -> v.toLong()
            else -> null
        }
    }
}

/**
 * Что выдал `claimSurpriseGift`: скин (уже в инвентаре и, если [equipped], надет), биты и новый
 * срок PRO. [demo] — предпросмотр: сервер ничего не записал.
 */
data class SurpriseGiftResult(
    val skin: IdSkinLook?,
    val skinMintedAt: Long,
    val equipped: Boolean,
    val bits: Int,
    val proDays: Int,
    val proUntil: Long?,
    val demo: Boolean,
) {
    companion object {
        fun parse(raw: Map<*, *>?): SurpriseGiftResult? {
            if (raw == null) return null
            val skin = raw["skin"] as? Map<*, *>
            return SurpriseGiftResult(
                skin = IdSkinLook.parse(skin),
                skinMintedAt = (skin?.get("mintedAt") as? Number)?.toLong() ?: 0,
                equipped = raw["equipped"] == true,
                bits = (raw["bits"] as? Number)?.toInt() ?: 0,
                proDays = (raw["proDays"] as? Number)?.toInt() ?: 0,
                proUntil = (raw["proUntil"] as? Number)?.toLong(),
                demo = raw["demo"] == true,
            )
        }
    }
}
