package org.visorlink.app.ui.components.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.visorlink.app.utils.MusicLevelsTracker
import org.visorlink.app.utils.MusicPlayerManager

/** Уровни полос и признак «они от настоящего FFT» (см. [rememberMusicLevels]). */
class MusicLevels(val values: State<List<Float>>, val isReal: State<Boolean>)

/**
 * Уровни полос 0..1 текущего трека (порт Flutter `MusicLevelsBuilder`): настоящий FFT
 * ([MusicPlayerManager.visualizer]), пока кадры свежие, иначе процедурный спектр; на паузе
 * столбики плавно опадают и замирают. Тикер driven `withFrameNanos`.
 */
@Composable
fun rememberMusicLevels(manager: MusicPlayerManager, bands: Int): MusicLevels {
    val tracker = remember(bands) { MusicLevelsTracker(bands) }
    val levels = remember(bands) { mutableStateOf(List(bands) { 0f }) }
    val isReal = remember(bands) { mutableStateOf(false) }

    val state by manager.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.currentTrack?.id, state.isPlaying, state.speed, state.currentMs) {
        tracker.onPlayerState(state.currentTrack?.id, state.isPlaying, state.speed, state.currentMs)
    }
    LaunchedEffect(Unit) {
        manager.visualizer.fft.collect { frame ->
            tracker.onFft(frame, state.currentTrack?.id, state.isPlaying)
        }
    }
    // Тикер: посчитанные по кадрам уровни. Опадание на паузе — тот же цикл, дешёвый.
    LaunchedEffect(state.isPlaying) {
        while (true) {
            withFrameNanos { nanos ->
                levels.value = tracker.tick(nanos / 1_000_000L)
                isReal.value = tracker.isReal(nanos / 1_000_000L)
            }
        }
    }
    return MusicLevels(levels, isReal)
}
