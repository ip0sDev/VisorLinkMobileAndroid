package org.visorlink.app.ui.components.led

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import kotlin.math.*

// ── VlLedLevelMeter ─────────────────────────────────────────────────────────

fun vlLedBarHeight(level: Float, rows: Int): Int =
    (level.coerceIn(0f, 1f) * rows).roundToInt()

private fun isBarLit(h: Int, y: Int, rows: Int, symmetric: Boolean): Boolean {
    if (!symmetric) return y >= rows - h
    val lo = (rows - h) / 2f
    return y >= floor(lo).toInt() && y < ceil(lo + h).toInt()
}

/**
 * Спектр / индикатор уровня: столбик точек на каждое значение [levels] (0..1).
 * Градиент снизу вверх — от LED-цвета к primary.
 * [peaks] — пики падают медленнее столбиков.
 */
@Composable
fun VlLedLevelMeter(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    rows: Int = 8,
    symmetric: Boolean = false,
    peaks: Boolean = false,
    dot: Dp = 2.dp,
    gap: Dp = 1.dp,
    semanticLabel: String? = null,
) {
    val ink = rememberLedInk()
    val cs = MaterialTheme.colorScheme
    val topColor = cs.primary

    var smoothedBars by remember { mutableStateOf(levels) }
    var peakLevels by remember { mutableStateOf(levels) }

    LaunchedEffect(levels, peaks) {
        if (!peaks || levels.size != smoothedBars.size) {
            smoothedBars = levels
            peakLevels = levels
            return@LaunchedEffect
        }
        val barFall = 0.12f
        val peakFall = 0.035f
        val nextBars = ArrayList<Float>(levels.size)
        val nextPeaks = ArrayList<Float>(levels.size)
        for (i in levels.indices) {
            val prevBar = smoothedBars.getOrElse(i) { 0f }
            val prevPeak = peakLevels.getOrElse(i) { 0f }
            val b = max(levels[i], prevBar - barFall)
            val p = max(b, prevPeak - peakFall)
            nextBars.add(b)
            nextPeaks.add(p)
        }
        smoothedBars = nextBars
        peakLevels = nextPeaks
    }

    val cols = smoothedBars.size
    val semModifier = if (semanticLabel != null) {
        Modifier.semantics { this.contentDescription = semanticLabel }
    } else Modifier

    Canvas(
        modifier = modifier
            .then(semModifier)
            .size(
                width = if (cols <= 0) 0.dp else (dot + gap) * cols - gap,
                height = if (rows <= 0) 0.dp else (dot + gap) * rows - gap,
            )
    ) {
        val dotPx = dot.toPx()
        val gapPx = gap.toPx()

        val rowColors = List(rows) { y ->
            val t = if (rows <= 1) 0f else if (symmetric) {
                abs(y - (rows - 1) / 2f) / ((rows - 1) / 2f)
            } else {
                (rows - 1 - y).toFloat() / (rows - 1).toFloat()
            }
            lerp(ink.on, topColor, t.coerceIn(0f, 1f))
        }

        val barHeights = smoothedBars.map { vlLedBarHeight(it, rows) }
        val peakHeights = if (peaks) peakLevels.map { vlLedBarHeight(it, rows) } else null

        vlPaintLedDots(
            cols = cols,
            rows = rows,
            dotPx = dotPx,
            gapPx = gapPx,
            ink = ink,
            rowColor = { y -> rowColors[y] },
            lit = { x, y ->
                val h = barHeights.getOrElse(x) { 0 }
                if (isBarLit(h, y, rows, symmetric)) return@vlPaintLedDots true
                val p = peakHeights?.getOrElse(x) { 0 } ?: 0
                p > h && y == rows - p
            }
        )
    }
}

// ── VlLedProgress ───────────────────────────────────────────────────────────

/**
 * Прогресс рядом точек на всю доступную ширину с плавной субпиксельной границей.
 * [value] = null — бегущий неопределённый индикатор с плавной инерцией.
 */
@Composable
fun VlLedProgress(
    modifier: Modifier = Modifier,
    value: Float? = null,
    dot: Dp = 3.dp,
    gap: Dp = 2.dp,
    color: Color? = null,
    semanticLabel: String? = null,
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val ink = rememberLedInk(color)

    val transition = rememberInfiniteTransition(label = "vl_led_prog")
    val animOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "prog_offset"
    )

    val label = semanticLabel ?: (if (value == null) "Загрузка" else "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%")

    BoxWithConstraints(modifier = modifier.semantics { this.contentDescription = label }) {
        val totalW = maxWidth
        val dotPx = with(androidx.compose.ui.platform.LocalDensity.current) { dot.toPx() }
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { gap.toPx() }
        val stepPx = dotPx + gapPx

        val cols = if (totalW != Dp.Unspecified && totalW != Dp.Infinity && totalW > 0.dp) {
            val totalWPx = with(androidx.compose.ui.platform.LocalDensity.current) { totalW.toPx() }
            ((totalWPx + gapPx) / stepPx).toInt().coerceAtLeast(1)
        } else {
            24
        }

        Canvas(
            modifier = Modifier.size(
                width = (dot + gap) * cols - gap,
                height = dot,
            )
        ) {
            val block = max(3, cols / 6)
            val isIndeterminate = value == null
            val startF = if (!isIndeterminate) {
                0f
            } else if (still) {
                ((cols - block) / 2).toFloat()
            } else {
                (cols + block) * animOffset - block
            }
            val endF = if (!isIndeterminate) {
                value!!.coerceIn(0f, 1f) * cols
            } else {
                startF + block
            }

            vlPaintLedDots(
                cols = cols,
                rows = 1,
                dotPx = dotPx,
                gapPx = gapPx,
                ink = ink,
                intensity = { x, _ ->
                    val x0 = x.toFloat()
                    val x1 = (x + 1).toFloat()
                    val overlap = (min(x1, endF) - max(x0, startF)).coerceIn(0f, 1f)
                    overlap
                }
            )
        }
    }
}

// ── VlLedSpinner ────────────────────────────────────────────────────────────

/**
 * Загрузчик-«змейка»: бежит по кольцу из 12 точек, хвост плавно затухает по кривой люминофора.
 */
@Composable
fun VlLedSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    value: Float? = null,
    color: Color? = null,
    semanticLabel: String = "Загрузка",
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val ink = rememberLedInk(color)

    val transition = rememberInfiniteTransition(label = "vl_led_spin")
    val anim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(960, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin_head"
    )

    Canvas(
        modifier = modifier
            .semantics { this.contentDescription = semanticLabel }
            .size(size)
    ) {
        val n = 12
        val tail = 6f
        val dotPx = size.toPx() * 0.16f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = (this.size.minDimension - dotPx) / 2f

        fun posAt(i: Int): Offset {
            val angle = -PI.toFloat() / 2f + (i * 2f * PI.toFloat() / n)
            return center + Offset(cos(angle) * radius, sin(angle) * radius)
        }

        val headFloat = if (value != null || still) -1f else ((anim * n) % n)
        val litCount = if (value != null) value.coerceIn(0f, 1f) * n else null

        fun level(i: Int): Float {
            if (litCount != null) {
                val rem = litCount - i
                return rem.coerceIn(0f, 1f)
            }
            if (still) return 1f
            val dist = (headFloat - i + n) % n
            return if (dist < tail) {
                val decay = 1f - (dist / tail)
                decay * decay
            } else 0f
        }

        val r = dotPx / 2f
        val blurPx = dotPx * 1.5f
        val sigma = blurPx * 0.57735f + 0.5f
        val glowRadius = r * 1.4f

        for (i in 0 until n) {
            val pt = posAt(i)
            val l = level(i)

            // 1. Погашенная база матрицы
            drawCircle(
                color = ink.off,
                radius = r,
                center = pt
            )

            if (l > 0.02f) {
                // 2. Ореол свечения
                if (ink.glowBlur > 0.dp && l > 0.1f) {
                    drawIntoCanvas { canvas ->
                        val glowPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.FILL
                            this.color = ink.on.copy(alpha = 0.70f * l).toArgb()
                            maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawCircle(pt.x, pt.y, glowRadius, glowPaint)
                    }
                }

                // 3. Сам светодиод с инерцией
                drawCircle(
                    color = lerp(ink.off, ink.on, l),
                    radius = r * (0.86f + 0.14f * l),
                    center = pt
                )
            }
        }
    }
}

// ── VlLedIcon ───────────────────────────────────────────────────────────────

enum class VlLedIconKind { CHECK, CROSS, WARN, HEART, WIFI_OFF, MIC }

private val IconGlyphs = mapOf(
    VlLedIconKind.CHECK to listOf(".......", "......#", ".....#.", "#...#..", ".#.#...", "..#....", "......."),
    VlLedIconKind.CROSS to listOf("#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"),
    VlLedIconKind.WARN to listOf("...#...", "..#.#..", ".#.#.#.", ".#.#.#.", "#.....#", "#..#..#", "#######"),
    VlLedIconKind.HEART to listOf(".......", ".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."),
    VlLedIconKind.WIFI_OFF to listOf("######.", "##....#", "..###..", ".#.#.#.", "....#..", "...#.#.", "......#"),
    VlLedIconKind.MIC to listOf("..###..", "..###..", "#.###.#", "#.###.#", ".#...#.", "..###..", "...#..."),
)

fun vlLedIconRows(kind: VlLedIconKind): List<String> = IconGlyphs[kind] ?: List(7) { "......." }

/**
 * Пиксельная иконка 7×7 на LED-матрице для тостов, бейджей и статусов.
 */
@Composable
fun VlLedIcon(
    kind: VlLedIconKind,
    modifier: Modifier = Modifier,
    dot: Dp = 2.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
    semanticLabel: String? = null,
) {
    VlLedMatrix(
        rows = vlLedIconRows(kind),
        dot = dot,
        gap = gap,
        color = color,
        contentDescription = semanticLabel ?: kind.name,
        modifier = modifier,
    )
}

// ── VlProgressIndicator ─────────────────────────────────────────────────────

/**
 * Адаптивный индикатор загрузки: в теме Forge (или при наличии LED-токенов)
 * отображает LED-змейку VlLedSpinner, иначе Material 3 CircularProgressIndicator.
 */
@Composable
fun VlProgressIndicator(
    modifier: Modifier = Modifier,
    value: Float? = null,
    color: Color? = null,
    strokeWidth: Dp = 4.dp,
    size: Dp = 32.dp,
) {
    val tokens = VlTheme.tokens
    if (tokens.led != null || tokens.isForge) {
        VlLedSpinner(
            size = size,
            value = value,
            color = color,
            modifier = modifier,
        )
    } else {
        if (value != null) {
            CircularProgressIndicator(
                progress = { value },
                modifier = modifier.size(size),
                color = color ?: MaterialTheme.colorScheme.primary,
                strokeWidth = strokeWidth,
            )
        } else {
            CircularProgressIndicator(
                modifier = modifier.size(size),
                color = color ?: MaterialTheme.colorScheme.primary,
                strokeWidth = strokeWidth,
            )
        }
    }
}
