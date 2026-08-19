package by.iposdev.visorlink.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Windows 95 Classic 3D Bevel
 */
fun Modifier.win95Panel(
    isDark: Boolean,
    raised: Boolean = true,
    thickness: Dp = 2.dp
): Modifier = this.drawBehind {
    val t = thickness.toPx()
    val outerColor1 = if (raised) (if (isDark) Color(0xFF333333) else Color(0xFFFFFFFF)) else (if (isDark) Color(0xFF000000) else Color(0xFF808080))
    val outerColor2 = if (raised) (if (isDark) Color(0xFF000000) else Color(0xFF808080)) else (if (isDark) Color(0xFF333333) else Color(0xFFFFFFFF))
    
    val innerColor1 = if (raised) (if (isDark) Color(0xFF1A1A1A) else Color(0xFFDFDFDF)) else (if (isDark) Color(0xFF0D0D0D) else Color(0xFF404040))
    val innerColor2 = if (raised) (if (isDark) Color(0xFF0D0D0D) else Color(0xFF404040)) else (if (isDark) Color(0xFF1A1A1A) else Color(0xFFDFDFDF))

    // 1. Outer bevel
    drawLine(outerColor1, Offset(0f, 0f), Offset(size.width, 0f), t)
    drawLine(outerColor1, Offset(0f, 0f), Offset(0f, size.height), t)
    drawLine(outerColor2, Offset(0f, size.height), Offset(size.width, size.height), t)
    drawLine(outerColor2, Offset(size.width, 0f), Offset(size.width, size.height), t)

    // 2. Inner bevel
    if (t > 1.dp.toPx()) {
        val iT = t / 2
        drawLine(innerColor1, Offset(t, t), Offset(size.width - t, t), iT)
        drawLine(innerColor1, Offset(t, t), Offset(t, size.height - t), iT)
        drawLine(innerColor2, Offset(t, size.height - t), Offset(size.width - t, size.height - t), iT)
        drawLine(innerColor2, Offset(size.width - t, t), Offset(size.width - t, size.height - t), iT)
    }
}

/**
 * Windows 95 Title Bar Gradient
 */
fun Modifier.win95TitleBar(
    isDark: Boolean,
    isActive: Boolean = true
): Modifier = this.drawBehind {
    val startColor = if (isActive) (if (isDark) Color(0xFF003366) else Color(0xFF000080)) else Color(0xFF808080)
    val endColor = if (isActive) (if (isDark) Color(0xFF001A33) else Color(0xFF1084D0)) else Color(0xFFB0B0B0)
    
    drawRect(
        brush = Brush.horizontalGradient(listOf(startColor, endColor)),
        size = size
    )
}

/**
 * Animated Scanlines (kept for premium touch, but made more subtle)
 */
fun Modifier.terminalScanlines(
    lineColor: Color = Color.Black.copy(alpha = 0.03f),
    lineSpacing: Dp = 4.dp
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline_flicker")
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )
    
    this.drawBehind {
        val step = lineSpacing.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = lineColor.copy(alpha = lineColor.alpha * flicker),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += step
        }
    }
}
