package org.visorlink.app.utils

import org.visorlink.app.ui.components.led.VlLedExpression
import org.visorlink.app.ui.components.led.vlLedFaceRows

// LED-мордочка плеера (Forge), которая кивает в такт (порт Flutter `beat_logic.dart`):
// детектор ударов по басу спектра, форма кивка и выбор выражения — отдельно от виджета.

/**
 * Ищет удары в потоке уровней спектра 0…1. Бас (первые полосы) сравнивается со своим
 * скользящим средним: удар — резкий подъём заметно выше среднего, не чаще чем раз в
 * [cooldown]. Адаптивный порог не требует настройки под громкость трека.
 */
class BeatDetector(
    /** Сколько нижних полос считаем басом. */
    val bassBands: Int = 6,
    /** Во сколько раз бас должен превысить своё среднее. */
    val sensitivity: Float = 1.35f,
    /** Ниже этой энергии удара нет (тишина, шум). */
    val minEnergy: Float = 0.12f,
    /** Минимум времени между ударами: быстрее 260 уд/мин мордочка не успеет кивнуть. */
    val cooldownMs: Long = 230,
    /** Сколько последних кадров усредняем (≈0.7 с при 60 кадрах/с). */
    val window: Int = 43,
) {
    private val history = ArrayDeque<Float>()
    private var lastBeatMs: Long? = null
    private var prev = 0f
    private var energyValue = 0f

    /** Энергия баса последнего кадра, 0…1. */
    val energy: Float get() = energyValue

    /** Время последнего удара (мс от произвольной базы). */
    val lastBeatMsValue: Long? get() = lastBeatMs

    /** Сброс (смена трека, пауза): прежнее среднее к новой музыке не относится. */
    fun reset() {
        history.clear()
        lastBeatMs = null
        prev = 0f
        energyValue = 0f
    }

    /** Подать кадр уровней [levels] в момент [nowMs]; `true` — на этом кадре удар. */
    fun feed(levels: List<Float>, nowMs: Long): Boolean {
        if (levels.isEmpty()) return false
        val n = minOf(bassBands, levels.size)
        var sum = 0f
        for (i in 0 until n) {
            sum += levels[i].coerceIn(0f, 1f)
        }
        val bass = sum / n
        energyValue = bass

        var avg = 0f
        for (v in history) avg += v
        val warm = history.size >= 8
        if (warm) avg /= history.size

        val last = lastBeatMs
        val rested = last == null || nowMs - last >= cooldownMs
        // Подъём, а не плато: громкий ровный бас («пол») не должен давать удар на каждом кадре
        val rising = bass > prev
        val beat = warm && rested && rising && bass >= minEnergy && bass > avg * sensitivity

        history.addLast(bass)
        if (history.size > window) history.removeFirst()
        prev = bass
        if (beat) lastBeatMs = nowMs
        return beat
    }
}

/** Насколько голова опущена (в рядах матрицы) через [sinceBeatMs] после удара: быстро вниз на 2 ряда, обратно за 170 мс. */
fun beatNodRows(sinceBeatMs: Long?): Int {
    if (sinceBeatMs == null || sinceBeatMs < 0) return 0
    return when {
        sinceBeatMs < 50 -> 1
        sinceBeatMs < 110 -> 2
        sinceBeatMs < 170 -> 1
        else -> 0
    }
}

/** Дыхание без музыки (нет настоящего спектра): голова мягко опускается на ряд раз в 1.6 с. */
fun idleBreathRows(elapsedMs: Long): Int = if ((elapsedMs / 1600) % 2 == 0L) 0 else 1

/** Глаза закрыты на 140 мс раз в 4.2 с (детерминированно: ни случайных чисел, ни таймеров). */
fun beatFaceBlink(elapsedMs: Long): Boolean = elapsedMs % 4200 < 140

/** Состояние плеера, из которого выбирается выражение. */
enum class BeatFaceMood { SLEEPING, SCANNING, PLAYING, LOUD }

/** Выражение по настроению: спит на паузе, бегающие глаза при загрузке, весёлая при игре, глаза-блюдца на громком басу. */
fun beatFaceExpression(mood: BeatFaceMood): VlLedExpression = when (mood) {
    BeatFaceMood.SLEEPING -> VlLedExpression.SLEEPY
    BeatFaceMood.SCANNING -> VlLedExpression.SCAN
    BeatFaceMood.PLAYING -> VlLedExpression.HAPPY
    BeatFaceMood.LOUD -> VlLedExpression.WIDE
}

fun beatFaceMood(playing: Boolean, loading: Boolean, beatNow: Boolean, energy: Float): BeatFaceMood = when {
    loading -> BeatFaceMood.SCANNING
    !playing -> BeatFaceMood.SLEEPING
    beatNow && energy >= 0.7f -> BeatFaceMood.LOUD
    else -> BeatFaceMood.PLAYING
}

/** Рядов, на которые мордочка может опуститься (запас под кивок снизу). */
const val BEAT_FACE_PAD = 2

/**
 * Кадр мордочки: 12 рядов выражения и [BEAT_FACE_PAD] запасных, сам рисунок сдвинут вниз
 * на [nod] рядов (0…[BEAT_FACE_PAD]). Так кивок не обрезает рот и не меняет размер матрицы.
 */
fun beatFaceRows(expression: VlLedExpression, nod: Int = 0, mouthShift: Int = 0, blink: Boolean = false): List<String> {
    val face = vlLedFaceRows(expression, mouthShift = mouthShift, blink = blink)
    val n = nod.coerceIn(0, BEAT_FACE_PAD)
    val width = face.firstOrNull()?.length ?: 0
    val blank = ".".repeat(width)
    return buildList {
        repeat(n) { add(blank) }
        addAll(face)
        repeat(BEAT_FACE_PAD - n) { add(blank) }
    }
}
