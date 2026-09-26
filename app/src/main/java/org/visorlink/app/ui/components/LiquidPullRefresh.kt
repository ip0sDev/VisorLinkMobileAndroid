package org.visorlink.app.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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

/**
 * Высокопроизводительный AGSL шейдер для рендеринга органических капель жидкого стекла
 * (Signed Distance Field Smooth-Minimum Metaballs).
 * Выполняется на GPU (Android 13+ / Tiramisu, API 33+).
 */
private const val LIQUID_METABALL_AGSL = """
    uniform float2 p1;
    uniform float2 p2;
    uniform float r1;
    uniform float r2;
    uniform float k;
    uniform float4 liquidColor;
    uniform float4 edgeColor;

    float smin(float a, float b, float k) {
        float h = max(k - abs(a - b), 0.0) / k;
        return min(a, b) - h * h * k * 0.25;
    }

    half4 main(float2 fragCoord) {
        float d1 = length(fragCoord - p1) - r1;
        float d2 = length(fragCoord - p2) - r2;
        float d = (k > 0.5) ? smin(d1, d2, k) : min(d1, d2);
        
        // Сглаженный антиалиасинг внешнего контура (1px)
        float alpha = clamp(0.5 - d, 0.0, 1.0);
        if (alpha <= 0.0) {
            return half4(0.0);
        }
        
        // Внутренний светящийся мениск жидкости вдоль границы
        float edge = smoothstep(-3.5, 0.0, d) * (1.0 - smoothstep(0.0, 1.0, d));
        float4 col = mix(liquidColor, edgeColor, edge * 0.85);
        
        float a = col.a * alpha;
        return half4(col.rgb * a, a);
    }
"""

/**
 * Неоморфный жидкостный Pull-to-Refresh контейнер (Liquid Pull-to-Refresh).
 * Применяет аппаратный AGSL-шейдер метаболлов на Android 13+ для бесшовного вытягивания
 * органической капли стекла/ртути прямо из нижней кромки верхней панели, с истончением
 * перешейка и физикой отпочковывания (Snap).
 */
@Composable
fun LiquidPullRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    enabled: Boolean = true,
    liquidEnabled: Boolean = true,
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
                    val consumed = available.y.coerceAtLeast(-pullAnim.value)
                    rawPullPx = (rawPullPx + available.y).coerceAtLeast(0f)
                    val newTarget = if (liquidEnabled) {
                        rubberBand(rawPullPx, thresholdPx, maxOverflowPx, tension = 0.50f)
                    } else {
                        rawPullPx.coerceAtMost(thresholdPx * 1.25f)
                    }
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
                    rawPullPx += available.y * 0.50f
                    val newTarget = if (liquidEnabled) {
                        rubberBand(rawPullPx, thresholdPx, maxOverflowPx, tension = 0.50f)
                    } else {
                        rawPullPx.coerceAtMost(thresholdPx * 1.25f)
                    }
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
        // Контент списка: смещается пропорционально натяжению без разрыва макета
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (pullAnim.value * 0.55f).roundToInt()) }
        ) {
            content()
        }

        // Индикатор капли жидкости (AGSL на Android 13+ / Canvas на Android 12-)
        if (pullAnim.value > 1f || isRefreshing) {
            LiquidPullRefreshIndicator(
                pullOffsetPx = pullAnim.value,
                thresholdPx = thresholdPx,
                topPadding = topPadding,
                isRefreshing = isRefreshing,
                isSnapped = rawPullPx >= thresholdPx || isRefreshing,
                liquidEnabled = liquidEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Отрисовка жидкой капли через AGSL Shader (Android 13+) с fallback на Canvas.
 */
@Composable
private fun LiquidPullRefreshIndicator(
    pullOffsetPx: Float,
    thresholdPx: Float,
    topPadding: Dp,
    isRefreshing: Boolean,
    isSnapped: Boolean,
    liquidEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val tokens = VlTheme.tokens

    val topAnchorY = with(density) { topPadding.toPx() }
    val progress = (pullOffsetPx / thresholdPx).coerceIn(0f, 1.5f)

    // Цвета жидкого стекла
    val liquidColor = if (tokens.isBiolume) {
        cs.surfaceContainerHigh.copy(alpha = 0.88f)
    } else {
        cs.surfaceContainerHighest.copy(alpha = 0.90f)
    }
    val edgeColor = cs.primary.copy(alpha = 0.85f)

    // Анимации пульсации и вращения
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_agsl_refresh")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin_angle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    // Инициализация AGSL шейдера на Android 13+ (API 33+)
    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(LIQUID_METABALL_AGSL)
            } catch (t: Throwable) {
                null
            }
        } else null
    }

    val paint = remember { Paint().apply { isAntiAlias = true } }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            // Точка основания p1 на нижней кромке тулбара
            val p1Y = topAnchorY
            val r1Base = 22.dp.toPx()
            val r1 = if (isSnapped) 0f else (r1Base * (1f - progress * 0.35f)).coerceAtLeast(8.dp.toPx())

            // Точка центра падающей капли p2
            val dropY = if (isRefreshing) {
                topAnchorY + 36.dp.toPx()
            } else {
                topAnchorY + (pullOffsetPx * 0.70f) + 14.dp.toPx()
            }
            val r2Base = 17.dp.toPx()
            val r2 = r2Base * if (isRefreshing) pulseScale else (0.85f + progress * 0.15f)

            // Коэффициент вязкости перешейка k (при снапе сбрасывается в 0 -> отрыв капли)
            val k = if (isSnapped || r1 <= 0f) {
                0f
            } else {
                (36.dp.toPx() * (1f - progress * 0.55f)).coerceAtLeast(10.dp.toPx())
            }

            if (!liquidEnabled) {
                // Обычный классический круговой индикатор без жидкости
                drawCircle(
                    color = cs.surfaceContainerHighest,
                    radius = r2Base,
                    center = Offset(centerX, dropY)
                )
                drawCircle(
                    color = cs.primary,
                    radius = r2Base,
                    center = Offset(centerX, dropY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else if (agslShader != null) {
                // ── РЕНДЕРИНГ ЧЕРЕЗ AGSL SHADER НА GPU ──
                agslShader.setFloatUniform("p1", centerX, p1Y)
                agslShader.setFloatUniform("p2", centerX, dropY)
                agslShader.setFloatUniform("r1", r1)
                agslShader.setFloatUniform("r2", r2)
                agslShader.setFloatUniform("k", k)
                agslShader.setFloatUniform(
                    "liquidColor",
                    liquidColor.red,
                    liquidColor.green,
                    liquidColor.blue,
                    liquidColor.alpha
                )
                agslShader.setFloatUniform(
                    "edgeColor",
                    edgeColor.red,
                    edgeColor.green,
                    edgeColor.blue,
                    edgeColor.alpha
                )

                paint.shader = agslShader
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(
                        0f,
                        topAnchorY - r1Base - 2f,
                        size.width,
                        dropY + r2 + 10.dp.toPx(),
                        paint
                    )
                }
            } else {
                // ── FALLBACK ДЛЯ ANDROID 12 И НИЖЕ (Гладкие кривые Безье) ──
                if (!isSnapped && r1 > 0f && pullOffsetPx > 6.dp.toPx()) {
                    val waist = (14.dp.toPx() * (1f - progress * 0.5f)).coerceAtLeast(6.dp.toPx())
                    val midY = (p1Y + dropY) * 0.5f
                    val path = Path().apply {
                        moveTo(centerX - r1, p1Y)
                        cubicTo(
                            centerX - r1, (p1Y + midY) * 0.5f,
                            centerX - waist, midY,
                            centerX - r2 * 0.8f, dropY - r2 * 0.3f
                        )
                        arcTo(
                            rect = Rect(
                                left = centerX - r2,
                                top = dropY - r2,
                                right = centerX + r2,
                                bottom = dropY + r2
                            ),
                            startAngleDegrees = 150f,
                            sweepAngleDegrees = -300f,
                            forceMoveTo = false
                        )
                        cubicTo(
                            centerX + waist, midY,
                            centerX + r1, (p1Y + midY) * 0.5f,
                            centerX + r1, p1Y
                        )
                        close()
                    }
                    drawPath(path, color = liquidColor)
                    drawPath(path, color = edgeColor, style = Stroke(width = 1.2.dp.toPx()))
                } else {
                    drawCircle(color = liquidColor, radius = r2, center = Offset(centerX, dropY))
                    drawCircle(color = edgeColor, radius = r2, center = Offset(centerX, dropY), style = Stroke(width = 1.2.dp.toPx()))
                }
            }
        }

        // Иконка обновления внутри капли p2
        val iconSize = 18.dp
        val dropCenterY = if (isRefreshing) {
            topAnchorY + with(density) { 36.dp.toPx() }
        } else {
            topAnchorY + (pullOffsetPx * 0.70f) + with(density) { 14.dp.toPx() }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = 0,
                        y = (dropCenterY - with(density) { (iconSize / 2f).toPx() }).roundToInt()
                    )
                }
                .align(Alignment.TopCenter)
                .size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        rotationZ = if (isRefreshing) spinAngle else progress * 260f
                        scaleX = if (isRefreshing) pulseScale else 1f
                        scaleY = if (isRefreshing) pulseScale else 1f
                    }
            )
        }
    }
}
