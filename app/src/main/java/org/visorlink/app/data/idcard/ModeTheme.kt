package org.visorlink.app.data.idcard

/**
 * Чистые правила темы по режиму ID-карты — порт `src/utils/modeTheme.js` (веб-тесты
 * functions/test/modeTheme.test.js перенесены в ModeThemeTest).
 */

/** Контекст темы открытого чата. */
enum class ChatThemeContext {
    /** Biolume: собеседник со Standard-ID, группа/канал без «ID группы». */
    NEUTRAL,

    /** Тема своего режима. */
    MODE,
}

enum class ModeStyle { BIOLUME, FORGE }

/**
 * @property flavor оттенок Forge (protogen / beast), только при [ModeStyle.FORGE].
 * @property forceDark тёмная держится весь особый режим, даже в Biolume-чате.
 */
data class ModeTheme(val style: ModeStyle, val flavor: IdMode?, val forceDark: Boolean) {
    companion object {
        val Biolume = ModeTheme(ModeStyle.BIOLUME, null, false)
    }
}

object ModeThemeRules {

    /**
     * Тема интерфейса.
     * @param chatTheme контекст открытого чата; `null` — чат не открыт (список, настройки).
     */
    fun resolve(idCardsEnabled: Boolean, idMode: Any?, masked: Boolean = false, chatTheme: ChatThemeContext? = null): ModeTheme {
        val mode = IdMode.of(idMode)
        val special = idCardsEnabled && IdMode.isSpecial(idMode) && !masked
        val forge = special && chatTheme != ChatThemeContext.NEUTRAL
        return ModeTheme(
            style = if (forge) ModeStyle.FORGE else ModeStyle.BIOLUME,
            flavor = if (forge) mode else null,
            forceDark = special,
        )
    }

    /**
     * Контекст темы открытого чата. `null` — ещё неизвестно (профиль собеседника грузится):
     * тема не меняется, чтобы не мигать.
     *
     * ЛС: собеседник с особым режимом или бот → MODE (боты тему не трогают), Standard → NEUTRAL;
     * группа/канал: включён «ID группы» → MODE, иначе NEUTRAL.
     */
    fun chatThemeFor(isDirect: Boolean, partnerLoaded: Boolean, partnerIsBot: Boolean, partnerIdMode: Any?, groupIdEnabled: Boolean?): ChatThemeContext? {
        if (isDirect) {
            if (!partnerLoaded) return null
            if (partnerIsBot) return ChatThemeContext.MODE
            return if (IdMode.isSpecial(partnerIdMode)) ChatThemeContext.MODE else ChatThemeContext.NEUTRAL
        }
        return if (groupIdEnabled == true) ChatThemeContext.MODE else ChatThemeContext.NEUTRAL
    }

    /**
     * Тема панели чужого профиля: смотрящий с особым режимом видит гамму владельца
     * (его оттенок Forge или Biolume), всегда тёмную. `null` — панель в общей теме.
     */
    fun ownerProfileTheme(viewerSpecial: Boolean, isMe: Boolean, ownerMode: Any?): ModeTheme? {
        if (!viewerSpecial || isMe) return null
        val owner = IdMode.of(ownerMode)
        return if (owner.isSpecial) ModeTheme(ModeStyle.FORGE, owner, true) else ModeTheme(ModeStyle.BIOLUME, null, true)
    }
}
