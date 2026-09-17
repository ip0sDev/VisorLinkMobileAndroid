package org.visorlink.app.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Вспомогательная функция отрисовки мягкой неоморфной тени по произвольному [Path].
 */
fun DrawScope.drawNeumorphicSoftShadow(
    path: Path,
    color: Color,
    blurPx: Float,
    dx: Float,
    dy: Float,
    strokeWidthPx: Float? = null,
) {
    if (color.alpha <= 0.001f || blurPx <= 0f) return

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            if (strokeWidthPx != null) {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
            } else {
                style = Paint.Style.FILL
            }
            setShadowLayer(blurPx, dx, dy, color.toArgb())
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/**
 * Расчет деформации желе/капли (Squash & Stretch).
 * Растягивает объект вдоль направления движения и сужает перпендикулярно, сохраняя объем.
 */
fun calculateJellyScale(velocityOrStretch: Float, maxStretch: Float = 0.60f): Pair<Float, Float> {
    val stretch = velocityOrStretch.coerceIn(-maxStretch, maxStretch)
    val scaleY = (1f + stretch).coerceAtLeast(0.15f)
    // Точное сохранение 2D площади (объема): sx = 1 / sy
    val scaleX = (1f / scaleY).coerceIn(0.4f, 2.5f)
    return Pair(scaleX, scaleY)
}

/**
 * Неоморфная жидкая перемычка (Viscous Neck) между двумя карточками или тулбаром и карточкой.
 * Реализует органическое отпочковывание: по мере удаления дочерней карточки перешеек
 * плавно истончается по бокам (талия), образуя натягивающийся жидкий мостик,
 * пока не наступит упругий разрыв.
 */
@Composable
fun NeumorphicLiquidBridge(
    topRect: Rect,
    bottomRect: Rect,
    maxDistancePx: Float,
    isSnapped: Boolean = false,
    surfaceTension: Float = 1.0f,
    progress: Float? = null,
    baseWidthPx: Float? = null,
    minWaistPx: Float? = null,
    retractProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Если перемычка разорвана и ретракция завершена — не рисуем
    if (isSnapped && retractProgress <= 0.001f) return

    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val structure = tokens.structure
    val surfaceColor = cs.surfaceContainerHighest

    Canvas(modifier = modifier.fillMaxSize()) {
        // Отрисовка упругих хвостиков ретракции (после разрыва нити)
        if (isSnapped) {
            if (retractProgress <= 0.001f) return@Canvas
            val centerX = (topRect.center.x + bottomRect.center.x) * 0.5f
            val topNubH = retractProgress * 10.dp.toPx()
            val botNubH = retractProgress * 8.dp.toPx()
            val nubBaseW = ((minWaistPx ?: 14.dp.toPx()) * (1f + retractProgress * 0.8f))

            val pathRetract = Path().apply {
                // Верхний бугорок (втягивается обратно в родителя)
                val topY = topRect.bottom
                moveTo(centerX - nubBaseW * 0.5f, topY)
                cubicTo(
                    centerX - nubBaseW * 0.35f, topY + topNubH * 0.6f,
                    centerX - nubBaseW * 0.15f, topY + topNubH,
                    centerX, topY + topNubH
                )
                cubicTo(
                    centerX + nubBaseW * 0.15f, topY + topNubH,
                    centerX + nubBaseW * 0.35f, topY + topNubH * 0.6f,
                    centerX + nubBaseW * 0.5f, topY
                )
                close()

                // Нижний бугорок (втягивается в карточку)
                val botY = bottomRect.top
                moveTo(centerX - nubBaseW * 0.5f, botY)
                cubicTo(
                    centerX - nubBaseW * 0.35f, botY - botNubH * 0.6f,
                    centerX - nubBaseW * 0.15f, botY - botNubH,
                    centerX, botY - botNubH
                )
                cubicTo(
                    centerX + nubBaseW * 0.15f, botY - botNubH,
                    centerX + nubBaseW * 0.35f, botY - botNubH * 0.6f,
                    centerX + nubBaseW * 0.5f, botY
                )
                close()
            }

            if (structure.enabled) {
                drawNeumorphicSoftShadow(
                    path = pathRetract,
                    color = structure.shadowLight,
                    blurPx = structure.raisedLightBlur.toPx(),
                    dx = -structure.raisedLightOffset.toPx(),
                    dy = -structure.raisedLightOffset.toPx()
                )
                drawNeumorphicSoftShadow(
                    path = pathRetract,
                    color = structure.shadowDark,
                    blurPx = structure.raisedBlur.toPx(),
                    dx = structure.raisedOffset.toPx(),
                    dy = structure.raisedOffset.toPx()
                )
            }
            drawPath(path = pathRetract, color = surfaceColor)
            return@Canvas
        }

        val gap = bottomRect.top - topRect.bottom
        val effectiveMaxDistance = maxDistancePx * surfaceTension

        // Если не передан явный прогресс — отсекаем по дистанции
        if (progress == null && (gap <= -4f || gap >= effectiveMaxDistance)) return@Canvas

        // Фактор удаления: 0 при касании, 1 на грани разрыва
        val factor = progress?.coerceIn(0f, 1f) ?: (gap / effectiveMaxDistance).coerceIn(0f, 1f)

        // Заход перемычки внутрь элементов для монолитного бесшовного слияния
        val overlapPx = 16f
        val yAnchorTop = topRect.bottom - overlapPx
        val yAnchorBot = bottomRect.top + overlapPx
        val totalH = yAnchorBot - yAnchorTop
        if (totalH <= 0f) return@Canvas

        val centerX = (topRect.center.x + bottomRect.center.x) * 0.5f

        // Ширина оснований воронки перешейка:
        val topMaxLimit = baseWidthPx ?: 50.dp.toPx()
        val botMaxLimit = (baseWidthPx?.times(0.92f)) ?: 44.dp.toPx()
        val topHalfW = minOf(topRect.width * 0.5f, topMaxLimit) * (1f - factor * 0.20f)
        val botHalfW = minOf(bottomRect.width * 0.5f, botMaxLimit) * (1f - factor * 0.18f)

        val tLeftX = centerX - topHalfW
        val tRightX = centerX + topHalfW
        val bLeftX = centerX - botHalfW
        val bRightX = centerX + botHalfW

        // Динамическое сужение талии (waist):
        val minWaistHalfW = (minWaistPx?.div(2f)) ?: 7.dp.toPx()
        val maxWaistHalfW = minOf(topHalfW, botHalfW) * 0.85f
        // Нелинейное истончение: удерживает ширину и резко схлопывается перед отрывом
        val nonLinearFactor = factor.pow(2.2f)
        val baseWaistHalfW = maxWaistHalfW - (maxWaistHalfW - minWaistHalfW) * nonLinearFactor
        val collapse = ((factor - 0.85f) / 0.15f).coerceIn(0f, 1f)
        val waistHalfW = baseWaistHalfW * (1f - collapse * 0.55f)

        // Талия перешейка чуть выше середины под действием гравитации
        val midY = yAnchorTop + totalH * 0.42f
        val midLeftX = centerX - waistHalfW
        val midRightX = centerX + waistHalfW

        val path = Path().apply {
            moveTo(tRightX, yAnchorTop)

            // Правый вогнутый край перешейка
            cubicTo(
                tRightX - (tRightX - midRightX) * 0.18f, yAnchorTop + totalH * 0.28f,
                midRightX, midY - totalH * 0.22f,
                midRightX, midY
            )
            cubicTo(
                midRightX, midY + totalH * 0.22f,
                bRightX - (bRightX - midRightX) * 0.18f, yAnchorBot - totalH * 0.28f,
                bRightX, yAnchorBot
            )

            // Нижний шов
            lineTo(bLeftX, yAnchorBot)

            // Левый вогнутый край перешейка
            cubicTo(
                bLeftX + (midLeftX - bLeftX) * 0.18f, yAnchorBot - totalH * 0.28f,
                midLeftX, midY + totalH * 0.22f,
                midLeftX, midY
            )
            cubicTo(
                midLeftX, midY - totalH * 0.22f,
                tLeftX + (midLeftX - tLeftX) * 0.18f, yAnchorTop + totalH * 0.28f,
                tLeftX, yAnchorTop
            )

            close()
        }

        // Неоморфные тени по контуру перешейка
        if (structure.enabled) {
            drawNeumorphicSoftShadow(
                path = path,
                color = structure.shadowLight,
                blurPx = structure.raisedLightBlur.toPx(),
                dx = -structure.raisedLightOffset.toPx(),
                dy = -structure.raisedLightOffset.toPx()
            )
            drawNeumorphicSoftShadow(
                path = path,
                color = structure.shadowDark,
                blurPx = structure.raisedBlur.toPx(),
                dx = structure.raisedOffset.toPx(),
                dy = structure.raisedOffset.toPx()
            )
        }

        // Сплошная заливка материалом Biolume (никакой прозрачности)
        drawPath(path = path, color = surfaceColor)
    }
}

/**
 * Неоморфные жидкие метаболлы (2D Neumorphic Metaballs).
 * Математически строгий непрерывный замкнутый контур:
 * - Обход строго по часовой стрелке
 * - Точные касательные к окружностям для идеально гладкого сопряжения (C1 continuity)
 * - Втягивание перемычки (concave neck) с физическим натяжением жидкости
 * - 100% плотный неоморфный материал Biolume без полупрозрачности
 */
@Composable
fun NeumorphicMetaballsCanvas(
    centerA: Offset,
    radiusA: Float,
    centerB: Offset,
    radiusB: Float,
    maxDistance: Float,
    wobble: Float = 0f,
    modifier: Modifier = Modifier
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val structure = tokens.structure
    val surfaceColor = cs.surfaceContainerHighest

    Canvas(modifier = modifier.fillMaxSize()) {
        val dxAB = centerB.x - centerA.x
        val dyAB = centerB.y - centerA.y
        val dist = hypot(dxAB, dyAB)

        // Эффект упругого дыхания/колебания капель (jelly wobble)
        val effRadiusA = radiusA * (1f + wobble * 0.08f)
        val effRadiusB = radiusB * (1f - wobble * 0.08f)

        // Функция отрисовки изолированной капли
        val drawSingleDrop: (Offset, Float) -> Unit = { center, radius ->
            val dropPath = Path().apply {
                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
            }
            if (structure.enabled) {
                drawNeumorphicSoftShadow(
                    path = dropPath,
                    color = structure.shadowLight,
                    blurPx = structure.raisedLightBlur.toPx(),
                    dx = -structure.raisedLightOffset.toPx(),
                    dy = -structure.raisedLightOffset.toPx()
                )
                drawNeumorphicSoftShadow(
                    path = dropPath,
                    color = structure.shadowDark,
                    blurPx = structure.raisedBlur.toPx(),
                    dx = structure.raisedOffset.toPx(),
                    dy = structure.raisedOffset.toPx()
                )
            }
            drawPath(dropPath, color = surfaceColor)
        }

        // Если капли слишком далеко — рисуем раздельно
        if (dist >= maxDistance || dist < 0.001f) {
            drawSingleDrop(centerA, effRadiusA)
            drawSingleDrop(centerB, effRadiusB)
            return@Canvas
        }

        // Если одна капля внутри другой — рисуем только большую
        if (dist <= abs(effRadiusA - effRadiusB)) {
            if (effRadiusA >= effRadiusB) drawSingleDrop(centerA, effRadiusA)
            else drawSingleDrop(centerB, effRadiusB)
            return@Canvas
        }

        val phi = atan2(dyAB, dxAB)
        val touchingDist = effRadiusA + effRadiusB

        // Фактор сближения: 0 на грани разрыва (maxDistance), 1 при соприкосновении
        val factor = if (dist > touchingDist) {
            ((maxDistance - dist) / (maxDistance - touchingDist)).coerceIn(0f, 1f)
        } else {
            1f
        }

        // Угол раскрытия горловины (радианы): от ~12° при разрыве до ~82° при слиянии
        val minSpread = 0.20f
        val maxSpread = (PI.toFloat() / 2.2f)
        val beta = minSpread + (maxSpread - minSpread) * factor

        // 4 точки сопряжения:
        // P1: верхняя точка на окружности A
        val p1 = Offset(
            centerA.x + effRadiusA * cos(phi - beta),
            centerA.y + effRadiusA * sin(phi - beta)
        )
        // P2: нижняя точка на окружности A
        val p2 = Offset(
            centerA.x + effRadiusA * cos(phi + beta),
            centerA.y + effRadiusA * sin(phi + beta)
        )
        // P3: нижняя точка на окружности B
        val p3 = Offset(
            centerB.x + effRadiusB * cos(phi + PI.toFloat() - beta),
            centerB.y + effRadiusB * sin(phi + PI.toFloat() - beta)
        )
        // P4: верхняя точка на окружности B
        val p4 = Offset(
            centerB.x + effRadiusB * cos(phi + PI.toFloat() + beta),
            centerB.y + effRadiusB * sin(phi + PI.toFloat() + beta)
        )

        // Длина направляющих для кубических Безье:
        // Чем дальше капли, тем сильнее втягивается горловина (concave pinch)
        val bridgeLenTop = hypot(p4.x - p1.x, p4.y - p1.y)
        val bridgeLenBot = hypot(p3.x - p2.x, p3.y - p2.y)
        val handleLenTop = bridgeLenTop * 0.40f * (1f - factor * 0.22f)
        val handleLenBot = bridgeLenBot * 0.40f * (1f - factor * 0.22f)

        // Касательные к окружностям для C1 гладкости:
        // Направляющая от P1 к P4 (верхний мостик, угол phi - beta + PI/2)
        val h1 = Offset(
            p1.x + handleLenTop * cos(phi - beta + PI.toFloat() / 2f),
            p1.y + handleLenTop * sin(phi - beta + PI.toFloat() / 2f)
        )
        // Направляющая к P4 от P1 (угол phi + beta + PI/2)
        val h4 = Offset(
            p4.x + handleLenTop * cos(phi + beta + PI.toFloat() / 2f),
            p4.y + handleLenTop * sin(phi + beta + PI.toFloat() / 2f)
        )

        // Направляющая от P3 к P2 (нижний мостик, угол phi - beta - PI/2)
        val h3 = Offset(
            p3.x + handleLenBot * cos(phi - beta - PI.toFloat() / 2f),
            p3.y + handleLenBot * sin(phi - beta - PI.toFloat() / 2f)
        )
        // Направляющая к P2 от P3 (угол phi + beta - PI/2)
        val h2 = Offset(
            p2.x + handleLenBot * cos(phi + beta - PI.toFloat() / 2f),
            p2.y + handleLenBot * sin(phi + beta - PI.toFloat() / 2f)
        )

        // Углы для внешних дуг (в градусах):
        // Дуга A: начинается в P2 (phi + beta) и идёт ПО ЧАСОВОЙ СТРЕЛКЕ вокруг тыльной стороны A к P1 (phi - beta)
        val startAngleDegA = Math.toDegrees((phi + beta).toDouble()).toFloat()
        val betaDeg = Math.toDegrees(beta.toDouble()).toFloat()
        val sweepAngleDegA = 360f - 2f * betaDeg

        // Дуга B: начинается в P4 (phi + PI + beta) и идёт ПО ЧАСОВОЙ СТРЕЛКЕ вокруг тыльной стороны B к P3 (phi + PI - beta)
        val startAngleDegB = Math.toDegrees((phi + PI.toFloat() + beta).toDouble()).toFloat()
        val sweepAngleDegB = 360f - 2f * betaDeg

        val mergedPath = Path().apply {
            // 1. Внешняя дуга капли A: от P2 по часовой стрелке к P1
            arcTo(
                rect = Rect(
                    centerA.x - effRadiusA,
                    centerA.y - effRadiusA,
                    centerA.x + effRadiusA,
                    centerA.y + effRadiusA
                ),
                startAngleDegrees = startAngleDegA,
                sweepAngleDegrees = sweepAngleDegA,
                forceMoveTo = true
            )

            // 2. Верхний жидкий мостик: от P1 к P4 (плавное втягивание)
            cubicTo(h1.x, h1.y, h4.x, h4.y, p4.x, p4.y)

            // 3. Внешняя дуга капли B: от P4 по часовой стрелке к P3
            arcTo(
                rect = Rect(
                    centerB.x - effRadiusB,
                    centerB.y - effRadiusB,
                    centerB.x + effRadiusB,
                    centerB.y + effRadiusB
                ),
                startAngleDegrees = startAngleDegB,
                sweepAngleDegrees = sweepAngleDegB,
                forceMoveTo = false
            )

            // 4. Нижний жидкий мостик: от P3 к P2 (плавное втягивание)
            cubicTo(h3.x, h3.y, h2.x, h2.y, p2.x, p2.y)

            close()
        }

        // Неоморфные тени по единому замкнутому контуру слившихся капель
        if (structure.enabled) {
            drawNeumorphicSoftShadow(
                path = mergedPath,
                color = structure.shadowLight,
                blurPx = structure.raisedLightBlur.toPx(),
                dx = -structure.raisedLightOffset.toPx(),
                dy = -structure.raisedLightOffset.toPx()
            )
            drawNeumorphicSoftShadow(
                path = mergedPath,
                color = structure.shadowDark,
                blurPx = structure.raisedBlur.toPx(),
                dx = structure.raisedOffset.toPx(),
                dy = structure.raisedOffset.toPx()
            )
        }

        // Сплошная заливка материалом
        drawPath(path = mergedPath, color = surfaceColor)
    }
}

/**
 * Неоморфный переключатель вкладок с жидким желейным индикатором (Liquid Segmented Control).
 * При переключении капля вытягивается вдоль направления движения (Squash & Stretch)
 * и мягко спружинивает на новой позиции.
 */
@Composable
fun NeumorphicLiquidSegmentedControl(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackShape = RoundedCornerShape(18.dp)
    val thumbShape = RoundedCornerShape(14.dp)

    var totalWidthPx by remember { mutableFloatStateOf(1f) }
    val tabCount = tabs.size.coerceAtLeast(1)
    val tabWidthPx = totalWidthPx / tabCount

    val animatedIndex = remember { Animatable(selectedIndex.toFloat()) }
    val stretchAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex) {
        val prevIndex = animatedIndex.value
        val diff = selectedIndex - prevIndex
        val stretchDirection = if (diff > 0) 1f else -1f

        // Анимация вытягивания капли в пути
        scope.launch {
            stretchAnim.animateTo(
                targetValue = stretchDirection * 0.28f,
                animationSpec = tween(120, easing = FastOutSlowInEasing)
            )
            stretchAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f)
            )
        }

        // Перемещение самого бегунка
        animatedIndex.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .vlInset(tokens.structure, trackShape)
            .clip(trackShape)
            .background(cs.surfaceContainerLow)
            .padding(4.dp)
            .onGloballyPositioned { coordinates ->
                totalWidthPx = coordinates.size.width.toFloat() - with(density) { 8.dp.toPx() }
            }
    ) {
        // Желейный неоморфный бегунок
        val currentTabWidthDp = with(density) { (totalWidthPx / tabCount).toDp() }
        val thumbOffsetXDp = with(density) { (animatedIndex.value * tabWidthPx).toDp() }
        val stretch = stretchAnim.value
        val scaleX = 1f + abs(stretch) * 0.45f
        val scaleY = 1f - abs(stretch) * 0.25f

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetXDp)
                .fillMaxHeight()
                .width(currentTabWidthDp)
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                }
                .vlRaised(tokens.structure, thumbShape)
                .clip(thumbShape)
                .background(cs.surfaceContainerHighest)
                .vlHairline(color = cs.outlineVariant, shape = thumbShape)
        )

        // Тексты вкладок
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
