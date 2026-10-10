package org.visorlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import org.visorlink.app.ui.theme.vlRaised
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.roundToInt

private const val PULL_RESISTANCE = 0.50f

/**
 * Pull-to-Refresh: список уезжает вниз вслед за пальцем, в освободившемся месте под
 * верхней панелью появляется круглый индикатор с кольцом прогресса. Пружины и резинка
 * натяжения — как у остальных «жидких» компонентов.
 */
@Composable
fun LiquidPullRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    enabled: Boolean = true,
    hapticEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    val thresholdDp = 76.dp
    val thresholdPx = with(density) { thresholdDp.toPx() }
    val refreshHoldPx = with(density) { 50.dp.toPx() }
    val maxOverflowPx = with(density) { 46.dp.toPx() }

    val pullAnim = remember { Animatable(0f) }
    var rawPullPx by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullAnim.animateTo(
                targetValue = refreshHoldPx,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
        } else {
            pullAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.60f, stiffness = 340f)
            )
            rawPullPx = 0f
            hasTriggeredHaptic = false
        }
    }

    val nestedScrollConnection = remember(enabled, isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || isRefreshing) return Offset.Zero
                // При скролле вверх во время натяжения сворачиваем индикатор
                if (available.y < 0f && pullAnim.value > 0f) {
                    // Тот же коэффициент 0.5, что и при натяжении: индикатор следует за пальцем 1:1 в обе стороны
                    val consumed = available.y.coerceAtLeast(-rawPullPx / PULL_RESISTANCE)
                    rawPullPx = (rawPullPx + consumed * PULL_RESISTANCE).coerceAtLeast(0f)
                    val newTarget = rubberBand(rawPullPx, thresholdPx, maxOverflowPx, tension = 0.50f)
                    scope.launch { pullAnim.snapTo(newTarget) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enabled || isRefreshing) return Offset.Zero
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    rawPullPx += available.y * PULL_RESISTANCE
                    val newTarget = rubberBand(rawPullPx, thresholdPx, maxOverflowPx, tension = 0.50f)
                    scope.launch { pullAnim.snapTo(newTarget) }

                    if (rawPullPx >= thresholdPx && !hasTriggeredHaptic) {
                        hasTriggeredHaptic = true
                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                    } else if (rawPullPx < thresholdPx && hasTriggeredHaptic) {
                        hasTriggeredHaptic = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || isRefreshing) return Velocity.Zero
                if (rawPullPx >= thresholdPx) {
                    onRefresh()
                } else {
                    rawPullPx = 0f
                    hasTriggeredHaptic = false
                    pullAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f)
                    )
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        // Контент сдвигается ровно на величину натяжения: в освободившийся промежуток
        // под верхней панелью встаёт индикатор, ничего не перекрывая
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pullAnim.value.roundToInt()) }
        ) {
            content()
        }

        if (pullAnim.value > 1f || isRefreshing) {
            PullRefreshIndicator(
                pullOffsetPx = pullAnim.value,
                thresholdPx = thresholdPx,
                topPadding = topPadding,
                isRefreshing = isRefreshing,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Круглая «таблетка» по центру промежутка между верхней панелью и списком: пока тянем —
 * кольцо прогресса со стрелкой, при достижении порога кольцо замкнуто, при обновлении —
 * бесконечный спиннер. Поверхность и форма — из токенов темы (в Forge квадратная).
 */
@Composable
private fun PullRefreshIndicator(
    pullOffsetPx: Float,
    thresholdPx: Float,
    topPadding: Dp,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val density = LocalDensity.current

    val size = 40.dp
    val sizePx = with(density) { size.toPx() }
    val progress = (pullOffsetPx / thresholdPx).coerceIn(0f, 1f)
    // Появляется по мере того, как промежуток становится достаточно широким
    val reveal = if (isRefreshing) 1f else (pullOffsetPx / (sizePx * 1.2f)).coerceIn(0f, 1f)
    val shape = tokens.shapes.adapt(CircleShape)
    val centerY = with(density) { topPadding.toPx() } + pullOffsetPx / 2f

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (centerY - sizePx / 2f).roundToInt()) }
                .size(size)
                .graphicsLayer {
                    alpha = reveal
                    scaleX = 0.6f + 0.4f * reveal
                    scaleY = 0.6f + 0.4f * reveal
                }
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape) else Modifier
                )
                .background(cs.surfaceContainerHigh, shape)
                .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = cs.primary,
                )
            } else {
                val ringColor = cs.primary
                val trackColor = cs.outlineVariant.copy(alpha = 0.4f)
                Canvas(Modifier.size(26.dp)) {
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(trackColor, 0f, 360f, false, style = stroke)
                    drawArc(ringColor, -90f, 360f * progress, false, style = stroke)
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = cs.primary.copy(alpha = 0.4f + 0.6f * progress),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = progress * 180f }
                )
            }
        }
    }
}
