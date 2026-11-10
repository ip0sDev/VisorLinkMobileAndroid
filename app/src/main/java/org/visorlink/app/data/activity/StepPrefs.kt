package org.visorlink.app.data.activity

import android.content.Context

/**
 * Локальное состояние шагомера: согласие пользователя и база кумулятивного счётчика.
 * SharedPreferences по образцу `visorlink_flags_prefs` (FlagsRepository).
 */
class StepPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Явное согласие на подсчёт шагов (после экрана-объяснения и разрешения ACTIVITY_RECOGNITION). */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) reset()
        }

    fun state(): StepSampleState = StepSampleState(
        baseline = prefs.getInt(KEY_BASELINE, 0),
        bootCount = prefs.getInt(KEY_BOOT_COUNT, -1),
        lastDay = prefs.getString(KEY_LAST_DAY, null),
        lastSteps = prefs.getInt(KEY_LAST_STEPS, 0),
        lastSampleAt = prefs.getLong(KEY_LAST_SAMPLE_AT, 0L)
    )

    fun save(state: StepSampleState) {
        prefs.edit()
            .putInt(KEY_BASELINE, state.baseline)
            .putInt(KEY_BOOT_COUNT, state.bootCount)
            .putString(KEY_LAST_DAY, state.lastDay)
            .putInt(KEY_LAST_STEPS, state.lastSteps)
            .putLong(KEY_LAST_SAMPLE_AT, state.lastSampleAt)
            .apply()
    }

    /** Сброс базы сэмплирования (счётчик, не согласие). */
    fun reset() {
        prefs.edit()
            .remove(KEY_BASELINE)
            .remove(KEY_BOOT_COUNT)
            .remove(KEY_LAST_DAY)
            .remove(KEY_LAST_STEPS)
            .remove(KEY_LAST_SAMPLE_AT)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "visorlink_steps_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASELINE = "baseline"
        const val KEY_BOOT_COUNT = "boot_count"
        const val KEY_LAST_DAY = "last_day"
        const val KEY_LAST_STEPS = "last_steps"
        const val KEY_LAST_SAMPLE_AT = "last_sample_at"
    }
}
