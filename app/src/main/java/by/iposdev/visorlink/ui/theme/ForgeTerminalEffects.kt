package by.iposdev.visorlink.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hardware Out (Выпуклые панели/Карточки):
 * - Падающая тень: 6dp смещение, blur 12dp, цвет rgba(0,0,0,0.9).
 * - Внутренний блик (сверху-слева): 1dp смещение, цвет rgba(255,255,255,0.06).
 * - Внутреннее затенение (снизу-справа): -1dp смещение, цвет rgba(0,0,0,0.7).
 */
fun Modifier.hardwareOut(
    cornerRadius: Dp = 2.dp
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    
    drawIntoCanvas { canvas ->
        // 1. Падающая тень
        val shadowPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(12.dp.toPx(), 6.dp.toPx(), 6.dp.toPx(), Color.Black.copy(alpha = 0.9f).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, shadowPaint)
    }
    
    // Имитация фаски через рисование линий по краям
    val highlightColor = Color.White.copy(alpha = 0.08f)
    val shadowColor = Color.Black.copy(alpha = 0.7f)
    
    // Рисуем металлический градиент фона, если это не перекрывается
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A1A1E),
                Color(0xFF121214),
                Color(0xFF0D0D0F)
            )
        )
    )
    
    // Верхний и левый края (блик)
    drawRect(
        color = highlightColor,
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 1.dp.toPx())
    )
    drawRect(
        color = highlightColor,
        topLeft = Offset(0f, 0f),
        size = Size(1.dp.toPx(), size.height)
    )
    
    // Нижний и правый края (затенение)
    drawRect(
        color = shadowColor,
        topLeft = Offset(0f, size.height - 1.dp.toPx()),
        size = Size(size.width, 1.dp.toPx())
    )
    drawRect(
        color = shadowColor,
        topLeft = Offset(size.width - 1.dp.toPx(), 0f),
        size = Size(1.dp.toPx(), size.height)
    )
}

/**
 * Hardware In (Вдавленные экраны/Инпуты/Поля ввода):
 * - Внутренняя глубокая тень: 3dp смещение, blur 8dp, цвет rgba(0,0,0,0.9).
 * - Внутренний блик (только снизу-справа): -1dp смещение, rgba(255,255,255,0.03).
 */
fun Modifier.hardwareIn(
    cornerRadius: Dp = 2.dp
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val blur = 8.dp.toPx()
    val offset = 3.dp.toPx()
    
    drawIntoCanvas { canvas ->
        // Внутренняя тень (сверху-слева падает внутрь)
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(blur, offset, offset, Color.Black.copy(alpha = 0.9f).toArgb())
        }
        
        // Чтобы тень была внутри, используем клип
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    0f, 0f, size.width, size.height,
                    CornerRadius(cr)
                )
            )
        }
        
        canvas.save()
        canvas.clipPath(path)
        // Рисуем рамку снаружи, которая отбрасывает тень внутрь
        canvas.nativeCanvas.drawRoundRect(-offset*2, -offset*2, size.width + offset*2, size.height + offset*2, cr, cr, paint)
        canvas.restore()
        
        // Внутренний блик (снизу-справа)
        val highlightColor = Color.White.copy(alpha = 0.03f)
        canvas.drawRect(
            0f, size.height - 1.dp.toPx(), size.width, size.height,
            Paint().apply { color = highlightColor }
        )
        canvas.drawRect(
            size.width - 1.dp.toPx(), 0f, size.width, size.height,
            Paint().apply { color = highlightColor }
        )
    }
}

/**
 * Hardware Button (Механические кнопки):
 * - Падающая тень: 3dp смещение, blur 6dp, цвет rgba(0,0,0,0.8).
 * - Внутренний сильный блик (сверху): inset 1px 1px, rgba(255,255,255,0.1).
 * - Неоновое свечение: вокруг активных кнопок красная тень (blur 10dp, rgba(229,0,39,0.5)).
 */
fun Modifier.hardwareButton(
    isPressed: Boolean = false,
    isActive: Boolean = false,
    cornerRadius: Dp = 2.dp
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    
    drawIntoCanvas { canvas ->
        if (!isPressed) {
            // 1. Падающая тень
            val shadowPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(6.dp.toPx(), 3.dp.toPx(), 3.dp.toPx(), Color.Black.copy(alpha = 0.8f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, shadowPaint)
        }

        // 3. Неоновое свечение (если активна)
        if (isActive) {
            val glowPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(10.dp.toPx(), 0f, 0f, Color(0xFFE50027).copy(alpha = 0.5f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, glowPaint)
        }
    }
    
    // Внутренний блик сверху
    val highlightColor = Color.White.copy(alpha = 0.15f)
    drawRect(
        color = highlightColor,
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = Size(size.width - 2.dp.toPx(), 1.2.dp.toPx())
    )
    
    // Добавляем металлическую текстуру кнопке
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.03f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.1f)
            )
        )
    )
    
    // Если нажата - имитируем вдавленность
    if (isPressed) {
        // Добавляем эффект нажатия
        drawIntoCanvas { canvas ->
            val insetShadowPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(4.dp.toPx(), 2.dp.toPx(), 2.dp.toPx(), Color.Black.copy(alpha = 0.6f).toArgb())
            }
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        0f, 0f, size.width, size.height,
                        CornerRadius(cr)
                    )
                )
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.nativeCanvas.drawRoundRect(-10f, -10f, size.width + 10f, size.height + 10f, cr, cr, insetShadowPaint)
            canvas.restore()
        }
    }
}

/**
 * Рисует текстуру матового карбона:
 * Очень мелкая сетка из чередующихся темных квадратов или диагональных линий.
 */
fun Modifier.carbonFiberBackground(): Modifier = this.drawBehind {
    // 1. Базовый глубокий фон
    drawRect(color = Color(0xFF050506))

    // 2. Рисуем сетку "углеволокна"
    val patternSize = 6.dp.toPx()
    val columns = (size.width / patternSize).toInt() + 1
    val rows = (size.height / patternSize).toInt() + 1
    
    val fiberColor = Color(0xFF0A0A0C)
    
    for (c in 0 until columns) {
        for (r in 0 until rows) {
            if ((c + r) % 2 == 0) {
                drawRect(
                    color = fiberColor,
                    topLeft = Offset(c * patternSize, r * patternSize),
                    size = Size(patternSize, patternSize)
                )
            }
        }
    }
    
    // 3. Добавляем "металлический" блеск (оружейная сталь)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.03f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.2f, size.height * 0.1f),
            radius = size.maxDimension * 1.5f
        )
    )
    
    // 4. Тонкие горизонтальные линии шлифовки
    val strokeColor = Color.White.copy(alpha = 0.015f)
    val step = 3.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = strokeColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

/**
 * Рисует эффект "оружейной стали" (Gunmetal Steel):
 * Матовый градиент с горизонтальными микро-штрихами.
 */
fun Modifier.gunmetalSteelBackground(): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A1A1E),
                Color(0xFF121214),
                Color(0xFF0A0A0C)
            )
        )
    )
    
    // Микро-штрихи (текстура шлифовки)
    val strokeColor = Color.White.copy(alpha = 0.02f)
    val step = 2.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = strokeColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}
