package org.visorlink.app.ui.components.music

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.visorlink.app.data.model.MusicTrack
import org.visorlink.app.ui.components.led.VlLedLevelMeter
import org.visorlink.app.ui.components.led.VlLedMarquee
import org.visorlink.app.ui.components.led.VlLedProgress
import org.visorlink.app.ui.components.led.VlLedScreen
import org.visorlink.app.ui.components.led.VlLedText
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.MusicPlayerManager
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.floor
import kotlin.math.roundToInt

/** Время на LED-табло: m:ss. */
private fun formatMusicTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Табло плеера (Forge), порт Flutter `_PlayerBoard` (music_player_screen.dart:846-932):
 * «ИСПОЛНИТЕЛЬ — ТРЕК» бегущей строкой, мордочка + спектр с пиками, время и прогресс точками.
 * По ряду прогресса можно перематывать (тап — select, каждая пятая часть — тик, края — порог).
 */
@Composable
fun PlayerLedBoard(
    track: MusicTrack,
    playerManager: MusicPlayerManager,
    positionMs: Long,
    totalMs: Long,
    value: Float,
    onSeek: ((Float) -> Unit)?,
    playing: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    val still = VlTheme.tokens.reduceMotion

    BoxWithConstraints(modifier) {
        val maxWidthDp = maxWidth
        val faceDot = 2.7.dp
        val faceGap = 0.9.dp
        val withFace = maxWidthDp >= 280.dp
        val bands = if (withFace) 34 else 46
        val levels = rememberMusicLevels(playerManager, bands)
        // Размер матрицы мордочки (32 колонки × 12 рядов + запас под кивок) и шаг спектра
        val (faceWidthPx, spectrumWidthPx, emPx) = with(density) {
            Triple(
                32 * faceDot.toPx() + 31 * faceGap.toPx(),
                maxWidth.toPx() - (if (withFace) faceDot.toPx() * 12 + faceGap.toPx() * 11 + 12.dp.toPx() else 0f),
                1.dp.toPx(),
            )
        }
        val pitchDp = ((spectrumWidthPx / bands) / emPx).coerceIn(2f, 5.5f).dp
        val dot = pitchDp * 0.72f

        // Перемотка: bucket 0..20 с хаптикой на смене (F _PlayerBoardState._seek)
        var seekBucket by remember { mutableIntStateOf(-1) }
        fun seekFraction(fRaw: Float, tap: Boolean) {
            val f = fRaw.coerceIn(0f, 1f)
            val b = floor(f * 20).toInt()
            if (tap) {
                haptic.perform(HapticType.SELECTION)
            } else if (b != seekBucket) {
                haptic.perform(if (f >= 1f || f <= 0f) HapticType.THRESHOLD else HapticType.SELECTION)
            }
            seekBucket = b
            onSeek?.invoke(f)
        }

        VlLedScreen(radius = 20.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                VlLedMarquee(
                    "${track.performer} — ${track.title}",
                    modifier = Modifier.fillMaxWidth(),
                    fallbackStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    if (withFace) {
                        PlayerBeatFace(
                            levels = levels.values.value,
                            real = levels.isReal.value,
                            playing = playing,
                            loading = loading,
                            dot = faceDot,
                            gap = faceGap,
                        )
                        Spacer(Modifier.padding(horizontal = 6.dp))
                    }
                    VlLedLevelMeter(
                        levels = levels.values.value,
                        rows = 14,
                        peaks = !still,
                        dot = dot,
                        gap = pitchDp - dot,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    VlLedText(formatMusicTime(positionMs), dot = 1.5.dp, gap = 0.75.dp)
                    Spacer(Modifier.weight(1f))
                    VlLedText(formatMusicTime(totalMs), dot = 1.5.dp, gap = 0.75.dp)
                }
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(onSeek) {
                            detectTapGestures { pos ->
                                if (onSeek != null) seekFraction(pos.x / size.width.toFloat(), tap = true)
                            }
                        }
                        .pointerInput(onSeek) {
                            detectHorizontalDragGestures(
                                onDragStart = { if (onSeek != null) seekBucket = floor(value * 20).toInt() },
                            ) { change, _ ->
                                if (onSeek != null) seekFraction(change.position.x / size.width.toFloat(), tap = false)
                            }
                        }
                        .padding(vertical = 8.dp),
                ) {
                    VlLedProgress(value = value.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
