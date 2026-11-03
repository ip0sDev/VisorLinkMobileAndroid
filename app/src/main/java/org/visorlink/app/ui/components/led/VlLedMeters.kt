package org.visorlink.app.ui.components.led

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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
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
 * Прогресс рядом точек на всю доступную ширину.
 * [value] = null — бегущий неопределённый индикатор.
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
            val start = if (value != null) {
                0
            } else if (still) {
                (cols - block) / 2
            } else {
                ((cols + block) * animOffset).toInt() - block
            }
            val end = if (value != null) {
                (value.coerceIn(0f, 1f) * cols).roundToInt()
            } else {
                start + block
            }

            vlPaintLedDots(
                cols = cols,
                rows = 1,
                dotPx = dotPx,
                gapPx = gapPx,
                ink = ink,
                lit = { x, _ -> x in start until end }
            )
        }
    }
}

// ── VlLedSpinner ────────────────────────────────────────────────────────────

/**
 * Загрузчик-«змейка»: бежит по кольцу из 12 точек, хвост затухает.
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
        val tail = 6
        val dotPx = size.toPx() * 0.16f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = (this.size.minDimension - dotPx) / 2f

        fun posAt(i: Int): Offset {
            val angle = -PI.toFloat() / 2f + (i * 2f * PI.toFloat() / n)
            return center + Offset(cos(angle) * radius, sin(angle) * radius)
        }

        val head = if (value != null || still) -1 else (floor(anim * n).toInt() % n)
        val litCount = if (value != null) (value.coerceIn(0f, 1f) * n).roundToInt() else null

        fun level(i: Int): Float {
            if (litCount != null) return if (i < litCount) 1f else 0f
            if (still) return 1f
            val k = (head - i + n) % n
            return if (k < tail) 1f - (k.toFloat() / tail) else 0f
        }

        val offPts = ArrayList<Offset>()
        val onPts = ArrayList<Offset>()
        for (i in 0 until n) {
            val pt = posAt(i)
            if (level(i) > 0f) onPts.add(pt) else offPts.add(pt)
        }

        if (offPts.isNotEmpty()) {
            drawPoints(
                points = offPts,
                pointMode = PointMode.Points,
                color = ink.off,
                strokeWidth = dotPx,
                cap = StrokeCap.Round,
            )
        }

        for (i in 0 until n) {
            val l = level(i)
            if (l > 0f) {
                drawPoints(
                    points = listOf(posAt(i)),
                    pointMode = PointMode.Points,
                    color = lerp(ink.off, ink.on, l),
                    strokeWidth = dotPx,
                    cap = StrokeCap.Round,
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
