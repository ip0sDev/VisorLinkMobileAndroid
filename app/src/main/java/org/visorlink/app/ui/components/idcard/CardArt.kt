package org.visorlink.app.ui.components.idcard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.ui.theme.ART_H
import org.visorlink.app.ui.theme.ART_W
import org.visorlink.app.ui.theme.IdCardMaterials
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Графика ID-карты — порт src/components/idcard/cardArt.jsx. Всё детерминировано seed'ом
 * и рисуется в координатах SVG-источника (viewBox 856×540 и т. п.), поэтому числа
 * совпадают с вебом один в один. Порядок вызовов генератора — как в вебе.
 */
private const val TAU = (PI * 2).toFloat()

internal val PAW_PATH: Path by lazy {
    PathParser().parsePathString(
        "M0 6c-5.2 0-9.4 3.6-9.4 7.2 0 2.6 2.4 3.9 4.8 3.9 1.8 0 3-.9 4.6-.9s2.8.9 4.6.9c2.4 0 4.8-1.3 4.8-3.9C9.4 9.6 5.2 6 0 6zM-10.6 4.4a2.9 3.7-18 1 0 1.2-7.3 2.9 3.7-18 0 0-1.2 7.3zM-4 -1a3 3.9-6 1 0 .8-7.8 3 3.9-6 0 0-.8 7.8zM4 -1a3 3.9 6 1 0-.8-7.8 3 3.9 6 0 0 .8 7.8zM10.6 4.4a2.9 3.7 18 1 0-1.2-7.3 2.9 3.7 18 0 0 1.2 7.3z",
    ).toPath()
}

internal fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private fun polyline(pts: List<Offset>): Path = Path().apply {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

/**
 * Прорисовка пути при выдаче (`stroke-dasharray` с `pathLength="1"`): [progress] 0…1 —
 * какая доля каждого пути уже нарисована.
 */
private fun DrawScope.drawPartial(path: Path, color: Color, stroke: Stroke, progress: Float) {
    if (progress >= 1f) {
        drawPath(path, color, style = stroke)
        return
    }
    if (progress <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * progress, part, true)
    drawPath(part, color, style = stroke)
}

// ── Standard: гильош — кольца-волны и волнистые линии, как на банкнотах ──

internal class GuillocheArt(g: IdCardGenerator.Guilloche, w: IdCardGenerator.Waves) {
    val rings: List<Path> = List(g.rings) { k ->
        val base = g.r0 + k * 9.0
        val amp = 12 + k * 0.5
        val steps = g.n * 12
        polyline(List(steps + 1) { i ->
            val t = (i.toDouble() / steps) * PI * 2
            val r = base * (1 + 0.05 * sin(g.m * t)) + amp * sin(g.n * t + k * g.phase * 2)
            Offset((g.cx + r * cos(t)).toFloat(), (g.cy + r * sin(t)).toFloat())
        })
    }
    val lines: List<Path> = List(10) { j ->
        val pts = ArrayList<Offset>()
        var x = -8
        while (x <= ART_W + 8) {
            val y = 436 + j * 7 + w.amp * sin((x / ART_W.toDouble()) * PI * 2 * w.freq + w.phase + j * 0.38)
            pts += Offset(x.toFloat(), y.toFloat())
            x += 8
        }
        polyline(pts)
    }

    fun draw(scope: DrawScope, line: Color, line2: Color, progress: Float) = with(scope) {
        val a = Stroke(width = 0.9f)
        val b = Stroke(width = 1.1f)
        rings.forEach { drawPartial(it, line, a, progress) }
        lines.forEach { drawPartial(it, line2, b, progress) }
    }
}

// ── Protogen: печатные дорожки с переходными отверстиями ──

private val ROT45 = mapOf(
    (1 to 0) to (1 to 1), (1 to 1) to (0 to 1), (0 to 1) to (-1 to 1), (-1 to 1) to (-1 to 0),
    (-1 to 0) to (-1 to -1), (-1 to -1) to (0 to -1), (0 to -1) to (1 to -1), (1 to -1) to (1 to 0),
)

private fun turn(dir: Pair<Int, Int>, cw: Boolean): Pair<Int, Int> =
    if (cw) ROT45.getValue(dir) else ROT45.entries.first { it.value == dir }.key

internal class CircuitsArt(seed: Long) {
    val traces = ArrayList<Path>()
    /** (x, y, r) */
    val vias = ArrayList<Triple<Float, Float, Float>>()

    init {
        val random = IdCardGenerator.rng(seed xor 0xC1C)
        val g = 12.0
        fun snap(n: Double) = IdCardGenerator.jsRound(n / g) * g
        repeat(13) {
            val side = listOf("right", "right", "bottom", "bottom", "top")[floor(random() * 5).toInt()]
            var x: Double
            var y: Double
            var dir: Pair<Int, Int>
            when (side) {
                "right" -> { x = ART_W.toDouble(); y = snap(60 + random() * (ART_H - 120)); dir = -1 to 0 }
                "bottom" -> { x = snap(ART_W * 0.32 + random() * ART_W * 0.62); y = ART_H.toDouble(); dir = 0 to -1 }
                else -> { x = snap(ART_W * 0.5 + random() * ART_W * 0.45); y = 0.0; dir = 0 to 1 }
            }
            val lanes = if (random() < 0.35) 3 else 1
            val segs = 2 + floor(random() * 3).toInt()
            val cw = random() < 0.5
            val pts = arrayListOf(x to y)
            for (s in 0 until segs) {
                val diag = dir.first != 0 && dir.second != 0
                val len = g * if (diag) 2 + floor(random() * 4) else 3 + floor(random() * 9)
                x = max(g, min(ART_W - g, x + dir.first * len))
                y = max(g, min(ART_H - g, y + dir.second * len))
                pts += x to y
                dir = turn(dir, if (s % 2 == 0) cw else !cw)
            }
            val perp = if (side == "right") 0 to 1 else 1 to 0
            for (l in 0 until lanes) {
                val off = (l - (lanes - 1) / 2.0) * 9
                val moved = pts.map { (px, py) -> Offset((px + perp.first * off).toFloat(), (py + perp.second * off).toFloat()) }
                traces += polyline(moved)
                val end = moved.last()
                vias += Triple(end.x, end.y, if (l == 0 && lanes == 1) 5f else 3.4f)
            }
        }
    }

    fun draw(scope: DrawScope, line: Color, progress: Float) = with(scope) {
        val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        traces.forEach { drawPartial(it, line, stroke, progress) }
        vias.forEach { (cx, cy, r) ->
            drawCircle(IdCardMaterials.Via, r, Offset(cx, cy))
            drawCircle(line, r, Offset(cx, cy), style = Stroke(1.7f))
        }
    }
}

// ── Beast: окрас — мех, пятна, полосы, крапины, следы ──

private fun brush(points: List<Offset>, width: Double): Path {
    // Мазок с сужением к концу: контур слева + справа в обратном порядке
    val left = ArrayList<Offset>()
    val right = ArrayList<Offset>()
    points.forEachIndexed { i, p ->
        val n = points[min(points.size - 1, i + 1)]
        val pr = points[max(0, i - 1)]
        val dx = n.x - pr.x
        val dy = n.y - pr.y
        val len = hypot(dx, dy).takeIf { it != 0f } ?: 1f
        val w = (width * (1.0 - i.toDouble() / (points.size - 1)).pow(0.8)).toFloat()
        left += Offset(p.x - (dy / len) * w, p.y + (dx / len) * w)
        right += Offset(p.x + (dy / len) * w, p.y - (dx / len) * w)
    }
    return polyline(left + right.asReversed()).apply { close() }
}

internal class CoatArt(seed: Long, val coat: Int) {
    /** Мех — штрихи. */
    val fur = Path()
    /** Заливки (пятна, полосы, крапины, следы) уже в итоговых координатах. */
    val fills = ArrayList<Path>()

    init {
        val random = IdCardGenerator.rng(seed xor 0xBEA57)
        val w = ART_W.toDouble()
        val h = ART_H.toDouble()
        fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, rotDeg: Double): Path =
            android.graphics.Path().apply {
                addOval(android.graphics.RectF(f1(cx - rx), f1(cy - ry), f1(cx + rx), f1(cy + ry)), android.graphics.Path.Direction.CW)
                transform(android.graphics.Matrix().apply { setRotate(f1(rotDeg), f1(cx), f1(cy)) })
            }.asComposePath()
        when (coat) {
            0 -> repeat(260) {
                val x = w * 0.3 + random() * w * 0.72
                val y = random() * h
                val a = -0.6 + sin(x / 140 + y / 210) * 0.5
                val l = 10 + random() * 14
                // Округление до десятых — как f1() в вебе: штрихи совпадают и по координатам
                val sx = f1(x); val sy = f1(y)
                fur.moveTo(sx, sy)
                fur.relativeQuadraticTo(f1(cos(a) * l * 0.5 + 3), f1(sin(a) * l * 0.5), f1(cos(a) * l), f1(sin(a) * l))
            }
            1 -> {
                repeat(24) {
                    val cx = w * 0.34 + random() * w * 0.7
                    val cy = random() * h
                    val r = 10 + random() * 8
                    val petals = 3 + floor(random() * 3).toInt()
                    val start = random() * PI * 2
                    for (k in 0 until petals) {
                        val a = start + (k.toDouble() / petals) * PI * 2 + (random() - 0.5) * 0.5
                        val rr = r * (0.85 + random() * 0.3)
                        val px = cx + cos(a) * rr
                        val py = cy + sin(a) * rr
                        val rx = 4 + random() * 4
                        val ry = 2.4 + random() * 1.6
                        fills += ellipse(px, py, rx, ry, (a * 180) / PI + 90)
                    }
                }
                repeat(30) {
                    val cx = w * 0.32 + random() * w * 0.72
                    val cy = random() * h
                    val r = 2 + random() * 3
                    fills += ellipse(cx, cy, r, r, 0.0)
                }
            }
            2 -> repeat(11) { i ->
                val top = i % 2 == 0
                val x0 = w * 0.36 + (i / 11.0) * w * 0.66 + random() * 20
                val len = 90 + random() * 120
                val bend = (random() - 0.5) * 70
                val pts = List(9) { k ->
                    val t = k / 8.0
                    Offset((x0 + sin(t * PI) * bend + t * 24).toFloat(), (if (top) t * len else h - t * len).toFloat())
                }
                fills += brush(pts, 9 + random() * 7)
            }
            3 -> for (row in 0 until 3) for (i in 0 until 9) {
                val cx = w * 0.4 + i * 58 + random() * 24 + row * 18
                val cy = 110.0 + row * 130 + i * 9 + random() * 26
                val rx = 7 + random() * 6
                val ry = 5 + random() * 4
                fills += ellipse(cx, cy, rx, ry, random() * 60 - 30)
            }
            else -> {
                // Цепочка следов по диагонали
                val steps = 7
                val a = -0.42 + random() * 0.2
                for (i in 0 until steps) {
                    val t = i / (steps - 1.0)
                    val side = if (i % 2 == 0) -1 else 1
                    val cx = w * 0.36 + t * w * 0.6 - sin(a) * side * 22
                    val cy = h * 0.86 + sin(a) * t * w * 0.6 + cos(a) * side * 22
                    // translate(cx cy) rotate(a+90) scale(1.6): сначала масштаб, затем поворот и сдвиг
                    val m = android.graphics.Matrix().apply {
                        setScale(1.6f, 1.6f)
                        postRotate(f1((a * 180) / PI + 90))
                        postTranslate(f1(cx), f1(cy))
                    }
                    fills += android.graphics.Path(PAW_PATH.asAndroidPath()).apply { transform(m) }.asComposePath()
                }
            }
        }
    }

    fun draw(scope: DrawScope, line2: Color) = with(scope) {
        if (coat == 0) drawPath(fur, line2, style = Stroke(1.5f, cap = StrokeCap.Round))
        fills.forEach { drawPath(it, line2) }
    }
}

/** `f1` из cardStyle.js: округление до десятых. */
private fun f1(n: Double): Float = (IdCardGenerator.jsRound(n * 10) / 10.0).toFloat()

// ── Protogen: LED-визор 30×10 — глаза (зеркально) и рот ──

private val EYES = listOf(
    listOf(".####...", "######..", "#######.", "########", ".#######", "...####."), // обычный
    listOf("...##...", "..####..", ".##..##.", "##....##", "#......#", "........"), // ^ ^
    listOf(".##..##.", "########", "########", ".######.", "..####..", "...##..."), // сердечки
    listOf("##......", ".###....", "...###..", "...###..", ".###....", "##......"), // > <
    listOf("..####..", ".##..##.", "##....##", "##....##", ".##..##.", "..####.."), // o o
    listOf("........", "........", "########", ".######.", "..####..", "........"), // сонный
    listOf("##......", "####....", "######..", "########", ".#######", "..#####."), // сердитый
    listOf("##....##", ".##..##.", "..####..", "..####..", ".##..##.", "##....##"), // x x
)
private val MOUTH_ZIG = listOf("#...#..", ".#.#.#.", "..#...#")
private val MOUTH_W = listOf("#......", ".#....#", "..####.")
private val HAPPY_FACES = setOf(1, 2, 4)

/** Точки визора в сетке 30×10: левый верхний угол каждой точки. */
internal fun ledDots(face: Int): List<Offset> {
    val cols = 30
    val dots = ArrayList<Offset>()
    (EYES.getOrNull(face) ?: EYES[0]).forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == '#') {
                dots += Offset((x + 1).toFloat(), y.toFloat())
                dots += Offset((cols - 2 - x).toFloat(), y.toFloat())
            }
        }
    }
    val mouth = if (face in HAPPY_FACES) MOUTH_W else MOUTH_ZIG
    mouth.forEachIndexed { y, row ->
        val full = row + row.reversed()
        full.forEachIndexed { x, c -> if (c == '#') dots += Offset((8 + x).toFloat(), (7 + y).toFloat()) }
    }
    return dots
}

// ── Голограмма: формы-маски в квадрате 100×100 ──

private fun starPoints(n: Int, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until n * 2) {
        val r = if (i % 2 == 1) inner else outer
        val a = (i.toFloat() / (n * 2)) * TAU - (PI / 2).toFloat()
        val x = (50 + cos(a) * r)
        val y = (50 + sin(a) * r)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun rosettePoints(): Path = Path().apply {
    for (i in 0 until 160) {
        val a = (i / 160f) * TAU
        val r = 44 + 5 * abs(cos(8 * a))
        if (i == 0) moveTo(50 + cos(a) * r, 50 + sin(a) * r) else lineTo(50 + cos(a) * r, 50 + sin(a) * r)
    }
    close()
}

internal fun holoShapePath(shape: IdCardGenerator.HoloShape): Path = when (shape) {
    IdCardGenerator.HoloShape.HEX -> svgPath("M50 2L92 26L92 74L50 98L8 74L8 26Z")
    IdCardGenerator.HoloShape.SHIELD -> svgPath("M50 3L91 15V47C91 73 73 90 50 97 27 90 9 73 9 47V15Z")
    IdCardGenerator.HoloShape.STAR -> starPoints(8, 49f, 36f)
    IdCardGenerator.HoloShape.ROSETTE -> rosettePoints()
}

private fun hypotrochoid(bigR: Int, r: Int, d: Double, scale: Double, cx: Double, cy: Double): Path {
    fun gcd(a: Int, b: Int): Int = if (b != 0) gcd(b, a % b) else a
    val loops = r / gcd(bigR, r).toDouble()
    val steps = IdCardGenerator.jsRound((bigR / gcd(bigR, r).toDouble()) * 22).toInt()
    return polyline(List(steps + 1) { i ->
        val t = (i.toDouble() / steps) * PI * 2 * loops
        Offset(
            (cx + scale * ((bigR - r) * cos(t) + d * cos(((bigR - r).toDouble() / r) * t))).toFloat(),
            (cy + scale * ((bigR - r) * sin(t) - d * sin(((bigR - r).toDouble() / r) * t))).toFloat(),
        )
    })
}

private val EMBLEM_ROSETTE: Path by lazy { hypotrochoid(96, 36, 30.0, 0.42, 50.0, 50.0) }
private val EMBLEM_VISOR: Path by lazy {
    Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(-17f, -10f, 17f, 10f, 8f, 8f))
        addPath(svgPath("M-9-3.5l4.5 3.5-4.5 3.5M9-3.5L4.5 0 9 3.5"))
    }
}

/** Тиснёная эмблема голограммы в квадрате 100×100: тёмный оттиск со сдвигом и светлый. */
internal fun DrawScope.drawHoloEmblem(mode: IdMode, brandTypeface: Typeface?) {
    fun art(color: Color) {
        val stroke = Stroke(1.3f)
        drawCircle(color, 44f, Offset(50f, 50f), style = stroke)
        drawCircle(color, 38.5f, Offset(50f, 50f), style = stroke)
        drawPath(EMBLEM_ROSETTE, color, style = Stroke(0.6f))
        when (mode) {
            IdMode.PROTOGEN -> translate(50f, 50f) { drawPath(EMBLEM_VISOR, color, style = stroke) }
            IdMode.BEAST -> translate(50f, 49f) { scale(1.05f, pivot = Offset.Zero) { drawPath(PAW_PATH, color) } }
            IdMode.STANDARD -> drawIntoCanvas {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    textSize = 21f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(brandTypeface ?: Typeface.DEFAULT, Typeface.BOLD)
                }
                it.nativeCanvas.drawText("VL", 50f, 57f, paint)
            }
        }
    }
    translate(0.8f, 0.9f) { art(Color.Black.copy(alpha = 0.28f)) }
    art(Color.White.copy(alpha = 0.75f))
}

// ── «Скрытое изображение» фольги аврора / галактика (viewBox 856×540) ──

internal class RevealArt(galaxy: Boolean, seed: Long) {
    val fill = Path()
    val strokes = Path()

    init {
        val random = IdCardGenerator.rng(seed xor 0xA0A)
        if (galaxy) {
            repeat(70) {
                val x = (random() * ART_W).toFloat()
                val y = (random() * ART_H).toFloat()
                val s = (if (random() < 0.12) 9 + random() * 8 else 2 + random() * 4).toFloat()
                // Четырёхлучевая искра из квадратичных кривых
                fill.moveTo(x, y - s)
                fill.quadraticTo(x + s * 0.16f, y - s * 0.16f, x + s, y)
                fill.quadraticTo(x + s * 0.16f, y + s * 0.16f, x, y + s)
                fill.quadraticTo(x - s * 0.16f, y + s * 0.16f, x - s, y)
                fill.quadraticTo(x - s * 0.16f, y - s * 0.16f, x, y - s)
                fill.close()
            }
            repeat(160) {
                val cx = (random() * ART_W).toFloat()
                val cy = (random() * ART_H).toFloat()
                val r = (0.8 + random() * 1.4).toFloat()
                fill.addOval(Rect(Offset(cx, cy), r))
            }
        } else {
            val ph = random() * PI * 2
            val fr = 1.2 + random() * 1.2
            for (j in 0 until 11) {
                var x = -10
                var first = true
                while (x <= ART_W + 10) {
                    val y = (50 + j * 42 + 26 * sin((x / ART_W.toDouble()) * PI * 2 * fr + ph + j * 0.32)).toFloat()
                    if (first) strokes.moveTo(x.toFloat(), y) else strokes.lineTo(x.toFloat(), y)
                    first = false
                    x += 12
                }
            }
        }
    }
}

// ── Значок режима (24×24): визор протогена или лапа ──

internal val GLYPH_VISOR: Path by lazy {
    Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(2.5f, 6f, 21.5f, 18f, 5f, 5f))
    }
}
internal val GLYPH_VISOR_EYES: Path by lazy { svgPath("M7 10.5l2.5 1.5L7 13.5M17 10.5L14.5 12l2.5 1.5") }

internal fun DrawScope.drawModeGlyph(mode: IdMode, color: Color) {
    val s = size.minDimension / 24f
    scale(s, s, pivot = Offset.Zero) {
        when (mode) {
            IdMode.PROTOGEN -> {
                drawPath(GLYPH_VISOR, color, style = Stroke(1.8f))
                drawPath(GLYPH_VISOR_EYES, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            IdMode.BEAST -> {
                drawOval(color, Offset(12f - 4.6f, 16f - 3.8f), Size(9.2f, 7.6f))
                drawOval(color, Offset(5.6f - 2f, 10.4f - 2.6f), Size(4f, 5.2f))
                drawOval(color, Offset(9.4f - 2f, 6.6f - 2.7f), Size(4f, 5.4f))
                drawOval(color, Offset(14.6f - 2f, 6.6f - 2.7f), Size(4f, 5.4f))
                drawOval(color, Offset(18.4f - 2f, 10.4f - 2.6f), Size(4f, 5.2f))
            }
            IdMode.STANDARD -> Unit
        }
    }
}

// ── Штампы: резиновая печать с текстом по кругу ──

internal data class StampText(val ring: String, val label: String, val date: String, val sub: String? = null)

internal enum class StampShape(val w: Float, val h: Float, val vbW: Float, val vbH: Float) {
    ROUND(5.8f, 5.8f, 100f, 100f),
    OVAL(7f, 4.6f, 140f, 92f),
    RECT(7.2f, 3.46f, 150f, 72f),
    HEX(6.6f, 5.7f, 120f, 104f),
}

internal class StampFonts(val text: Typeface, val mono: Typeface)

private fun textPaint(color: Color, size: Float, typeface: Typeface, align: Paint.Align = Paint.Align.CENTER) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = size
        this.typeface = typeface
        textAlign = align
    }

/** Текст с `textLength` + `lengthAdjust="spacingAndGlyphs"`: растягивается до ширины. */
private fun android.graphics.Canvas.fitText(text: String, cx: Float, y: Float, width: Float, paint: Paint) {
    val measured = paint.measureText(text)
    if (measured > 0f) paint.textScaleX = width / measured
    drawText(text, cx, y, paint)
    paint.textScaleX = 1f
}

/** Текст по пути с `textLength` + `lengthAdjust="spacing"`: трекинг подгоняет длину. */
private fun android.graphics.Canvas.ringText(text: String, path: android.graphics.Path, length: Float, paint: Paint) {
    paint.textAlign = Paint.Align.LEFT
    paint.letterSpacing = 0.06f
    val measured = paint.measureText(text)
    if (text.isNotEmpty()) paint.letterSpacing += (length - measured) / (text.length * paint.textSize)
    drawTextOnPath(text, path, 0f, 0f, paint)
    paint.letterSpacing = 0f
}

private fun ellipsePath(cx: Float, cy: Float, rx: Float, ry: Float) = android.graphics.Path().apply {
    // Как в SVG: старт слева, по часовой — верхняя дуга, затем нижняя
    addArc(android.graphics.RectF(cx - rx, cy - ry, cx + rx, cy + ry), 180f, 359.9f)
}

/** Рисует штамп в его viewBox (размер [StampShape.vbW]×[StampShape.vbH]) цветом [color]. */
internal fun DrawScope.drawStamp(shape: StampShape, mode: IdMode, text: StampText, fonts: StampFonts, color: Color) {
    val line = { w: Float -> Stroke(w) }
    when (shape) {
        StampShape.ROUND -> {
            drawCircle(color, 47f, Offset(50f, 50f), style = line(3.2f))
            drawCircle(color, 42.3f, Offset(50f, 50f), style = line(1.1f))
            drawCircle(color, 27.5f, Offset(50f, 50f), style = line(1.1f))
            val paw = mode == IdMode.BEAST
            if (paw) {
                translate(50f, 44f) { scale(0.95f, pivot = Offset.Zero) { drawPath(PAW_PATH, color) } }
            } else {
                drawPath(svgPath("M50 30.5l2.3 4.8 5.2.6-3.9 3.5 1 5.2L50 42l-4.6 2.6 1-5.2-3.9-3.5 5.2-.6z"), color)
            }
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.ringText(text.ring, ellipsePath(50f, 50f, 35.5f, 35.5f), 214f, textPaint(color, 8.4f, Typeface.create(fonts.text, Typeface.BOLD)))
                nc.drawText(text.date, 50f, if (paw) 66f else 57f, textPaint(color, 8.6f, fonts.mono))
                text.sub?.let { nc.drawText(it, 50f, 68f, textPaint(color, 5.4f, Typeface.create(fonts.text, Typeface.BOLD))) }
            }
        }

        StampShape.RECT -> {
            drawRoundRect(color, Offset(2f, 2f), Size(146f, 68f), androidx.compose.ui.geometry.CornerRadius(6f), style = line(3.2f))
            drawRoundRect(color, Offset(7.5f, 7.5f), Size(135f, 57f), androidx.compose.ui.geometry.CornerRadius(3f), style = line(1.1f))
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val bold = Typeface.create(fonts.text, Typeface.BOLD)
                nc.drawText("VISORLINK", 75f, 21f, textPaint(color, 8f, bold).apply { letterSpacing = 3f / 8f })
                nc.fitText(text.label, 75f, 41f, 122f, textPaint(color, 14.5f, bold))
                nc.drawText(text.date, 75f, 57f, textPaint(color, 9.5f, fonts.mono))
            }
        }

        StampShape.OVAL -> {
            drawOval(color, Offset(3f, 3f), Size(134f, 86f), style = line(3f))
            drawOval(color, Offset(8.5f, 8f), Size(123f, 76f), style = line(1f))
            drawOval(color, Offset(26f, 23f), Size(88f, 46f), style = line(1f))
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val bold = Typeface.create(fonts.text, Typeface.BOLD)
                nc.ringText(text.ring, ellipsePath(70f, 46f, 53f, 33f), 268f, textPaint(color, 7.6f, bold))
                nc.fitText(text.label, 70f, 44f, 74f, textPaint(color, 9f, bold))
                nc.drawText(text.date, 70f, 56f, textPaint(color, 8.6f, fonts.mono))
            }
        }

        StampShape.HEX -> {
            drawPath(svgPath("M31 3h58l29 49-29 49H31L2 52z"), color, style = line(3.2f))
            drawPath(svgPath("M34.5 9h51l25.5 43-25.5 43h-51L9 52z"), color, style = line(1.1f))
            repeat(6) { i -> rotate(i * 60f, pivot = Offset(60f, 52f)) { drawLine(color, Offset(60f, 15f), Offset(60f, 21f), 1.6f) } }
            drawLine(color, Offset(30f, 56f), Offset(90f, 56f), 1.1f)
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.drawText("QC", 60f, 49f, textPaint(color, 24f, fonts.mono).apply { letterSpacing = 2f / 24f })
                nc.fitText(text.label, 60f, 67f, 58f, textPaint(color, 9.5f, Typeface.create(fonts.text, Typeface.BOLD)))
                nc.drawText(text.date, 60f, 79f, textPaint(color, 7f, fonts.mono))
            }
        }
    }
}

internal fun stampShape(mode: IdMode, style: Int, small: Boolean): StampShape = when {
    small -> StampShape.RECT
    mode == IdMode.PROTOGEN -> StampShape.HEX
    mode == IdMode.BEAST -> StampShape.ROUND
    style == 1 -> StampShape.RECT
    style == 2 -> StampShape.OVAL
    else -> StampShape.ROUND
}

internal fun Float.r1() = (this * 10).roundToInt() / 10f
