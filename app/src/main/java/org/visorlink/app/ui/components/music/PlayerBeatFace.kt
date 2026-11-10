package org.visorlink.app.ui.components.music

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import org.visorlink.app.ui.components.led.VlLedExpression
import org.visorlink.app.ui.components.led.VlLedMatrix
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.BEAT_FACE_PAD
import org.visorlink.app.utils.BeatDetector
import org.visorlink.app.utils.BeatFaceMood
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.beatFaceBlink
import org.visorlink.app.utils.beatFaceExpression
import org.visorlink.app.utils.beatFaceMood
import org.visorlink.app.utils.beatFaceRows
import org.visorlink.app.utils.beatNodRows
import org.visorlink.app.utils.idleBreathRows
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.roundToInt

/** Кадр бегающих глаз для мордочки с запасом под кивок (F player_beat_face.dart:149-153). */
private fun vlLedScanRows(pupilX: Int): List<String> {
    val face = org.visorlink.app.ui.components.led.vlLedFaceRows(VlLedExpression.SCAN, pupilX = pupilX)
    val blank = ".".repeat(face.first().length)
    return face + List(BEAT_FACE_PAD) { blank }
}

private const val SCAN_PERIOD_MS = 1600L
private const val BOOP_MS = 1000L

/**
 * Пиксельная мордочка на LED-табло плеера (Forge), порт Flutter `player_beat_face.dart`:
 * на настоящем спектре кивает в такт баса, на паузе спит, пока грузится трек — бегающие глаза.
 * Без настоящего спектра в такт не кивает — только дышит и моргает: выдуманного ритма на табло
 * нет. Нажатие — «буп».
 *
 * [levels] — уровни полос 0…1 (по ним ищутся удары), [real] — они от настоящего FFT.
 */
@Composable
fun PlayerBeatFace(
    levels: List<Float>,
    real: Boolean,
    playing: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    dot: Dp = Dp(2.7f),
    gap: Dp = Dp(0.9f),
) {
    val haptic = rememberHaptic()
    val detector = remember { BeatDetector() }
    var rows by remember { mutableStateOf(beatFaceRows(if (playing) VlLedExpression.HAPPY else VlLedExpression.SLEEPY)) }
    // Время «живёт» только пока мордочка не спит: на паузе база копится, тикер стоит
    var baseMs by remember { mutableLongStateOf(0L) }
    var boopUntil by remember { mutableLongStateOf(0L) }
    var beats by remember { mutableIntStateOf(0) }
    var lastFed by remember { mutableStateOf<List<Float>?>(null) }

    val currentLevels by rememberUpdatedState(levels)
    val currentReal by rememberUpdatedState(real)
    val currentPlaying by rememberUpdatedState(playing)
    val currentLoading by rememberUpdatedState(loading)
    val still = VlTheme.tokens.reduceMotion
    val awake = playing || loading || boopUntil > 0L

    fun frame(nowMs: Long): List<String> {
        if (still) return beatFaceRows(if (currentPlaying) VlLedExpression.HAPPY else VlLedExpression.SLEEPY)
        if (boopUntil > 0L && nowMs < boopUntil) {
            return beatFaceRows(VlLedExpression.BOOP, mouthShift = ((nowMs / 90) % 6).toInt())
        }
        if (boopUntil > 0L) boopUntil = 0L

        val last = detector.lastBeatMsValue
        val since = last?.let { nowMs - it }
        val beatNow = since != null && since < 170L
        val mood = beatFaceMood(
            playing = currentPlaying, loading = currentLoading,
            beatNow = beatNow, energy = detector.energy,
        )
        val expr = beatFaceExpression(mood)

        val nod = if (currentPlaying) {
            if (currentReal) beatNodRows(since) else idleBreathRows(nowMs)
        } else 0
        val blink = currentPlaying && mood != BeatFaceMood.LOUD && beatFaceBlink(nowMs)
        if (expr == VlLedExpression.SCAN) {
            val p = (nowMs % SCAN_PERIOD_MS).toFloat() / SCAN_PERIOD_MS
            val px = ((if (p < 0.5f) p * 2 else 2f - p * 2) * 4).roundToInt()
            return vlLedScanRows(px)
        }
        // «Рот едет» шагом на каждый удар; без настоящего ритма рот не шевелится
        return beatFaceRows(
            expr,
            nod = nod,
            mouthShift = if (currentReal && currentPlaying) beats % 6 else 0,
            blink = blink,
        )
    }

    LaunchedEffect(awake, still) {
        if (still || !awake) {
            rows = frame(baseMs)
            return@LaunchedEffect
        }
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { n ->
                if (lastFrameNanos != 0L) baseMs += (n - lastFrameNanos) / 1_000_000L
                lastFrameNanos = n
            }
            val nowMs = baseMs
            // Детектор баса: новый кадр уровней — один feed; без спектра или на паузе — сброс
            val lv = currentLevels
            if (currentPlaying && currentReal) {
                if (lv !== lastFed) {
                    lastFed = lv
                    if (detector.feed(lv, nowMs)) beats++
                }
            } else {
                detector.reset()
                lastFed = null
            }
            rows = frame(nowMs)
        }
    }

    VlLedMatrix(
        rows = rows,
        dot = dot,
        gap = gap,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures {
                haptic.perform(HapticType.BOOP)
                if (still) return@detectTapGestures
                boopUntil = baseMs + BOOP_MS
            }
        },
    )
}
