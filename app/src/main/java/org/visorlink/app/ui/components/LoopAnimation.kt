package org.visorlink.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember

/**
 * Бесконечная анимация, которая выключается целиком (покой — [rest]) при «убрать анимации» и
 * в неактивных состояниях. Возвращает [State], а не число: читать его через `by` нужно внутри
 * graphicsLayer/drawBehind, тогда каждый кадр перерисовывается только слой, а не пересобирается
 * весь composable (`.value` в теле composable делал именно это — на каждом кадре, в ленте чата).
 */
@Composable
fun rememberLoopFloat(
    enabled: Boolean,
    rest: Float = 0f,
    animate: @Composable () -> State<Float>,
): State<Float> = if (enabled) animate() else remember { mutableFloatStateOf(rest) }
