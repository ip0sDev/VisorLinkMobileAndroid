package org.visorlink.app.ui.idcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject
import org.visorlink.app.data.idcard.ChatThemeContext
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.ModeTheme
import org.visorlink.app.utils.MaskState

/**
 * Состояние режима ID-карты для всего интерфейса. Считается один раз в MainActivity
 * (флаг, свой `users.idMode`, маска, контекст чата) и раздаётся вниз, чтобы экраны не
 * собирали его каждый сам.
 */
@Immutable
data class IdModeUiState(
    /** Флаг `id_cards_enabled`. */
    val enabled: Boolean = false,
    val myUid: String? = null,
    val myMode: IdMode = IdMode.STANDARD,
    val mask: MaskState = MaskState.Off,
    val theme: ModeTheme = ModeTheme.Biolume,
) {
    /** Свой особый режим без учёта маски — маска доступна только при нём. */
    val special: Boolean get() = enabled && myMode.isSpecial

    /**
     * Смотрящий с особым режимом и без маски: видит чужие карты, значки режима и
     * чужие профили в гамме владельца (спека §8–§10).
     */
    val viewerSpecial: Boolean get() = special && !mask.active

    /** Карты в профилях показываются (маска скрывает и свою, и чужие). */
    val cardsVisible: Boolean get() = enabled && !mask.active
}

val LocalIdModeState = staticCompositionLocalOf { IdModeUiState() }

/**
 * Контекст темы открытого чата (веб: useChatThemeContext). ChatScreen объявляет его
 * через [DeclareChatTheme], при закрытии чата сбрасывается в `null`.
 */
class ChatThemeController {
    private val _context = MutableStateFlow<ChatThemeContext?>(null)
    val context: StateFlow<ChatThemeContext?> = _context.asStateFlow()

    fun set(value: ChatThemeContext?) {
        _context.value = value
    }
}

/** `null` — ещё неизвестно (профиль собеседника грузится): тема не меняется. */
@Composable
fun DeclareChatTheme(value: ChatThemeContext?) {
    val controller: ChatThemeController = koinInject()
    DisposableEffect(value) {
        if (value != null) controller.set(value)
        onDispose { if (value != null) controller.set(null) }
    }
}
