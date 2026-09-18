package org.visorlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Состояние упругой желейной деформации (Squash & Stretch).
 * Поддерживает мягкие сжатия при нажатии, упругие баунсы при отпускании и импульсы событий.
 */
@Stable
class LiquidJellyState(
    private val scope: CoroutineScope,
    val softness: Float = 0.10f,
    val damping: Float = 0.65f,
    val stiffness: Float = 320f
) {
    val animatable = Animatable(0f)

    val scaleX: Float
        get() = calculateJellyScale(animatable.value).first

    val scaleY: Float
        get() = calculateJellyScale(animatable.value).second

    fun press(customSoftness: Float = softness) {
        scope.launch {
            animatable.animateTo(-customSoftness, tween(80))
        }
    }

    fun release(bounceStretch: Float = softness * 0.7f) {
        scope.launch {
            animatable.animateTo(bounceStretch, tween(70))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }

    fun pulse(intensity: Float = softness) {
        scope.launch {
            animatable.animateTo(intensity, tween(80))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }
}

/**
 * Создание и запоминание состояния желейной деформации.
 */
@Composable
fun rememberLiquidJellyState(
    softness: Float = 0.10f,
    damping: Float = 0.65f,
    stiffness: Float = 320f
): LiquidJellyState {
    val scope = rememberCoroutineScope()
    return remember(softness, damping, stiffness) {
        LiquidJellyState(scope, softness, damping, stiffness)
    }
}

/**
 * Модификатор применения желейной упругой деформации (Squash & Stretch).
 */
fun Modifier.liquidJelly(
    state: LiquidJellyState,
    enabled: Boolean = true
): Modifier = if (enabled) {
    this.graphicsLayer {
        this.scaleX = state.scaleX
        this.scaleY = state.scaleY
    }
} else this

/**
 * Состояние непрерывного жидкостного бегунка для таббаров и навбаров.
 * Мгновенный отклик на смену индекса (0ms latency), упругое вытягивание вдоль вектора перемещения.
 */
@Stable
class LiquidRunnerState(
    private val scope: CoroutineScope,
    initialIndex: Int = 0
) {
    val animatedIndex = Animatable(initialIndex.toFloat())
    val stretchAnim = Animatable(0f)

    val currentIndex: Float
        get() = animatedIndex.value

    val stretch: Float
        get() = stretchAnim.value

    val scaleX: Float
        get() = 1f + abs(stretch) * 0.28f

    val scaleY: Float
        get() = 1f - abs(stretch) * 0.18f

    fun moveTo(targetIndex: Int) {
        val prevIndex = animatedIndex.value
        val diff = targetIndex - prevIndex
        if (abs(diff) < 0.001f) return
        val stretchDir = if (diff > 0) 1f else -1f

        scope.launch {
            stretchAnim.animateTo(stretchDir * 0.20f, tween(90, easing = FastOutSlowInEasing))
            stretchAnim.animateTo(0f, spring(dampingRatio = 0.60f, stiffness = 340f))
        }
        scope.launch {
            animatedIndex.animateTo(
                targetValue = targetIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.70f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
}

/**
 * Создание и запоминание состояния жидкостного бегунка.
 */
@Composable
fun rememberLiquidRunnerState(
    initialIndex: Int = 0
): LiquidRunnerState {
    val scope = rememberCoroutineScope()
    return remember {
        LiquidRunnerState(scope, initialIndex)
    }
}
