package org.visorlink.app.data.model

import androidx.compose.ui.graphics.toArgb

/**
 * Оформление профиля — типизированный вид `UserProfile.customization`.
 *
 * Раньше каждый экран сам доставал ключи из `Map<String, Any?>`, и правила
 * расходились: ключ `theme` читался, но не записывался; неизвестная тема молча
 * превращалась в M3E вместо темы зрителя; PRO-проверка баннера в одном экране
 * была, в другом обходилась через `?:`. Теперь разбор, значения по умолчанию и
 * правило «кому что показывать» живут только здесь.
 *
 * `null` в любом поле означает «не задано — используй настройку зрителя».
 */
data class ProfileAppearance(
    val theme: AppTheme? = null,
    val accent: ColorPreset? = null,
    /** Произвольный цвет акцента `#RRGGBB` (из веба); приоритетнее пресета, только для экрана профиля. */
    val accentHex: String? = null,
    val font: ProfileFont? = null,
    val layout: ProfileLayout = ProfileLayout.DEFAULT,
    /** Фон профиля и обои чата «как у собеседника». */
    val backgroundUrl: String? = null,
    /** Анимированный баннер в шапке профиля. */
    val bannerUrl: String? = null,
    /** Эмодзи рядом с именем. */
    val emojis: String? = null,
) {
    val isEmpty: Boolean get() = this == None

    /** Цвет из [accentHex]; формат проверен при разборе. */
    val accentHexColor: androidx.compose.ui.graphics.Color?
        get() = accentHex?.let { runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

    /**
     * Записывает поля обратно в карту customization, не трогая чужие ключи:
     * в той же карте бэкенд хранит `diaryEnabled`, `acceptedVersion` и т.п.
     */
    fun writeTo(customization: Map<String, Any?>): Map<String, Any?> =
        customization.toMutableMap().apply {
            put(KEY_THEME, theme?.id)
            put(KEY_ACCENT, accent?.let { ACCENT_IDS.getValue(it) })
            put(KEY_ACCENT_HEX, accentHex)
            put(KEY_FONT, font?.id)
            put(KEY_LAYOUT, layout.id.takeIf { layout != ProfileLayout.DEFAULT })
            put(KEY_BG, backgroundUrl)
            put(KEY_BANNER, bannerUrl)
            put(KEY_EMOJIS, emojis)
            // null-значения не храним: в Firestore это лишние поля, в старом
            // клиенте — "null" вместо отсутствия
            entries.removeAll { (k, v) -> k in OWN_KEYS && v == null }
        }

    companion object {
        val None = ProfileAppearance()

        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_ACCENT_HEX = "accentHex"
        const val KEY_FONT = "font"
        const val KEY_LAYOUT = "layout"
        const val KEY_BG = "bgUrl"
        const val KEY_BANNER = "gifUrl"
        const val KEY_EMOJIS = "emojis"
        private val OWN_KEYS = setOf(KEY_THEME, KEY_ACCENT, KEY_ACCENT_HEX, KEY_FONT, KEY_LAYOUT, KEY_BG, KEY_BANNER, KEY_EMOJIS)

        /** Строковые id акцентов в Firestore. `DEFAULT` хранится как "default". */
        private val ACCENT_IDS = mapOf(
            ColorPreset.DEFAULT to "default",
            ColorPreset.PURPLE to "purple",
            ColorPreset.BLUE to "blue",
            ColorPreset.EMERALD to "emerald",
            ColorPreset.CRIMSON to "crimson",
        )

        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        /** Цвет пресета в `#RRGGBB` — пишется в `accentHex`, чтобы веб показал тот же цвет. */
        fun hexOf(preset: ColorPreset?): String? = preset?.seedColor?.let {
            "#%06X".format(it.toArgb() and 0xFFFFFF)
        }

        /**
         * Разбор без исключений: неизвестное или битое значение даёт `null`
         * (то есть настройку зрителя), а не дефолт приложения.
         * "default" у акцента и шрифта — тоже «не задано».
         */
        fun parse(customization: Map<String, Any?>?): ProfileAppearance {
            if (customization.isNullOrEmpty()) return None
            fun str(key: String) = (customization[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return ProfileAppearance(
                theme = str(KEY_THEME)?.let { id -> AppTheme.entries.firstOrNull { it.id == id } },
                accent = str(KEY_ACCENT)
                    ?.let { id -> ACCENT_IDS.entries.firstOrNull { it.value == id }?.key }
                    ?.takeIf { it != ColorPreset.DEFAULT },
                accentHex = str(KEY_ACCENT_HEX)?.takeIf { HEX_COLOR.matches(it) },
                font = str(KEY_FONT)?.let(ProfileFont::fromId),
                layout = ProfileLayout.fromId(str(KEY_LAYOUT)),
                backgroundUrl = str(KEY_BG),
                bannerUrl = str(KEY_BANNER),
                emojis = str(KEY_EMOJIS),
            )
        }

        /**
         * Что именно увидит [viewer], открыв профиль или чат [owner].
         *
         * - Свой профиль — всегда своё оформление: человек должен видеть результат
         *   редактора, даже если PRO истёк (тогда его не видят другие).
         * - Чужой — только если у владельца активен PRO и зритель не отключил
         *   чужое оформление (`ignoreCustomizations`).
         */
        fun resolve(owner: UserProfile?, viewer: UserProfile?): ProfileAppearance {
            if (owner == null || viewer == null) return None
            val visible = owner.uid == viewer.uid ||
                (owner.isProActive() && !viewer.ignoreCustomizations)
            return if (visible) parse(owner.customization) else None
        }
    }
}

/** Гарнитура профиля. Каждая должна покрывать кириллицу — см. FontGlyphCoverageTest. */
enum class ProfileFont(val id: String) {
    MONO("mono"),
    SERIF("serif"),
    ROUNDED("rounded");

    companion object {
        fun fromId(id: String?): ProfileFont? = entries.firstOrNull { it.id == id }
    }
}

enum class ProfileLayout(val id: String) {
    DEFAULT("default"),
    COMPACT("compact");

    companion object {
        fun fromId(id: String?): ProfileLayout = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
