package org.visorlink.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.withFrameNanos
import org.visorlink.app.ui.theme.VlTheme

/**
 * Сессия backdrop-блюра приложения: общая [HazeState] + флаг готовности блюра.
 *
 * Экраны размечают контент через [vlHazeSource], шапки и нижние панели блюрят его
 * через [vlBackdropBlur] (`HazeInput.Sources`). Разметка источников нужна потому,
 * что на Android < 37.2 `HazeInput.Backdrop` деградирует в Sources — без них блюрить
 * нечего.
 */
class VlHazeSession {
    val state = HazeState()

    /**
     * Блюр включается один раз за сессию приложения (со второго кадра первого экрана):
     * компиляция RenderEffect на кадре открытия экрана давала лаг, а пер-композишн-гейт —
     * мигание шапки при каждом возврате на экран.
     */
    var blurReady = false
        private set

    fun markReady() {
        blurReady = true
    }
}

val LocalVlHazeSession = staticCompositionLocalOf<VlHazeSession?> { null }

/** Контент за шапкой/панелью: список/фон экрана. Вне сессии — no-op. */
fun Modifier.vlHazeSource(): Modifier = composed {
    val session = LocalVlHazeSession.current ?: return@composed Modifier
    then(hazeSource(session.state))
}

/** Блюр активен: Biolume/Forge, не reduce-motion и есть [LocalVlHazeSession]. */
@Composable
fun isVlMatte(): Boolean {
    val tokens = VlTheme.tokens
    return LocalVlHazeSession.current != null && (tokens.isBiolume || tokens.isForge) && !tokens.reduceMotion
}

/**
 * Матовый backdrop-блюр (σ16, как у Flutter `VlBackdropBlur`) для шапок и нижних панелей.
 * Ставить ПОСЛЕ `clip(shape)` и ПЕРЕД полупрозрачной заливкой-тином.
 *
 * Перфоманс: [HazePerformanceMode.Balanced] просаживает вход блюра (дешёвые кадры).
 */
fun Modifier.vlBackdropBlur(): Modifier = composed {
    val session = LocalVlHazeSession.current
    if (!isVlMatte() || session == null) {
        Modifier
    } else {
        val backdrop = MaterialTheme.colorScheme.background
        if (!session.blurReady) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                session.markReady()
            }
            Modifier
        } else {
            hazeBlur(
                input = HazeInput.Sources(session.state),
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
