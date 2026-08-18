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
 * Глобальный "индустриальный" модификатор для панелей Forge.
 * УЛУЧШЕНО: Добавлен эффект толстой металлической пластины и более глубокие тени.
 */
fun Modifier.industrialPanel(
    cornerRadius: Dp = 4.dp,
    isDark: Boolean = true,
    accentGlow: Boolean = false,
    accentColor: Color = Color(0xFFFF1A1A),
    thickness: Dp = 2.dp
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val thickPx = thickness.toPx()
    
    // 1. Базовый металлический градиент (фон)
    val baseGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF25252B),
                Color(0xFF1A1A1E),
                Color(0xFF0D0D0F)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFDCDFE5),
                Color(0xFFC0C0C0),
                Color(0xFFA1A1AA)
            )
        )
    }
    
    drawRoundRect(brush = baseGradient, cornerRadius = CornerRadius(cr))

    // 2. Текстура шлифовки (Brushed Metal) - более выраженная
    val strokeColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    val step = 2.5.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = strokeColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2.dp.toPx()
        )
        y += step
    }

    // 3. Двойная Фаска (Double Bevel) для объема пластины
    val outerHighlight = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f)
    val innerShadow = if (isDark) Color.Black.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.3f)
    
    // Внешний контур (толщина листа)
    drawRoundRect(
        color = outerHighlight,
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = 1.dp.toPx())
    )
    
    // Внутренняя тень фаски (создает эффект закругления края внутрь)
    drawRoundRect(
        color = innerShadow,
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = 1.dp.toPx())
    )

    // 4. Акцентированные блики на гранях
    val shinyColor = Color.White.copy(alpha = 0.2f)
    drawLine(shinyColor, Offset(cr, thickPx), Offset(size.width - cr, thickPx), 1.5.dp.toPx()) // Верхняя грань
    drawLine(shinyColor, Offset(thickPx, cr), Offset(thickPx, size.height - cr), 1.5.dp.toPx()) // Левая грань
    
    // Вторичный "спекулярный" блик в самом углу для объема
    drawPath(
        path = Path().apply {
            moveTo(0f, cr * 2)
            lineTo(0f, 0f)
            lineTo(cr * 2, 0f)
            close()
        },
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
            start = Offset(0f, 0f),
            end = Offset(cr, cr)
        )
    )

    // 5. Акцентное свечение
    if (accentGlow) {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(12.dp.toPx(), 0f, 0f, accentColor.copy(alpha = 0.5f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, paint)
        }
    }
}

/**
 * Эффект вдавленной дорожки (Recessed Track) для свитчей или инпутов.
 */
fun Modifier.recessedTrack(
    cornerRadius: Dp = 2.dp,
    isDark: Boolean = true
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    
    // Фон углубления
    drawRoundRect(
        color = if (isDark) Color(0xFF070709) else Color(0xFF8E9196),
        cornerRadius = CornerRadius(cr)
    )
    
    // Внутренняя тень сверху
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(6.dp.toPx(), 2.dp.toPx(), 2.dp.toPx(), Color.Black.copy(alpha = 0.8f).toArgb())
        }
        val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cr))) }
        canvas.save()
        canvas.clipPath(path)
        canvas.nativeCanvas.drawRoundRect(-10f, -10f, size.width + 10f, 10f, cr, cr, paint)
        canvas.restore()
    }
    
    // Нижний блик (грань углубления)
    val highlightColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.3f)
    drawLine(
        color = highlightColor,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx()
    )
}

/**
 * Эффект индикатора LED.
 */
fun Modifier.glowingIndicator(
    active: Boolean,
    color: Color = Color(0xFFFF1A1A)
): Modifier = this.drawBehind {
    val radius = size.minDimension / 2
    val center = Offset(size.width / 2, size.height / 2)
    
    // Ободок (корпус светодиода)
    drawCircle(
        color = Color(0xFF1A1A1E),
        radius = radius,
        center = center
    )
    
    // Сам светодиод
    val ledColor = if (active) color else color.copy(alpha = 0.2f)
    drawCircle(
        color = ledColor,
        radius = radius * 0.7f,
        center = center
    )
    
    if (active) {
        // Свечение
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(8.dp.toPx(), 0f, 0f, color.copy(alpha = 0.8f).toArgb())
            }
            canvas.nativeCanvas.drawCircle(center.x, center.y, radius * 0.7f, paint)
        }
        
        // Блик сверху
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = radius * 0.2f,
            center = center - Offset(radius * 0.2f, radius * 0.2f)
        )
    }
}

/**
 * Эффект строчной развертки (Scanlines) для терминала с легким мерцанием.
 */
fun Modifier.terminalScanlines(
    lineColor: Color = Color.Black.copy(alpha = 0.12f),
    lineSpacing: Dp = 3.dp
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline_flicker")
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
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

/**
 * Hardware Button (Механические кнопки) - УЛУЧШЕНО
 */
fun Modifier.hardwareButton(
    isPressed: Boolean = false,
    isActive: Boolean = false,
    cornerRadius: Dp = 2.dp,
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    
    // 1. Фон с градиентом
    val btnGradient = if (isPressed) {
        Brush.verticalGradient(listOf(Color(0xFF0F0F12), Color(0xFF1A1A1E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF32323A), Color(0xFF1C1C22)))
    }
    drawRoundRect(btnGradient, cornerRadius = CornerRadius(cr))

    // 2. Фаска
    val highlight = if (isPressed) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.18f)
    val shadow = if (isPressed) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.6f)
    
    if (!isPressed) {
        drawLine(highlight, Offset(cr, 1.dp.toPx()), Offset(size.width - cr, 1.dp.toPx()), 1.5.dp.toPx())
        drawLine(shadow, Offset(cr, size.height - 1.dp.toPx()), Offset(size.width - cr, size.height - 1.dp.toPx()), 1.5.dp.toPx())
    }

    // 3. Свечение активной кнопки
    if (isActive) {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(10.dp.toPx(), 0f, 0f, Color(0xFFFF1A1A).copy(alpha = 0.5f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, paint)
        }
    }
}

// Старые алиасы для совместимости
fun Modifier.gunmetalSteelBackground(): Modifier = this.industrialPanel(isDark = true)
fun Modifier.hardwareOut(cornerRadius: Dp = 2.dp): Modifier = this.industrialPanel(cornerRadius = cornerRadius, isDark = true)
fun Modifier.carbonFiberBackground(): Modifier = this.drawBehind {
    drawRect(color = Color(0xFF050506))
    val patternSize = 6.dp.toPx()
    val columns = (size.width / patternSize).toInt() + 1
    val rows = (size.height / patternSize).toInt() + 1
    for (c in 0 until columns) {
        for (r in 0 until rows) {
            if ((c + r) % 2 == 0) {
                drawRect(color = Color(0xFF0A0A0C), topLeft = Offset(c * patternSize, r * patternSize), size = Size(patternSize, patternSize))
            }
        }
    }
}
