// ui/theme/ExthruModifiers.kt
package by.iposdev.visorlink.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer

// ── Forge Neo-Brutalism Shadow (Терминальный стиль) ──────────────────────────

fun Modifier.forgeNeuBrutalism(
    isPressed: Boolean,
    isDark: Boolean,
    offsetDp: Dp = 6.dp,
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
            if (!isPressed) {
                drawRect(
                    color = shadowColor.copy(alpha = 0.9f),
                    topLeft = Offset(offPx, offPx),
                    size = size
                )

                val edgeGradient = Brush.linearGradient(
                    colors = if (isDark)
                        listOf(Color(0xFF25252B), Color(0xFF0D0D0F))
                    else
                        listOf(Color(0xFFDCDFE5), Color(0xFFA1A1AA)),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width + offPx, offPx)
                )

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

// ── Объемные внешние тени (Raised) - ИСПОЛЬЗУЕМ МЯГКОЕ РАССЕИВАНИЕ ───────────

fun Modifier.nmRaisedShadow(
    isDark: Boolean = false,
    shadowRadius: Dp = 20.dp, // Увеличен радиус для мягкости
    offsetDp: Dp = 8.dp,
    cornerRadius: Dp = 20.dp,
    darkAlpha: Float = if (isDark) 0.35f else 0.20f, // Усилена видимость в светлом режиме
    lightAlpha: Float = if (isDark) 0.01f else 0.70f,
): Modifier = this.drawBehind {
    val radiusPx  = shadowRadius.toPx()
    val offsetPx  = offsetDp.toPx()
    val cornerPx  = cornerRadius.toPx()

    val darkShadowColor = if (isDark) Color.Black else Color(0xFFA3B1C6)
    val lightShadowColor = Color.White

    drawIntoCanvas { canvas ->
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
    }
}

// ── Вдавленные внутренние тени (Inset) ───────────────────────────────────────

fun Modifier.nmInsetShadow(
    isDark: Boolean = false,
    cornerRadius: Dp = 22.dp,
    darkAlpha: Float = if (isDark) 0.5f else 0.25f,
    lightAlpha: Float = if (isDark) 0.02f else 0.6f,
    lineWidthDp: Dp = 2.dp,
    blurRadiusDp: Dp = 8.dp
): Modifier = this.drawWithCache {
    val lw = lineWidthDp.toPx()
    val blur = blurRadiusDp.toPx()
    val cr = cornerRadius.toPx()

    val darkShadowColor = if (isDark) Color.Black else Color(0xFFA3B1C6)
    val lightShadowColor = Color.White

    onDrawWithContent {
        drawContent()

        val clipPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cr)))
        }

        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)

            val darkPaint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = lw * 2
                    color = darkShadowColor.copy(alpha = darkAlpha).toArgb()
                    setShadowLayer(blur, lw, lw, darkShadowColor.copy(alpha = darkAlpha).toArgb())
                }
            }
            canvas.nativeCanvas.drawRoundRect(
                -lw, -lw, size.width + lw, size.height + lw,
                cr + lw, cr + lw, darkPaint.asFrameworkPaint()
            )

            val lightPaint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = lw * 2
                    color = lightShadowColor.copy(alpha = lightAlpha).toArgb()
                    setShadowLayer(blur, -lw, -lw, lightShadowColor.copy(alpha = lightAlpha).toArgb())
                }
            }
            canvas.nativeCanvas.drawRoundRect(
                -lw, -lw, size.width + lw, size.height + lw,
                cr + lw, cr + lw, lightPaint.asFrameworkPaint()
            )

            canvas.restore()
        }
    }
}

// ── Exthru Wrappers ───────────────────────────────────────────────────────────

fun Modifier.exthruRaisedShadow(
    isDark: Boolean = false,
    darkAlpha: Float = if (isDark) 0.35f else 0.12f,
    lightAlpha: Float = if (isDark) 0.01f else 0.60f,
) = nmRaisedShadow(
    isDark = isDark,
    shadowRadius = 20.dp,
    offsetDp = 8.dp,
    cornerRadius = 20.dp,
    darkAlpha = darkAlpha,
    lightAlpha = lightAlpha,
)

fun Modifier.exthruSmallRaisedShadow(
    isDark: Boolean = false,
    darkAlpha: Float = if (isDark) 0.30f else 0.18f, // Усилена видимость
    lightAlpha: Float = if (isDark) 0.01f else 0.60f,
) = nmRaisedShadow(
    isDark = isDark,
    shadowRadius = 10.dp,
    offsetDp = 4.dp,
    cornerRadius = 50.dp,
    darkAlpha = darkAlpha,
    lightAlpha = lightAlpha,
)

// ── Neumorphic dividers ───────────────────────────────────────────────────────

fun Modifier.nmDividerBottom(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.4f else 0.15f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.02f else 0.5f)

    val darkPx  = 1.dp.toPx()
    val lightPx = 1.dp.toPx()

    drawRect(
        color = darkColor,
        topLeft = Offset(0f, size.height - darkPx),
        size = Size(size.width, darkPx)
    )
    drawRect(
        color = lightColor,
        topLeft = Offset(0f, size.height - darkPx - lightPx),
        size = Size(size.width, lightPx)
    )
}

fun Modifier.nmDividerTop(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.4f else 0.15f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.02f else 0.5f)

    val lightPx = 1.dp.toPx()
    val darkPx  = 1.dp.toPx()

    drawRect(
        color = lightColor,
        topLeft = Offset(0f, 0f),
        size = Size(size.width, lightPx)
    )
    drawRect(
        color = darkColor,
        topLeft = Offset(0f, lightPx),
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
    val highlightAlpha = if (isDark) 0.04f else 0.4f
    val lineHeight = 1.5.dp.toPx()

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

// ── Accent glow ───────────────────────────────────────────────────────────────

fun Modifier.accentGlowShadow(
    accent: Color,
    isPressed: Boolean,
    cornerRadius: Dp,
): Modifier = this.drawBehind {
    val alpha = if (isPressed) 0.20f else 0.08f
    val blur = if (isPressed) 24.dp.toPx() else 12.dp.toPx()
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

// ── Иридисцентная фаска (ОБНОВЛЕННАЯ БЕЗ ЧЕРНОГО КОНТУРА) ────────────────────

fun Modifier.biolumeGlassBorder(
    shape: Shape,
    isDark: Boolean,
    accent: Color,
    isPressed: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }

    // Мягкий диагональный градиент: Белый/Светлый акцент -> Прозрачный -> Легкий акцент
    val borderBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                accent.copy(alpha = 0.25f),
                Color.Transparent,
                Color.White.copy(alpha = 0.03f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.6f),
                Biolume.ShadowDark.copy(alpha = 0.15f),
                Biolume.ShadowDark.copy(alpha = 0.30f)
            )
        },
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
    )

    onDrawWithContent {
        drawContent()

        // Рисуем рамку СТРОГО внутри маски клипа
        clipPath(path) {
            drawPath(
                path = path,
                brush = borderBrush,
                style = Stroke(width = (borderWidth * 2).toPx()) // *2 т.к. clipPath срезает половину толщины
            )
        }
    }
}