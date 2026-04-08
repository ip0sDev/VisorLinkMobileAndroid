package by.iposdev.visorlink.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath

// ── Raised shadow — угло-осознанный вариант ───────────────────────────────────

fun Modifier.nmRaisedShadow(
    isDark: Boolean = false,
    shadowRadius: Dp = 8.dp,
    offsetDp: Dp = 4.dp,
    cornerRadius: Dp = 0.dp,
    darkAlpha: Float = if (isDark) 0.65f else 0.50f,
    lightAlpha: Float = if (isDark) 0.08f else 0.70f,
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

// ── Inset shadow — для поля ввода (скруглённый контейнер) ────────────────────

fun Modifier.nmInsetShadow(
    isDark: Boolean = false,
    cornerRadius: Dp = 22.dp,
    darkAlpha: Float = if (isDark) 0.55f else 0.38f,
    lightAlpha: Float = if (isDark) 0.06f else 0.50f,
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
    darkAlpha: Float = if (isDark) 0.65f else 0.50f,
    lightAlpha: Float = if (isDark) 0.08f else 0.72f,
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
    darkAlpha: Float = if (isDark) 0.60f else 0.45f,
    lightAlpha: Float = if (isDark) 0.08f else 0.65f,
) = nmRaisedShadow(
    isDark = isDark,
    shadowRadius = 6.dp,
    offsetDp = 3.dp,
    cornerRadius = 50.dp,
    darkAlpha = darkAlpha,
    lightAlpha = lightAlpha,
)

// ── Neumorphic dividers ───────────────────────────────────────────────────────

fun Modifier.nmDividerBottom(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.6f else 0.35f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.08f else 0.55f)

    val darkPx  = 1.2.dp.toPx()
    val lightPx = 0.8.dp.toPx()

    drawRect(
        color = darkColor,
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - darkPx),
        size = androidx.compose.ui.geometry.Size(size.width, darkPx)
    )
    drawRect(
        color = lightColor,
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - darkPx - lightPx - 0.5.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, lightPx)
    )
}

fun Modifier.nmDividerTop(isDark: Boolean = false): Modifier = this.drawBehind {
    val dColor = if (isDark) Biolume.DarkShadowDark else Biolume.ShadowDark
    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight

    val darkColor  = dColor.copy(alpha = if (isDark) 0.6f else 0.35f)
    val lightColor = lColor.copy(alpha = if (isDark) 0.08f else 0.55f)

    val lightPx = 1.2.dp.toPx()
    val darkPx  = 0.8.dp.toPx()

    drawRect(
        color = lightColor,
        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(size.width, lightPx)
    )
    drawRect(
        color = darkColor,
        topLeft = androidx.compose.ui.geometry.Offset(0f, lightPx + 0.5.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, darkPx)
    )
}

// ── Bubble inner highlight ────────────────────────────────────────────────────

fun Modifier.bubbleInnerHighlight(
    shape: Shape, // <-- Теперь модификатор требует форму для обрезки
    isDark: Boolean = false
): Modifier = this.drawWithCache {
    // Создаем контур на основе переданной формы баббла (с учетом всех скруглений)
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }

    val lColor = if (isDark) Biolume.DarkShadowLight else Biolume.ShadowLight
    val highlightAlpha = if (isDark) 0.06f else 0.55f
    val lineHeight = 1.2.dp.toPx()

    onDrawWithContent {
        drawContent() // Сначала рисуем сам баббл (и его содержимое)

        // Рисуем блик, жестко обрезая его по скругленным краям баббла
        clipPath(path) {
            drawRect(
                color = lColor.copy(alpha = highlightAlpha),
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, lineHeight)
            )
        }
    }
}