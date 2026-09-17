package org.visorlink.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import kotlin.math.roundToInt

/**
 * Фоновое цветное свечение-меш.
 *
 * В Biolume НЕ отображается: три постоянно анимированных цветных пятна прямо
 * противоречат §1.2 («один сигнал за раз») и §10 («не подсвечивать glow-ом
 * состояние покоя»). Там роль фона играет неоморфный рельеф, а свечение
 * зарезервировано под focus/press/live.
 *
 * Также уважает системное отключение анимаций (§8).
 */
@Composable
fun VlAmbientGlow(
    modifier: Modifier = Modifier,
    overrideAccent: Color? = null,
    simplifiedGraphics: Boolean = false
) {
    if (simplifiedGraphics) return

    val tokens = VlTheme.tokens
    if (tokens.reduceMotion) return

    val cs = MaterialTheme.colorScheme
    val isBiolume = tokens.isBiolume
    val isDark = cs.surface.luminance() < 0.5f

    // Для Biolume используем палитру мягкого глубоководного биолюминесцентного градиента
    val c1 = remember(overrideAccent, cs.primary, isBiolume, isDark) {
        if (isBiolume) {
            if (isDark) Color(0xFF35C7E8).copy(alpha = 0.035f) // Abyss Primary Cyan (мягкий)
            else cs.primary.copy(alpha = 0.04f)
        } else {
            val accent = overrideAccent ?: cs.primary
            accent.copy(alpha = if (isDark) 0.20f else 0.35f)
        }
    }
    val c2 = remember(cs.secondary, cs.tertiary, isBiolume, isDark) {
        if (isBiolume) {
            if (isDark) Color(0xFF8C6BFF).copy(alpha = 0.030f) // Abyss Secondary Violet (мягкий)
            else cs.secondary.copy(alpha = 0.035f)
        } else {
            cs.tertiary.copy(alpha = if (isDark) 0.15f else 0.25f)
        }
    }
    val c3 = remember(cs.secondary, cs.primary, isBiolume, isDark) {
        if (isBiolume) {
            if (isDark) Color(0xFF6FC6FF).copy(alpha = 0.025f) // Abyss Tertiary Moon Cyan (мягкий)
            else cs.tertiary.copy(alpha = 0.03f)
        } else {
            cs.secondary.copy(alpha = if (isDark) 0.15f else 0.25f)
        }
    }

    val brush1 = remember(c1) { Brush.radialGradient(listOf(c1, Color.Transparent)) }
    val brush2 = remember(c2) { Brush.radialGradient(listOf(c2, Color.Transparent)) }
    val brush3 = remember(c3) { Brush.radialGradient(listOf(c3, Color.Transparent)) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow_mesh")

    // Плавные случайные орбиты для каждой цветовой сферы
    val o1x by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 80f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "o1x"
    )
    val o1y by infiniteTransition.animateFloat(
        initialValue = -50f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "o1y"
    )

    val o2x by infiniteTransition.animateFloat(
        initialValue = -40f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Reverse),
        label = "o2x"
    )
    val o2y by infiniteTransition.animateFloat(
        initialValue = 60f, targetValue = -20f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "o2y"
    )

    val o3x by infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Reverse),
        label = "o3x"
    )
    val o3y by infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(8500, easing = LinearEasing), RepeatMode.Reverse),
        label = "o3y"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Аппаратная изоляция слоя для исключения рекомпозиции родительских контейнеров
                clip = false
            }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(o1x.dp.roundToPx(), o1y.dp.roundToPx()) }
                .size(350.dp)
                .background(brush1, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(o2x.dp.roundToPx(), o2y.dp.roundToPx()) }
                .size(400.dp)
                .background(brush2, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(o3x.dp.roundToPx(), o3y.dp.roundToPx()) }
                .size(300.dp)
                .background(brush3, CircleShape)
        )
    }
}