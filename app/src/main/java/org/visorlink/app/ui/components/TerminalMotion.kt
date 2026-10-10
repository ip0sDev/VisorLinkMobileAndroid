package org.visorlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme

/**
 * Терминальное движение Forge v2 — противоположность жидкому Biolume.
 *
 * Жидкость течёт, пружинит и тянется; терминал движется точно и ровно: без перелётов,
 * растяжений и отскоков, с плавным торможением. Киберпанк — в виде (печать заголовков за
 * курсором, расшифровка подписей, неоновая линия развёртки), а не в тряске: ничего не
 * дрожит и не мерцает, приложение остаётся плавным.
 * Включается флагом `VlMotionTokens.glitch` (см. `VlTokens.glitchMotion`), при системном
 * «убрать анимации» не используется.
 */
object TerminalMotion {

    /**
     * Кривая терминала: резкий старт и долгое мягкое торможение, без перелёта.
     * Отличие от Biolume — не в рывках (приложение должно оставаться плавным), а в характере:
     * жидкость пружинит и тянется, терминал точно «доводит» элемент на место.
     */
    val Easing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Переход терминала: без пружины и перелёта, с плавным торможением. */
    fun <T> tween(durationMs: Int, delayMs: Int = 0): TweenSpec<T> =
        androidx.compose.animation.core.tween(durationMs, delayMs, Easing)
}

/**
 * Заголовок «печатается» в терминале: текст плавно открывается слева направо за блочным
 * курсором, в начале по краям сходятся мягкие RGB-тени (хроматическая аберрация ЭЛТ), в конце
 * курсор плавно гаснет. Работает с любым содержимым слота, не только со строкой.
 * Вне Forge v2 (и при «убрать анимации») — no-op.
 *
 * @param key при смене ключа эффект проигрывается заново (например, сменился заголовок).
 */
@Composable
fun Modifier.terminalTitleReveal(key: Any? = Unit): Modifier {
    val tokens = VlTheme.tokens
    if (!tokens.glitchMotion) return this
    val cs = MaterialTheme.colorScheme
    val cursorColor = cs.primary
    val ghostA = cs.primary
    val ghostB = cs.secondary
    val reveal = remember(key) { Animatable(0f) }
    val blink = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        reveal.animateTo(1f, tween(520, easing = TerminalMotion.Easing))
        // Курсор плавно гаснет: в покое ничего не горит и не мигает (правило §10)
        blink.animateTo(1f, tween(420, easing = LinearEasing))
    }
    return this.drawWithContent {
        val p = reveal.value
        if (p >= 1f && blink.value >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val edge = size.width * p
        // RGB-расслоение в первой половине раскрытия
        if (p < 0.6f) {
            val shift = (0.6f - p) / 0.6f * 4.dp.toPx()
            val a = (0.6f - p) / 0.6f * 0.55f
            listOf(ghostA to -shift, ghostB to shift).forEach { (c, dx) ->
                val paint = Paint().apply {
                    colorFilter = ColorFilter.tint(c, BlendMode.SrcIn)
                    alpha = a
                }
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
                clipRect(right = edge) { translate(left = dx) { this@drawWithContent.drawContent() } }
                drawContext.canvas.restore()
            }
        }
        clipRect(right = edge) { this@drawWithContent.drawContent() }
        // Блочный курсор на краю: во время печати горит, после — плавно гаснет
        val cursorAlpha = if (p < 1f) 0.85f else 0.85f * (1f - blink.value)
        if (cursorAlpha > 0f) {
            val h = size.height * 0.62f
            val w = 0.55f * h
            drawRect(
                color = cursorColor.copy(alpha = cursorAlpha),
                topLeft = Offset((edge + 2.dp.toPx()).coerceAtMost(size.width - w), (size.height - h) / 2),
                size = Size(w, h),
            )
        }
    }
}

/**
 * Текст «расшифровывается», как в терминале: символы встают на место слева направо,
 * а перед ними бежит полоса случайных знаков. Для подписей HUD (заголовки разделов Forge v2).
 * Вне Forge v2 — обычный [Text].
 */
@Composable
fun TerminalDecodeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
) {
    val glitch = VlTheme.tokens.glitchMotion
    if (!glitch) {
        androidx.compose.material3.Text(text, modifier = modifier, color = color, style = style)
        return
    }
    val progress = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        progress.animateTo(1f, androidx.compose.animation.core.tween(32 * text.length + 200, delayMs, LinearEasing))
    }
    val p = progress.value
    val shown = if (p >= 1f) text else buildString {
        val settled = (p * (text.length + BAND)).toInt() - BAND
        text.forEachIndexed { i, ch ->
            append(
                when {
                    ch == ' ' || i < settled -> ch
                    i < settled + BAND -> NOISE[(i * 7 + (p * 60).toInt()).mod(NOISE.length)]
                    else -> ' '
                }
            )
        }
    }
    // Ширина не прыгает: моноширинный шрифт, пустые места — пробелы
    androidx.compose.material3.Text(shown, modifier = modifier, color = color, style = style, maxLines = 1)
}

private const val BAND = 2
private const val NOISE = "#/<>_=+*01░▒▓"
