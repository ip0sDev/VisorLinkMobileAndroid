// ui/theme/ExthruModifiers.kt
package by.iposdev.visorlink.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer

// ── Forge Neo-Brutalism Shadow (Терминальный стиль) ──────────────────────────

fun Modifier.forgeNeuBrutalism(
    isPressed: Boolean,
    isDark: Boolean,
    offsetDp: Dp = 6.dp, // Deeper by default
    borderWidth: Dp = 1.dp,
    borderColor: Color = if (isDark) Color(0xFF404040) else Color(0xFF808080),
    shadowColor: Color = Color.Black
): Modifier = composed {
    val trans by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 800f, dampingRatio = 0.6f),
        label = "forge_push"
    )
    this
        .drawBehind {
            val offPx = offsetDp.toPx()
            
            // 1. "Extrusion" shadow - рисуем ступенчатую тень для эффекта объема
            if (!isPressed) {
                // Основная глубокая тень
                drawRect(
                    color = shadowColor.copy(alpha = 0.9f),
                    topLeft = Offset(offPx, offPx),
                    size = size
                )
                
                // Рисуем "грань" экструзии с металлическим градиентом
                val edgeGradient = Brush.linearGradient(
                    colors = if (isDark) 
                        listOf(Color(0xFF25252B), Color(0xFF0D0D0F)) 
                    else 
                        listOf(Color(0xFFDCDFE5), Color(0xFFA1A1AA)),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width + offPx, offPx)
                )
                
                // Рисуем правое и нижнее ребро экструзии
                val path = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width + offPx, offPx)
                    lineTo(size.width + offPx, size.height + offPx)
                    lineTo(offPx, size.height + offPx)
                    lineTo(0f, size.height)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, brush = edgeGradient)
            }
        }
        .graphicsLayer {
            val offPx = offsetDp.toPx()
            translationX = offPx * trans
            translationY = offPx * trans
        }
        .border(borderWidth, borderColor, RectangleShape)
}

// ── Raised shadow — угло-осознанный вариант ──────────────────────────────────

fun Modifier.nmRaisedShadow(
    isDark: Boolean = false,
    shadowRadius: Dp = 12.dp, // Slightly larger blur
    offsetDp: Dp = 6.dp,
    cornerRadius: Dp = 0.dp,
    darkAlpha: Float = if (isDark) 0.35f else 0.18f, // Lower alpha
    lightAlpha: Float = if (isDark) 0.04f else 0.55f, // Lower alpha
): Modifier = this.drawBehind {
    val radiusPx  = shadowRadius.toPx()
    val offsetPx  = offsetDp.toPx()
    val cornerPx  = cornerRadius.toPx()

    val darkShadowColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lightShadowColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(
                    radiusPx, offsetPx, offsetPx,
                    darkShadowColor.copy(alpha = darkAlpha).toArgb()
                )
            }
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f,
            right = size.width, bottom = size.height,
            radiusX = cornerPx, radiusY = cornerPx,
            paint = darkPaint
        )

        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(
                    radiusPx, -offsetPx, -offsetPx,
                    lightShadowColor.copy(alpha = lightAlpha).toArgb()
                )
            }
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f,
            right = size.width, bottom = size.height,
            radiusX = cornerPx, radiusY = cornerPx,
            paint = lightPaint
        )
    }
}

// ── Inset shadow — для поля ввода и вдавленных кнопок ────────────────────────

fun Modifier.nmInsetShadow(
    isDark: Boolean = false,
    cornerRadius: Dp = 22.dp,
    darkAlpha: Float = if (isDark) 0.45f else 0.25f,
    lightAlpha: Float = if (isDark) 0.05f else 0.60f,
    lineWidthDp: Dp = 1.5.dp,
): Modifier = this.drawBehind {
    val lw = lineWidthDp.toPx()
    val cr = cornerRadius.toPx()

    val darkColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lightColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val dark = darkColor.copy(alpha = darkAlpha)
    val light = lightColor.copy(alpha = lightAlpha)

    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(lw * 2f, lw, lw, dark.toArgb())
            }
        }
        canvas.drawRoundRect(
            left = lw, top = lw, right = size.width - lw, bottom = size.height - lw,
            radiusX = cr, radiusY = cr, paint = darkPaint
        )

        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(lw * 2f, -lw, -lw, light.toArgb())
            }
        }
        canvas.drawRoundRect(
            left = lw, top = lw, right = size.width - lw, bottom = size.height - lw,
            radiusX = cr, radiusY = cr, paint = lightPaint
        )
    }
}

// ── Exthru Wrappers ───────────────────────────────────────────────────────────

fun Modifier.exthruRaisedShadow(
    isDark: Boolean = false,
    darkAlpha: Float = if (isDark) 0.45f else 0.22f,
    lightAlpha: Float = if (isDark) 0.05f else 0.65f,
) = nmRaisedShadow(
    isDark = isDark,
    shadowRadius = 12.dp,
    offsetDp = 6.dp,
    cornerRadius = 18.dp,
    darkAlpha = darkAlpha,
    lightAlpha = lightAlpha,
)

fun Modifier.exthruSmallRaisedShadow(
    isDark: Boolean = false,
    darkAlpha: Float = if (isDark) 0.40f else 0.20f,
    lightAlpha: Float = if (isDark) 0.05f else 0.60f,
) = nmRaisedShadow(
    isDark = isDark,
    shadowRadius = 8.dp,
    offsetDp = 4.dp,
    cornerRadius = 50.dp,
    darkAlpha = darkAlpha,
    lightAlpha = lightAlpha,
)

// ── Neumorphic dividers ───────────────────────────────────────────────────────

fun Modifier.nmDividerBottom(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.4f else 0.2f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.05f else 0.5f)

    val darkPx  = 1.2.dp.toPx()
    val lightPx = 0.8.dp.toPx()

    drawRect(
        color = darkColor,
        topLeft = Offset(0f, size.height - darkPx),
        size = Size(size.width, darkPx)
    )
    drawRect(
        color = lightColor,
        topLeft = Offset(0f, size.height - darkPx - lightPx - 0.5.dp.toPx()),
        size = Size(size.width, lightPx)
    )
}

fun Modifier.nmDividerTop(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.4f else 0.2f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.05f else 0.5f)

    val lightPx = 1.2.dp.toPx()
    val darkPx  = 0.8.dp.toPx()

    drawRect(
        color = lightColor,
        topLeft = Offset(0f, 0f),
        size = Size(size.width, lightPx)
    )
    drawRect(
        color = darkColor,
        topLeft = Offset(0f, lightPx + 0.5.dp.toPx()),
        size = Size(size.width, darkPx)
    )
}

// ── Bubble inner highlight ────────────────────────────────────────────────────

fun Modifier.bubbleInnerHighlight(
    shape: Shape,
    isDark: Boolean = false
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }

    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight
    val highlightAlpha = if (isDark) 0.06f else 0.55f
    val lineHeight = 1.2.dp.toPx()

    onDrawWithContent {
        drawContent()
        clipPath(path) {
            drawRect(
                color = lColor.copy(alpha = highlightAlpha),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, lineHeight)
            )
        }
    }
}

// ── Старая реализация Forge оставлена для обратной совместимости ──────────────

fun Modifier.forgeHardShadow(
    color: Color,
    offset: Dp = 4.dp,
): Modifier = this.drawBehind {
    val offPx = offset.toPx()
    drawRect(
        color = color,
        topLeft = Offset(offPx, offPx),
        size = Size(size.width, size.height)
    )
}
// ── Accent glow ───────────────────────────────────────────────────────────────

fun Modifier.accentGlowShadow(
    accent: Color,
    isPressed: Boolean,
    cornerRadius: Dp,
): Modifier = this.drawBehind {
    val alpha = if (isPressed) 0.25f else 0.08f
    val blur = if (isPressed) 18.dp.toPx() else 8.dp.toPx()
    val cr = cornerRadius.toPx()

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(blur, 0f, 0f, accent.copy(alpha = alpha).toArgb())
            }
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = cr, radiusY = cr, paint = paint
        )
    }
}

/**
 * Острая переливчатая граница для Biolume.
 * Исправляет "размытость" за счет инсетной отрисовки (внутри границ).
 */
fun Modifier.biolumeGlassBorder(
    shape: Shape,
    isDark: Boolean,
    isPressed: Boolean = false
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    
    // Переливчатый градиент (Иризация) - делаем более сдержанным
    val iridescentBrush = Brush.linearGradient(
        colors = listOf(
            Biolume.IridescentStart.copy(alpha = if (isDark) 0.15f else 0.35f),
            Biolume.IridescentMid.copy(alpha = if (isDark) 0.05f else 0.20f),
            Biolume.IridescentEnd.copy(alpha = if (isDark) 0.20f else 0.45f)
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
    )
    
    // Блик на ребре (белый сверху, черный снизу)
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.10f else 0.45f),
            Color.Transparent,
            Color.Black.copy(alpha = if (isDark) 0.25f else 0.03f)
        )
    )

    onDrawWithContent {
        drawContent()
        
        // Рисуем с клипом, чтобы граница была строго по контуру или чуть внутри
        clipPath(path) {
            // 1. Основная иридисцентная рамка (рисуем чуть толще, т.к. клип съест половину)
            drawPath(
                path = path,
                brush = iridescentBrush,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // 2. Внутренний блик для четкости
            drawPath(
                path = path,
                brush = highlightBrush,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        // 3. Дополнительный мягкий свет при нажатии (снаружи)
        if (isPressed) {
            drawPath(
                path = path,
                color = Biolume.IridescentStart.copy(alpha = 0.2f),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}
