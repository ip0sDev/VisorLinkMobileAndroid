package org.visorlink.app.data.idcard

/**
 * ID-карта VisorLink (веб: functions/idCardLogic.js, src/services/idCardService.ts).
 *
 * Карту, её признаки и серийник выдаёт только сервер: клиент читает `idCards/{uid}`,
 * `users.idMode`, `chats.groupId` и вызывает callables, но никогда не пишет туда сам —
 * правила Firestore это запрещают.
 */
enum class IdMode(val id: String) {
    STANDARD("standard"),
    PROTOGEN("protogen"),
    BEAST("beast");

    val isSpecial: Boolean get() = this != STANDARD

    companion object {
        /** Неизвестное или пустое значение — Standard (поле `users.idMode` может отсутствовать). */
        fun of(id: Any?): IdMode = entries.firstOrNull { it.id == id } ?: STANDARD

        /** Как `isSpecialIdMode` в вебе: мусор в поле — не особый режим. */
        fun isSpecial(id: Any?): Boolean = of(id).isSpecial
    }
}

enum class IdFinish(val id: String) {
    BASE("base"), PEARL("pearl"), METALLIC("metallic"), OBSIDIAN("obsidian"), GOLD("gold");

    companion object {
        fun of(id: Any?): IdFinish = entries.firstOrNull { it.id == id } ?: BASE
    }
}

enum class IdFoil(val id: String) {
    NONE("none"), CLASSIC("classic"), PRISM("prism"), AURORA("aurora"), GALAXY("galaxy");

    companion object {
        fun of(id: Any?): IdFoil = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Тираж: у карты считается из признаков (`editionOf`), у скина хранится (у старых записей может не быть). */
enum class IdEdition(val id: String) {
    COMMON("common"), UNCOMMON("uncommon"), RARE("rare"), EPIC("epic"), LEGENDARY("legendary");

    companion object {
        fun of(id: Any?): IdEdition? = entries.firstOrNull { it.id == id }
    }
}

/** Где в профиле карта (`customization.idCardPosition`; не PRO — работает у всех). */
enum class IdCardPosition(val id: String) {
    TOP("top"), AFTER_HEADER("afterHeader"), BOTTOM("bottom");

    companion object {
        const val KEY = "idCardPosition"
        fun of(customization: Map<String, Any?>?): IdCardPosition =
            entries.firstOrNull { it.id == customization?.get(KEY) } ?: AFTER_HEADER
    }
}

/** Признаки выпуска: решает сервер, по ним клиент рисует редкость. */
data class IdCardTraits(
    /** uint32 — из него клиент выводит все мелкие детали ([IdCardGenerator.deriveDetails]). */
    val seed: Long = 1,
    val finish: IdFinish = IdFinish.BASE,
    val laminated: Boolean = false,
    val wear: Int = 0,
    val foil: IdFoil = IdFoil.NONE,
    /** Палитра 0–11. */
    val variant: Int = 0,
    /** Наклон фото, градусы (−1.5…1.5). */
    val tilt: Double = 0.0,
) {
    val edition: IdEdition get() = editionOf(finish, foil)

    companion object {
        /** [fallbackSeed] — `seed` рядом с `traits` (так хранится скин), если в самих признаках его нет. */
        fun parse(raw: Any?, fallbackSeed: Any? = null): IdCardTraits {
            val m = raw as? Map<*, *> ?: return IdCardTraits()
            return IdCardTraits(
                seed = ((m["seed"] ?: fallbackSeed) as? Number)?.toLong()?.and(0xFFFFFFFFL)?.takeIf { it != 0L } ?: 1L,
                finish = IdFinish.of(m["finish"]),
                laminated = m["laminated"] == true,
                wear = ((m["wear"] as? Number)?.toInt() ?: 0).coerceIn(0, 3),
                foil = IdFoil.of(m["foil"]),
                variant = (m["variant"] as? Number)?.toInt() ?: 0,
                tilt = (m["tilt"] as? Number)?.toDouble() ?: 0.0,
            )
        }
    }
}

data class IdCard(
    val mode: IdMode = IdMode.STANDARD,
    /** Только у protogen/beast; у protogen подпись поля — «Модель». */
    val species: String? = null,
    val serial: String = "VL-0000-0000",
    val traits: IdCardTraits = IdCardTraits(),
    val issuedAt: Long = 0,
    /** Дата регистрации аккаунта — печатается на карте. */
    val registeredAt: Long = 0,
    val modeChangedAt: Long = 0,
    /** Последняя «Заменить документ» (её больше нет) — для кулдауна прокрутки у старых карт. */
    val reissuedAt: Long? = null,
    val version: Int = 1,
    /**
     * Надетый скин. У карт, выданных до инвентаря, нет — сервер перенесёт бланк в инвентарь
     * при первом действии, до тех пор карта показывается единственным скином ([IdSkinRules.inventoryOf]).
     */
    val skinId: String? = null,
    /** Слотов инвентаря (5…20); нет — базовые 5. */
    val slots: Int? = null,
    val rolledAt: Long? = null,
    val equippedAt: Long? = null,
) {
    companion object {
        /** Разбор документа `idCards/{uid}` или ответа callable (`{ card }`). */
        fun parse(raw: Map<*, *>?): IdCard? {
            if (raw == null) return null
            return IdCard(
                mode = IdMode.of(raw["mode"]),
                species = (raw["species"] as? String)?.takeIf { it.isNotBlank() },
                serial = raw["serial"] as? String ?: "VL-0000-0000",
                traits = IdCardTraits.parse(raw["traits"]),
                issuedAt = millis(raw["issuedAt"]) ?: 0,
                registeredAt = millis(raw["registeredAt"]) ?: 0,
                modeChangedAt = millis(raw["modeChangedAt"]) ?: 0,
                reissuedAt = millis(raw["reissuedAt"]),
                version = (raw["version"] as? Number)?.toInt() ?: 1,
                skinId = (raw["skinId"] as? String)?.takeIf { it.isNotEmpty() },
                slots = (raw["slots"] as? Number)?.toInt(),
                rolledAt = millis(raw["rolledAt"]),
                equippedAt = millis(raw["equippedAt"]),
            )
        }
    }
}

/** «ID группы» (`chats/{chatId}.groupId`). */
data class GroupIdCard(
    val enabled: Boolean = false,
    val serial: String? = null,
    val traits: IdCardTraits = IdCardTraits(),
    val issuedAt: Long = 0,
    val changedAt: Long = 0,
    val issuedBy: String? = null,
) {
    /** Карта группы рисуется в режиме владельца; «зарегистрирована» = выдана. */
    fun asCard(ownerMode: IdMode): IdCard? = serial?.let {
        IdCard(mode = ownerMode, serial = it, traits = traits, issuedAt = issuedAt, registeredAt = issuedAt)
    }

    companion object {
        fun parse(raw: Any?): GroupIdCard? {
            val m = raw as? Map<*, *> ?: return null
            return GroupIdCard(
                enabled = m["enabled"] == true,
                serial = m["serial"] as? String,
                traits = IdCardTraits.parse(m["traits"]),
                issuedAt = millis(m["issuedAt"]) ?: 0,
                changedAt = millis(m["changedAt"]) ?: 0,
                issuedBy = m["issuedBy"] as? String,
            )
        }
    }
}

object IdCardRules {
    const val MODE_COOLDOWN_MS = 15_000L
    const val SPECIES_MAX = 40

    /** Когда снова можно сменить режим (мс эпохи). */
    fun modeAvailableAt(card: IdCard): Long = card.modeChangedAt + MODE_COOLDOWN_MS
}

private val FINISH_SCORE = mapOf(
    IdFinish.BASE to 0, IdFinish.PEARL to 1, IdFinish.METALLIC to 2, IdFinish.OBSIDIAN to 3, IdFinish.GOLD to 5,
)
private val FOIL_SCORE = mapOf(
    IdFoil.NONE to 0, IdFoil.CLASSIC to 0, IdFoil.PRISM to 1, IdFoil.AURORA to 2, IdFoil.GALAXY to 4,
)

/** Тираж по редкости (как `editionOf` в functions/idCardLogic.js). */
fun editionOf(finish: IdFinish, foil: IdFoil): IdEdition {
    val score = FINISH_SCORE.getValue(finish) + FOIL_SCORE.getValue(foil)
    return when {
        score >= 7 -> IdEdition.LEGENDARY
        score >= 5 -> IdEdition.EPIC
        score >= 3 -> IdEdition.RARE
        score >= 2 -> IdEdition.UNCOMMON
        else -> IdEdition.COMMON
    }
}

/** Время из Firestore: мс числом, Timestamp или Date. */
internal fun millis(v: Any?): Long? = when (v) {
    is Number -> v.toLong()
    is com.google.firebase.Timestamp -> v.toDate().time
    is java.util.Date -> v.time
    else -> null
}
