package org.visorlink.app.ui.components.led

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.random.Random

/**
 * Цвета точек в текущей теме: горящая, погашенная и сила ореола (0 — без ореола).
 */
@Immutable
data class VlLedInk(
    val on: Color,
    val off: Color,
    val glowBlur: Dp,
)

/**
 * Получить ink токенов LED темы (или primary/fallback).
 */
@Composable
fun rememberLedInk(color: Color? = null): VlLedInk {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val led = tokens.led
    val onColor = color ?: led?.color ?: cs.primary
    val glow = if (led == null) 4.dp else (if (tokens.isDark) led.glowBlur else 0.dp)
    val offAlpha = led?.offAlpha ?: 0.08f
    return remember(onColor, glow, offAlpha) {
        VlLedInk(on = onColor, off = onColor.copy(alpha = offAlpha), glowBlur = glow)
    }
}

/**
 * Отрисовка всех точек через DrawScope / drawCircle (с субпиксельным сглаживанием без муара):
 *  1. Погашенные точки;
 *  2. Общий размытый ореол всех горящих точек (на темном стекле);
 *  3. Горящие точки (с опциональной построчной раскраской rowColor).
 */
fun DrawScope.vlPaintLedDots(
    cols: Int,
    rows: Int,
    dotPx: Float,
    gapPx: Float,
    ink: VlLedInk,
    rowColor: ((Int) -> Color)? = null,
    lit: (x: Int, y: Int) -> Boolean,
) {
    vlPaintLedDots(
        cols = cols,
        rows = rows,
        dotPx = dotPx,
        gapPx = gapPx,
        ink = ink,
        rowColor = rowColor,
        intensity = { x, y -> if (lit(x, y)) 1f else 0f },
    )
}

/**
 * Отрисовка матрицы светодиодов с аналоговой непрерывной интенсивностью (инерция люминофора):
 * [intensity] возвращает яркость точки от 0.0f (погашена) до 1.0f (полный накал).
 */
@JvmName("vlPaintLedDotsIntensity")
fun DrawScope.vlPaintLedDots(
    cols: Int,
    rows: Int,
    dotPx: Float,
    gapPx: Float,
    ink: VlLedInk,
    rowColor: ((Int) -> Color)? = null,
    intensity: (x: Int, y: Int) -> Float,
) {
    if (cols <= 0 || rows <= 0) return
    val r = dotPx / 2f
    val blurPx = if (ink.glowBlur > 0.dp) ink.glowBlur.toPx() * 0.6f else 0f
    val hasGlow = blurPx > 0f
    val sigma = if (hasGlow) blurPx * 0.57735f + 0.5f else 0f
    val glowRadius = r * 1.4f

    // 1. Погашенные точки — база светодиодной матрицы
    for (y in 0 until rows) {
        val top = y * (dotPx + gapPx) + r
        for (x in 0 until cols) {
            val v = intensity(x, y)
            if (v <= 0.01f) {
                drawCircle(
                    color = ink.off,
                    radius = r,
                    center = Offset(x * (dotPx + gapPx) + r, top),
                )
            }
        }
    }

    // 2. Ореол свечения для светящихся точек
    if (hasGlow) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val glowPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
            }
            for (y in 0 until rows) {
                val top = y * (dotPx + gapPx) + r
                val onColor = rowColor?.invoke(y) ?: ink.on
                for (x in 0 until cols) {
                    val v = intensity(x, y)
                    if (v > 0.05f) {
                        val px = x * (dotPx + gapPx) + r
                        val alpha = (0.70f * v.coerceIn(0f, 1f))
                        glowPaint.color = onColor.copy(alpha = alpha).toArgb()
                        nativeCanvas.drawCircle(px, top, glowRadius, glowPaint)
                    }
                }
            }
        }
    }

    // 3. Сами светодиоды с аналоговым нарастанием и затуханием яркости
    for (y in 0 until rows) {
        val top = y * (dotPx + gapPx) + r
        val onColor = rowColor?.invoke(y) ?: ink.on
        for (x in 0 until cols) {
            val v = intensity(x, y)
            if (v > 0.01f) {
                val pt = Offset(x * (dotPx + gapPx) + r, top)
                val clamped = v.coerceIn(0f, 1f)
                val dotColor = androidx.compose.ui.graphics.lerp(ink.off, onColor, clamped)
                val dotRadius = r * (0.86f + 0.14f * clamped)
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = pt,
                )
            }
        }
    }
}

/**
 * Матрица светодиодов: строки, где `#` — горящая точка, любой другой символ — погашенная.
 * Если передан [intensity], используется плавная непрерывная яркость для инерции светодиодов.
 */
@Composable
fun VlLedMatrix(
    rows: List<String>,
    modifier: Modifier = Modifier,
    dot: Dp = 3.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
    contentDescription: String? = null,
    intensity: ((x: Int, y: Int) -> Float)? = null,
) {
    val cols = remember(rows) { rows.maxOfOrNull { it.length } ?: 0 }
    val rowCount = rows.size
    val ink = rememberLedInk(color)

    val semModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier

    Canvas(
        modifier = modifier
            .then(semModifier)
            .size(
                width = if (cols <= 0) 0.dp else (dot + gap) * cols - gap,
                height = if (rowCount <= 0) 0.dp else (dot + gap) * rowCount - gap,
            )
    ) {
        val dotPx = dot.toPx()
        val gapPx = gap.toPx()
        if (intensity != null) {
            vlPaintLedDots(
                cols = cols,
                rows = rowCount,
                dotPx = dotPx,
                gapPx = gapPx,
                ink = ink,
                intensity = intensity,
            )
        } else {
            vlPaintLedDots(
                cols = cols,
                rows = rowCount,
                dotPx = dotPx,
                gapPx = gapPx,
                ink = ink,
                lit = { x, y ->
                    val line = rows.getOrNull(y) ?: ""
                    x < line.length && line[x] == '#'
                }
            )
        }
    }
}

/**
 * Одиночный светодиод с ореолом: индикатор активной вкладки, выбранной клавиши, «в сети».
 */
@Composable
fun VlLedDot(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    color: Color? = null,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val on = color ?: tokens.led?.color ?: cs.primary

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                val blurPx = (size * 1.6f).toPx()
                if (blurPx > 0f) {
                    val sigma = blurPx * 0.57735f + 0.5f
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.FILL
                            this.color = on.copy(alpha = 0.8f).toArgb()
                            maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawCircle(center.x, center.y, r, paint)
                    }
                }
            }
            .clip(CircleShape)
            .background(on)
    )
}

/**
 * Утопленное стекло LED-экрана: темный фон в теме Blackout, светлый в Clean Room.
 */
@Composable
fun VlLedScreen(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    padding: PaddingValues = PaddingValues(10.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val screenColor = tokens.led?.screen ?: cs.surfaceContainerHighest
    val shape = tokens.shapes.rounded(radius)

    Box(
        modifier = modifier
            .vlInset(tokens.structure, shape)
            .clip(shape)
            .background(screenColor, shape)
            .padding(padding),
        content = content,
    )
}

/**
 * Выражение LED-мордочки.
 */
enum class VlLedExpression {
    HAPPY, BOOP, HEART, WIDE, SLEEPY, SCAN, CAT, COOL, GLITCH, CRY, ANGRY;

    companion object {
        fun fromUrl(url: String?): VlLedExpression {
            if (url.isNullOrEmpty()) return HAPPY
            var name = url.trim()
            if (name.startsWith("led:")) name = name.removePrefix("led:")
            if (name.startsWith("led_")) name = name.removePrefix("led_")
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HAPPY
        }
    }
}

// ── Матричные формы мордочки ────────────────────────────────────────────────

private val Eyes = mapOf(
    VlLedExpression.HAPPY to listOf("..###..", ".##.##.", "##...##", "#.....#", ".......", "......."),
    VlLedExpression.BOOP to listOf("##.....", ".###...", "...###.", ".###...", "##.....", "......."),
    VlLedExpression.HEART to listOf(".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."),
    VlLedExpression.WIDE to listOf(".#####.", "#######", "#######", "#######", ".#####.", "......."),
    VlLedExpression.SLEEPY to listOf(".......", ".......", ".......", "#######", ".#####.", "......."),
    VlLedExpression.CAT to listOf(".......", ".......", "#.....#", "##...##", ".#####.", "......."),
    VlLedExpression.COOL to listOf("#######", "#######", "#######", ".#####.", "..###..", "......."),
    VlLedExpression.GLITCH to listOf("#.#.#.#", ".#.#.#.", "###.###", ".#.#.#.", "#.#.#.#", "......."),
    VlLedExpression.CRY to listOf(".......", "....###", "..####.", "####...", "##.....", "......."),
    VlLedExpression.ANGRY to listOf("#......", ".##....", "..#####", ".######", "..####.", "......."),
)

private val BlinkEye = listOf(".......", ".......", ".......", ".#####.", ".......", ".......")

private fun scanEye(px: Int, py: Int): List<String> {
    val wide = Eyes[VlLedExpression.WIDE] ?: BlinkEye
    val rows = ArrayList<String>(6)
    for (r in 0 until 6) {
        val sb = StringBuilder(7)
        for (c in 0 until 7) {
            val lit = r < 5 && wide[r][c] == '#' && !(c >= px && c < px + 3 && r >= py && r < py + 2)
            sb.append(if (lit) '#' else '.')
        }
        rows.add(sb.toString())
    }
    return rows
}

private val Mouths = mapOf(
    VlLedExpression.CAT to listOf("#...##...#", "#..#..#..#", ".##....##."),
    VlLedExpression.COOL to listOf("..............##", "............##..", "############...."),
    VlLedExpression.CRY to listOf("............", "....####....", "..##....##..", "##........##"),
    VlLedExpression.ANGRY to listOf(".............", "#############", "#..#..#..#..#", "#############"),
)

/**
 * Строки мордочки 32×12: глаза в строках 1–6, рот в строках 8–11.
 */
fun vlLedFaceRows(
    expression: VlLedExpression,
    pupilX: Int = 2,
    pupilY: Int = 1,
    mouthShift: Int = 0,
    blink: Boolean = false,
    tick: Int = 0,
): List<String> {
    val scan = expression == VlLedExpression.SCAN
    val glitch = expression == VlLedExpression.GLITCH
    val left = if (blink) BlinkEye else (if (scan) scanEye(pupilX, pupilY) else (Eyes[expression] ?: BlinkEye))
    val right = if (blink) BlinkEye else (if (scan) scanEye(4 - pupilX, pupilY) else left)
    val mouth = Mouths[expression]
    val rows = ArrayList<String>(12)

    for (r in 0 until 12) {
        val sb = StringBuilder(32)
        for (c in 0 until 32) {
            var lit = false
            if (r in 1..6) {
                if (c in 4..10) lit = left[r - 1][c - 4] == '#'
                if (c in 21..27) lit = right[r - 1][6 - (c - 21)] == '#'
                if (glitch && !blink && (tick % 2 != 0) && r <= 5 && ((c in 4..10) || (c in 21..27))) lit = !lit
                if (expression == VlLedExpression.COOL && !blink && r == 1 && c in 11..20) lit = true
            }
            if (r >= 8) {
                if (mouth != null) {
                    val w = mouth[0].length
                    val x = c - (32 - w) / 2
                    val line = if (r - 8 < mouth.size) mouth[r - 8] else null
                    if (line != null && x in 0 until w) lit = line[x] == '#'
                } else if (c in 2..29) {
                    val targetY = 8 + kotlin.math.abs((c - 2 + mouthShift + (if (glitch) tick else 0)) % 6 - 3)
                    if (r == targetY) {
                        lit = !glitch || ((c * 31 + tick * 17) % 7) > 1
                    }
                }
            }
            if (expression == VlLedExpression.CRY && !blink && r >= 7) {
                val drop = 7 + (tick % 6)
                if ((c == 5 && (r == drop || r == drop - 1)) || (c == 26 && (r == 7 + ((tick + 3) % 6) || r == 6 + ((tick + 3) % 6)))) {
                    lit = true
                }
            }
            sb.append(if (lit) '#' else '.')
        }
        rows.add(sb.toString())
    }
    return rows
}

/**
 * Кадр перехода между выражениями за долю [t]: лишние точки сначала гаснут, потом
 * загораются новые — в псевдослучайном, но детерминированном порядке.
 */
fun vlLedDissolve(from: List<String>, to: List<String>, t: Float): List<String> {
    val clampedT = t.coerceIn(0f, 1f)
    if (clampedT >= 1f) return to
    if (clampedT <= 0f) return from
    val rows = ArrayList<String>(to.size)
    for (y in to.indices) {
        val toRow = to[y]
        val fromRow = if (y < from.size) from[y] else ""
        val sb = StringBuilder(toRow.length)
        for (x in toRow.indices) {
            val a = x < fromRow.length && fromRow[x] == '#'
            val b = toRow[x] == '#'
            if (a == b) {
                sb.append(if (a) '#' else '.')
            } else {
                val h = ((x * 7919 + y * 104729) % 97) / 97f
                val lit = if (a) clampedT < h * 0.5f else clampedT > 0.5f + h * 0.5f
                sb.append(if (lit) '#' else '.')
            }
        }
        rows.add(sb.toString())
    }
    return rows
}

/**
 * Расчет непрерывной яркости светодиода с люминофорной инерцией при смене выражения:
 * Затухающие точки плавно угасают с экспоненциальным шлейфом, новые плавно разгораются.
 */
fun vlLedSmoothTransitionIntensity(
    from: List<String>?,
    to: List<String>,
    progress: Float,
    x: Int,
    y: Int,
): Float {
    val toLine = to.getOrNull(y) ?: ""
    val toLit = x < toLine.length && toLine[x] == '#'
    if (from == null || progress >= 1f) {
        return if (toLit) 1f else 0f
    }
    val fromLine = from.getOrNull(y) ?: ""
    val fromLit = x < fromLine.length && fromLine[x] == '#'

    if (fromLit && toLit) return 1f
    if (!fromLit && !toLit) return 0f

    val p = progress.coerceIn(0f, 1f)
    val phase = ((x * 7919 + y * 104729) % 97) / 97f

    return if (fromLit && !toLit) {
        // Угасание точки: инерция затухания люминофора
        val startDecay = phase * 0.20f
        val endDecay = (startDecay + 0.70f).coerceAtMost(1f)
        if (p <= startDecay) 1f
        else if (p >= endDecay) 0f
        else {
            val localT = (p - startDecay) / (endDecay - startDecay)
            (1f - localT) * (1f - localT)
        }
    } else {
        // Разгорание точки: плавный прогрев кристалла
        val startRise = (phase * 0.25f + 0.15f).coerceAtMost(0.75f)
        val endRise = 1f
        if (p <= startRise) 0f
        else {
            val localT = (p - startRise) / (endRise - startRise)
            localT * (2f - localT)
        }
    }
}

fun vlLedFaceHaptic(e: VlLedExpression): HapticType? = when (e) {
    VlLedExpression.HAPPY -> HapticType.CLICK
    VlLedExpression.BOOP -> HapticType.BOOP
    VlLedExpression.HEART -> HapticType.HEARTBEAT
    VlLedExpression.WIDE -> HapticType.POP
    VlLedExpression.SLEEPY -> HapticType.SIGH
    VlLedExpression.SCAN -> null
    VlLedExpression.CAT -> HapticType.BOOP
    VlLedExpression.COOL -> HapticType.POP
    VlLedExpression.GLITCH -> HapticType.THUD
    VlLedExpression.CRY -> HapticType.SIGH
    VlLedExpression.ANGRY -> HapticType.THUD
}

/**
 * Контроллер для кратковременных вспышек эмоций на лице.
 */
class VlLedFaceController {
    var flashExpression by mutableStateOf<VlLedExpression?>(null)
        private set
    var flashDurationMs by mutableLongStateOf(1000L)
        private set
    var flashHaptic by mutableStateOf(true)
        private set
    var flashTrigger by mutableLongStateOf(0L)
        private set

    private var nextTrigger = 0L

    fun flash(expression: VlLedExpression, durationMs: Long = 1000L, haptic: Boolean = true) {
        flashExpression = expression
        flashDurationMs = durationMs
        flashHaptic = haptic
        flashTrigger = ++nextTrigger
    }

    internal fun clearFlash() {
        flashExpression = null
    }
}

@Composable
fun rememberLedFaceController(): VlLedFaceController = remember { VlLedFaceController() }

/**
 * LED-мордочка 32×12 светодиодов.
 * Автоморгание раз в 3-5 с, dissolve-переход 120 мс при смене выражения,
 * интерактивный тап («буп»), перебор эмоций и тактильный отклик через HapticHelper.
 */
@Composable
fun VlLedFace(
    modifier: Modifier = Modifier,
    expression: VlLedExpression = VlLedExpression.HAPPY,
    interactive: Boolean = false,
    dot: Dp = 3.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
    controller: VlLedFaceController? = null,
    gaze: Offset? = null,
    autoBlink: Boolean = true,
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val haptic = rememberHaptic()

    var activeCycled by remember { mutableStateOf<VlLedExpression?>(null) }
    var activeCycledTrigger by remember { mutableLongStateOf(0L) }
    val currentBase = activeCycled ?: expression
    val activeFlash = controller?.flashExpression
    val targetExpr = activeFlash ?: currentBase

    // Автоматический возврат к базовому статусу через 3.5 секунды после тапа
    LaunchedEffect(activeCycledTrigger) {
        if (activeCycledTrigger > 0L && activeCycled != null) {
            delay(3500)
            activeCycled = null
        }
    }

    // При внешнем изменении базового выражения (сеть, синхронизация) сбрасываем локальный цикл
    LaunchedEffect(expression) {
        activeCycled = null
    }

    var blink by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    // Контроллер flash: сброс по таймеру
    LaunchedEffect(controller?.flashTrigger) {
        val flash = controller?.flashExpression ?: return@LaunchedEffect
        if (controller.flashHaptic) {
            vlLedFaceHaptic(flash)?.let { h -> haptic.perform(h) }
        }
        delay(controller.flashDurationMs)
        controller.clearFlash()
    }

    // Анимационный тик для бегающих глаз, слез и глитча
    if (!still && (targetExpr == VlLedExpression.SCAN || targetExpr == VlLedExpression.CRY || targetExpr == VlLedExpression.GLITCH)) {
        LaunchedEffect(targetExpr) {
            val step = if (targetExpr == VlLedExpression.GLITCH) 90L else 160L
            while (isActive) {
                delay(step)
                tick++
            }
        }
    }

    // Расчет зрачков для scan
    val (pupilX, pupilY) = remember(targetExpr, gaze, tick) {
        if (targetExpr != VlLedExpression.SCAN) 2 to 1
        else if (gaze != null) {
            val px = (2 + gaze.x.coerceIn(-1f, 1f) * 2).toInt()
            val py = (1.5f + gaze.y.coerceIn(-1f, 1f) * 1.5f).toInt()
            px to py
        } else {
            val period = 16
            val p = (tick % period) / period.toFloat()
            val px = ((if (p < 0.5f) p * 2f else 2f - p * 2f) * 4).toInt()
            px to 1
        }
    }

    // Автоморгание раз в 3–5 секунд
    if (autoBlink && !still) {
        LaunchedEffect(targetExpr) {
            while (isActive) {
                val nextDelay = 3000L + Random.nextInt(2000)
                delay(nextDelay)
                if (targetExpr != VlLedExpression.SLEEPY) {
                    blink = true
                    delay(140)
                    blink = false
                }
            }
        }
    }

    // Синхронное управление кадрами перехода:
    // Исключает гонку рендеринга (1-кадровую вспышку целевого кадра перед анимацией)
    var activeFromRows by remember { mutableStateOf<List<String>?>(null) }
    var activeToRows by remember {
        mutableStateOf(vlLedFaceRows(expression = targetExpr, pupilX = pupilX, pupilY = pupilY, blink = blink, tick = tick))
    }
    var transitionProgress by remember { mutableFloatStateOf(1f) }

    var lastRenderedExpr by remember { mutableStateOf(targetExpr) }
    var lastRenderedBlink by remember { mutableStateOf(blink) }

    if (lastRenderedExpr != targetExpr || lastRenderedBlink != blink) {
        val nextTarget = vlLedFaceRows(expression = targetExpr, pupilX = pupilX, pupilY = pupilY, blink = blink, tick = tick)
        if (!still) {
            // Если предыдущий переход был в процессе — берем текущее промежуточное состояние
            activeFromRows = if (activeFromRows != null && transitionProgress < 1f) {
                List(12) { y ->
                    val sb = java.lang.StringBuilder(32)
                    for (x in 0 until 32) {
                        val v = vlLedSmoothTransitionIntensity(activeFromRows, activeToRows, transitionProgress, x, y)
                        sb.append(if (v > 0.45f) '#' else '.')
                    }
                    sb.toString()
                }
            } else {
                activeToRows
            }
            activeToRows = nextTarget
            transitionProgress = 0f
            lastRenderedExpr = targetExpr
            lastRenderedBlink = blink
        } else {
            activeFromRows = null
            activeToRows = nextTarget
            transitionProgress = 1f
            lastRenderedExpr = targetExpr
            lastRenderedBlink = blink
        }
    } else if (transitionProgress >= 1f) {
        // В покое синхронно обновляем тики (зрачки scan, капли cry, глитч)
        activeToRows = vlLedFaceRows(expression = targetExpr, pupilX = pupilX, pupilY = pupilY, blink = blink, tick = tick)
    }

    // Плавное приращение прогресса перехода (люминофорная инерция)
    LaunchedEffect(lastRenderedExpr, lastRenderedBlink) {
        if (transitionProgress < 1f) {
            val startTime = System.currentTimeMillis()
            val duration = if (lastRenderedBlink) 100f else 220f
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed / duration).coerceIn(0f, 1f)
                transitionProgress = p
                if (p >= 1f) break
                delay(16)
            }
            activeFromRows = null
            transitionProgress = 1f
        }
    }

    val order = remember {
        listOf(
            VlLedExpression.HAPPY,
            VlLedExpression.BOOP,
            VlLedExpression.HEART,
            VlLedExpression.WIDE,
            VlLedExpression.SLEEPY,
            VlLedExpression.CAT,
            VlLedExpression.COOL,
            VlLedExpression.GLITCH,
            VlLedExpression.CRY,
            VlLedExpression.ANGRY,
        )
    }

    val clickModifier = if (interactive) {
        Modifier
            .semantics {
                this.contentDescription = "LED-мордочка"
                this.role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val currentIndex = order.indexOf(currentBase)
                val next = order[(currentIndex + 1) % order.size]
                activeCycled = next
                activeCycledTrigger++
                vlLedFaceHaptic(next)?.let { h -> haptic.perform(h) }
            }
    } else Modifier

    VlLedMatrix(
        rows = activeToRows,
        dot = dot,
        gap = gap,
        color = color,
        modifier = modifier.then(clickModifier),
        intensity = { x, y ->
            vlLedSmoothTransitionIntensity(activeFromRows, activeToRows, transitionProgress, x, y)
        }
    )
}

// ── VlLedDigits ─────────────────────────────────────────────────────────────

private val DigitGlyphs = mapOf(
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
    '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("###", "..#", "###", "#..", "###"),
    '3' to listOf("###", "..#", ".##", "..#", "###"),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
    '5' to listOf("###", "#..", "###", "..#", "###"),
    '6' to listOf("###", "#..", "###", "#.#", "###"),
    '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"),
    '9' to listOf("###", "#.#", "###", "..#", "###"),
    '+' to listOf("...", ".#.", "###", ".#.", "..."),
)

fun vlLedDigitRows(text: String): List<String> {
    val glyphs = text.mapNotNull { DigitGlyphs[it] }
    if (glyphs.isEmpty()) return List(5) { "" }
    return List(5) { r -> glyphs.joinToString(".") { it[r] } }
}

/**
 * Компактные цифры 3×5 для счетчиков и бейджей.
 */
@Composable
fun VlLedDigits(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 2.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
) {
    val rows = remember(text) { vlLedDigitRows(text) }
    VlLedMatrix(
        rows = rows,
        dot = dot,
        gap = gap,
        color = color,
        contentDescription = text,
        modifier = modifier,
    )
}

// ── VlLedTyping ─────────────────────────────────────────────────────────────

/**
 * Индикатор статуса «печатает»: бегущая волна из 3 светодиодов.
 */
@Composable
fun VlLedTyping(
    modifier: Modifier = Modifier,
    dot: Dp = 4.dp,
    color: Color? = null,
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val ink = rememberLedInk(color)

    val transition = rememberInfiniteTransition(label = "vl_led_typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dot * 0.75f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            val alpha = if (still) 1f else {
                val v = (phase - i / 3f + 1f) % 1f
                0.18f + 0.82f * (if (v < 0.5f) v * 2f else (1f - v) * 2f)
            }
            Box(
                modifier = Modifier
                    .size(dot)
                    .drawBehind {
                        if (alpha > 0.6f) {
                            val r = this.size.minDimension / 2f
                            val blurPx = (dot * 1.5f).toPx()
                            val sigma = blurPx * 0.57735f + 0.5f
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.FILL
                                    this.color = ink.on.copy(alpha = alpha * 0.8f).toArgb()
                                    maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
                                }
                                canvas.nativeCanvas.drawCircle(center.x, center.y, r, paint)
                            }
                        }
                    }
                    .clip(CircleShape)
                    .background(ink.on.copy(alpha = alpha))
            )
        }
    }
}
