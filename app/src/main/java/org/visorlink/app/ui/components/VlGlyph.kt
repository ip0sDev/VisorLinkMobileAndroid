package org.visorlink.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Оттенок значка Forge: вместо плоской иконки в плашке — рельефный глиф на стеклянной плитке.
 */
enum class VlGlyphHue(val color: Color) {
    ORANGE(Color(0xFFFF6A2B)),
    PINK(Color(0xFFFF4F9A)),
    VIOLET(Color(0xFF8B5CFF)),
    BLUE(Color(0xFF3D7BFF)),
    CYAN(Color(0xFF19B8E6)),
    TEAL(Color(0xFF14B8A6)),
    GREEN(Color(0xFF2BB673)),
    AMBER(Color(0xFFFFB020)),
    RED(Color(0xFFF0384F)),
    GRAPHITE(Color(0xFF6B6E78)),
}

/**
 * Подбор оттенка по смыслу иконки.
 */
fun vlGlyphHueOf(icon: ImageVector): VlGlyphHue {
    val name = icon.name.lowercase()
    return when {
        "notif" in name || "fire" in name || "vibrat" in name || "swipe" in name -> VlGlyphHue.ORANGE
        "palette" in name || "brush" in name || "awesome" in name || "image" in name || "layer" in name -> VlGlyphHue.PINK
        "badge" in name || "toy" in name || "dark" in name || "bug" in name -> VlGlyphHue.VIOLET
        "lang" in name || "translat" in name || "globe" in name || "info" in name || "book" in name || "explore" in name -> VlGlyphHue.BLUE
        "send" in name || "cloud" in name || "sync" in name || "device" in name || "speed" in name || "telegram" in name -> VlGlyphHue.CYAN
        "google" in name -> VlGlyphHue.BLUE
        "signal" in name -> VlGlyphHue.AMBER
        "shield" in name || "secur" in name || "verif" in name || "finger" in name || "pass" in name || "lock" in name || "key" in name -> VlGlyphHue.GREEN
        "storage" in name || "audio" in name || "clean" in name || "sched" in name || "auto" in name || "agenda" in name -> VlGlyphHue.TEAL
        "toll" in name || "light" in name || "warn" in name || "admin" in name || "bright" in name || "premium" in name || "star" in name -> VlGlyphHue.AMBER
        "logout" in name || "delete" in name || "off" in name || "close" in name -> VlGlyphHue.RED
        else -> VlGlyphHue.GRAPHITE
    }
}

/**
 * Виды векторных авторских мини-иллюстраций из Flutter (vl_glyph.dart).
 */
private enum class VlIllustration {
    ID_CARD,
    BELL,
    PALETTE,
    SHIELD,
    GLOBE,
    DEVICES,
    MEDAL,
    COIN,
    FLAME,
    BRUSH,
    IMAGE_OFF,
    TELEGRAM,
}

private fun illustrationOf(icon: ImageVector): VlIllustration? {
    val name = icon.name.lowercase()
    return when {
        "badge" in name -> VlIllustration.ID_CARD
        "notif" in name -> VlIllustration.BELL
        "palette" in name -> VlIllustration.PALETTE
        "shield" in name || "secur" in name || "verif" in name -> VlIllustration.SHIELD
        "lang" in name || "translat" in name -> VlIllustration.GLOBE
        "device" in name -> VlIllustration.DEVICES
        "premium" in name -> VlIllustration.MEDAL
        "toll" in name -> VlIllustration.COIN
        "fire" in name -> VlIllustration.FLAME
        "brush" in name -> VlIllustration.BRUSH
        "hideimage" in name || "hide_image" in name -> VlIllustration.IMAGE_OFF
        "telegram" in name || name == "ic_telegram" -> VlIllustration.TELEGRAM
        else -> null
    }
}

/**
 * Градиентный значок и мини-иллюстрация VisorLink:
 * Для ключевых пунктов настроек рисуется векторный 3D-предмет (ID-карта с голограммой, кисть,
 * фото со срезом, золотая медаль PRO, щит, глобус и т.д.); для остальных — объемная стеклянная
 * плитка со световым бликом, тенью и градиентным символом.
 */
@Composable
fun VlGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    hue: VlGlyphHue? = null,
    color: Color? = null,
    destructive: Boolean = false,
    muted: Boolean = false,
) {
    val dark = VlTheme.tokens.isDark
    val led = VlTheme.tokens.led?.color ?: MaterialTheme.colorScheme.primary
    val illustration = if (destructive) null else illustrationOf(icon)
    val kit = if (illustration != null) null else vlKitOf(icon)

    val base = when {
        destructive -> VlGlyphHue.RED.color
        muted -> VlGlyphHue.GRAPHITE.color
        color != null -> color
        hue != null -> hue.color
        else -> vlGlyphHueOf(icon).color
    }

    Box(
        modifier = modifier
            .size(size)
            .then(if (muted) Modifier.graphicsLayer { alpha = 0.65f } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (illustration != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val s = this.size.width
                when (illustration) {
                    VlIllustration.ID_CARD -> drawIdCard(s, dark, led)
                    VlIllustration.BELL -> drawBell(s, dark, led)
                    VlIllustration.PALETTE -> drawPalette(s, dark)
                    VlIllustration.SHIELD -> drawShield(s, dark)
                    VlIllustration.GLOBE -> drawGlobe(s, dark)
                    VlIllustration.DEVICES -> drawDevices(s, dark, led)
                    VlIllustration.MEDAL -> drawMedal(s, dark)
                    VlIllustration.COIN -> drawCoin(s, dark)
                    VlIllustration.FLAME -> drawFlame(s, dark)
                    VlIllustration.BRUSH -> drawBrush(s, dark)
                    VlIllustration.IMAGE_OFF -> drawImageOff(s, dark)
                    VlIllustration.TELEGRAM -> drawTelegram(s, dark)
                }
            }
        } else if (kit != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val s = this.size.width
                drawVlKit(kit, s, dark, led)
            }
        } else {
            // Подложка: стеклянная плитка с градиентом, бликом и тонким кантом
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = this.size.width
                val h = this.size.height
                val radius = CornerRadius(w * 0.31f, w * 0.31f)

                // Тень вниз
                drawRoundRect(
                    color = base.copy(alpha = if (dark) 0.22f else 0.18f),
                    topLeft = Offset(0f, h * 0.08f),
                    size = Size(w, h),
                    cornerRadius = radius,
                )

                // Основной градиент плитки
                val topColor = if (dark) lerp(base, Color.Black, 0.35f) else lerp(base, Color.White, 0.82f)
                val bottomColor = if (dark) lerp(base, Color.Black, 0.72f) else lerp(base, Color.White, 0.58f)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(topColor, bottomColor)),
                    size = Size(w, h),
                    cornerRadius = radius,
                )

                // Верхний блик кромки
                val sheenColor = Color.White.copy(alpha = if (dark) 0.08f else 0.28f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.0f to sheenColor,
                        0.38f to Color.Transparent,
                    ),
                    size = Size(w, h),
                    cornerRadius = radius,
                )

                // Тонкий контур
                drawRoundRect(
                    color = if (dark) base.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.90f),
                    size = Size(w, h),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Иконка с градиентом
            val iconColors = if (dark) {
                listOf(lerp(base, Color.White, 0.65f), lerp(base, Color.White, 0.15f))
            } else {
                listOf(base, lerp(base, Color.Black, 0.30f))
            }
            val iconBrush = remember(base, dark) {
                Brush.linearGradient(
                    colors = iconColors,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(size * 0.52f)
                    .graphicsLayer(alpha = 0.99f)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(iconBrush, blendMode = BlendMode.SrcAtop)
                        }
                    },
                tint = Color.White,
            )
        }
    }
}

// ── Отрисовка векторных мини-иллюстраций из Flutter ──────────────────────────

private fun DrawScope.drawLens(center: Offset, radius: Float, hue: Color) {
    val top = lerp(hue, Color.White, 0.34f)
    val bottom = lerp(hue, Color.Black, 0.40f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(top, hue, bottom),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.4f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawPathShadow(path: Path, s: Float, color: Color? = null, dark: Boolean) {
    val shadowColor = color ?: (if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.20f))
    val shifted = Path()
    shifted.addPath(path, Offset(0f, s * 0.06f))
    drawPath(shifted, color = shadowColor)
}

private fun DrawScope.drawIdCard(s: Float, dark: Boolean, led: Color) {
    rotate(degrees = -6f, pivot = Offset(s / 2f, s / 2f)) {
        val cardRect = Rect(s * 0.06f, s * 0.22f, s * 0.94f, s * 0.80f)
        val p = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = cardRect,
                    cornerRadius = CornerRadius(s * 0.10f, s * 0.10f)
                )
            )
        }
        drawPathShadow(p, s, dark = dark)
        val cardBrush = if (dark) {
            Brush.verticalGradient(listOf(Color(0xFF3A3C43), Color(0xFF18191C)), startY = cardRect.top, endY = cardRect.bottom)
        } else {
            Brush.linearGradient(listOf(Color.White, Color(0xFFE4E6E9)), start = cardRect.topLeft, end = cardRect.bottomRight)
        }
        drawPath(p, brush = cardBrush)
        drawPath(
            p,
            color = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f),
            style = Stroke(width = 1f)
        )

        drawLens(Offset(cardRect.left + s * 0.20f, cardRect.center.y), s * 0.13f, Color(0xFF8B5CFF))

        val barColor = if (dark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.25f)
        drawRoundRect(
            color = barColor,
            topLeft = Offset(cardRect.left + s * 0.40f, cardRect.top + s * 0.17f),
            size = Size(s * 0.36f, s * 0.07f),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
        )

        if (dark) {
            val stripRect = Rect(cardRect.left + s * 0.40f, cardRect.top + s * 0.31f, cardRect.left + s * 0.76f, cardRect.top + s * 0.41f)
            drawRoundRect(
                color = Color(0xFF050507),
                topLeft = stripRect.topLeft,
                size = stripRect.size,
                cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
            )
            for (i in 0 until 6) {
                val dotColor = if (i % 2 == 0) led else led.copy(alpha = 0.25f)
                drawCircle(
                    color = dotColor,
                    radius = s * 0.014f,
                    center = Offset(stripRect.left + s * 0.04f + i * s * 0.055f, stripRect.center.y)
                )
            }
        } else {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(cardRect.left + s * 0.40f, cardRect.top + s * 0.32f),
                size = Size(s * 0.26f, s * 0.07f),
                cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
            )
        }

        val holoCenter = Offset(cardRect.right - s * 0.13f, cardRect.bottom - s * 0.13f)
        val holoColors = listOf(Color(0xFFFFD3E8), Color(0xFFC4EEFF), Color(0xFFDCCBFF), Color(0xFFFFE9C4), Color(0xFFFFD3E8))
        drawCircle(
            brush = Brush.sweepGradient(holoColors, center = holoCenter),
            radius = s * 0.075f,
            center = holoCenter
        )
    }
}

private fun DrawScope.drawBell(s: Float, dark: Boolean, led: Color) {
    val body = Path().apply {
        moveTo(s * 0.50f, s * 0.14f)
        cubicTo(s * 0.72f, s * 0.14f, s * 0.78f, s * 0.32f, s * 0.78f, s * 0.50f)
        lineTo(s * 0.78f, s * 0.62f)
        lineTo(s * 0.88f, s * 0.74f)
        lineTo(s * 0.12f, s * 0.74f)
        lineTo(s * 0.22f, s * 0.62f)
        lineTo(s * 0.22f, s * 0.50f)
        cubicTo(s * 0.22f, s * 0.32f, s * 0.28f, s * 0.14f, s * 0.50f, s * 0.14f)
        close()
    }
    drawPathShadow(body, s, color = Color(0xFFFF6A2B).copy(alpha = if (dark) 0.55f else 0.35f), dark = dark)
    drawPath(
        body,
        brush = Brush.verticalGradient(listOf(Color(0xFFFFC46B), Color(0xFFFF6A2B)), startY = s * 0.14f, endY = s * 0.74f)
    )
    drawPath(body, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 1f))

    // Блик на куполе
    drawOval(
        color = Color.White.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.33f, s * 0.22f),
        size = Size(s * 0.09f, s * 0.20f)
    )
    drawLens(Offset(s * 0.50f, s * 0.82f), s * 0.08f, Color(0xFFFF8A3D))

    // Светодиод уведомлений
    val dotCenter = Offset(s * 0.80f, s * 0.20f)
    drawCircle(
        color = led.copy(alpha = 0.35f),
        radius = s * 0.12f,
        center = dotCenter
    )
    drawCircle(color = led, radius = s * 0.08f, center = dotCenter)
    drawCircle(
        color = if (dark) Color(0xFF1A1B1F) else Color.White,
        radius = s * 0.08f,
        center = dotCenter,
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawPalette(s: Float, dark: Boolean) {
    val p = Path().apply {
        moveTo(s * 0.50f, s * 0.12f)
        cubicTo(s * 0.80f, s * 0.12f, s * 0.92f, s * 0.32f, s * 0.90f, s * 0.50f)
        cubicTo(s * 0.88f, s * 0.66f, s * 0.70f, s * 0.62f, s * 0.66f, s * 0.72f)
        cubicTo(s * 0.62f, s * 0.84f, s * 0.56f, s * 0.90f, s * 0.44f, s * 0.88f)
        cubicTo(s * 0.20f, s * 0.86f, s * 0.08f, s * 0.68f, s * 0.10f, s * 0.46f)
        cubicTo(s * 0.12f, s * 0.26f, s * 0.28f, s * 0.12f, s * 0.50f, s * 0.12f)
        close()
    }
    drawPathShadow(p, s, dark = dark)
    val brush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF3A3C43), Color(0xFF1E1F23)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFE6E7E9)))
    }
    drawPath(p, brush = brush)
    drawPath(p, color = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), style = Stroke(width = 1f))

    drawLens(Offset(s * 0.32f, s * 0.36f), s * 0.085f, Color(0xFFFF4F9A))
    drawLens(Offset(s * 0.54f, s * 0.28f), s * 0.085f, Color(0xFFFFB020))
    drawLens(Offset(s * 0.72f, s * 0.42f), s * 0.085f, Color(0xFF19B8E6))
    drawLens(Offset(s * 0.28f, s * 0.60f), s * 0.085f, Color(0xFF8B5CFF))
}

private fun DrawScope.drawShield(s: Float, dark: Boolean) {
    val p = Path().apply {
        moveTo(s * 0.50f, s * 0.10f)
        lineTo(s * 0.84f, s * 0.22f)
        cubicTo(s * 0.84f, s * 0.56f, s * 0.72f, s * 0.78f, s * 0.50f, s * 0.90f)
        cubicTo(s * 0.28f, s * 0.78f, s * 0.16f, s * 0.56f, s * 0.16f, s * 0.22f)
        close()
    }
    drawPathShadow(p, s, color = Color(0xFF2BB673).copy(alpha = if (dark) 0.50f else 0.35f), dark = dark)
    drawPath(
        p,
        brush = Brush.linearGradient(
            listOf(Color(0xFF6FE3A8), Color(0xFF14B8A6)),
            start = Offset.Zero,
            end = Offset(s, s)
        )
    )
    clipPath(p) {
        drawRect(Color.White.copy(alpha = 0.07f), topLeft = Offset.Zero, size = Size(s * 0.50f, s))
    }
    val checkmark = Path().apply {
        moveTo(s * 0.36f, s * 0.50f)
        lineTo(s * 0.47f, s * 0.61f)
        lineTo(s * 0.66f, s * 0.40f)
    }
    drawPath(
        checkmark,
        color = Color.White,
        style = Stroke(width = s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawGlobe(s: Float, dark: Boolean) {
    val c = Offset(s / 2f, s / 2f)
    val r = s * 0.38f
    val disc = Path().apply {
        addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }
    drawPathShadow(disc, s, color = Color(0xFF2F66E0).copy(alpha = if (dark) 0.30f else 0.28f), dark = dark)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF4F86EE), Color(0xFF2C5BD0), Color(0xFF1B3597)),
            center = Offset(c.x - r * 0.3f, c.y - r * 0.4f),
            radius = r * 1.5f
        ),
        radius = r,
        center = c
    )
    clipPath(disc) {
        val lineStyle = Stroke(width = 1f)
        val lineColor = Color.White.copy(alpha = 0.26f)
        drawOval(
            color = lineColor,
            topLeft = Offset(c.x - (r * 0.9f) / 2f, c.y - r),
            size = Size(r * 0.9f, r * 2f),
            style = lineStyle
        )
        drawLine(lineColor, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 1f)
        drawLine(lineColor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1f)
        drawLine(lineColor, Offset(c.x - r, c.y - r * 0.5f), Offset(c.x + r, c.y - r * 0.5f), strokeWidth = 1f)
        drawLine(lineColor, Offset(c.x - r, c.y + r * 0.5f), Offset(c.x + r, c.y + r * 0.5f), strokeWidth = 1f)
    }
    drawPath(disc, color = Color.White.copy(alpha = if (dark) 0.14f else 0.30f), style = Stroke(width = 1f))
}

private fun DrawScope.drawDevices(s: Float, dark: Boolean, led: Color) {
    val lidRect = Rect(s * 0.10f, s * 0.20f, s * 0.76f, s * 0.64f)
    val lidP = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(lidRect, CornerRadius(s * 0.06f, s * 0.06f)))
    }
    drawPathShadow(lidP, s, dark = dark)
    val lidBrush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF3A3C43), Color(0xFF1E1F23)), startY = lidRect.top, endY = lidRect.bottom)
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E4E6)), startY = lidRect.top, endY = lidRect.bottom)
    }
    drawPath(lidP, brush = lidBrush)

    // Экран ноутбука
    val screenRect = lidRect.deflate(s * 0.05f)
    drawRoundRect(
        color = Color(0xFF0A0B0E),
        topLeft = screenRect.topLeft,
        size = screenRect.size,
        cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
    )
    for (i in 0 until 5) {
        val dotColor = if (i < 3) led else led.copy(alpha = 0.25f)
        drawCircle(color = dotColor, radius = s * 0.016f, center = Offset(screenRect.left + s * 0.07f + i * s * 0.08f, screenRect.center.y))
    }

    // Подставка ноутбука
    drawRoundRect(
        color = if (dark) Color(0xFF2A2B30) else Color(0xFFD5D8DC),
        topLeft = Offset(s * 0.04f, s * 0.64f),
        size = Size(s * 0.78f, s * 0.07f),
        cornerRadius = CornerRadius(s * 0.035f, s * 0.035f)
    )

    // Смартфон спереди
    val phoneRect = Rect(s * 0.62f, s * 0.38f, s * 0.90f, s * 0.86f)
    val phoneP = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(phoneRect, CornerRadius(s * 0.07f, s * 0.07f)))
    }
    drawPathShadow(phoneP, s, dark = dark)
    drawPath(
        phoneP,
        brush = Brush.verticalGradient(listOf(Color(0xFF8B5CFF), Color(0xFF3D2A8A)), startY = phoneRect.top, endY = phoneRect.bottom)
    )
    val phoneScreen = phoneRect.deflate(s * 0.035f)
    drawRoundRect(
        color = Color(0xFF0A0B0E),
        topLeft = phoneScreen.topLeft,
        size = phoneScreen.size,
        cornerRadius = CornerRadius(s * 0.045f, s * 0.045f)
    )
    drawCircle(color = led, radius = s * 0.03f, center = phoneRect.center)
}

private fun starPath(center: Offset, outerR: Float, innerR: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val angle = -PI / 2.0 + i * PI / 5.0
        val r = if (i % 2 == 0) outerR else innerR
        val x = (center.x + cos(angle) * r).toFloat()
        val y = (center.y + sin(angle) * r).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun DrawScope.drawMedal(s: Float, dark: Boolean) {
    val goldHi = Color(0xFFFFE08A)
    val goldMid = Color(0xFFE4A92B)
    val goldLo = Color(0xFF9A6A12)

    fun tail(left: Boolean): Path {
        fun x(v: Float): Float = if (left) v else 1f - v
        return Path().apply {
            moveTo(s * x(0.30f), s * 0.56f)
            lineTo(s * x(0.50f), s * 0.64f)
            lineTo(s * x(0.44f), s * 0.93f)
            lineTo(s * x(0.35f), s * 0.84f)
            lineTo(s * x(0.25f), s * 0.91f)
            close()
        }
    }

    for (left in listOf(true, false)) {
        val t = tail(left)
        drawPathShadow(t, s, dark = dark)
        val colors = if (left) listOf(Color(0xFFFF6B8A), Color(0xFFC72A4B)) else listOf(Color(0xFFE0405A), Color(0xFF9E1B3C))
        drawPath(t, brush = Brush.verticalGradient(colors, startY = s * 0.56f, endY = s * 0.93f))
    }

    val c = Offset(s * 0.50f, s * 0.38f)
    val r = s * 0.29f
    drawCircle(color = goldMid.copy(alpha = 0.40f), radius = r, center = Offset(c.x, c.y + s * 0.04f))
    drawCircle(
        brush = Brush.linearGradient(listOf(goldHi, goldMid, goldLo), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)),
        radius = r,
        center = c
    )

    val innerR = r * 0.74f
    drawCircle(
        brush = Brush.linearGradient(listOf(Color(0xFFF2BC45), Color(0xFFD99A22)), start = Offset(c.x + innerR, c.y + innerR), end = Offset(c.x - innerR, c.y - innerR)),
        radius = innerR,
        center = c
    )
    drawCircle(color = Color.Black.copy(alpha = 0.20f), radius = innerR, center = c, style = Stroke(width = 1f))

    val star = starPath(c, r * 0.50f, r * 0.22f)
    val starShadow = Path().apply { addPath(star, Offset(0f, s * 0.012f)) }
    drawPath(starShadow, color = goldLo.copy(alpha = 0.55f))
    drawPath(star, color = Color(0xFFFFF4CC))
}

private fun DrawScope.drawCoin(s: Float, dark: Boolean) {
    val goldHi = Color(0xFFFFE08A)
    val goldMid = Color(0xFFE4A92B)
    val goldLo = Color(0xFF9A6A12)

    fun drawOneCoin(c: Offset, r: Float, front: Boolean) {
        drawCircle(color = goldMid.copy(alpha = 0.35f), radius = r, center = Offset(c.x, c.y + s * 0.04f))
        val hi = if (front) goldHi else Color(0xFFE9C45E)
        val lo = if (front) goldLo else Color(0xFF7E560E)
        drawCircle(
            brush = Brush.linearGradient(listOf(hi, goldMid, lo), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)),
            radius = r,
            center = c
        )
        val innerR = r * 0.76f
        val innerColors = if (front) listOf(Color(0xFFF2BC45), Color(0xFFD99A22)) else listOf(Color(0xFFF2BC45), Color(0xFFB9821C))
        drawCircle(
            brush = Brush.linearGradient(innerColors, start = Offset(c.x + innerR, c.y + innerR), end = Offset(c.x - innerR, c.y - innerR)),
            radius = innerR,
            center = c
        )
        drawCircle(color = Color.Black.copy(alpha = 0.18f), radius = innerR, center = c, style = Stroke(width = 1f))
    }

    drawOneCoin(Offset(s * 0.38f, s * 0.38f), s * 0.27f, front = false)
    val f = Offset(s * 0.60f, s * 0.60f)
    drawOneCoin(f, s * 0.29f, front = true)

    val matrix = listOf(
        "##.",
        "#.#",
        "##.",
        "#.#",
        "##."
    )
    val pitch = s * 0.07f
    for (y in 0 until 5) {
        for (x in 0 until 3) {
            if (matrix[y][x] == '#') {
                val dot = Offset(f.x + (x - 1) * pitch, f.y + (y - 2) * pitch)
                drawCircle(color = Color(0xFFFFF4CC).copy(alpha = 0.70f), radius = s * 0.024f, center = Offset(dot.x, dot.y + s * 0.008f))
                drawCircle(color = Color(0xFF7A520C), radius = s * 0.024f, center = dot)
            }
        }
    }
}

private fun DrawScope.drawFlame(s: Float, dark: Boolean) {
    val outer = Path().apply {
        moveTo(s * 0.50f, s * 0.07f)
        cubicTo(s * 0.57f, s * 0.26f, s * 0.82f, s * 0.36f, s * 0.82f, s * 0.61f)
        cubicTo(s * 0.82f, s * 0.80f, s * 0.68f, s * 0.93f, s * 0.50f, s * 0.93f)
        cubicTo(s * 0.32f, s * 0.93f, s * 0.18f, s * 0.80f, s * 0.18f, s * 0.61f)
        cubicTo(s * 0.18f, s * 0.49f, s * 0.25f, s * 0.41f, s * 0.33f, s * 0.34f)
        cubicTo(s * 0.33f, s * 0.45f, s * 0.39f, s * 0.50f, s * 0.44f, s * 0.47f)
        cubicTo(s * 0.41f, s * 0.32f, s * 0.43f, s * 0.18f, s * 0.50f, s * 0.07f)
        close()
    }
    drawPathShadow(outer, s, color = Color(0xFFE11D48).copy(alpha = if (dark) 0.50f else 0.35f), dark = dark)
    drawPath(
        outer,
        brush = Brush.verticalGradient(
            listOf(Color(0xFFFFA23D), Color(0xFFFF5A2B), Color(0xFFE11D48)),
            startY = s * 0.07f,
            endY = s * 0.93f
        )
    )

    val core = Path().apply {
        moveTo(s * 0.50f, s * 0.48f)
        cubicTo(s * 0.58f, s * 0.60f, s * 0.67f, s * 0.66f, s * 0.67f, s * 0.76f)
        cubicTo(s * 0.67f, s * 0.86f, s * 0.59f, s * 0.92f, s * 0.50f, s * 0.92f)
        cubicTo(s * 0.41f, s * 0.92f, s * 0.33f, s * 0.86f, s * 0.33f, s * 0.76f)
        cubicTo(s * 0.33f, s * 0.66f, s * 0.43f, s * 0.60f, s * 0.50f, s * 0.48f)
        close()
    }
    drawPath(
        core,
        brush = Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFFFB020)), startY = s * 0.48f, endY = s * 0.92f)
    )
}

private fun DrawScope.drawBrush(s: Float, dark: Boolean) {
    // Мазок краски под кистью
    val stroke = Path().apply {
        moveTo(s * 0.08f, s * 0.90f)
        quadraticTo(s * 0.24f, s * 0.80f, s * 0.46f, s * 0.90f)
    }
    drawPath(
        stroke,
        color = Color(0xFFFF4F9A).copy(alpha = 0.85f),
        style = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
    )

    rotate(degrees = 40f, pivot = Offset(s * 0.50f, s * 0.50f)) {
        val handle = Path().apply {
            moveTo(s * 0.45f, s * 0.04f)
            lineTo(s * 0.55f, s * 0.04f)
            lineTo(s * 0.57f, s * 0.52f)
            lineTo(s * 0.43f, s * 0.52f)
            close()
        }
        drawPathShadow(handle, s, dark = dark)
        val handleColors = if (dark) listOf(Color(0xFF50535E), Color(0xFF2B2C32)) else listOf(Color(0xFF9094A0), Color(0xFF5B5E6C))
        drawPath(handle, brush = Brush.verticalGradient(handleColors, startY = 0f, endY = s * 0.52f))

        val ferruleRect = Rect(s * 0.40f, s * 0.50f, s * 0.60f, s * 0.63f)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFEEF0F3), Color(0xFFA2A7B2), Color(0xFFDADDE3)), startY = ferruleRect.top, endY = ferruleRect.bottom),
            topLeft = ferruleRect.topLeft,
            size = ferruleRect.size,
            cornerRadius = CornerRadius(s * 0.025f, s * 0.025f)
        )

        val bristles = Path().apply {
            moveTo(s * 0.41f, s * 0.62f)
            lineTo(s * 0.59f, s * 0.62f)
            cubicTo(s * 0.62f, s * 0.75f, s * 0.58f, s * 0.86f, s * 0.50f, s * 0.95f)
            cubicTo(s * 0.42f, s * 0.86f, s * 0.38f, s * 0.75f, s * 0.41f, s * 0.62f)
            close()
        }
        drawPath(
            bristles,
            brush = Brush.verticalGradient(listOf(Color(0xFFFF9CC8), Color(0xFFFF4F9A)), startY = s * 0.62f, endY = s * 0.95f)
        )
    }
}

private fun DrawScope.drawImageOff(s: Float, dark: Boolean) {
    val frameRect = Rect(s * 0.08f, s * 0.18f, s * 0.92f, s * 0.82f)
    val p = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(frameRect, CornerRadius(s * 0.10f, s * 0.10f)))
    }
    drawPathShadow(p, s, dark = dark)
    val bgBrush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF3A3C43), Color(0xFF18191C)), startY = frameRect.top, endY = frameRect.bottom)
    } else {
        Brush.linearGradient(listOf(Color.White, Color(0xFFE4E6E9)), start = frameRect.topLeft, end = frameRect.bottomRight)
    }
    drawPath(p, brush = bgBrush)
    drawPath(p, color = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), style = Stroke(width = 1f))

    clipPath(p) {
        val mountains = Path().apply {
            moveTo(s * 0.08f, s * 0.82f)
            lineTo(s * 0.34f, s * 0.50f)
            lineTo(s * 0.52f, s * 0.70f)
            lineTo(s * 0.64f, s * 0.56f)
            lineTo(s * 0.92f, s * 0.82f)
            close()
        }
        drawPath(mountains, color = if (dark) Color(0xFF4A4D58) else Color(0xFFBCC0C9))
    }

    drawCircle(
        color = if (dark) Color(0xFF6B6E78) else Color(0xFFD0D3DA),
        radius = s * 0.06f,
        center = Offset(s * 0.70f, s * 0.36f)
    )

    // Срез: диагональная линия с зазором
    val cutStart = Offset(s * 0.16f, s * 0.84f)
    val cutEnd = Offset(s * 0.84f, s * 0.16f)

    drawLine(
        color = if (dark) Color(0xFF1A1B1F) else Color.White,
        start = cutStart,
        end = cutEnd,
        strokeWidth = s * 0.15f,
        cap = StrokeCap.Round
    )
    drawLine(
        brush = Brush.linearGradient(listOf(Color(0xFFFF6B8A), Color(0xFFE11D48)), start = cutStart, end = cutEnd),
        start = cutStart,
        end = cutEnd,
        strokeWidth = s * 0.075f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawTelegram(s: Float, dark: Boolean) {
    val center = Offset(s * 0.50f, s * 0.50f)
    val radius = s * 0.40f

    // Тень диска
    drawCircle(
        color = Color(0xFF2AABEE).copy(alpha = if (dark) 0.45f else 0.30f),
        radius = radius,
        center = Offset(center.x, center.y + s * 0.05f)
    )
    // Градиентный диск Telegram
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF4FC3F7), Color(0xFF1B8FD0)),
            startY = center.y - radius,
            endY = center.y + radius
        ),
        radius = radius,
        center = center
    )
    // Тонкий кант
    drawCircle(
        color = Color.White.copy(alpha = if (dark) 0.20f else 0.40f),
        radius = radius,
        center = center,
        style = Stroke(width = 1f)
    )

    // Белый бумажный самолётик
    val plane = Path().apply {
        moveTo(s * 0.20f, s * 0.49f)
        lineTo(s * 0.76f, s * 0.27f)
        lineTo(s * 0.66f, s * 0.74f)
        lineTo(s * 0.50f, s * 0.62f)
        lineTo(s * 0.42f, s * 0.71f)
        lineTo(s * 0.40f, s * 0.58f)
        close()
    }
    val shadowPlane = Path().apply {
        addPath(plane, Offset(0f, s * 0.012f))
    }
    drawPath(shadowPlane, color = Color(0xFF0B4F7C).copy(alpha = 0.35f))
    drawPath(plane, color = Color.White)

    // Тень сгиба крыла
    val fold = Path().apply {
        moveTo(s * 0.40f, s * 0.58f)
        lineTo(s * 0.70f, s * 0.35f)
        lineTo(s * 0.50f, s * 0.62f)
        lineTo(s * 0.42f, s * 0.71f)
        close()
    }
    drawPath(fold, color = Color(0xFFD3E6F2))
}
