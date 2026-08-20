package by.iposdev.visorlink.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VlAmbientGlow(
    modifier: Modifier = Modifier,
    overrideAccent: Color? = null,
    simplifiedGraphics: Boolean = false
) {
    if (simplifiedGraphics) return

    val cs = MaterialTheme.colorScheme
    val accent = overrideAccent ?: cs.primary
    val isDark = cs.surface.luminance() < 0.5f

    // Более богатая цветовая палитра для утонченного глассморфизма
    val c1 = accent.copy(alpha = if (isDark) 0.20f else 0.35f)
    val c2 = cs.tertiary.copy(alpha = if (isDark) 0.15f else 0.25f)
    val c3 = cs.secondary.copy(alpha = if (isDark) 0.15f else 0.25f)

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

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = o1x.dp, y = o1y.dp)
                .size(350.dp)
                .background(Brush.radialGradient(listOf(c1, Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = o2x.dp, y = o2y.dp)
                .size(400.dp)
                .background(Brush.radialGradient(listOf(c2, Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = o3x.dp, y = o3y.dp)
                .size(300.dp)
                .background(Brush.radialGradient(listOf(c3, Color.Transparent)), CircleShape)
        )
    }
}