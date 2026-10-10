package org.visorlink.app.data.idcard

/**
 * Инвентарь скинов ID-карты и обмен скинами в чатах (спека ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md,
 * веб: src/services/idCardService.ts, src/hooks/useIdSkins.js).
 *
 * Скин — бланк карты: `seed` → признаки и серийник. Режим и вид — свойства владельца, не скина:
 * один и тот же скин у Standard- и Protogen-пользователя рисуется в их режиме. Пишет только сервер
 * (`idSkinInventory`, `idSkinTrade`), клиент читает `idCards/{uid}/skins`, `idTrades/{tradeId}`.
 */
object IdSkinRules {
    const val ROLL_COST_BITS = 200
    const val SELL_PRICE_BITS = 100
    const val SLOT_PRICE_BITS = 100
    const val BASE_SLOTS = 5
    const val MAX_SLOTS = 20
    const val ROLL_COOLDOWN_MS = 7L * 24 * 3600 * 1000
    const val TRADE_NOTE_MAX = 140

    /** id виртуального скина старой карты без `skinId` (как `'__card'` в вебе). */
    const val VIRTUAL_SKIN_ID = "__card"

    /** Последняя прокрутка (как lastRollAt на сервере): у старых карт — замена документа или выдача. */
    fun lastRollAt(card: IdCard): Long = card.rolledAt ?: card.reissuedAt ?: card.issuedAt

    fun rollAvailableAt(card: IdCard): Long = lastRollAt(card) + ROLL_COOLDOWN_MS

    fun slotsOf(card: IdCard): Int = (card.slots ?: BASE_SLOTS).coerceIn(BASE_SLOTS, MAX_SLOTS)

    /**
     * Инвентарь для показа (как `inventoryOf` в вебе): у карты, выданной до инвентаря, подписка
     * пуста — её бланк показываем единственным надетым скином без действий, сервер перенесёт его
     * при первом действии с инвентарём.
     */
    fun inventoryOf(card: IdCard?, skins: List<IdSkin>): List<IdSkin> {
        if (card == null) return emptyList()
        if (skins.isNotEmpty()) return skins
        return listOf(IdSkin.virtualOf(card))
    }

    /** Надетый скин: виртуальный (старая карта) или `card.skinId`. */
    fun equippedId(card: IdCard?, items: List<IdSkin>): String? = items.firstOrNull { it.virtual }?.id ?: card?.skinId

    /** Состояние панели прокрутки — в порядке проверок веба (кулдаун важнее нехватки слота и Bits). */
    fun rollState(card: IdCard, bits: Int, used: Int, now: Long): RollState = when {
        rollAvailableAt(card) > now -> RollState.COOLDOWN
        slotsOf(card) - used <= 0 -> RollState.FULL
        bits < ROLL_COST_BITS -> RollState.BITS
        else -> RollState.READY
    }
}

enum class RollState { COOLDOWN, FULL, BITS, READY }

/** Как скин попал к текущему владельцу. */
enum class IdSkinOrigin(val id: String) {
    ISSUE("issue"), ROLL("roll"), TRADE("trade");

    companion object {
        fun of(id: Any?): IdSkinOrigin = entries.firstOrNull { it.id == id } ?: ISSUE
    }
}

/** Вид скина — то, что видно на карте; в обмене хранится снимком `{ seed, serial, traits, edition, custom }`. */
data class IdSkinLook(
    val serial: String,
    val traits: IdCardTraits,
    val edition: IdEdition,
    /** Кастомный скин (подарок админа): палитра, голограмма, надпись. */
    val custom: IdCustomLook? = null,
) {
    /**
     * Скин на карте [wearer]: режим, вид и дата регистрации — его (чужой режим по скину не узнать),
     * серийник и признаки — от бланка. [issuedAt] — когда напечатан бланк (у снимка обмена его нет).
     */
    fun asCard(wearer: IdSkinWearer, issuedAt: Long): IdCard = IdCard(
        mode = wearer.mode,
        species = wearer.species,
        serial = serial,
        traits = traits,
        issuedAt = issuedAt,
        registeredAt = wearer.registeredAt,
        version = 1,
        custom = custom,
    )

    companion object {
        fun parse(raw: Any?): IdSkinLook? {
            val m = raw as? Map<*, *> ?: return null
            val serial = m["serial"] as? String ?: return null
            val traits = IdCardTraits.parse(m["traits"], m["seed"])
            val custom = IdCustomLook.parse(m["custom"])
            // Кастомный скин — всегда эпический (у старых записей тиража может не быть)
            val edition = IdEdition.of(m["edition"]) ?: if (custom != null) IdEdition.EPIC else traits.edition
            return IdSkinLook(serial, traits, edition, custom)
        }
    }
}

/** Чья карта «примеряет» скин: режим, вид и дата регистрации смотрящего (веб: `look`). */
data class IdSkinWearer(
    val mode: IdMode = IdMode.STANDARD,
    val species: String? = null,
    val registeredAt: Long = 0,
)

/** Скин в инвентаре: `idCards/{uid}/skins/{skinId}` (читает только владелец). */
data class IdSkin(
    val id: String,
    val serial: String,
    val traits: IdCardTraits,
    val edition: IdEdition,
    /** Когда бланк напечатан. */
    val mintedAt: Long,
    /** Когда попал к текущему владельцу. */
    val obtainedAt: Long,
    val origin: IdSkinOrigin,
    /** Сколько раз менял владельца. */
    val trades: Int = 0,
    /** Выставлен в открытом обмене. */
    val listedIn: String? = null,
    /** Бланк карты, выданной до инвентаря: показывается надетым, действий нет. */
    val virtual: Boolean = false,
    val custom: IdCustomLook? = null,
) {
    val look: IdSkinLook get() = IdSkinLook(serial, traits, edition, custom)

    companion object {
        fun parse(id: String, raw: Map<*, *>?): IdSkin? {
            if (raw == null || id.isEmpty()) return null
            val look = IdSkinLook.parse(raw) ?: return null
            return IdSkin(
                id = id,
                serial = look.serial,
                traits = look.traits,
                edition = look.edition,
                mintedAt = millis(raw["mintedAt"]) ?: 0,
                obtainedAt = millis(raw["obtainedAt"]) ?: 0,
                origin = IdSkinOrigin.of(raw["origin"]),
                trades = (raw["trades"] as? Number)?.toInt() ?: 0,
                listedIn = (raw["listedIn"] as? String)?.takeIf { it.isNotEmpty() },
                custom = look.custom,
            )
        }

        /** Бланк текущей карты как скин — пока сервер не перенёс старую карту в инвентарь. */
        fun virtualOf(card: IdCard) = IdSkin(
            id = card.skinId ?: IdSkinRules.VIRTUAL_SKIN_ID,
            serial = card.serial,
            traits = card.traits,
            edition = card.edition,
            mintedAt = card.reissuedAt ?: card.issuedAt,
            obtainedAt = card.issuedAt,
            origin = IdSkinOrigin.ISSUE,
            virtual = true,
            custom = card.custom,
        )
    }
}

enum class IdTradeStatus(val id: String) {
    OPEN("open"), DONE("done"), CLOSED("closed");

    companion object {
        /** Неизвестный статус — как закрытый: действовать по нему нельзя. */
        fun of(id: Any?): IdTradeStatus = entries.firstOrNull { it.id == id } ?: CLOSED
    }
}

/** Состоявшийся обмен: что получил автор взамен. */
data class IdTradeDeal(
    val offerId: String,
    val fromUid: String,
    val fromName: String,
    val skinId: String,
    val skin: IdSkinLook?,
) {
    companion object {
        fun parse(raw: Any?): IdTradeDeal? {
            val m = raw as? Map<*, *> ?: return null
            return IdTradeDeal(
                offerId = m["offerId"] as? String ?: "",
                fromUid = m["fromUid"] as? String ?: "",
                fromName = m["fromName"] as? String ?: "",
                skinId = m["skinId"] as? String ?: "",
                skin = IdSkinLook.parse(m["skin"]),
            )
        }
    }
}

/** Обмен: `idTrades/{tradeId}` (читают участники чата). */
data class IdTrade(
    val id: String,
    val chatId: String,
    val messageId: String,
    val ownerUid: String,
    val ownerName: String,
    val skinId: String,
    val skin: IdSkinLook,
    val note: String = "",
    val status: IdTradeStatus = IdTradeStatus.OPEN,
    /** `owner` | `equipped` | `sold` | `traded` — при [IdTradeStatus.CLOSED]. */
    val closedReason: String? = null,
    /** Ожидающих предложений. */
    val offerCount: Int = 0,
    val deal: IdTradeDeal? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /**
     * Сообщение `id_trade` само ничего не доказывает (клиенты могут писать сообщения любого типа):
     * карточку показываем, только если обмен принадлежит этому чату и этому сообщению.
     */
    fun belongsTo(chatId: String, messageId: String): Boolean = this.chatId == chatId && this.messageId == messageId

    val closedByOwner: Boolean get() = closedReason == "owner"

    companion object {
        fun parse(id: String, raw: Map<*, *>?): IdTrade? {
            if (raw == null) return null
            val skin = IdSkinLook.parse(raw["skin"]) ?: return null
            return IdTrade(
                id = id,
                chatId = raw["chatId"] as? String ?: "",
                messageId = raw["messageId"] as? String ?: "",
                ownerUid = raw["ownerUid"] as? String ?: "",
                ownerName = raw["ownerName"] as? String ?: "",
                skinId = raw["skinId"] as? String ?: "",
                skin = skin,
                note = raw["note"] as? String ?: "",
                status = IdTradeStatus.of(raw["status"]),
                closedReason = raw["closedReason"] as? String,
                offerCount = (raw["offerCount"] as? Number)?.toInt() ?: 0,
                deal = IdTradeDeal.parse(raw["deal"]),
                createdAt = millis(raw["createdAt"]) ?: 0,
                updatedAt = millis(raw["updatedAt"]) ?: 0,
            )
        }
    }
}

enum class IdOfferStatus(val id: String) {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"), WITHDRAWN("withdrawn"), VOID("void");

    companion object {
        fun of(id: Any?): IdOfferStatus = entries.firstOrNull { it.id == id } ?: VOID
    }
}

/** Предложение: `idTrades/{tradeId}/offers/{uid}` — id документа = uid предложившего, одно на человека. */
data class IdTradeOffer(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val skinId: String,
    val skin: IdSkinLook,
    val status: IdOfferStatus,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        fun parse(id: String, raw: Map<*, *>?): IdTradeOffer? {
            if (raw == null) return null
            val skin = IdSkinLook.parse(raw["skin"]) ?: return null
            return IdTradeOffer(
                id = id,
                fromUid = raw["fromUid"] as? String ?: id,
                fromName = raw["fromName"] as? String ?: "",
                skinId = raw["skinId"] as? String ?: "",
                skin = skin,
                status = IdOfferStatus.of(raw["status"]),
                createdAt = millis(raw["createdAt"]) ?: 0,
                updatedAt = millis(raw["updatedAt"]) ?: 0,
            )
        }
    }
}
