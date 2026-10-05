package org.visorlink.app.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * @property until время окончания (мс) или `null` — до выключения.
 */
data class MaskState(val active: Boolean, val until: Long?) {
    companion object {
        val Off = MaskState(false, null)
    }
}

/** Варианты срока маски (веб: MASK_DURATIONS в hooks/useMaskMode.js). */
enum class MaskDuration {
    FOREVER, M15, H1, H4, TOMORROW;

    /** Время окончания для «сейчас» = [now]; `null` — до выключения. */
    fun until(now: Long): Long? = when (this) {
        FOREVER -> null
        M15 -> now + 15 * 60_000L
        H1 -> now + 60 * 60_000L
        H4 -> now + 4 * 60 * 60_000L
        TOMORROW -> Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        /** «ЧЧ:ММ» → ближайшее такое время в будущем (сегодня или завтра). */
        fun untilClock(hour: Int, minute: Int, now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
}

/**
 * Mask Mode (спека §8): скрыть свой особый режим только на этом устройстве — Biolume везде,
 * обычный выбор светлой/тёмной темы, карты и значки режима скрыты. На сервер ничего не пишется.
 *
 * Хранится в SharedPreferences `visorlink_mask_prefs` (в проекте нет DataStore): `until`
 * в мс или −1 — бессрочно. Истёкший срок снимается при чтении (в том числе при запуске,
 * если приложение было закрыто) и по таймеру.
 */
class MaskModeManager(context: Context, private val now: () -> Long = System::currentTimeMillis) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timer: Job? = null

    private val _state = MutableStateFlow(read())
    val state: StateFlow<MaskState> = _state.asStateFlow()

    init {
        schedule()
    }

    private fun read(): MaskState {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return MaskState.Off
        val until = prefs.getLong(KEY_UNTIL, FOREVER)
        if (until == FOREVER) return MaskState(true, null)
        if (until > now()) return MaskState(true, until)
        prefs.edit().clear().apply() // истёк, пока приложение было закрыто
        return MaskState.Off
    }

    /** Включить маску: [until] — время окончания (мс) или `null` — до выключения. */
    fun enable(until: Long?) {
        prefs.edit().putBoolean(KEY_ACTIVE, true).putLong(KEY_UNTIL, until ?: FOREVER).apply()
        _state.value = MaskState(true, until)
        schedule()
    }

    fun disable() {
        prefs.edit().clear().apply()
        _state.value = MaskState.Off
        schedule()
    }

    /** Перепроверить срок (например, при возврате приложения на передний план). */
    fun refresh() {
        val next = read()
        if (next != _state.value) _state.value = next
        schedule()
    }

    private fun schedule() {
        timer?.cancel()
        val until = _state.value.until ?: return
        timer = scope.launch {
            delay((until - now()).coerceAtLeast(0) + 50)
            refresh()
        }
    }

    private companion object {
        const val PREFS = "visorlink_mask_prefs"
        const val KEY_ACTIVE = "active"
        const val KEY_UNTIL = "until"
        const val FOREVER = -1L
    }
}
