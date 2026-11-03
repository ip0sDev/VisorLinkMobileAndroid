package org.visorlink.app.ui.components.idcard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.ui.theme.ART_H
import org.visorlink.app.ui.theme.ART_W
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.cssRepeating
import kotlin.math.hypot
import kotlin.math.min

/**
 * Авторский узор ROOT «Корона» (`custom.pattern = "root"`) — карта администратора, единственная
 * на весь VisorLink (сервер: `SINGLE_PATTERNS`, серийник VL-ROOT-0001). Устроен аналогично [NekoArt]:
 * координаты карты 856×540 и голограммы 100×100, всё детерминировано.
 */
internal object RootArt {
    // ── Голограмма (100×100) ──

    /** Маска голограммы: щит с короной из трёх зубцов. */
    val holoMask: Path by lazy {
        svgPath("M50 97C29 90 11 76 9 52L9 26L26 40L37 9L50 30L63 9L74 40L91 26L91 52C89 76 71 90 50 97Z")
    }

    private val holoContour: Path by lazy {
        Path().apply {
            addPath(holoMask)
            transform(Matrix().apply { translate(50f, 52f); scale(0.84f, 0.84f); translate(-50f, -52f) })
        }
    }

    /** Ключ: кольцо, стержень и бородка. */
    private val emblemStrokes: Path by lazy {
        Path().apply {
            addOval(Rect(Offset(50f, 50f), 9.5f))
            addOval(Rect(Offset(50f, 50f), 4f))
            addPath(svgPath("M50 59.5V86M50 75H58M50 81H56M50 86H55"))
        }
    }

    /** Камни на зубцах короны. */
    private val emblemGems: Path by lazy {
        Path().apply {
            addOval(Rect(Offset(37.5f, 21f), 2.6f))
            addOval(Rect(Offset(62.5f, 21f), 2.6f))
            addOval(Rect(Offset(19f, 41f), 2.1f))
            addOval(Rect(Offset(81f, 41f), 2.1f))
        }
    }

    /** Тиснёный ключ в квадрате 100×100: тёмный оттиск со сдвигом и светлый. */
    fun DrawScope.drawHoloEmblem() {
        fun art(color: Color) {
            drawPath(holoContour, color, style = Stroke(1.3f))
            drawPath(emblemStrokes, color, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        translate(0.8f, 0.9f) { art(Color.Black.copy(alpha = 0.3f)) }
        art(Color.White.copy(alpha = 0.78f))
        translate(0.6f, 0.7f) { drawPath(emblemGems, Color.Black.copy(alpha = 0.28f)) }
        drawPath(emblemGems, IdCardMaterials.RootGold)
    }

    // ── Защитная сетка (856×540): короны и ключи в шахматном порядке ──

    private val miniCrown: Path by lazy {
        svgPath("M-10 6L-10-6L-5-1L0-9L5-1L10-6L10 6Z")
    }

    private val miniKey: Path by lazy {
        Path().apply {
            addOval(Rect(Offset(0f, -5.5f), 3.6f))
            addPath(svgPath("M0 -1.9V9M0 4.5H3.6M0 7.5H2.6"))
        }
    }

    val latticeCrowns = Path()
    val latticeKeys = Path()

    init {
        val stepX = 76f
        val stepY = 64f
        var j = 0
        var y = -stepY / 2f
        while (y < ART_H + stepY) {
            var i = 0
            var x = if (j % 2 == 0) 0f else stepX / 2f
            while (x < ART_W + stepX) {
                val rot = if ((i + j) % 4 < 2) -12f else 12f
                val m = Matrix().apply {
                    translate(x, y)
                    rotateZ(rot)
                }
                if ((i + j) % 2 == 0) {
                    latticeCrowns.addPath(Path().apply { addPath(miniCrown); transform(m) })
                } else {
                    val km = Matrix().apply {
                        translate(x, y)
                        rotateZ(rot + 45f)
                    }
                    latticeKeys.addPath(Path().apply { addPath(miniKey); transform(km) })
                }
                x += stepX
                i++
            }
            y += stepY
            j++
        }
    }

    // ── Корона (856×540): справа над полем ──

    private val placeMatrix: Matrix by lazy {
        Matrix().apply {
            translate(652f, 514f)
            scale(0.82f, 0.82f)
            translate(-712f, -514f)
        }
    }

    val crownTop = Offset(652f, 286.04f)

    val crown: Path by lazy {
        val baseCrown = Path().apply {
            val outline = svgPath("M606 470L590 316L652 384L712 236L772 384L834 316L818 470ZM604 486H820V514H604Z")
            val holes = Path().apply {
                addOval(Rect(Offset(712f, 420f), 15f))
                addOval(Rect(Offset(650f, 432f), 9f))
                addOval(Rect(Offset(774f, 432f), 9f))
            }
            op(outline, holes, PathOperation.Difference)
        }
        baseCrown.apply { transform(placeMatrix) }
    }

    val crownGems: Path by lazy {
        Path().apply {
            addOval(Rect(Offset(712f, 236f), 13f))
            addOval(Rect(Offset(590f, 316f), 10f))
            addOval(Rect(Offset(834f, 316f), 10f))
            transform(placeMatrix)
        }
    }

    val sparkles: Path by lazy {
        val rng = IdCardGenerator.rng(0x28102026)
        val p = Path()
        for (n in 0 until 28) {
            val x = (rng() * ART_W).toFloat()
            val y = (rng() * ART_H).toFloat()
            val s = (if (rng() < 0.2) 8.0 + rng() * 7.0 else 2.5 + rng() * 3.5).toFloat()
            p.moveTo(x, y - s)
            p.quadraticTo(x + s * 0.16f, y - s * 0.16f, x + s, y)
            p.quadraticTo(x + s * 0.16f, y + s * 0.16f, x, y + s)
            p.quadraticTo(x - s * 0.16f, y + s * 0.16f, x - s, y)
            p.quadraticTo(x - s * 0.16f, y - s * 0.16f, x, y - s)
            p.close()
        }
        p
    }
}

/** Защитная сетка под печатью ROOT. */
internal fun DrawScope.drawRootLattice(tilt: CardTiltState, light: Boolean) {
    val w = size.width
    val h = size.height
    val fx = tilt.fx
    val fy = tilt.fy
    val mag = min(1f, hypot(fx, fy))
    val box = Size(w * 2.4f, h * 2.4f)
    val origin = Offset(-w * 0.7f - fx * 0.22f * box.width, -h * 0.7f - fy * 0.18f * box.height)
    val brush = cssRepeating(118f, IdCardMaterials.RootRainbow, box, origin)
    val alpha = (if (light) 0.2f else 0.13f) + 0.16f * mag
    scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
        drawPath(RootArt.latticeCrowns, brush, alpha = alpha)
        drawPath(RootArt.latticeKeys, brush, alpha = alpha, style = Stroke(1.7f, cap = StrokeCap.Round))
    }
}

/** Скрытое изображение ROOT: корона, камни и искры. */
internal fun DrawScope.drawRootReveal(fx: Float, fy: Float, opacity: Float, light: Boolean) {
    val w = size.width
    val h = size.height
    val box = Size(w * 2.2f, h * 2.2f)
    val origin = Offset(-w * 0.6f - fx * 0.16f * box.width, -h * 0.6f - fy * 0.13f * box.height)
    val brush = cssRepeating(118f, IdCardMaterials.RootReveal, box, origin)
    val fill = opacity * (if (light) 0.42f else 0.34f)
    scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
        val glow = 0.35f + 0.55f * opacity
        val glowR = 13f * 4.2f
        drawCircle(
            Brush.radialGradient(
                0f to IdCardMaterials.RootCyan.copy(alpha = 0.3f * glow), 1f to Color.Transparent,
                center = RootArt.crownTop, radius = glowR
            ),
            radius = glowR, center = RootArt.crownTop
        )
        // Контур короны
        drawPath(
            RootArt.crown,
            IdCardMaterials.RootGold.copy(alpha = 0.16f + 0.3f * opacity),
            style = Stroke(2.4f, join = StrokeJoin.Round)
        )
        if (fill > 0f) {
            drawPath(RootArt.crown, brush, alpha = fill)
        }
        drawPath(RootArt.crownGems, IdCardMaterials.RootCyan.copy(alpha = glow))
        drawPath(RootArt.sparkles, IdCardMaterials.RootSparkle, alpha = 0.2f + 0.6f * opacity)
    }
}
