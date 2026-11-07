package org.visorlink.app.data.activity

/**
 * Кумулятивный счётчик шагов (TYPE_STEP_COUNTER) — чистая логика сэмплирования
 * (зеркало серверного activityLogic.js по порогам; начисляет Bits только сервер).
 *
 * Счётчик считает шаги с момента загрузки устройства и обнуляется при перезагрузке,
 * поэтому слушаем его точечно (экран открыт / периодический воркер), а не непрерывно:
 * один сэмпл возвращает все шаги, накопившиеся с прошлого сэмпла.
 */
data class StepSampleState(
    /** Значение TYPE_STEP_COUNTER на последнем сэмпле. */
    val baseline: Int = 0,
    /** Settings.Global.BOOT_COUNT на последнем сэмпле; -1 — состояния ещё нет. */
    val bootCount: Int = -1,
    /** Локальный день последнего сэмпла, 'YYYY-MM-DD'. */
    val lastDay: String? = null,
    /** Дневной тотал на последний сэмпл. */
    val lastSteps: Int = 0,
    /** Момент последнего сэмпла, мс. */
    val lastSampleAt: Long = 0L
)

data class StepSample(
    val todaySteps: Int,
    val state: StepSampleState
)

object StepCounterModel {

    /** Пороги дня и цена порога — зеркало activityLogic.js (сервер отдаёт актуальные в ответе). */
    val THRESHOLDS = listOf(1000, 3000, 6000, 10000)
    const val AWARD_BASE = 5
    const val AWARD_BOOST = 10

    /**
     * Новый сэмпл.
     *
     * @param bootCount  Settings.Global.BOOT_COUNT сейчас
     * @param bootDayKey локальный день загрузки устройства (now - elapsedRealtime)
     * @param cumulative текущее значение TYPE_STEP_COUNTER
     * @param dayKey     локальный день сейчас, 'YYYY-MM-DD'
     */
    fun onSample(
        saved: StepSampleState,
        bootCount: Int,
        bootDayKey: String?,
        cumulative: Int,
        nowMs: Long,
        dayKey: String
    ): StepSample = when {
        // Первый запуск: базы ещё нет — начинаем с текущего значения счётчика
        saved.bootCount < 0 -> StepSample(
            todaySteps = 0,
            state = StepSampleState(cumulative, bootCount, dayKey, 0, nowMs)
        )
        // Ребут обнуляет счётчик: шаги до выключения теряются («шаги считаются, пока телефон включён»).
        // Загрузка случилась сегодня — пост-бутовые шаги уже в cumulative; иначе начинаем день с нуля.
        bootCount != saved.bootCount -> {
            val today = if (bootDayKey == dayKey) cumulative.coerceAtLeast(0) else 0
            StepSample(today, StepSampleState(cumulative, bootCount, dayKey, today, nowMs))
        }
        // Тот же день: дельта с прошлого сэмпла
        saved.lastDay == dayKey -> {
            val today = saved.lastSteps + (cumulative - saved.baseline).coerceAtLeast(0)
            StepSample(today, StepSampleState(cumulative, bootCount, dayKey, today, nowMs))
        }
        // Новый день: шаги с прошлого сэмпла (включая ночные) относим к новому дню
        else -> {
            val today = (cumulative - saved.baseline).coerceAtLeast(0)
            StepSample(today, StepSampleState(cumulative, bootCount, dayKey, today, nowMs))
        }
    }

    /** Следующий непройденный порог, null — все пройдены. */
    fun nextThreshold(steps: Int, thresholds: List<Int> = THRESHOLDS): Int? =
        thresholds.firstOrNull { it > steps }

    /** Сколько порогов пройдено (для локальной оценки Bits до ответа сервера). */
    fun crossedCount(steps: Int, thresholds: List<Int> = THRESHOLDS): Int =
        thresholds.count { steps >= it }
}
