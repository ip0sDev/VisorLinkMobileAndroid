package org.visorlink.app.utils

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Раскладка FFT системного Visualizer по полосам LED-спектра (порт Flutter `music_fft.dart`).
// Формат `Visualizer.getFft`: [0] — действительная часть DC, [1] — частоты Найквиста,
// дальше пары (Re, Im) для бинов 1..n/2−1; байты знаковые. Полосы — логарифмические,
// как слышит ухо: бас слева, верх справа.

/** Амплитуда бина [k] (1..n/2−1). */
fun fftMagnitude(fft: ByteArray, k: Int): Float {
    val re = fft[2 * k].toInt()
    val im = fft[2 * k + 1].toInt()
    return sqrt((re * re + im * im).toFloat())
}

/** Границы полосы [i] из [bands] в герцах: логарифмически от [lo] до [hi]. */
fun fftBandEdges(i: Int, bands: Int, lo: Float = 40f, hi: Float = 16000f): Pair<Float, Float> =
    (lo * (hi / lo).pow(i.toFloat() / bands)) to (lo * (hi / lo).pow((i + 1).toFloat() / bands))

/**
 * Уровни [bands] полос 0..1: в полосе берётся самый громкий бин, его громкость в дБ от
 * [floorDb] до 0 (полная шкала байта) ложится на 0..1. Узкая басовая полоса без своего
 * бина берёт ближайший.
 */
fun fftBands(frame: MusicFftFrame, bands: Int, lo: Float = 40f, hi: Float = 16000f, floorDb: Float = -50f): List<Float> {
    val fft = frame.fft
    val half = fft.size / 2
    if (half < 2 || frame.rateMilliHz <= 0 || bands <= 0) return List(max(bands, 0)) { 0f }
    val binHz = frame.rateMilliHz / 1000f / fft.size
    val top = min(hi, binHz * (half - 1))
    return List(bands) { i ->
        val (f0, f1) = fftBandEdges(i, bands, lo, top)
        val k0 = (f0 / binHz).toInt().coerceIn(1, half - 1)
        val k1 = max(k0 + 1, (f1 / binHz).toInt()).coerceIn(k0 + 1, half)
        var mag = 0f
        for (k in k0 until k1) {
            mag = max(mag, fftMagnitude(fft, k))
        }
        if (mag <= 0f) return@List 0f
        val db = 20f * ln(mag / 128f) / ln(10f)
        ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }
}

// ── Процедурный «спектр» (порт Flutter `music_spectrum.dart`) ───────────────────────────────────
// Это НЕ анализ звука: столбики — детерминированная функция от id трека и позиции воспроизведения:
// у каждого трека свой темп и рисунок, «бас» бьёт в такт, перемотка даёт ту же картинку. В
// интерфейсе его нельзя подписывать как анализ звука.

private fun fnvSeed(id: String): Long {
    // FNV-1a: String.hashCode не обязан совпадать между запусками
    var h = 0x811c9dc5L
    for (c in id) {
        h = ((h xor c.code.toLong()) * 0x01000193L) and 0xffffffffL
    }
    return h
}

/** Темп «баса» трека, ударов в минуту (88–143). */
fun spectrumBpm(trackId: String): Int = 88 + (fnvSeed(trackId) % 56).toInt()

/** Уровни [bands] полос (0 — бас) в момент [positionMs], каждый 0..1. */
fun proceduralSpectrum(trackId: String, positionMs: Long, bands: Int): List<Float> {
    val seed = fnvSeed(trackId)
    // Простая линейная последовательность от seed: детерминирована между запусками
    var rndState = seed
    fun nextFloat(): Float {
        rndState = (rndState * 6364136223846793005L + 1442695040888963407L) and 0x7fffffffffffffffL
        return (rndState ushr 40).toFloat() / (1 shl 24).toFloat()
    }
    val t = positionMs / 1_000_000f
    val beat = 60f / spectrumBpm(trackId)
    // Удар: резкий всплеск в начале доли и спад к её концу
    val kick = exp(-((t % beat) / beat) * 6)
    val rates = List(bands) { 0.6f + nextFloat() * 2.4f }
    val phases = List(bands) { nextFloat() * 2f * kotlin.math.PI.toFloat() }
    return List(bands) { i ->
        val f = if (bands <= 1) 0f else i.toFloat() / (bands - 1)
        val rate = rates[i]
        val phase = phases[i]
        val wobble = 0.5f + 0.3f * sin(t * rate * kotlin.math.PI.toFloat() + phase) + 0.2f * sin(t * rate * 2.7f + phase * 1.7f)
        val envelope = 0.72f - 0.42f * f
        val sparkle = 0.18f * f * max(0f, sin(t * (5f + rate) + phase)).pow(4)
        val v = envelope * (0.3f + 0.5f * wobble) + kick * (1f - f) * (1f - f) * 0.45f + sparkle
        v.coerceIn(0f, 1f)
    }
}

// ── Сглаживание уровней (порт Flutter `MusicLevelsBuilder`, music_widgets.dart:263-288) ─────────

/**
 * Трекер уровней полос текущего трека: настоящий FFT, пока кадры свежие (300 мс), иначе
 * процедурный спектр; на паузе столбики плавно опадают (×0.88 за тик) и замирают.
 */
class MusicLevelsTracker(private val bands: Int) {
    var levels: List<Float> = List(bands) { 0f }
        private set

    private var real: List<Float>? = null
    private var realAtMs = 0L
    private var posMs = 0L
    private var posAtMs = 0L
    private var nowMs = 0L
    private var playing = false
    private var speed = 1f
    private var trackId: String? = null
    private var dead = false

    /** Свежесть реального FFT-кадра. */
    private val realFreshMs = 300L

    fun onFft(frame: MusicFftFrame, trackId: String?, playing: Boolean) {
        this.trackId = trackId
        this.playing = playing
        if (playing) real = fftBands(frame, bands)
        realAtMs = nowMs
    }

    fun onPlayerState(currentTrackId: String?, playing: Boolean, speed: Float, positionMs: Long) {
        trackId = currentTrackId
        this.playing = playing
        this.speed = speed
        if (positionMs != posMs) {
            posMs = positionMs
            posAtMs = nowMs
        }
    }

    /** Шаг тикера: возвращает актуальные уровни. */
    fun tick(nowMs: Long): List<Float> {
        this.nowMs = nowMs
        val id = trackId
        val real = this.real
        levels = when {
            playing && real != null && nowMs - realAtMs < realFreshMs -> {
                // Подъём быстрый, спад мягкий — как у стрелочного индикатора
                List(bands) { i -> levels[i] + (real[i] - levels[i]) * (if (real[i] > levels[i]) 0.6f else 0.2f) }
            }
            playing && id != null -> proceduralSpectrum(id, posMs + ((nowMs - posAtMs) * speed).toLong(), bands)
            else -> {
                val next = List(bands) { levels[it] * 0.88f }
                if (next.all { it < 0.02f }) {
                    dead = true
                    List(bands) { 0f }
                } else {
                    next
                }
            }
        }
        return levels
    }

    /** Все уровни опали до нуля — тикер можно останавливать. */
    val isIdle: Boolean get() = dead

    /** Уровни в этот момент пришли от настоящего FFT (свежий кадр), а не процедурные. */
    fun isReal(nowMs: Long): Boolean = playing && real != null && nowMs - realAtMs < realFreshMs
}
