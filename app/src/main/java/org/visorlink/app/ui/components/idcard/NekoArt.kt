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
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdPattern
import org.visorlink.app.ui.theme.ART_H
import org.visorlink.app.ui.theme.ART_W
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.cssRepeating
import kotlin.math.hypot
import kotlin.math.min

/** Узор надетого кастомного скина — только при валидной палитре (как надпись выпуска). */
internal val IdCard.pattern: IdPattern? get() = custom?.takeIf { it.colors != null }?.pattern

/**
 * Авторский узор NEKO (`custom.pattern = "neko"`). Всё детерминировано и рисуется в координатах
 * карты 856×540 (как [RevealArt]) и голограммы 100×100 — чтобы веб мог повторить те же числа.
 *
 * Три слоя: защитная сетка под печатью (лапки и мордочки, переливаются при наклоне), кот под
 * луной поверх всего (проступает под углом, как «скрытое изображение») и искры. Голограмма —
 * кошачья голова с тиснёной мордочкой вместо эмблемы режима.
 */
internal object NekoArt {
    val MOON_CENTER = Offset(596f, 134f)
    const val MOON_R = 48f

    /** Маска голограммы: кошачья голова с острыми ушами. */
    val holoMask: Path by lazy {
        svgPath("M50 97C25 97 8 81 8 60C8 49 12 41 17 35L13 5L39 21C42.5 20.3 46.2 20 50 20C53.8 20 57.5 20.3 61 21L87 5L83 35C88 41 92 49 92 60C92 81 75 97 50 97Z")
    }

    private val holoContour: Path by lazy {
        Path().apply {
            addPath(holoMask)
            transform(Matrix().apply { translate(50f, 52f); scale(0.86f, 0.86f); translate(-50f, -52f) })
        }
    }
    private val emblemStrokes: Path by lazy {
        svgPath(
            // Глаза-миндалины
                "M27 57Q36 47.5 45 57Q36 64.5 27 57ZM55 57Q64 47.5 73 57Q64 64.5 55 57Z" +
                // Рот «ω»
                "M50 70.5Q50 75.5 45.5 75.5Q42.5 75.5 41.5 73M50 70.5Q50 75.5 54.5 75.5Q57.5 75.5 58.5 73" +
                // Усы
                "M33 68L12 64.5M33 71.5L11 73M34 75L15 81M67 68L88 64.5M67 71.5L89 73M66 75L85 81",
        )
    }
    private val emblemFill: Path by lazy {
        Path().apply {
            addOval(Rect(Offset(36f, 56.5f), 1.3f).let { Rect(it.left, 52f, it.right, 61f) })
            addOval(Rect(Offset(64f, 56.5f), 1.3f).let { Rect(it.left, 52f, it.right, 61f) })
        }
    }
    /** Носик и внутренние ушки — коралловые. */
    private val emblemCoral: Path by lazy { svgPath("M46.2 66.5L53.8 66.5L50 70.5ZM20.5 30L18 14L32.5 23ZM79.5 30L82 14L67.5 23Z") }

    /** Тиснёная мордочка в квадрате 100×100: тёмный оттиск со сдвигом и светлый, как [drawHoloEmblem]. */
    fun DrawScope.drawHoloEmblem() {
        fun art(color: Color) {
            drawPath(holoContour, color, style = Stroke(1.3f))
            drawPath(emblemStrokes, color, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(emblemFill, color)
        }
        translate(0.8f, 0.9f) { art(Color.Black.copy(alpha = 0.28f)) }
        art(Color.White.copy(alpha = 0.8f))
        translate(0.6f, 0.7f) { drawPath(emblemCoral, Color.Black.copy(alpha = 0.25f)) }
        drawPath(emblemCoral, IdCardMaterials.NekoCoral)
    }

    // ── Защитная сетка (856×540): лапки и мордочки в шахматном порядке ──

    private val miniHead: Path by lazy {
        svgPath("M0 11C-7 11-11 6.5-11 1C-11-2-10-4.5-8.5-6L-9.5-12.5L-3.5-8.6C-2.3-8.9-1.2-9 0-9C1.2-9 2.3-8.9 3.5-8.6L9.5-12.5L8.5-6C10-4.5 11-2 11 1C11 6.5 7 11 0 11Z")
    }

    /** Лапки — заливка. */
    val latticePaws = Path()
    /** Мордочки — контур. */
    val latticeHeads = Path()
    /** Глаза мордочек — заливка. */
    val latticeEyes = Path()

    init {
        val stepX = 76f
        val stepY = 64f
        var j = 0
        var y = -stepY / 2f
        while (y < ART_H + stepY) {
            var i = 0
            var x = if (j % 2 == 0) 0f else stepX / 2f
            while (x < ART_W + stepX) {
                val m = Matrix().apply {
                    translate(x, y)
                    rotateZ(if ((i + j) % 4 < 2) -16f else 14f)
                }
                if ((i + j) % 2 == 0) {
                    latticePaws.addPath(Path().apply { addPath(PAW_PATH); transform(m.apply { scale(1.05f, 1.05f) }) })
                } else {
                    latticeHeads.addPath(Path().apply { addPath(miniHead); transform(m) })
                    latticeEyes.addPath(
                        Path().apply {
                            addOval(Rect(Offset(-4f, 0.5f), 1.6f))
                            addOval(Rect(Offset(4f, 0.5f), 1.6f))
                            transform(m)
                        },
                    )
                }
                x += stepX
                i++
            }
            y += stepY
            j++
        }
    }

    // ── Кот под луной (856×540): сидит справа, смотрит влево, хвост закручен ──

    /** Силуэт одним контуром: лапы, грудь, мордочка, уши, спина, бедро. Глаз — сквозное окно. */
    val cat: Path by lazy {
        val silhouette = svgPath(
            "M700 528C680 528 668 520 670 505C672 470 680 420 676 380C672 345 658 330 652 312" +
                "C630 305 616 285 618 262C620 245 626 236 632 228L628 176L657 205C667 202 677 202 687 205" +
                "L713 178L711 232C721 252 721 278 711 298C741 320 781 360 793 410C803 450 801 490 783 512" +
                "C773 524 757 528 740 528Z",
        )
        val eye = svgPath("M634 250Q645 241 656 248Q645 256 634 250Z")
        Path().apply { op(silhouette, eye, PathOperation.Difference) }
    }

    val tail: Path by lazy { svgPath("M783 512C838 520 858 478 846 440C838 412 816 412 818 430") }

    val moon: Path by lazy {
        val disc = Path().apply { addOval(Rect(MOON_CENTER, MOON_R)) }
        val bite = Path().apply { addOval(Rect(MOON_CENTER + Offset(21f, -17f), MOON_R * 0.86f)) }
        Path().apply { op(disc, bite, PathOperation.Difference) }
    }

    /** Искры: четырёхлучевые звёзды, позиции из фиксированного seed (одинаковы на всех устройствах). */
    val sparkles: Path by lazy {
        val random = IdCardGenerator.rng(0x22102026L)
        Path().apply {
            repeat(34) {
                val x = (random() * ART_W).toFloat()
                val y = (random() * ART_H).toFloat()
                val s = (if (random() < 0.18) 8 + random() * 7 else 2.5 + random() * 3.5).toFloat()
                moveTo(x, y - s)
                quadraticTo(x + s * 0.16f, y - s * 0.16f, x + s, y)
                quadraticTo(x + s * 0.16f, y + s * 0.16f, x, y + s)
                quadraticTo(x - s * 0.16f, y + s * 0.16f, x - s, y)
                quadraticTo(x - s * 0.16f, y - s * 0.16f, x, y - s)
                close()
            }
        }
    }
}

/** Защитная сетка под печатью: переливается и чуть ярчает при наклоне. */
internal fun DrawScope.drawNekoLattice(tilt: CardTiltState, light: Boolean) {
    val w = size.width
    val h = size.height
    val fx = tilt.fx
    val fy = tilt.fy
    val mag = min(1f, hypot(fx, fy))
    val box = Size(w * 2.4f, h * 2.4f)
    val origin = Offset(-w * 0.7f - fx * 0.22f * box.width, -h * 0.7f - fy * 0.18f * box.height)
    val brush = cssRepeating(118f, IdCardMaterials.NekoRainbow, box, origin)
    val alpha = (if (light) 0.2f else 0.12f) + 0.16f * mag
    scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
        drawPath(NekoArt.latticePaws, brush, alpha = alpha)
        drawPath(NekoArt.latticeHeads, brush, alpha = alpha, style = Stroke(1.6f))
        drawPath(NekoArt.latticeEyes, brush, alpha = alpha)
    }
}

/** Кот под луной и искры поверх всего: проступают под углом ([opacity] — как у «скрытого изображения»). */
internal fun DrawScope.drawNekoReveal(fx: Float, fy: Float, opacity: Float, light: Boolean) {
    val w = size.width
    val h = size.height
    val box = Size(w * 2.2f, h * 2.2f)
    val origin = Offset(-w * 0.6f - fx * 0.16f * box.width, -h * 0.6f - fy * 0.13f * box.height)
    val brush = cssRepeating(118f, IdCardMaterials.NekoReveal, box, origin)
    val fill = opacity * (if (light) 0.4f else 0.3f)
    scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
        // Луна — главная фольга карты: видна всегда, под углом разгорается и светит ореолом
        val moonGlow = 0.35f + 0.55f * opacity
        drawCircle(
            Brush.radialGradient(
                0f to IdCardMaterials.NekoMoon.copy(alpha = 0.32f * moonGlow), 1f to Color.Transparent,
                center = NekoArt.MOON_CENTER, radius = NekoArt.MOON_R * 2.6f,
            ),
            radius = NekoArt.MOON_R * 2.6f, center = NekoArt.MOON_CENTER,
        )
        drawPath(NekoArt.moon, IdCardMaterials.NekoMoon, alpha = moonGlow)
        drawPath(NekoArt.cat, brush, alpha = fill)
        drawPath(NekoArt.tail, brush, alpha = fill, style = Stroke(17f, cap = StrokeCap.Round))
        drawPath(NekoArt.sparkles, IdCardMaterials.NekoSparkle, alpha = 0.22f + 0.6f * opacity)
    }
}
