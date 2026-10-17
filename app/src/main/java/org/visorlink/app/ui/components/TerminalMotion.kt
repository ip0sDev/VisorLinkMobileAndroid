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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            // Курсор — высота одной строки: под заголовком может стоять подзаголовок («36 чатов»),
            // и курсор высотой в весь слот закрывал обе строки
            val line = minOf(size.height, 30.dp.toPx())
            val h = minOf(line * 0.62f, 20.dp.toPx())
            val w = 0.5f * h
            drawRect(
                color = cursorColor.copy(alpha = cursorAlpha),
                topLeft = Offset((edge + 2.dp.toPx()).coerceAtMost(size.width - w), (line - h) / 2),
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

/**
 * Прогресс «трассировки» (0 → 1) для [org.visorlink.app.ui.theme.vlTraceBorder]: стартует при смене
 * [key] (появление панели, фокус поля). Без терминальной анимации (другая тема, «убрать анимации»)
 * сразу 1 — эффект не рисуется. Часы скриншот-тестов добегают до 1 за 1,5 с.
 */
@Composable
fun rememberTrace(key: Any?, enabled: Boolean = true, durationMs: Int = 700): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val on = enabled && VlTheme.tokens.glitchMotion
    val anim = remember(key, on) { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(key, on) { if (on) anim.animateTo(1f, TerminalMotion.tween(durationMs)) }
    return anim
}

/**
 * Линейка: тонкая линия с засечками (каждая пятая выше), гаснущая вправо. Разделитель заголовков
 * разделов Forge v2 вместо простой линии.
 */
@Composable
fun TerminalRuler(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .height(8.dp)
            .drawBehind {
                val step = 6.dp.toPx()
                val base = size.height - 1.dp.toPx()
                var i = 0
                while (i * step < size.width) {
                    val x = i * step
                    val fade = 1f - x / size.width * 0.9f
                    val tick = if (i % 5 == 0) 6.dp.toPx() else 3.dp.toPx()
                    drawRect(color.copy(alpha = 0.5f * fade), Offset(x, base - tick), Size(1.dp.toPx(), tick))
                    i++
                }
                drawRect(color.copy(alpha = 0.4f), Offset(0f, base), Size(size.width, 1.dp.toPx()))
            }
    )
}

/**
 * Строка состояния терминала над заголовком верхней панели: метка режима и реальный статус сети.
 * Время не показываем: оно меняло бы скриншоты каждую минуту.
 */
@Composable
fun TerminalStatusStrip(modifier: Modifier = Modifier) {
    val terminal = VlTheme.tokens.terminal ?: return
    val monitor = org.koin.compose.getKoin().getOrNull<org.visorlink.app.utils.NetworkMonitor>()
    val online = monitor?.isOnline?.collectAsStateWithLifecycle()?.value ?: true
    androidx.compose.material3.Text(
        text = "VL//${terminal.tag}  NET:${if (online) "OK" else "OFF"}",
        modifier = modifier,
        color = terminal.neon.copy(alpha = if (online) 0.55f else 0.9f),
        style = org.visorlink.app.ui.theme.ForgeV2SectionLabel.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
        maxLines = 1,
    )
}

/**
 * Служебный код терминала: порядковый номер сообщения (seq) в шестнадцатеричном виде, `0x002F`.
 * Только в Forge v2 и только для настоящего seq (> 0) — выдуманных кодов нет.
 */
@Composable
fun TerminalSeqTag(seq: Long?, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    if (VlTheme.tokens.terminal == null || seq == null || seq <= 0L) return
    androidx.compose.material3.Text(
        text = "0x%04X".format(seq),
        modifier = modifier,
        color = color,
        style = org.visorlink.app.ui.theme.ForgeV2SectionLabel.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
        maxLines = 1,
    )
}

/**
 * Стиль для данных (время, даты, счётчики) в терминале: моноширинный с табличными цифрами.
 * В остальных темах стиль не меняется. Контраст «крупный дисплейный заголовок — мелкие моно-данные»
 * и даёт ощущение терминала, без декоративных добавок.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun androidx.compose.ui.text.TextStyle.terminalData(): androidx.compose.ui.text.TextStyle {
    val tokens = VlTheme.tokens
    if (tokens.terminal == null) return this
    return copy(fontFamily = tokens.data.dataSmall.fontFamily, letterSpacing = 0.4.sp, fontFeatureSettings = "tnum")
}
