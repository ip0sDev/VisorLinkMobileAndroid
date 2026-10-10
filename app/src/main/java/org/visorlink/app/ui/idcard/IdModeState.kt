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
    /** Пользователь вошёл: действуют ID-карты и тема по режиму. */
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
 * Контекст темы открытого чата (веб: useChatThemeContext).
 *
 * Тема чата привязана к **стеку навигации**, а не к жизни экрана:
 *  - NavGraph сообщает верхний экран чата в стеке ([setActive]) в момент навигации — до анимации
 *    перехода. Закрыли чат — его запись ушла из стека сразу, и тема возвращается в начале
 *    анимации «назад», а не после неё (раньше список чатов успевал показаться в теме чата);
 *  - ChatScreen объявляет точный контекст ([declare]), когда собеседник или чат загрузились;
 *  - пока точного нет — берётся последний известный для этого чата ([Memory], между запусками):
 *    повторно открытый чат сразу в своей теме, без перескока Forge ↔ Biolume через полсекунды.
 */
class ChatThemeController(private val memory: Memory = Memory.None) {

    /** Последний известный контекст по chatId (прогноз, пока профиль собеседника грузится). */
    interface Memory {
        fun get(chatId: String): ChatThemeContext?
        fun put(chatId: String, value: ChatThemeContext)

        object None : Memory {
            override fun get(chatId: String): ChatThemeContext? = null
            override fun put(chatId: String, value: ChatThemeContext) = Unit
        }
    }

    private data class Active(val entryId: String, val chatId: String)

    private val active = MutableStateFlow<Active?>(null)
    private val declared = MutableStateFlow<Map<String, ChatThemeContext>>(emptyMap())
    private val _context = MutableStateFlow<ChatThemeContext?>(null)

    /** Действующий контекст: точный для верхнего чата в стеке, иначе прогноз; `null` — чат не открыт. */
    val context: StateFlow<ChatThemeContext?> = _context.asStateFlow()

    /** Верхний экран чата в стеке навигации (`null` — чатов в стеке нет). */
    fun setActive(entryId: String?, chatId: String?) {
        active.value = if (entryId != null && chatId != null) Active(entryId, chatId) else null
        // Записи ушедших из стека экранов больше не нужны
        if (entryId == null) declared.value = emptyMap()
        recompute()
    }

    /** Точный контекст экрана чата [entryId]; `null` — ещё неизвестно (оставить прогноз). */
    fun declare(entryId: String, chatId: String, value: ChatThemeContext?) {
        if (value == null) return
        if (declared.value[entryId] != value) declared.value = declared.value + (entryId to value)
        memory.put(chatId, value)
        recompute()
    }

    fun forget(entryId: String) {
        if (entryId !in declared.value) return
        declared.value = declared.value - entryId
        recompute()
    }

    private fun recompute() {
        val a = active.value
        _context.value = if (a == null) null else declared.value[a.entryId] ?: memory.get(a.chatId)
    }
}

/** Прогноз темы чатов в SharedPreferences: «n» — нейтральная (Biolume), «m» — тема режима. */
class PrefsChatThemeMemory(context: android.content.Context) : ChatThemeController.Memory {
    private val prefs = context.getSharedPreferences("visorlink_chat_theme", android.content.Context.MODE_PRIVATE)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ChatThemeContext>()

    override fun get(chatId: String): ChatThemeContext? = cache[chatId] ?: when (prefs.getString(chatId, null)) {
        "n" -> ChatThemeContext.NEUTRAL
        "m" -> ChatThemeContext.MODE
        else -> null
    }?.also { cache[chatId] = it }

    override fun put(chatId: String, value: ChatThemeContext) {
        if (cache[chatId] == value) return
        cache[chatId] = value
        prefs.edit().putString(chatId, if (value == ChatThemeContext.NEUTRAL) "n" else "m").apply()
    }
}

/**
 * Объявить тему открытого чата. `null` — ещё неизвестно (собеседник грузится): действует
 * прогноз по прошлому открытию. Экран чата узнаётся по своей записи в стеке навигации.
 */
@Composable
fun DeclareChatTheme(chatId: String, value: ChatThemeContext?) {
    val controller: ChatThemeController = koinInject()
    val entryId = (androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current as? androidx.navigation.NavBackStackEntry)?.id ?: return
    // SideEffect, а не LaunchedEffect: контекст известен в этом же кадре, без лишнего кадра в старой теме
    androidx.compose.runtime.SideEffect { controller.declare(entryId, chatId, value) }
    DisposableEffect(entryId) { onDispose { controller.forget(entryId) } }
}
