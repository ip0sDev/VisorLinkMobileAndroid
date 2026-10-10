package org.visorlink.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdFinish
import org.visorlink.app.data.idcard.IdMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Материалы ID-карты — порт `cardPalette` из src/components/idcard/cardStyle.js.
 * Цвета здесь — пластик и краска, а не тема приложения: карта выглядит одинаково в
 * любой теме и у любого смотрящего, поэтому `Color(0x…)` живут тут, а не в ui/theme.
 */
internal const val ART_W = 856f
internal const val ART_H = 540f

/** Слой фона карты: CSS-градиент в координатах размера карты. */
internal sealed interface BaseLayer {
    /** `linear-gradient(<angle>deg, …)`. */
    data class Linear(val angleDeg: Float, val stops: List<Pair<Float, Color>>) : BaseLayer

    /** `radial-gradient(<rx%> <ry%> at <cx%> <cy%>, …)`. */
    data class Radial(val rx: Float, val ry: Float, val cx: Float, val cy: Float, val stops: List<Pair<Float, Color>>) : BaseLayer
}

internal enum class CardTone { LIGHT, DARK }

internal data class CardPalette(
    val tone: CardTone,
    /** Слои фона, нижний — последний (как в CSS `background`). */
    val base: List<BaseLayer>,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val line2: Color,
    val band: Color,
    val bandInk: Color,
    val neon: Color,
    val neon2: Color,
    val stamp: Color,
    val edge: Color,
    val photoBg: Color,
    val core: Color,
)

private val STD_HUES = intArrayOf(212, 196, 174, 150, 96, 40, 18, 352, 320, 270, 234, 204)
private val STD_INKS = listOf(hsl(224f, 68f, 38f), hsl(266f, 52f, 40f), hsl(354f, 62f, 42f))
private val PROTO = listOf(
    0xFF3DF2FF to 0xFFFF4FD8, 0xFF3DFFB8 to 0xFF9B6BFF, 0xFF5B9BFF to 0xFF3DF2FF, 0xFFFF3DCB to 0xFF3DF2FF,
    0xFFB6FF3D to 0xFF3DF2FF, 0xFFFFB23D to 0xFFFF4F6B, 0xFFA46BFF to 0xFFFF6BD1, 0xFFFF3D5A to 0xFFFFB23D,
    0xFF6BFFE0 to 0xFF5B9BFF, 0xFFFF7AC8 to 0xFFB28CFF, 0xFFFF7A3D to 0xFFFFE13D, 0xFFC8F6FF to 0xFF3DF2FF,
)
private val BEAST = listOf(
    0xFF2A160C to 0xFFFF8A3D, 0xFF151A22 to 0xFFA9BDD4, 0xFF1E2024 to 0xFFE6DCCB, 0xFF26170A to 0xFFFFB03D,
    0xFF150F1C to 0xFFB894FF, 0xFF24180F to 0xFFDDA36A, 0xFF0F1824 to 0xFF7CC6FF, 0xFF22190F to 0xFFE8C27A,
    0xFF0E1D1C to 0xFF5FD8BC, 0xFF2A120E to 0xFFFF6E52, 0xFF211C11 to 0xFFE3C664, 0xFF24111A to 0xFFFF94BC,
)

/** `hsl(h s% l% / a)`; оттенок за 360 (h + 36) заворачивается, как в CSS. */
internal fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s / 100f, l / 100f, a)

/**
 * `color-mix(in srgb, a p%, b)`; с `transparent` — тот же цвет с альфой p.
 *
 * Смешивание — покомпонентно в sRGB, как в CSS. Не Compose `lerp`: он смешивает в Oklab, и
 * каждый оттенок пластика и краски уходил от веба на несколько единиц (#DCE1E6 вместо #DFE3E7).
 */
internal fun mix(a: Color, pct: Float, b: Color?): Color {
    if (b == null) return a.copy(alpha = a.alpha * pct / 100f)
    val t = pct / 100f
    // Полупрозрачные операнды смешиваются премультиплицированными, как в CSS Color 5
    val alpha = a.alpha * t + b.alpha * (1f - t)
    if (alpha <= 0f) return Color.Transparent
    fun ch(ca: Float, cb: Float) = ((ca * a.alpha * t + cb * b.alpha * (1f - t)) / alpha).coerceIn(0f, 1f)
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), alpha)
}

internal fun cardPalette(mode: IdMode, variant: Int, finish: IdFinish): CardPalette {
    val v = ((variant % 12) + 12) % 12
    return when (mode) {
        IdMode.PROTOGEN -> {
            val (p, s) = if (finish == IdFinish.GOLD) Color(0xFFFFC94D) to Color(0xFFFF7A3D)
            else PROTO[v].let { Color(it.first) to Color(it.second) }
            val glow = listOf(
                BaseLayer.Radial(1.2f, 1f, 1f, 0f, listOf(0f to mix(p, 16f, null), 0.55f to Color.Transparent)),
                BaseLayer.Radial(0.9f, 0.8f, 0f, 1f, listOf(0f to mix(s, 11f, null), 0.6f to Color.Transparent)),
            )
            val base = when (finish) {
                IdFinish.METALLIC -> glow + BaseLayer.Linear(160f, listOf(0f to Color(0xFF222831), 0.55f to Color(0xFF0E1116), 1f to Color(0xFF1C2129)))
                IdFinish.OBSIDIAN -> listOf(BaseLayer.Linear(160f, listOf(0f to Color(0xFF060708), 1f to Color(0xFF000000))))
                else -> glow + BaseLayer.Linear(160f, listOf(0f to Color(0xFF0A0D12), 1f to Color(0xFF05070A)))
            }
            CardPalette(
                tone = CardTone.DARK, base = base,
                ink = mix(p, 12f, Color(0xFFF4FBFF)), muted = mix(p, 50f, Color(0xFF7E8A99)),
                line = mix(p, 30f, null), line2 = mix(s, 26f, null),
                band = p, bandInk = p, neon = p, neon2 = s, stamp = s,
                edge = mix(p, 42f, null), photoBg = mix(p, 10f, Color(0xFF0B0E13)), core = Color(0xFFC9D0D8),
            )
        }

        IdMode.BEAST -> {
            val bg = Color(BEAST[v].first)
            val a = if (finish == IdFinish.GOLD) Color(0xFFE9C46A) else Color(BEAST[v].second)
            val base = when (finish) {
                IdFinish.METALLIC -> listOf(BaseLayer.Linear(150f, listOf(0f to mix(a, 12f, Color(0xFF2A2C30)), 0.55f to Color(0xFF141518), 1f to mix(a, 8f, Color(0xFF24262A)))))
                IdFinish.OBSIDIAN -> listOf(BaseLayer.Linear(150f, listOf(0f to Color(0xFF0D0C0B), 1f to Color(0xFF030303))))
                else -> listOf(
                    BaseLayer.Radial(1.1f, 0.9f, 1f, 0f, listOf(0f to mix(a, 14f, null), 0.6f to Color.Transparent)),
                    BaseLayer.Linear(150f, listOf(0f to mix(a, 6f, bg), 0.6f to bg, 1f to mix(Color.Black, 30f, bg))),
                )
            }
            CardPalette(
                tone = CardTone.DARK, base = base,
                ink = mix(a, 10f, Color(0xFFF6EEE4)), muted = mix(a, 45f, Color(0xFF9C9286)),
                line = mix(a, 22f, null), line2 = mix(a, 12f, null),
                band = a, bandInk = a, neon = a, neon2 = a, stamp = a,
                edge = mix(a, 30f, null), photoBg = mix(a, 12f, Color(0xFF16130F)), core = Color(0xFFD9CFC2),
            )
        }

        IdMode.STANDARD -> {
            val h = if (finish == IdFinish.GOLD) 40f else STD_HUES[v].toFloat()
            if (finish == IdFinish.OBSIDIAN) {
                return CardPalette(
                    tone = CardTone.DARK,
                    base = listOf(
                        BaseLayer.Radial(1.2f, 1f, 1f, 0f, listOf(0f to hsl(h, 40f, 30f, 0.25f), 0.6f to Color.Transparent)),
                        BaseLayer.Linear(135f, listOf(0f to Color(0xFF1A1C21), 1f to Color(0xFF0B0C0F))),
                    ),
                    ink = Color(0xFFECEFF3), muted = hsl(h, 12f, 64f),
                    line = hsl(h, 50f, 70f, 0.16f), line2 = hsl(h + 36, 50f, 70f, 0.12f),
                    band = hsl(h, 30f, 17f), bandInk = hsl(h, 60f, 80f), neon = hsl(h, 60f, 70f), neon2 = hsl(h, 60f, 70f),
                    stamp = hsl(h, 70f, 72f), edge = Color.White.copy(alpha = 0.14f), photoBg = Color(0xFF22252B), core = Color(0xFF3A3E46),
                )
            }
            val base = when (finish) {
                IdFinish.METALLIC -> BaseLayer.Linear(125f, listOf(0f to hsl(h, 9f, 92f), 0.46f to hsl(h, 7f, 74f), 0.7f to hsl(h, 9f, 89f), 1f to hsl(h, 6f, 79f)))
                IdFinish.GOLD -> BaseLayer.Linear(125f, listOf(0f to Color(0xFFF6E7C1), 0.48f to Color(0xFFDCC07F), 0.72f to Color(0xFFF1DDA6), 1f to Color(0xFFCFAE68)))
                else -> BaseLayer.Linear(135f, listOf(0f to hsl(h, 32f, 95f), 1f to hsl(h, 24f, 86f)))
            }
            val gold = finish == IdFinish.GOLD
            CardPalette(
                tone = CardTone.LIGHT, base = listOf(base),
                ink = if (gold) Color(0xFF2B2110) else hsl(h, 32f, 14f),
                muted = if (gold) Color(0xFF6E5A32) else hsl(h, 14f, 37f),
                line = if (gold) Color(0x476E5014) else hsl(h, 42f, 36f, 0.26f),
                line2 = hsl(h + 36, 40f, 42f, 0.2f),
                band = if (gold) Color(0xFF5E4514) else hsl(h, 46f, 30f), bandInk = Color.White,
                neon = hsl(h, 46f, 30f), neon2 = hsl(h, 46f, 30f), stamp = STD_INKS[v % 3],
                edge = Color.White.copy(alpha = 0.7f), photoBg = hsl(h, 14f, 80f), core = Color.White,
            )
        }
    }
}

// ── Кастомный скин (подарок админа): палитра из трёх HEX — cardStyle.customPalette ──

/** Порог яркости: ниже на цвете лучше читается белое, выше — тёмное (равный контраст ≈ 0.18). */
private const val INK_SWITCH = 0.18

/** Относительная яркость WCAG по HEX — как `luminance(hex)` в cardStyle.js. */
internal fun hexLuminance(hex: String): Double {
    fun f(i: Int): Double {
        val c = hex.substring(i, i + 2).toInt(16) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * f(1) + 0.7152 * f(3) + 0.0722 * f(5)
}

private fun hexColor(hex: String) = Color(hex.substring(1).toLong(16) or 0xFF000000)

/**
 * Палитра кастомного скина: фон — пластик, основной — шапка, линии и неон, дополнительный —
 * узор, штамп и второй неон. Тон — по яркости фона. Обсидиан затемняет пластик, металл и
 * золото дают полосы блеска (у золота — тёплые). Режим карты (раскладка, графика) не меняется.
 */
internal fun customPalette(base: String, primary: String, secondary: String, finish: IdFinish): CardPalette {
    val p = hexColor(primary)
    val s = hexColor(secondary)
    val bg = hexColor(base)
    val plastic = if (finish == IdFinish.OBSIDIAN) mix(bg, 24f, Color(0xFF050607)) else bg
    val dark = finish == IdFinish.OBSIDIAN || hexLuminance(base) < INK_SWITCH
    val shiny = finish == IdFinish.METALLIC || finish == IdFinish.GOLD
    val hi = mix(if (finish == IdFinish.GOLD) Color(0xFFFFE9A8) else Color.White, if (shiny) (if (dark) 18f else 40f) else (if (dark) 8f else 30f), plastic)
    val lo = mix(Color.Black, if (dark) 32f else 14f, plastic)
    val glow = listOf(
        BaseLayer.Radial(1.2f, 1f, 1f, 0f, listOf(0f to mix(p, if (dark) 18f else 12f, null), 0.55f to Color.Transparent)),
        BaseLayer.Radial(0.9f, 0.8f, 0f, 1f, listOf(0f to mix(s, if (dark) 12f else 9f, null), 0.6f to Color.Transparent)),
    )
    val baseLayers = if (shiny) {
        listOf(BaseLayer.Linear(125f, listOf(0f to hi, 0.46f to lo, 0.7f to hi, 1f to plastic)))
    } else {
        glow + BaseLayer.Linear(150f, listOf(0f to hi, 0.55f to plastic, 1f to lo))
    }
    return CardPalette(
        tone = if (dark) CardTone.DARK else CardTone.LIGHT,
        base = baseLayers,
        ink = if (dark) mix(p, 10f, Color(0xFFF4F7FA)) else mix(p, 14f, Color(0xFF101418)),
        muted = if (dark) mix(p, 40f, Color(0xFF8E98A4)) else mix(p, 22f, Color(0xFF4F5964)),
        line = mix(p, 30f, null), line2 = mix(s, 26f, null),
        band = p, bandInk = if (hexLuminance(primary) < INK_SWITCH) Color.White else Color(0xFF101418),
        neon = p, neon2 = s, stamp = s,
        edge = if (dark) mix(p, 36f, null) else Color.White.copy(alpha = 0.7f),
        photoBg = mix(p, if (dark) 12f else 16f, plastic),
        core = if (dark) Color(0xFFC9D0D8) else Color.White,
    )
}

// ── CSS-градиенты в Compose ──

/**
 * `linear-gradient(<angle>deg, …)`: 0° — вверх, 90° — вправо; длина линии градиента
 * |w·sin a| + |h·cos a|, как в спецификации CSS.
 */
internal fun cssLinear(angleDeg: Float, stops: List<Pair<Float, Color>>, size: Size, origin: Offset = Offset.Zero, tile: TileMode = TileMode.Clamp): Brush {
    val a = angleDeg * PI.toFloat() / 180f
    val dx = sin(a)
    val dy = -cos(a)
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val c = origin + Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        colorStops = stops.toTypedArray(),
        start = c - Offset(dx * half, dy * half),
        end = c + Offset(dx * half, dy * half),
        tileMode = tile,
    )
}

/** Повторяющийся `repeating-linear-gradient`: стопы в долях длины линии градиента. */
internal fun cssRepeating(angleDeg: Float, stops: List<Pair<Float, Color>>, size: Size, origin: Offset = Offset.Zero): Brush {
    val period = stops.last().first
    val a = angleDeg * PI.toFloat() / 180f
    val dx = sin(a)
    val dy = -cos(a)
    val len = abs(size.width * dx) + abs(size.height * dy)
    val c = origin + Offset(size.width / 2f, size.height / 2f)
    val start = c - Offset(dx * len / 2f, dy * len / 2f)
    return Brush.linearGradient(
        colorStops = stops.map { (p, col) -> p / period to col }.toTypedArray(),
        start = start,
        end = start + Offset(dx * len * period, dy * len * period),
        tileMode = TileMode.Repeated,
    )
}

/** Слои фона карты на весь [DrawScope]. */
internal fun DrawScope.drawBase(layers: List<BaseLayer>) {
    for (layer in layers.asReversed()) when (layer) {
        is BaseLayer.Linear -> drawRect(cssLinear(layer.angleDeg, layer.stops, size))
        is BaseLayer.Radial -> {
            val rx = layer.rx * size.width
            val ry = layer.ry * size.height
            val center = Offset(layer.cx * size.width, layer.cy * size.height)
            // Эллипс — круг радиуса rx, сжатый по вертикали
            scale(1f, ry / rx, pivot = center) {
                drawRect(
                    Brush.radialGradient(colorStops = layer.stops.toTypedArray(), center = center, radius = rx),
                    topLeft = Offset(0f, center.y - (center.y) * rx / ry),
                    size = Size(size.width, size.height * rx / ry),
                )
            }
        }
    }
}

// ── Статичные текстуры (веб: canvas-текстуры один раз на страницу) ──

private fun valueNoise(random: () -> Double, g: Int): (Double, Double) -> Double {
    val grid = DoubleArray(g * g) { random() }
    fun at(i: Int, j: Int) = grid[((j % g) + g) % g * g + ((i % g) + g) % g]
    return { u, v ->
        val x = u * g
        val y = v * g
        val i = floor(x).toInt()
        val j = floor(y).toInt()
        val fx = x - i
        val fy = y - j
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val top = at(i, j) + (at(i + 1, j) - at(i, j)) * sx
        val bottom = at(i, j + 1) + (at(i + 1, j + 1) - at(i, j + 1)) * sx
        top + (bottom - top) * sy
    }
}

private fun makeTexture(ink: Boolean): ImageBitmap {
    val size = 128
    val pixels = IntArray(size * size)
    val random = IdCardGenerator.rng(if (ink) 0x1A7 else 0x6A1)
    val low = valueNoise(random, 5)
    val high = valueNoise(random, 26)
    for (y in 0 until size) for (x in 0 until size) {
        val u = x / size.toDouble()
        val v = y / size.toDouble()
        pixels[y * size + x] = if (ink) {
            // Краска ложится неровно: пятна нажима, непрокрасы и редкие крапины
            val press = 0.62 + low(u, v) * 0.5
            val gap = if (high(u, v) < 0.24) 0.2 else 1.0
            val speck = if (random() < 0.05) 0.25 else 1.0
            val a = Math.round(255 * min(1.0, press) * gap * speck).toInt()
            a shl 24
        } else {
            val n = random()
            val c = if (n < 0.5) 0 else 255
            val a = Math.round(abs(n - 0.5) * 2 * 70 * (0.5 + low(u, v))).toInt().coerceIn(0, 255)
            (a shl 24) or (c shl 16) or (c shl 8) or c
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

internal object CardTextures {
    val grain: ImageBitmap by lazy { makeTexture(ink = false) }
    val ink: ImageBitmap by lazy { makeTexture(ink = true) }
}

/** `Color.compositeOver` для полупрозрачной заливки поверх непрозрачной. */
internal fun Color.over(bg: Color) = compositeOver(bg)

internal fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
