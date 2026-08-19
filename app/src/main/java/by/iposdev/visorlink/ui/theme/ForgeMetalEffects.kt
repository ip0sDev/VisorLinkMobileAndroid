package by.iposdev.visorlink.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cyberpunk Scanlines overlay modifier
 */
fun Modifier.cyberpunkScanlines(
    color: Color = Color(0xFFFF0033).copy(alpha = 0.03f),
    spacing: Dp = 4.dp
): Modifier = this.drawBehind {
    val step = spacing.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
        y += step
    }
}

/**
 * Comic Book Panel Outline (Fixed)
 */
fun Modifier.comicPanelOutline(
    color: Color,
    strokeWidth: Dp = 2.dp,
    cornerRadius: Dp = 4.dp
): Modifier = this.drawBehind {
    val strokePx = strokeWidth.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = strokePx)
    )
}

/**
 * Comic Halftone Background (Opt-in)
 */
fun Modifier.comicHalftone(
    color: Color,
    density: Float = 0.08f,
    dotSize: Dp = 3.dp
): Modifier = this.drawBehind {
    if (density <= 0) return@drawBehind
    val dotPx = dotSize.toPx()
    val spacing = dotPx / density
    
    var row = 0
    while (row * spacing < size.height) {
        var col = 0
        while (col * spacing < size.width) {
            val x = col * spacing + (if (row % 2 == 0) 0f else spacing / 2)
            val y = row * spacing
            drawCircle(
                color = color,
                center = Offset(x, y),
                radius = dotPx / 2
            )
            col++
        }
        row++
    }
}
