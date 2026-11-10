package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class SparkPoint(val x: Float, val y: Float, val d: Float, val s: Float)

private val SPARKS = List(14) { i ->
    SparkPoint(
        x = ((i * 37) % 100) / 100f,
        y = ((i * 53 + 11) % 100) / 100f,
        d = ((i * 7) % 10) / 10f,
        s = 0.6f + ((i * 13) % 10) / 10f
    )
}

/**
 * Открытка-подарок (веб Postcard.jsx / Flutter postcard.dart).
 * spark — мерцающие искры, aurora — цветные волны, supernova — вспышка с лучами.
 */
@Composable
fun Postcard(
    design: String,
    modifier: Modifier = Modifier,
    text: String = "",
    from: String = "",
    small: Boolean = false,
    play: Boolean = false,
    width: Dp = 260.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "postcard_loop")
    val loopSecs by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loop"
    )

    val introAnim = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(play) {
        if (play) {
            introAnim.snapTo(0f)
            introAnim.animateTo(1f, tween(1400, easing = EaseOutCubic))
        } else {
            introAnim.snapTo(1f)
        }
    }
    val intro = introAnim.value

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val warning = VlTheme.tokens.status.warning
    val high = MaterialTheme.colorScheme.surfaceContainerHigh
    val highest = MaterialTheme.colorScheme.surfaceContainerHighest
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val shape = RoundedCornerShape(if (small) 12.dp else 24.dp)
    val hasBody = !small && (text.isNotBlank() || from.isNotBlank())

    Column(
        modifier = modifier
            .then(if (small) Modifier.fillMaxWidth() else Modifier.width(width))
            .clip(shape)
            .background(high)
    ) {
        val artHeight = if (small) 64.dp else 150.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight)
                .clip(shape)
                .then(if (design == "aurora") Modifier.blur(if (small) 8.dp else 18.dp) else Modifier)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                when (design) {
                    "spark" -> drawSpark(size, primary, high, loopSecs, intro)
                    "aurora" -> drawAurora(size, primary, secondary, tertiary, highest, loopSecs, intro)
                    else -> drawSupernova(size, warning, high, loopSecs, intro, small)
                }
            }
        }

        if (hasBody) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
                if (from.isNotBlank()) {
                    if (text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    Text(
                        text = "— @$from",
                        color = onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSpark(
    size: Size,
    primary: Color,
    high: Color,
    secs: Float,
    intro: Float
) {
    drawRect(high)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 1.4f),
            radius = size.width * 0.9f
        )
    )
    for (p in SPARKS) {
        val tw = 0.35f + 0.65f * (0.5f - 0.5f * cos(2f * PI.toFloat() * ((secs - p.d) / 2.6f)))
        val popProgress = ((intro * 1.4f - p.d * 0.6f) / 0.7f).coerceIn(0f, 1f)
        val pop = EaseOutBack.transform(popProgress)
        val r = 3f * p.s * pop * density
        if (r <= 0f) continue
        val center = Offset(p.x * size.width, p.y * size.height)
        drawCircle(primary.copy(alpha = 0.35f * tw), radius = r * 2.4f, center = center)
        drawCircle(primary.copy(alpha = tw), radius = r, center = center)
    }
}

private fun DrawScope.drawAurora(
    size: Size,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    highest: Color,
    secs: Float,
    intro: Float
) {
    drawRect(highest)
    val fade = intro

    fun wave(color: Color, strength: Float, centerFrac: Offset, period: Float, phase: Float) {
        val t = sin(2f * PI.toFloat() * (secs / period) + phase)
        val dx = 0.12f * t * size.width
        val dy = 0.08f * t * size.height
        val center = Offset(size.width * centerFrac.x + dx, size.height * centerFrac.y + dy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = strength * fade), Color.Transparent),
                center = center,
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = center
        )
    }

    wave(primary, 0.7f, Offset(0.3f, 0.4f), 13f, 0f)
    wave(secondary, 0.7f, Offset(0.7f, 0.6f), 26f, PI.toFloat())
    wave(tertiary, 0.55f, Offset(0.5f, 0.9f), 13f, PI.toFloat() / 2f)
}

private fun DrawScope.drawSupernova(
    size: Size,
    warning: Color,
    high: Color,
    secs: Float,
    intro: Float,
    small: Boolean
) {
    drawRect(high)
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(warning.copy(alpha = 0.3f), Color.Transparent),
            center = center,
            radius = size.height * 0.65f
        ),
        radius = size.height * 0.65f,
        center = center
    )

    // Лучи
    val rayT = EaseOutCubic.transform(((intro * 1.4f - 0.15f) / 1.0f).coerceIn(0f, 1f))
    val rayLen = size.height * 0.46f * rayT
    if (rayLen > 0f) {
        for (i in 0 until 12) {
            val a = (i * PI.toFloat() / 6f) + (PI.toFloat() / 2f)
            val end = center + Offset(cos(a) * rayLen, sin(a) * rayLen)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(warning.copy(alpha = 0.7f), Color.Transparent),
                    start = center,
                    end = end
                ),
                start = center,
                end = end,
                strokeWidth = 2f * density
            )
        }
    }

    // Кольцо
    val ringT = EaseOutCubic.transform((intro * 1.4f / 1.4f).coerceIn(0f, 1f))
    val baseRing = if (small) 22f else 45f
    val ringR = (baseRing * (0.2f + 1.4f * ringT)) * density
    drawCircle(
        color = warning.copy(alpha = 0.6f * (1f - 0.5f * ringT)),
        radius = ringR,
        center = center,
        style = Stroke(width = 2f * density)
    )

    // Ядро пульсирует
    val pulse = 1f + 0.18f * (0.5f - 0.5f * cos(2f * PI.toFloat() * secs / 2.6f))
    val coreR = ((if (small) 7f else 13f) * pulse) * density
    drawCircle(warning.copy(alpha = 0.5f), radius = coreR * 2f, center = center)
    drawCircle(warning, radius = coreR, center = center)
}
