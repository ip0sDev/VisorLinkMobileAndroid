package org.visorlink.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import org.visorlink.app.ui.theme.VlTheme

/**
 * Общая [HazeState] приложения: экраны размечают контент через [vlHazeSource],
 * шапки и нижние панели читают её для backdrop-блюра ([vlBackdropBlur], `HazeInput.Sources`).
 *
 * Нужна разметка источников, потому что на Android < 37.2 `HazeInput.Backdrop`
 * деградирует в Sources — без них блюрить нечего.
 */
val LocalVlHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Контент за шапкой/панелью: список/фон экрана. Вне [LocalVlHazeState] — no-op. */
fun Modifier.vlHazeSource(): Modifier = composed {
    val state = LocalVlHazeState.current ?: return@composed Modifier
    then(hazeSource(state))
}

/** Блюр активен: Biolume/Forge, не reduce-motion и есть общий [LocalVlHazeState]. */
@Composable
fun isVlMatte(): Boolean {
    val tokens = VlTheme.tokens
    return LocalVlHazeState.current != null && (tokens.isBiolume || tokens.isForge) && !tokens.reduceMotion
}

/**
 * Матовый backdrop-блюр (σ16, как у Flutter `VlBackdropBlur`) для шапок и нижних панелей.
 * Ставить ПОСЛЕ `clip(shape)` и ПЕРЕД полупрозрачной заливкой-тином.
 *
 * Перфоманс: [HazePerformanceMode.Balanced] просаживает вход блюра (дешёвые кадры),
 * а сам блюр включается со второго кадра — компиляция RenderEffect на кадре открытия
 * экрана давала заметный лаг.
 */
fun Modifier.vlBackdropBlur(): Modifier = composed {
    if (!isVlMatte()) {
        Modifier
    } else {
        val backdrop = MaterialTheme.colorScheme.background
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            ready = true
        }
        if (!ready) {
            Modifier
        } else {
            hazeBlur(
                input = HazeInput.Sources(LocalVlHazeState.current!!),
                performanceMode = HazePerformanceMode.Balanced,
                style = HazeBlurStyle {
                    blurEnabled(true)
                    blurRadius(16.dp)
                    noiseFactor(0f)
                    backgroundColor(backdrop)
                },
            )
        }
    }
}
