package org.visorlink.app.ui.components.idcard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.IdPattern
import org.visorlink.app.ui.theme.ART_H
import org.visorlink.app.ui.theme.ART_W
import org.visorlink.app.ui.theme.BaseLayer
import org.visorlink.app.ui.theme.CardPalette
import org.visorlink.app.ui.theme.CardTone
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.cssLinear
import org.visorlink.app.ui.theme.cssRepeating
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Серия VL (Forge) — порт веб-версии (visorlink: components/idcard/vlArt.jsx, styles/idcard-vl.css).
 * Карты: Ion, Blackout, Pulse, Prism, Buddy, Lumen, а также эксклюзивные PRO-карты Aurum и Noir.
 */
internal fun isVlArt(pattern: IdPattern?): Boolean = pattern != null && pattern.isVl && pattern.hasArt

private fun rgba(r: Int, g: Int, b: Int, a: Float = 1f): Color = Color(r, g, b, (a * 255).roundToInt())

internal fun vlPalette(pattern: IdPattern): CardPalette {
    fun pal(
        tone: CardTone,
        base: List<BaseLayer>,
        ink: Color,
        muted: Color,
        line: Color,
        line2: Color,
        band: Color,
        bandInk: Color,
        neon: Color,
        neon2: Color,
        edge: Color,
        photoBg: Color,
        core: Color
    ) = CardPalette(
        tone = tone, base = base, ink = ink, muted = muted, line = line, line2 = line2,
        band = band, bandInk = bandInk, neon = neon, neon2 = neon2, stamp = neon,
        edge = edge, photoBg = photoBg, core = core
    )

    val white = Color.White

    return when (pattern) {
        IdPattern.VL_BLACKOUT -> pal(
            tone = CardTone.DARK,
            base = listOf(BaseLayer.Linear(150f, listOf(0f to Color(0xFF262A31), 0.55f to Color(0xFF14161A), 1f to Color(0xFF1C1F25)))),
            ink = Color(0xFFEEF2F6), muted = Color(0xFF8A93A0), line = rgba(61, 242, 255, 0.22f), line2 = rgba(255, 61, 154, 0.2f),
            band = Color(0xFF3DF2FF), bandInk = Color(0xFF06090C), neon = Color(0xFF3DF2FF), neon2 = Color(0xFFFF3D9A),
            edge = rgba(61, 242, 255, 0.3f), photoBg = Color(0xFF1D2026), core = Color(0xFFC9D0D8)
        )
        IdPattern.VL_PULSE -> pal(
            tone = CardTone.DARK,
            base = listOf(
                BaseLayer.Radial(1.0f, 0.9f, 0.18f, 1f, listOf(0f to rgba(255, 61, 154, 0.18f), 0.6f to Color.Transparent)),
                BaseLayer.Linear(155f, listOf(0f to Color(0xFF17151D), 1f to Color(0xFF09090D)))
            ),
            ink = Color(0xFFF6EEF3), muted = Color(0xFF9A8C97), line = rgba(255, 61, 154, 0.26f), line2 = rgba(61, 242, 255, 0.18f),
            band = Color(0xFFFF3D9A), bandInk = Color(0xFF0B0709), neon = Color(0xFFFF3D9A), neon2 = Color(0xFF3DF2FF),
            edge = rgba(255, 61, 154, 0.32f), photoBg = Color(0xFF1F1A22), core = Color(0xFFD8C9D2)
        )
        IdPattern.VL_PRISM -> pal(
            tone = CardTone.LIGHT,
            base = listOf(BaseLayer.Linear(135f, listOf(0f to Color(0xFFF5F9FC), 0.55f to Color(0xFFDDE7EF), 1f to Color(0xFFEEF3F7)))),
            ink = Color(0xFF141A22), muted = Color(0xFF5E6B79), line = rgba(60, 90, 130, 0.2f), line2 = rgba(79, 123, 255, 0.28f),
            band = Color(0xFF141A22), bandInk = white, neon = Color(0xFF4F7BFF), neon2 = Color(0xFFFF5A1F),
            edge = rgba(255, 255, 255, 0.95f), photoBg = Color(0xFFCBD6E0), core = white
        )
        IdPattern.VL_BUDDY -> pal(
            tone = CardTone.LIGHT,
            base = listOf(
                BaseLayer.Radial(0.8f, 0.7f, 0.3f, 1f, listOf(0f to rgba(61, 242, 255, 0.13f), 0.62f to Color.Transparent)),
                BaseLayer.Linear(145f, listOf(0f to Color(0xFFF8F8F6), 0.58f to Color(0xFFE6E8E7), 1f to Color(0xFFF1F2F0)))
            ),
            ink = Color(0xFF14181C), muted = Color(0xFF66707A), line = rgba(50, 70, 84, 0.2f), line2 = rgba(61, 242, 255, 0.4f),
            band = Color(0xFF14181C), bandInk = white, neon = Color(0xFF19C8D8), neon2 = Color(0xFFFF3D9A),
            edge = rgba(255, 255, 255, 0.95f), photoBg = Color(0xFFD5DADB), core = white
        )
        IdPattern.VL_LUMEN -> pal(
            tone = CardTone.DARK,
            base = listOf(
                BaseLayer.Radial(0.9f, 0.8f, 0.5f, 1.2f, listOf(0f to rgba(255, 178, 61, 0.1f), 0.6f to Color.Transparent)),
                BaseLayer.Linear(160f, listOf(0f to Color(0xFF15131A), 1f to Color(0xFF050507)))
            ),
            ink = Color(0xFFF6F0E6), muted = Color(0xFF948A7A), line = rgba(255, 178, 61, 0.24f), line2 = rgba(255, 79, 107, 0.2f),
            band = Color(0xFFFFB23D), bandInk = Color(0xFF0A0806), neon = Color(0xFFFFB23D), neon2 = Color(0xFFFF4F6B),
            edge = rgba(255, 178, 61, 0.3f), photoBg = Color(0xFF1D1A20), core = Color(0xFFD9CDB8)
        )
        IdPattern.VL_PRO_AURUM -> CardPalette(
            tone = CardTone.LIGHT,
            base = listOf(
                BaseLayer.Radial(0.9f, 0.9f, 0.18f, 0f, listOf(0f to Color(255, 255, 255, 178), 0.55f to Color.Transparent)),
                BaseLayer.Linear(140f, listOf(0f to Color(0xFFF7EEDB), 0.52f to Color(0xFFE7D3A8), 1f to Color(0xFFF3E6C8)))
            ),
            ink = Color(0xFF2A1F0E), muted = Color(0xFF7A6440), line = rgba(122, 90, 30, 0.24f), line2 = rgba(200, 150, 46, 0.4f),
            band = Color(0xFF2A1F0E), bandInk = Color(0xFFF7EEDB), neon = Color(0xFFC8962E), neon2 = Color(0xFF7A5A1E), stamp = Color(0xFFA87A22),
            edge = rgba(255, 248, 230, 0.95f), photoBg = Color(0xFFE2D2AE), core = white
        )
        IdPattern.VL_PRO_NOIR -> CardPalette(
            tone = CardTone.DARK,
            base = listOf(
                BaseLayer.Radial(0.8f, 0.7f, 0.85f, 1.1f, listOf(0f to rgba(226, 182, 90, 0.12f), 0.6f to Color.Transparent)),
                BaseLayer.Linear(155f, listOf(0f to Color(0xFF17150F), 0.6f to Color(0xFF070605), 1f to Color(0xFF12100C)))
            ),
            ink = Color(0xFFF5E6C4), muted = Color(0xFFA08E68), line = rgba(226, 182, 90, 0.26f), line2 = rgba(245, 230, 196, 0.16f),
            band = Color(0xFFE2B65A), bandInk = Color(0xFF0B0A09), neon = Color(0xFFE2B65A), neon2 = Color(0xFFF5E6C4), stamp = Color(0xFFE2B65A),
            edge = rgba(226, 182, 90, 0.36f), photoBg = Color(0xFF1C1913), core = Color(0xFFD9C9A6)
        )
        else -> pal( // VL_ION
            tone = CardTone.LIGHT,
            base = listOf(
                BaseLayer.Radial(0.9f, 0.8f, 0.88f, 1f, listOf(0f to rgba(255, 90, 31, 0.1f), 0.62f to Color.Transparent)),
                BaseLayer.Linear(145f, listOf(0f to Color(0xFFF7F5F1), 0.58f to Color(0xFFE8E4DD), 1f to Color(0xFFF2EFE9)))
            ),
            ink = Color(0xFF1B1814), muted = Color(0xFF6F675D), line = rgba(70, 58, 44, 0.2f), line2 = rgba(255, 90, 31, 0.3f),
            band = Color(0xFF1B1814), bandInk = white, neon = Color(0xFFFF5A1F), neon2 = Color(0xFF3DF2FF),
            edge = rgba(255, 255, 255, 0.95f), photoBg = Color(0xFFD9D4CB), core = white
        )
    }
}

// ── Рандомизация экземпляра ──

private val VL_LEDS = listOf(Color(0xFF3DF2FF), Color(0xFFFF3D9A), Color(0xFFFF5A1F), Color(0xFF9BFF3D), Color(0xFFB58CFF), Color(0xFFFFB23D))

internal data class VlVariant(
    val led: Color?,
    val rot: Float,
    val speed: Float,
    val phase: Float
)

internal fun vlVariant(seed: Long): VlVariant {
    val r = IdCardGenerator.rng(((seed xor 0x7A11DL) and 0xFFFFFFFFL))
    val led = if (r() < 0.45) null else VL_LEDS[floor(r() * VL_LEDS.size).toInt()]
    val rot = (r() * 360).roundToInt().toFloat()
    val speed = ((0.8 + r() * 0.7) * 100).roundToInt() / 100f
    val phase = (r() * 100).roundToInt() / 100f
    return VlVariant(led, rot, speed, phase)
}

internal fun vlLedColor(pattern: IdPattern, variant: VlVariant): Color = when (pattern) {
    IdPattern.VL_PRO_AURUM -> Color(0xFFFFB23D)
    IdPattern.VL_PRO_NOIR -> Color(0xFFE2B65A)
    else -> variant.led ?: vlNativeLed(pattern)
}

private fun vlNativeLed(pattern: IdPattern): Color = when (pattern) {
    IdPattern.VL_ION -> Color(0xFFFF5A1F)
    IdPattern.VL_PULSE -> Color(0xFFFF3D9A)
    IdPattern.VL_LUMEN -> Color(0xFFFFB23D)
    else -> Color(0xFF3DF2FF)
}

// ── Точечный дисплей 5×7 ──

private const val LED_COLS = 44
private const val LED_ROWS = 7
private const val LED_PITCH = 0.34f // em

private val FONT_5X7 = mapOf(
    'A' to "01110/10001/10001/11111/10001/10001/10001", 'B' to "11110/10001/10001/11110/10001/10001/11110",
    'C' to "01110/10001/10000/10000/10000/10001/01110", 'D' to "11110/10001/10001/10001/10001/10001/11110",
    'E' to "11111/10000/10000/11110/10000/10000/11111", 'F' to "11111/10000/10000/11110/10000/10000/10000",
    'G' to "01110/10001/10000/10111/10001/10001/01111", 'H' to "10001/10001/10001/11111/10001/10001/10001",
    'I' to "01110/00100/00100/00100/00100/00100/01110", 'J' to "00111/00010/00010/00010/00010/10010/01100",
    'K' to "10001/10010/10100/11000/10100/10010/10001", 'L' to "10000/10000/10000/10000/10000/10000/11111",
    'M' to "10001/11011/10101/10101/10001/10001/10001", 'N' to "10001/11001/10101/10011/10001/10001/10001",
    'O' to "01110/10001/10001/10001/10001/10001/01110", 'P' to "11110/10001/10001/11110/10000/10000/10000",
    'Q' to "01110/10001/10001/10001/10101/10010/01101", 'R' to "11110/10001/10001/11110/10100/10010/10001",
    'S' to "01111/10000/10000/01110/00001/00001/11110", 'T' to "11111/00100/00100/00100/00100/00100/00100",
    'U' to "10001/10001/10001/10001/10001/10001/01110", 'V' to "10001/10001/10001/10001/10001/01010/00100",
    'W' to "10001/10001/10001/10101/10101/11011/10001", 'X' to "10001/10001/01010/00100/01010/10001/10001",
    'Y' to "10001/10001/01010/00100/00100/00100/00100", 'Z' to "11111/00001/00010/00100/01000/10000/11111",
    '0' to "01110/10001/10011/10101/11001/10001/01110", '1' to "00100/01100/00100/00100/00100/00100/01110",
    '2' to "01110/10001/00001/00010/00100/01000/11111", '3' to "11110/00001/00001/01110/00001/00001/11110",
    '4' to "00010/00110/01010/10010/11111/00010/00010", '5' to "11111/10000/11110/00001/00001/10001/01110",
    '6' to "00110/01000/10000/11110/10001/10001/01110", '7' to "11111/00001/00010/00100/01000/01000/01000",
    '8' to "01110/10001/10001/01110/10001/10001/01110", '9' to "01110/10001/10001/01111/00001/00010/01100",
    '-' to "00000/00000/00000/11111/00000/00000/00000", '.' to "00000/00000/00000/00000/00000/00000/00100",
    '*' to "01010/11111/11111/11111/01110/00100/00000"
)

private fun ledTextDots(text: String, startCol: Int = 0): Pair<List<Offset>, Int> {
    var col = startCol
    val dots = mutableListOf<Offset>()
    for (ch in text.uppercase()) {
        val glyph = FONT_5X7[ch]
        if (glyph == null) {
            col += 3
            continue
        }
        val rows = glyph.split('/')
        for (r in 0 until LED_ROWS) {
            for (c in 0 until 5) {
                if (rows[r][c] == '1') {
                    dots.add(Offset(col + c + 0.5f, r + 0.5f))
                }
            }
        }
        col += 6
    }
    return dots to (col - startCol)
}

private fun gridDots(rows: List<String>, ox: Int, oy: Int): List<Offset> {
    val dots = mutableListOf<Offset>()
    for (r in rows.indices) {
        for (c in rows[r].indices) {
            if (rows[r][c] == 'X') dots.add(Offset(ox + c + 0.5f, oy + r + 0.5f))
        }
    }
    return dots
}

private val EYE_ROWS = listOf(".XXX.", "XXXXX", "XXXXX", ".XXX.")
private val HEART_ROWS = listOf(".X.X.", "XXXXX", ".XXX.", "..X..")
private val MOUTH_ROW = listOf("XXXXXX")
private val SMILE_ROWS = listOf("X......X", ".XXXXXX.")

@Composable
internal fun VlDisplay(
    text: String,
    face: Boolean,
    led: Color,
    tone: CardTone,
    em: Dp,
    tilt: CardTiltState,
    phase: Float = 0f,
    modifier: Modifier = Modifier
) {
    val unitText = "$text   "
    val (textDots, cols) = remember(unitText) {
        val (a, c) = ledTextDots(unitText, 0)
        val (b, _) = ledTextDots(unitText, c)
        (a + b) to c
    }

    val eyes = remember { gridDots(EYE_ROWS, 10, 1) + gridDots(EYE_ROWS, 29, 1) }
    val hearts = remember { gridDots(HEART_ROWS, 10, 1) + gridDots(HEART_ROWS, 29, 1) }
    val mouth = remember { gridDots(MOUTH_ROW, 19, 6) }
    val smile = remember { gridDots(SMILE_ROWS, 18, 5) }

    // «Убрать анимации» системы — мордочка и строка стоят (F _VlDisplay:276-281)
    val context = LocalContext.current
    val calm = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }

    val transition = rememberInfiniteTransition(label = "vl_led_ticker")
    val ticker = if (face) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart), label = "face")
    } else {
        val dur = (cols * 110).coerceAtLeast(1)
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Restart), label = "text")
    }

    val width = em * (LED_COLS * LED_PITCH + 1.64f)
    val height = em * (LED_ROWS * LED_PITCH + 1.64f)

    Canvas(modifier.size(width, height)) {
        val emPx = em.toPx()
        val pitch = LED_PITCH * emPx
        val light = tone == CardTone.LIGHT
        val w = size.width
        val h = size.height

        // Рамка дисплея
        val bezelR = emPx * 0.85f
        val bezelOutline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(0f, 0f, w, h), androidx.compose.ui.geometry.CornerRadius(bezelR))) }
        drawPath(
            bezelOutline,
            Brush.linearGradient(
                if (light) listOf(Color(0xFFFBFAF8), Color(0xFFD6D1C8))
                else listOf(Color(0xFF3A3F48), Color(0xFF1A1D22)),
                start = Offset.Zero, end = Offset(w, h)
            )
        )
        drawPath(bezelOutline, Color.White.copy(alpha = 0.3f), style = Stroke(1f))

        // Экран дисплея
        val inset = emPx * 0.32f
        val screenRect = Rect(inset, inset, w - inset, h - inset)
        val screenR = emPx * 0.6f
        val screenOutline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(screenRect, androidx.compose.ui.geometry.CornerRadius(screenR))) }
        drawPath(
            screenOutline,
            Brush.verticalGradient(listOf(Color(0xFF0B0E12), Color(0xFF020304)), startY = screenRect.top, endY = screenRect.bottom)
        )

        clipPath(screenOutline) {
            val origin = screenRect.topLeft + Offset(emPx * 0.5f, emPx * 0.5f)

            // Погашенная матрица точек
            for (r in 0 until LED_ROWS) {
                for (c in 0 until LED_COLS) {
                    val pt = origin + Offset((c + 0.5f) * pitch, (r + 0.5f) * pitch)
                    drawCircle(Color.White.copy(alpha = 0.07f), radius = pitch * 0.22f, center = pt)
                }
            }

            // Зажжённые точки
            val tickerVal = if (calm) 0f else ticker.value
            val lit: List<Offset> = if (face) {
                val t = tickerVal
                val blink = if (t in 0.92f..0.98f) 0.12f else 1.0f
                val dx = if (tilt.interactive) (tilt.fx * 3f).roundToInt().coerceIn(-2, 2) else 0
                val dy = if (tilt.interactive) (-tilt.fy * 2f).roundToInt().coerceIn(-1, 1) else 0
                if (tilt.dragging) {
                    hearts + smile
                } else {
                    eyes.map { e -> Offset(e.x + dx, 1f + 2.5f + (e.y - 3.5f) * blink + dy) } + mouth
                }
            } else {
                // Стартовая фаза из seed: карты «мигают» вразнобой (F: ctrl.value = phase % 1)
                val shift = (tickerVal + phase % 1f) * cols
                textDots.map { d -> Offset(d.x - shift, d.y) }
            }

            val color = if (face && tilt.dragging) Color(0xFFFF3D9A) else led
            for (d in lit) {
                if (d.x > -1f && d.x < LED_COLS + 1f) {
                    val pt = origin + Offset(d.x * pitch, d.y * pitch)
                    drawCircle(color.copy(alpha = 0.22f), radius = pitch * 0.58f, center = pt)
                    drawCircle(color, radius = pitch * 0.33f, center = pt)
                }
            }

            // Блик стекла
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), startY = screenRect.top, endY = screenRect.top + screenRect.height * 0.34f)
            )
        }
    }
}

// ── Подложки лица VL ──

internal fun DrawScope.drawVlBackdrop(pattern: IdPattern, rot: Float) {
    val w = size.width
    val h = size.height
    when (pattern) {
        IdPattern.VL_ION -> {
            val cx = 700f
            val cy = 330f
            val bigR = 250f
            val r = 78f
            scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
                rotate(rot, pivot = Offset(cx, cy)) {
                    val p = Path()
                    val len = sqrt(bigR * bigR - r * r)
                    for (i in 0 until 7) {
                        val a = (i.toFloat() / 7f) * (PI.toFloat() * 2f) - (PI.toFloat() / 2f)
                        val tx = cx + r * cos(a)
                        val ty = cy + r * sin(a)
                        p.moveTo(tx, ty)
                        p.lineTo(tx - sin(a) * len, ty + cos(a) * len)
                    }
                    p.addOval(Rect(Offset(cx, cy), r))
                    p.addOval(Rect(Offset(cx, cy), bigR))

                    drawPath(p, rgba(60, 46, 32, 0.2f), style = Stroke(1.6f))
                    translate(1.4f, 1.6f) {
                        drawPath(p, rgba(255, 255, 255, 0.9f), style = Stroke(1.4f))
                    }
                }
            }
        }
        IdPattern.VL_BLACKOUT -> {
            drawRect(
                Brush.verticalGradient(listOf(rgba(255, 255, 255, 0.06f), Color.Transparent), startY = 0f, endY = h * 0.18f)
            )
        }
        IdPattern.VL_PULSE -> {
            drawVlGlow(Offset(w * 0.55f, h * 0.66f), w * 0.6f, h * 0.7f, rgba(255, 61, 154, 0.1f), 0.7f)
        }
        IdPattern.VL_PRISM -> {
            val emPx = w / 36f
            val rrect = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(emPx * 0.45f, emPx * 0.45f, w - emPx * 0.45f, h - emPx * 0.45f), androidx.compose.ui.geometry.CornerRadius(emPx * 0.8f)))
            }
            clipPath(rrect) {
                drawRect(
                    cssLinear(120f, listOf(0f to rgba(255, 255, 255, 0.55f), 0.38f to Color.Transparent, 1f to Color.Transparent), size)
                )
                translate(emPx * 0.1f, emPx * 0.1f) {
                    drawPath(rrect, rgba(255, 255, 255, 0.95f), style = Stroke(emPx * 0.2f))
                }
                translate(-emPx * 0.1f, -emPx * 0.1f) {
                    drawPath(rrect, rgba(80, 110, 150, 0.2f), style = Stroke(emPx * 0.2f))
                }
            }
        }
        IdPattern.VL_BUDDY -> {
            drawRect(
                Brush.verticalGradient(listOf(rgba(255, 255, 255, 0.5f), Color.Transparent), startY = 0f, endY = h * 0.3f)
            )
        }
        IdPattern.VL_PRO_AURUM -> {
            // Гильош: лучи от центра
            val c = Offset(w * 0.22f, h * 0.58f)
            val reach = hypot(w, h) * 1.2f
            val deg = (PI / 180).toFloat()
            val rays = Path()
            for (i in 0 until 120) {
                val a0 = i * 3f * deg - (PI.toFloat() / 2f)
                val a1 = a0 + 0.6f * deg
                rays.moveTo(c.x, c.y)
                rays.lineTo(c.x + reach * cos(a0), c.y + reach * sin(a0))
                rays.lineTo(c.x + reach * cos(a1), c.y + reach * sin(a1))
                rays.close()
            }
            drawPath(rays, rgba(122, 90, 30, 0.07f))
            drawRect(
                Brush.verticalGradient(listOf(rgba(255, 255, 255, 0.5f), Color.Transparent), startY = 0f, endY = h * 0.26f)
            )
            // Inset-полосы: 0…0.62em (.35) поверх 0…0.85em (.2) (F vl_series_art.dart:483-485)
            val emPx = w / EM_W_ART
            drawVlInsetBand(emPx * 0.85f, rgba(168, 122, 34, 0.2f))
            drawVlInsetBand(emPx * 0.62f, rgba(168, 122, 34, 0.35f))
        }
        IdPattern.VL_PRO_NOIR -> {
            // Тиснение: сетка ромбов ±45°
            val emPx = w / EM_W_ART
            val step = emPx * 1.4f
            val stripeColor = rgba(226, 182, 90, 0.07f)
            drawStripes(45f, step, 1.dp.toPx(), stripeColor)
            drawStripes(-45f, step, 1.dp.toPx(), stripeColor)
            drawVlGlow(Offset(w * 0.5f, 0f), w * 1.2f, h * 0.9f, rgba(255, 255, 255, 0.05f), 0.6f)
            drawVlInsetBand(emPx * 0.66f, rgba(226, 182, 90, 0.4f))
        }
        else -> {
            drawVlGlow(Offset(w * 0.5f, h * 0.5f), w * 0.7f, h * 0.6f, rgba(255, 255, 255, 0.03f), 0.7f)
        }
    }
}

/** Ширина/высота лица карты в ART-координатах (36 × 22.5 em). */
internal const val EM_W_ART = 36f

/** Эллиптическое свечение (порт Flutter `_glow`, vl_series_art.dart:570-576). */
internal fun DrawScope.drawVlGlow(c: Offset, rx: Float, ry: Float, color: Color, stop: Float) {
    scale(1f, ry / rx, pivot = c) {
        drawCircle(
            Brush.radialGradient(
                0f to color,
                stop.coerceIn(0.01f, 1f) to Color.Transparent,
                center = c, radius = rx,
            ),
            radius = rx, center = c
        )
    }
}

/** Полоса вдоль края лица: `box-shadow: inset 0 0 0 <width> <color>` у .vl-back (F vl_series_art.dart:500-503). */
internal fun DrawScope.drawVlInsetBand(width: Float, color: Color) {
    val rect = Rect(0f, 0f, size.width, size.height)
    val band = Path().apply {
        op(
            Path().apply { addRect(rect) },
            Path().apply { addRect(rect.deflate(width)) },
            PathOperation.Difference,
        )
    }
    drawPath(band, color)
}

private fun DrawScope.drawStripes(angleDeg: Float, period: Float, strokeWidth: Float, color: Color) {
    val rad = angleDeg * (PI / 180f).toFloat()
    val dx = sin(rad)
    val dy = -cos(rad)
    val nx = -dy
    val ny = dx
    val len = abs(size.width * sin(rad)) + abs(size.height * cos(rad))
    val start = Offset(size.width / 2f, size.height / 2f) - Offset(dx, dy) * (len / 2f)
    var k = 0f
    while (k <= len) {
        val a = start + Offset(dx, dy) * k
        val p1 = a - Offset(nx, ny) * len
        val p2 = a + Offset(nx, ny) * len
        drawLine(color, p1, p2, strokeWidth = strokeWidth)
        k += period
    }
}

// ── Эффекты при наклоне ──

/**
 * Подложка PRO, зависящая от наклона (порт Flutter `_VlProBackPainter`, vl_series_art.dart:533-567):
 * Aurum — золотое свечение уходит против наклона, Noir — золотой отблеск бежит по бархату под углом.
 */
internal fun DrawScope.drawVlProBack(pattern: IdPattern, tilt: CardTiltState) {
    val w = size.width
    val h = size.height
    val fx = tilt.fx
    val fy = tilt.fy
    val mag = if (tilt.interactive) min(1f, hypot(fx, fy)) else 0f
    if (pattern == IdPattern.VL_PRO_AURUM) {
        // .vl-bloom.vl-gold: inset −10%, 40%×36% at 55% 62% и 22%×20% at 84% 80%
        val a = 0.3f + 0.6f * mag
        translate(-fx * 0.07f * w * 1.2f, fy * 0.05f * h * 1.2f) {
            drawVlGlow(Offset(w * 0.56f, h * 0.644f), 0.48f * w, 0.432f * h, rgba(255, 206, 110, 0.5f * a), 0.7f)
            drawVlGlow(Offset(w * 0.908f, h * 0.86f), 0.264f * w, 0.24f * h, rgba(255, 236, 190, 0.6f * a), 0.7f)
        }
    } else {
        // .vl-gold-sheen: полоса 200% сдвигается вслед за наклоном
        val opacity = if (tilt.interactive) (mag * 1.5f - 0.05f).coerceIn(0.14f, 1f) else 0.14f
        val box = Size(w * 2f, h * 2f)
        val origin = Offset(-w * 0.5f - fx * 0.16f * box.width, -h * 0.5f - fy * 0.13f * box.height)
        drawRect(
            cssLinear(
                120f,
                listOf(
                    0f to Color.Transparent, 0.36f to Color.Transparent,
                    0.45f to rgba(226, 182, 90, 0.16f), 0.5f to rgba(255, 236, 190, 0.26f),
                    0.55f to rgba(226, 182, 90, 0.14f), 0.64f to Color.Transparent, 1f to Color.Transparent,
                ),
                box, origin,
            ),
            alpha = opacity,
        )
    }
}

private val RAINBOW_PULSE = listOf(0.0f to Color(0xFFFF3D9A), 0.04f to Color(0xFFFF9BD0), 0.09f to Color(0xFF3DF2FF), 0.14f to Color(0xFFFF3D9A))
private val RAINBOW_PRISM = listOf(
    0.0f to Color(0xFFFF4F7B), 0.04f to Color(0xFFFFB347), 0.08f to Color(0xFF7BE495),
    0.12f to Color(0xFF4FC3FF), 0.16f to Color(0xFF8A6BFF), 0.20f to Color(0xFFFF4F7B)
)

internal fun DrawScope.drawVlFx(pattern: IdPattern, tilt: CardTiltState, rot: Float) {
    val w = size.width
    val h = size.height
    val emPx = w / EM_W_ART
    val fx = tilt.fx
    val fy = tilt.fy
    val mag = if (tilt.interactive) min(1f, hypot(fx, fy)) else 0f

    when (pattern) {
        IdPattern.VL_ION, IdPattern.VL_BUDDY -> {
            // Свечение из-под LED уходит против наклона (F vl_series_art.dart:624-632)
            val a = 0.3f + 0.6f * mag
            translate(-fx * 0.07f * w * 1.2f, fy * 0.05f * h * 1.2f) {
                val warm = pattern == IdPattern.VL_ION
                drawVlGlow(
                    Offset(w * 0.5f, h * 0.692f), 0.408f * w, 0.36f * h,
                    if (warm) rgba(255, 90, 31, 0.34f * a) else rgba(61, 242, 255, 0.3f * a), 0.7f,
                )
                // У Ion — второй тёплый ореол
                if (warm) drawVlGlow(Offset(w * 0.812f, h * 0.812f), 0.216f * w, 0.192f * h, rgba(255, 170, 120, 0.28f * a), 0.7f)
            }
        }
        IdPattern.VL_BLACKOUT -> {
            // Точёный металл: конусные блики вращаются при наклоне (F vl_series_art.dart:633-661)
            val emC = Offset(28f * emPx, 14f * emPx)
            val r = 23f * emPx
            val rotDeg = if (tilt.interactive) fx * 28f + fy * 10f else 0f
            val rect = Rect(0f, 0f, w, h)
            // Конус 40° с TileMode.repeated: останавливаемые позиции растягиваются на полные 360°
            val cone = listOf(
                0f to Color.Transparent, 5f / 40f to Color.Transparent,
                9f / 40f to rgba(150, 240, 255, 0.2f), 13f / 40f to rgba(255, 255, 255, 0.02f),
                22f / 40f to rgba(255, 255, 255, 0.02f), 26f / 40f to rgba(255, 61, 154, 0.12f),
                30f / 40f to Color.Transparent, 40f / 40f to Color.Transparent,
            )
            val stops = buildList {
                val period = 40f / 360f
                var k = 0
                while (k * period <= 1f) {
                    val base = k * period
                    for ((pos, col) in cone) {
                        val p = base + pos * period
                        if (p <= 1f) add(p to col)
                    }
                    k++
                }
            }
            drawIntoCanvas { canvas ->
                val layerPaint = Paint().apply { color = Color.Black.copy(alpha = 0.3f + 0.5f * mag) }
                canvas.saveLayer(rect, layerPaint)
            }
            rotate(rotDeg, pivot = emC) {
                drawRect(Brush.sweepGradient(*stops.toTypedArray(), center = emC))
            }
            // Маска dstIn: блик живёт внутри круга r√2 вокруг (28em, 14em)
            drawRect(
                Brush.radialGradient(0.12f to Color.Black, 0.62f to Color.Transparent, center = emC, radius = r * 1.4142f),
                topLeft = Offset.Zero, size = Size(w, h), blendMode = BlendMode.DstIn,
            )
            drawIntoCanvas { canvas -> canvas.restore() }
            // По верхней кромке бежит голубо-розовый свет
            val edge = (mag * 1.7f - 0.2f).coerceIn(0f, 1f)
            if (edge > 0f) {
                val line = Rect(0f, 0f, w, emPx * 0.16f)
                // Гало: вертикальный градиент в полосе, раздутой на 0.5em
                drawRect(
                    Brush.verticalGradient(listOf(rgba(61, 242, 255, 0.25f * edge), Color.Transparent), startY = 0f, endY = emPx * 0.7f),
                    topLeft = Offset(0f, 0f), size = Size(w, (emPx * 0.66f).coerceAtMost(h)),
                )
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.3f to rgba(61, 242, 255, edge),
                        0.7f to rgba(255, 61, 154, edge), 1f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, 0f), size = Size(w, line.height),
                )
            }
        }
        IdPattern.VL_PRO_AURUM, IdPattern.VL_PRO_NOIR -> {
            // Золотой edge-light: линия + гало (F vl_series_art.dart:662-670)
            val edge = (mag * 1.7f - 0.2f).coerceIn(0f, 1f)
            if (edge > 0f) {
                drawRect(
                    Brush.verticalGradient(listOf(rgba(226, 182, 90, 0.55f * edge), Color.Transparent), startY = 0f, endY = emPx * 0.7f),
                    topLeft = Offset(0f, 0f), size = Size(w, (emPx * 0.66f).coerceAtMost(h)),
                )
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.3f to rgba(255, 227, 163, edge),
                        0.7f to rgba(200, 150, 46, edge), 1f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, 0f), size = Size(w, emPx * 0.16f),
                )
            }
        }
        IdPattern.VL_PULSE -> {
            val box = Size(w * 2.2f, h * 2.2f)
            val origin = Offset(-w * 0.6f - fx * 0.16f * box.width, -h * 0.6f - fy * 0.13f * box.height)
            // Шейдер в ART-координатах: маска рисуется прямо с ним, без saveLayer/SrcIn
            val brush = artRepeatingShader(RAINBOW_PULSE, box, origin)
            val opacity = if (tilt.interactive) (mag * 1.5f - 0.05f).coerceIn(0.14f, 1f) else 0.14f
            scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
                drawPath(PulseMask, brush, alpha = opacity, style = Stroke(3f, cap = StrokeCap.Round))
            }
        }
        IdPattern.VL_PRISM -> {
            val box = Size(w * 2.2f, h * 2.2f)
            val origin = Offset(-w * 0.6f - fx * 0.16f * box.width, -h * 0.6f - fy * 0.13f * box.height)
            val brush = artRepeatingShader(RAINBOW_PRISM, box, origin)
            scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
                drawPath(FanMask, brush, alpha = 0.32f + 0.6f * mag)
            }
        }
        IdPattern.VL_LUMEN -> {
            // «Фонарик»: диск света проявляет скрытую солнечную систему (F vl_series_art.dart:681-701).
            // Пятна света — радиальный градиент в ART-координатах (периоды компенсированы k)
            val idle = !tilt.interactive || mag < 0.03f
            val cx = if (idle) 0.7f else (0.5f + fx * 0.6f).coerceIn(0f, 1f)
            val cy = if (idle) 0.4f else (0.5f - fy * 0.6f).coerceIn(0f, 1f)
            val k = ART_W / w
            val spot = Brush.radialGradient(
                0f to Color(0xFFFFF1C4),
                0.16f to Color(0xFFFFB23D),
                0.34f to Color(0xFFFF4F6B),
                0.52f to rgba(255, 79, 107, 0f),
                center = Offset(cx * ART_W, cy * ART_H),
                radius = 18f * emPx * 1.4142f * k,
            )
            scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
                drawPath(LumenMask, spot, alpha = if (idle) 0.5f else 1f)
            }
        }
        else -> Unit
    }
}

/** ЭКГ + пунктирные концентрические дуги (F _buildPulseMask, vl_series_art.dart:706-724). */
private val PulseMask: Path by lazy {
    val p = svgPath("M300 46H470L486 46L500 18L520 74L538 30L550 46H640L656 46L668 26L684 62L694 46H856")
    for (i in 0 until 5) {
        val r = 120f + i * 46f
        val circle = Path().apply { addOval(Rect(Offset(495f, 358f), r)) }
        val on = r * 0.9f
        val off = r * 0.55f
        val measure = PathMeasure().apply { setPath(circle, false) }
        val len = measure.length
        var pos = -((i * 40f) % (on + off)) // dashoffset сдвигает рисунок назад
        while (pos < len) {
            val a = max(0f, pos)
            val b = min(len, pos + on)
            if (b > a) {
                val seg = Path()
                measure.getSegment(a, b, seg, true)
                p.addPath(seg)
            }
            pos += on + off
        }
    }
    p
}

/** Веа Prism (F _buildFanMask, vl_series_art.dart:726-739). */
private val FanMask: Path by lazy {
    val p = Path()
    val dx = 196f
    val step = 27f
    val sw = 15f
    val x0 = 340f
    for (i in 0 until 7) {
        val x = x0 + i * step
        p.moveTo(x, 0f)
        p.lineTo(x + sw, 0f)
        p.lineTo(x + sw - dx, ART_H)
        p.lineTo(x - dx, ART_H)
        p.close()
    }
    p
}

/** Скрытая солнечная система Lumen: диск, лучи, пунктирные орбиты, планеты (F _buildLumenMask:742-770). */
private val LumenMask: Path by lazy {
    val cx = 430f
    val cy = 270f
    val p = Path().apply { addOval(Rect(Offset(cx, cy), 62f)) }
    val stroke = Path()
    for (i in 0 until 48) {
        val a = i / 48f * PI.toFloat() * 2f
        val r0 = if (i % 2 == 1) 100f else 92f
        val r1 = if (i % 4 == 0) 170f else 140f
        stroke.moveTo(cx + r0 * cos(a), cy + r0 * sin(a))
        stroke.lineTo(cx + r1 * cos(a), cy + r1 * sin(a))
    }
    val orbits = listOf(210f, 262f, 330f, 410f)
    for ((i, r) in orbits.withIndex()) {
        val circle = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
        val on = if (i % 2 == 1) 3f else r * 1.1f
        val off = if (i % 2 == 1) 9f else r * 0.4f
        val measure = PathMeasure().apply { setPath(circle, false) }
        val len = measure.length
        var pos = 0f
        while (pos < len) {
            val seg = Path()
            measure.getSegment(pos, min(len, pos + on), seg, true)
            stroke.addPath(seg)
            pos += on + off
        }
    }
    val stroked = strokeToFill(stroke, 2.2f)
    val planets = listOf(
        Triple(0.5f, 210f, 9f), Triple(2.2f, 262f, 6f), Triple(3.9f, 330f, 12f),
        Triple(5.3f, 410f, 7f), Triple(1.3f, 330f, 4f),
    )
    for ((a, r, pr) in planets) {
        p.addOval(Rect(Offset(cx + r * cos(a), cy + r * sin(a)), pr))
    }
    Path().apply { op(p, stroked, PathOperation.Union) }
}

/** Контур обводки как заливка: тонкие пути → «капсулы» нужной толщины (F _strokeToFill:773-786). */
private fun strokeToFill(src: Path, width: Float): Path {
    val out = Path()
    val measure = PathMeasure().apply { setPath(src, false) }
    val len = measure.length
    val step = 4f
    var d = 0f
    while (d < len) {
        val p = measure.getPosition(d)
        val tangent = measure.getTangent(d)
        val p2 = measure.getPosition(min(len, d + step))
        if (tangent != Offset.Unspecified) {
            val nx = -tangent.y * (width / 2f)
            val ny = tangent.x * (width / 2f)
            out.moveTo(p.x + nx, p.y + ny)
            out.lineTo(p2.x + nx, p2.y + ny)
            out.lineTo(p2.x - nx, p2.y - ny)
            out.lineTo(p.x - nx, p.y - ny)
            out.close()
        }
        d += step
    }
    return out
}

// ── Голограммы серии VL ──

internal fun vlHoloMask(pattern: IdPattern): Path = when (pattern) {
    IdPattern.VL_BLACKOUT -> Path().apply {
        // Octagon
        moveTo(30f, 3f); lineTo(70f, 3f); lineTo(97f, 30f); lineTo(97f, 70f)
        lineTo(70f, 97f); lineTo(30f, 97f); lineTo(3f, 70f); lineTo(3f, 30f); close()
    }
    IdPattern.VL_PULSE -> Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(4f, 4f, 96f, 96f), androidx.compose.ui.geometry.CornerRadius(27f)))
    }
    IdPattern.VL_PRISM -> Path().apply {
        // Pentagon
        moveTo(26f, 5f); lineTo(74f, 5f); lineTo(97f, 37f); lineTo(50f, 96f); lineTo(3f, 37f); close()
    }
    IdPattern.VL_BUDDY -> Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(3f, 14f, 97f, 86f), androidx.compose.ui.geometry.CornerRadius(24f)))
    }
    IdPattern.VL_PRO_AURUM -> Path().apply {
        // 12-лепестковая розетка
        val pts = mutableListOf<Offset>()
        for (i in 0 until 24) {
            val a = (i.toFloat() / 24f) * (PI.toFloat() * 2f) - (PI.toFloat() / 2f)
            val rad = if (i % 2 == 1) 40f else 48f
            pts.add(Offset(50f + rad * cos(a), 50f + rad * sin(a)))
        }
        pts.forEachIndexed { idx, p -> if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
    IdPattern.VL_PRO_NOIR -> Path().apply {
        // Diamond
        moveTo(50f, 2f); lineTo(98f, 50f); lineTo(50f, 98f); lineTo(2f, 50f); close()
    }
    else -> Path().apply { // VL_ION, VL_LUMEN
        addOval(Rect(2f, 2f, 98f, 98f))
    }
}

internal fun vlEmblem(pattern: IdPattern): Path = when (pattern) {
    IdPattern.VL_BLACKOUT -> svgPath("M32 32H68V68H32ZM42 42H58V58H42ZM38 32V22M50 32V22M62 32V22M38 68V78M50 68V78M62 68V78M32 38H22M32 50H22M32 62H22M68 38H78M68 50H78M68 62H78")
    IdPattern.VL_PULSE -> svgPath("M10 52H30L38 28L50 76L60 40L66 52H90")
    IdPattern.VL_PRISM -> svgPath("M3 37H97M26 5L36 37L50 96M74 5L64 37L50 96M36 37L50 5L64 37")
    IdPattern.VL_BUDDY -> svgPath("M24 46H34M66 46H76M38 64Q50 74 62 64")
    IdPattern.VL_PRO_AURUM -> svgPath("M22 66L18 34L36 50L50 26L64 50L82 34L78 66ZM22 76H78M50 40V44")
    IdPattern.VL_PRO_NOIR -> svgPath("M50 14L86 50L50 86L14 50ZM50 30L70 50L50 70L30 50ZM50 42V58M42 50H58")
    IdPattern.VL_LUMEN -> Path().apply {
        addOval(Rect(Offset(50f, 50f), 14f))
        for (i in 0 until 12) {
            val a = (i.toFloat() / 12f) * (PI.toFloat() * 2f)
            moveTo(50f + 24f * cos(a), 50f + 24f * sin(a))
            lineTo(50f + 36f * cos(a), 50f + 36f * sin(a))
        }
    }
    else -> Path().apply { // VL_ION
        addOval(Rect(Offset(50f, 50f), 36f))
        val len = sqrt(34f * 34f - 11f * 11f)
        for (i in 0 until 6) {
            val a = (i.toFloat() / 6f) * (PI.toFloat() * 2f) - (PI.toFloat() / 2f)
            val tx = 50f + 11f * cos(a)
            val ty = 50f + 11f * sin(a)
            moveTo(tx, ty)
            lineTo(tx - sin(a) * len, ty + cos(a) * len)
        }
    }
}

internal data class VlHoloStyle(
    val base: List<Pair<Float, Color>>,
    val rainbow: List<Pair<Float, Color>>,
    val rainbowAlpha: Float,
    val light: Color,
    val darkAlpha: Float
)

internal fun vlHoloStyle(pattern: IdPattern): VlHoloStyle = when (pattern) {
    IdPattern.VL_BLACKOUT -> VlHoloStyle(
        base = listOf(0f to Color(0xFF3A4048), 0.5f to Color(0xFF14171B), 1f to Color(0xFF2A2F36)),
        rainbow = listOf(0f to Color(0xFF3DF2FF), 0.06f to Color(0xFF1B6C78), 0.11f to Color(0xFFFF3D9A), 0.17f to Color(0xFF3DF2FF)),
        rainbowAlpha = 0.8f, light = rgba(230, 255, 255, 0.9f), darkAlpha = 0.55f
    )
    IdPattern.VL_PULSE -> VlHoloStyle(
        base = listOf(0f to Color(0xFF3A2C36), 0.55f to Color(0xFF15101A), 1f to Color(0xFF2B1F29)),
        rainbow = listOf(0f to Color(0xFFFF3D9A), 0.05f to Color(0xFFFFB4DB), 0.10f to Color(0xFF3DF2FF), 0.16f to Color(0xFFFF3D9A)),
        rainbowAlpha = 0.75f, light = rgba(255, 235, 245, 0.95f), darkAlpha = 0.5f
    )
    IdPattern.VL_PRISM -> VlHoloStyle(
        base = listOf(0f to Color(0xFFFFFFFF), 0.45f to Color(0xFFCFE0EE), 0.6f to Color(0xFFF6FAFD), 1f to Color(0xFFB9CCDC)),
        rainbow = RAINBOW_PRISM, rainbowAlpha = 0.8f, light = rgba(255, 255, 255, 0.75f), darkAlpha = 0.28f
    )
    IdPattern.VL_BUDDY -> VlHoloStyle(
        base = listOf(0f to Color(0xFFFFFFFF), 0.45f to Color(0xFFDDE6E7), 0.6f to Color(0xFFF7FBFB), 1f to Color(0xFFC5D2D4)),
        rainbow = listOf(0f to Color(0xFF3DF2FF), 0.05f to Color(0xFFBFFBFF), 0.10f to Color(0xFFFF9BD0), 0.16f to Color(0xFF3DF2FF)),
        rainbowAlpha = 0.7f, light = rgba(255, 255, 255, 0.75f), darkAlpha = 0.28f
    )
    IdPattern.VL_PRO_AURUM -> VlHoloStyle(
        base = listOf(0f to Color(0xFFFFF4DA), 0.45f to Color(0xFFD9B66A), 0.6f to Color(0xFFFFF8E6), 1f to Color(0xFFB88A2E)),
        rainbow = listOf(0f to Color(0xFFC8962E), 0.05f to Color(0xFFFFF1C8), 0.08f to Color(0xFFFFFFFF), 0.12f to Color(0xFFE8C27A), 0.17f to Color(0xFFC8962E)),
        rainbowAlpha = 0.7f, light = rgba(255, 252, 240, 0.95f), darkAlpha = 0.45f
    )
    IdPattern.VL_PRO_NOIR -> VlHoloStyle(
        base = listOf(0f to Color(0xFF3A3122), 0.5f to Color(0xFF0E0C08), 1f to Color(0xFF2E271A)),
        rainbow = listOf(0f to Color(0xFFE2B65A), 0.06f to Color(0xFF6B5326), 0.11f to Color(0xFFF5E6C4), 0.17f to Color(0xFFE2B65A)),
        rainbowAlpha = 0.8f, light = rgba(255, 240, 205, 0.95f), darkAlpha = 0.55f
    )
    IdPattern.VL_LUMEN -> VlHoloStyle(
        base = listOf(0f to Color(0xFF3A2A12), 1f to Color(0xFF120D08)),
        rainbow = listOf(0f to Color(0xFFFFB23D), 0.05f to Color(0xFFFFE9A8), 0.10f to Color(0xFFFF4F6B), 0.16f to Color(0xFFFFB23D)),
        rainbowAlpha = 0.8f, light = rgba(255, 244, 214, 0.95f), darkAlpha = 0.5f
    )
    else -> VlHoloStyle( // VL_ION
        base = listOf(0f to Color(0xFFFFF6EE), 0.45f to Color(0xFFE9DCCF), 0.6f to Color(0xFFFFFFFF), 1f to Color(0xFFD8CBBD)),
        rainbow = listOf(0f to Color(0xFFFF5A1F), 0.05f to Color(0xFFFFD0A8), 0.08f to Color(0xFFFFFFFF), 0.12f to Color(0xFF7FE9FF), 0.17f to Color(0xFFFF5A1F)),
        rainbowAlpha = 0.62f, light = rgba(255, 255, 255, 0.75f), darkAlpha = 0.28f
    )
}

internal fun DrawScope.paintVlHolo(
    pattern: IdPattern,
    tilt: CardTiltState,
    sweep: Float,
    alpha: Float
) {
    val st = vlHoloStyle(pattern)
    val s = size.width / 100f
    val mask = vlHoloMask(pattern)

    scale(s, s, pivot = Offset.Zero) {
        clipPath(mask) {
            drawRect(
                cssLinear(135f, st.base, Size(100f, 100f)),
                alpha = alpha
            )
            val box = Size(300f, 300f)
            val fx = tilt.fx
            val fy = tilt.fy
            val rainbowOrigin = Offset(-100f + (-fx * 0.16f + 0.3f * sweep) * box.width, -100f + (-fy * 0.13f + 0.22f * sweep) * box.height)
            drawRect(
                cssRepeating(118f, st.rainbow, box, rainbowOrigin),
                alpha = alpha * st.rainbowAlpha
            )
            // Бегущий блик 115° (F vl_series_art.dart:1059-1062): у Blackout — cyan
            val glintOrigin = Offset(-100f - fx * 0.34f * box.width, -100f + fy * 0.26f * box.height)
            val glintColor = if (pattern == IdPattern.VL_BLACKOUT) rgba(160, 255, 255, 0.85f) else Color.White.copy(alpha = 0.8f)
            drawRect(
                cssLinear(
                    115f,
                    listOf(
                        0f to Color.Transparent, 0.44f to Color.Transparent, 0.5f to glintColor,
                        0.56f to Color.Transparent, 1f to Color.Transparent,
                    ),
                    box, glintOrigin,
                ),
                alpha = alpha,
            )

            val emblem = vlEmblem(pattern)
            translate(0.8f, 0.9f) {
                drawPath(
                    emblem,
                    if (pattern == IdPattern.VL_PRO_AURUM) rgba(90, 62, 14, st.darkAlpha) else Color.Black.copy(alpha = st.darkAlpha),
                    style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    alpha = alpha
                )
            }
            drawPath(
                emblem,
                st.light,
                style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                alpha = alpha
            )
        }
    }
}
