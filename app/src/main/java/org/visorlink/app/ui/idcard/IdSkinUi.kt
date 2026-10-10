package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdSkinLook
import org.visorlink.app.data.idcard.IdSkinRules
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardException
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.VlIdCard
import org.visorlink.app.ui.components.idcard.editionName
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Общие части инвентаря и обмена скинами (веб: idSkinUi.jsx, idSkinHelpers.js): превью скина на
 * карте смотрящего, значок тиража, искры, «±Bits», оверлей, тексты ошибок и отсчёт ожидания.
 */

// ── Время ──

/** Оставшееся до [until] время (мс), обновляется раз в секунду, пока есть что ждать. */
@Composable
internal fun rememberCountdown(until: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= until) break
            delay(1000)
        }
    }
    return max(0, until - now)
}

/** «12 с» / «5 ч» / «3 дн» — как formatWait в вебе. */
@Composable
internal fun formatWait(ms: Long): String {
    val s = ceil(ms / 1000.0).toInt()
    if (s < 90) return stringResource(R.string.idcard_wait_seconds, s)
    val h = ceil(s / 3600.0).toInt()
    if (h < 48) return stringResource(R.string.idcard_wait_hours, max(1, h))
    return stringResource(R.string.idcard_wait_days, ceil(h / 24.0).toInt())
}

// ── Ошибки ──

/** Текст ошибки callable инвентаря / обмена (`idskin.err.<reason>`); [cost] — сколько Bits не хватило. */
@Composable
internal fun skinErrorText(error: Throwable, cost: Int = IdSkinRules.ROLL_COST_BITS): String {
    val idError = error as? IdCardException
    val res = when (idError?.reason) {
        "cooldown" -> return stringResource(R.string.idcard_tab_err_cooldown, formatWait(idError?.waitMs ?: 0))
        "not-enough-bits" -> return stringResource(R.string.idcard_tab_err_bits, cost)
        "no-slot" -> R.string.idskin_err_no_slot
        "max-slots" -> R.string.idskin_err_max_slots
        "equipped" -> R.string.idskin_err_equipped
        "already-listed" -> R.string.idskin_err_already_listed
        "trade-closed" -> R.string.idskin_err_trade_closed
        "offer-gone" -> R.string.idskin_err_offer_gone
        "owner-skin-gone" -> R.string.idskin_err_owner_skin_gone
        "offer-skin-gone" -> R.string.idskin_err_offer_skin_gone
        "self-trade" -> R.string.idskin_err_self_trade
        "chat-type" -> R.string.idskin_err_chat_type
        "no-skin" -> R.string.idskin_err_no_skin
        "no-trade" -> R.string.idskin_err_no_trade
        "outdated" -> R.string.idskin_err_outdated
        "no-card" -> R.string.idskin_picker_no_card
        else -> null
    }
    return res?.let { stringResource(it) }
        ?: error.message?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.idcard_tab_err_generic)
}

// ── Кто примеряет скин ──

/** Своя карта как «примерочная»: режим, вид, дата регистрации. [masked] — Mask Mode: режим Standard. */
internal fun IdCard?.wearer(profile: UserProfile?, masked: Boolean = false): IdSkinWearer = IdSkinWearer(
    mode = if (!masked && this != null) mode else org.visorlink.app.data.idcard.IdMode.STANDARD,
    species = if (!masked) this?.species else null,
    registeredAt = this?.registeredAt?.takeIf { it > 0 } ?: profile?.registeredAtMs() ?: 0,
)

// ── Превью скина ──

/**
 * Часы превью: у снимка без даты выпуска «Выдана» — сегодня. Скриншот-тесты подставляют
 * фиксированное время, иначе эталоны расходились бы каждые сутки.
 */
internal val LocalIdCardClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/**
 * Скин в виде карты [wearer] (так он будет выглядеть у смотрящего). Без [width] — по ширине
 * контейнера. [mintedAt] — дата «Выдана»; у снимка в обмене её нет, тогда — сегодня.
 */
@Composable
internal fun IdSkinThumb(
    look: IdSkinLook,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    modifier: Modifier = Modifier,
    mintedAt: Long = 0,
    interactive: Boolean = false,
    width: Dp? = null,
) {
    val clock = LocalIdCardClock.current
    val now = remember(clock) { clock() }
    val card = remember(look, wearer, mintedAt) {
        look.asCard(
            wearer.copy(registeredAt = wearer.registeredAt.takeIf { it > 0 } ?: now),
            issuedAt = mintedAt.takeIf { it > 0 } ?: now,
        )
    }
    if (width != null) {
        VlIdCard(card, person, modifier, width = width, interactive = interactive)
    } else {
        BoxWithConstraints(modifier) { VlIdCard(card, person, width = maxWidth, interactive = interactive) }
    }
}

// ── Тираж ──

/** Цвет текста тиража: на светлой теме темнее (`color-mix(--ed 62%, #000)`). */
@Composable
internal fun editionInk(edition: IdEdition, darken: Float = 0.62f): Color {
    val ed = IdCardMaterials.edition(edition)
    return if (VlTheme.tokens.isDark) ed else lerp(Color.Black, ed, darken)
}

@Composable
internal fun EditionChip(edition: IdEdition, modifier: Modifier = Modifier, large: Boolean = false) {
    val fg = editionInk(edition)
    val shape = VlTheme.tokens.shapes.pill
    val legendary = edition == IdEdition.LEGENDARY
    val shimmer = if (legendary && !VlTheme.tokens.reduceMotion) {
        rememberInfiniteTransition(label = "edShimmer")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "edShimmerX").value
    } else 0f
    Row(
        modifier
            .height(if (large) 26.dp else 20.dp)
            .clip(shape)
            .drawBehind {
                if (legendary) {
                    // background-size 200%: полоса переливается слева направо
                    val w = size.width * 2f
                    val x = size.width * (2f - 3f * shimmer)
                    drawRect(
                        Brush.linearGradient(
                            listOf(IdCardMaterials.edition(edition).copy(alpha = 0.22f), IdCardMaterials.LegendaryHalo[1].copy(alpha = 0.3f), IdCardMaterials.edition(edition).copy(alpha = 0.22f)),
                            start = Offset(x - w, 0f), end = Offset(x, 0f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                        ),
                    )
                } else {
                    drawRect(IdCardMaterials.editionSoft(edition))
                }
            }
            .padding(horizontal = if (large) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(fg, VlTheme.tokens.shapes.indicator))
        Text(
            editionName(edition).uppercase(),
            color = fg,
            fontSize = if (large) 12.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (if (large) 12 else 10).sp * 0.08f,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** «Надета» ✓ / «На обмене» ⇄. Без [label] — только значок. */
@Composable
internal fun SkinBadge(equipped: Boolean, label: String? = null) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val fg = if (equipped) cs.primary else tokens.status.warning
    val bg = if (equipped) tokens.selectionFill else tokens.status.warning.copy(alpha = 0.18f)
    val desc = stringResource(if (equipped) R.string.idskin_equipped else R.string.idskin_listed)
    Row(
        Modifier
            .height(20.dp)
            .clip(tokens.shapes.pill)
            .background(bg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (equipped) Icons.Default.Check else Icons.Default.SwapHoriz,
            contentDescription = if (label == null) desc else null,
            tint = fg,
            modifier = Modifier.size(13.dp),
        )
        if (label != null) Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Полоса цвета тиража снизу плитки (у epic/legendary — со свечением). */
@Composable
internal fun BoxScope.EditionStrip(edition: IdEdition, height: Dp = 3.dp, inset: Dp = 14.dp) {
    val ed = IdCardMaterials.edition(edition)
    val glow = edition == IdEdition.EPIC || edition == IdEdition.LEGENDARY
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(height)
            .drawBehind {
                if (glow) {
                    for (i in 1..4) drawRect(ed.copy(alpha = 0.12f), Offset(0f, -i * 2.dp.toPx()), Size(size.width, size.height + i * 2.dp.toPx()))
                }
                drawRect(ed.copy(alpha = 0.85f))
            },
    )
}

// ── Эффекты ──

/** CSS-кривые эффектов скинов. */
internal object SkinEasing {
    val Spark = CubicBezierEasing(0.1f, 0.8f, 0.3f, 1f)
    val Reel = CubicBezierEasing(0.06f, 0.62f, 0.1f, 1f)
    val Settle = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)
    val CardIn = CubicBezierEasing(0.18f, 1.25f, 0.4f, 1f)
    val Letter = CubicBezierEasing(0.2f, 1.6f, 0.4f, 1f)
    val Swap = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)
    val Shock = CubicBezierEasing(0.1f, 0.7f, 0.3f, 1f)
}

/** Доля отрезка [delay, delay + dur] (секунды) на шкале [t], 0…1. */
internal fun seg(t: Float, delay: Float, dur: Float): Float = ((t - delay) / dur).coerceIn(0f, 1f)

/** Количество искр раскрытия по тиражу. */
internal fun sparkCount(edition: IdEdition): Int = when (edition) {
    IdEdition.COMMON -> 10
    IdEdition.UNCOMMON -> 18
    IdEdition.RARE -> 26
    IdEdition.EPIC -> 36
    IdEdition.LEGENDARY -> 56
}

private class Spark(val tx: Float, val ty: Float, val scale: Float, val dur: Float, val delay: Float, val bar: Boolean)

/**
 * Разлёт искр из центра [modifier] (детерминированно, как Sparks в вебе). [startDelay] — секунды
 * до старта, [coins] — монеты продажи. При «уменьшить анимацию» не рисуется.
 */
@Composable
internal fun Sparks(count: Int, spread: Dp, color: Color, modifier: Modifier = Modifier, startDelay: Float = 0f, coins: Boolean = false) {
    if (VlTheme.tokens.reduceMotion) return
    val sparks = remember(count, spread) {
        List(count) { i ->
            val angle = i.toFloat() / count * 2f * PI.toFloat() + (i % 3) * 0.21f
            val v = spread.value * (0.45f + ((i * 37) % 55) / 100f)
            Spark(cos(angle) * v, sin(angle) * v, 0.5f + ((i * 7) % 10) / 10f, 0.9f + ((i * 13) % 9) / 10f, ((i * 11) % 7) / 40f, !coins && i % 3 == 2)
        }
    }
    val total = remember(sparks) { (sparks.maxOfOrNull { it.dur + it.delay } ?: 0f) + startDelay }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing)) }
    Canvas(modifier) {
        val t = time.value - startDelay
        if (t <= 0f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        for (s in sparks) {
            val p = ((t - s.delay) / s.dur).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val e = SkinEasing.Spark.transform(p)
            val pos = c + Offset(s.tx.dp.toPx(), s.ty.dp.toPx()) * e
            val k = s.scale * (1f - e)
            val alpha = 1f - e
            when {
                coins -> {
                    val r = 5.dp.toPx() * k
                    drawCircle(IdCardMaterials.Coin.copy(alpha = 0.35f * alpha), r * 1.8f, pos)
                    drawCircle(IdCardMaterials.Coin.copy(alpha = alpha), r, pos)
                    drawCircle(IdCardMaterials.CoinEdge.copy(alpha = alpha), r, pos, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
                s.bar -> drawRoundRect(
                    Color.White.copy(alpha = alpha),
                    topLeft = pos - Offset(2.dp.toPx() * k, 6.dp.toPx() * k),
                    size = Size(4.dp.toPx() * k, 12.dp.toPx() * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx() * k),
                )
                else -> {
                    val r = 4.dp.toPx() * k
                    drawCircle(color.copy(alpha = 0.3f * alpha), r * 2.2f, pos)
                    drawCircle(color.copy(alpha = alpha), r, pos)
                }
            }
        }
    }
}

/** Всплывающая подпись «+100 Bits» / «−200 Bits». */
@Composable
internal fun BitsFloat(amount: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val reduce = VlTheme.tokens.reduceMotion
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) t.animateTo(1f, tween(1600, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f))) }
    val p = t.value
    val tokens = VlTheme.tokens
    Text(
        (if (amount > 0) "+" else "−") + "${kotlin.math.abs(amount)} Bits",
        color = if (amount > 0) tokens.status.success else tokens.status.warning,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = if (large) 26.sp else 15.sp,
        modifier = modifier.graphicsLayer {
            translationY = (8f - 44f * p) * density
            alpha = if (reduce) 0f else if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
        },
    )
}

// ── Оверлей ──

/**
 * Полноэкранный слой поверх приложения (`.idi-overlay`): фон с отсветом primary, кнопка закрытия,
 * прокручиваемая середина. Тап мимо панели закрывает, если [dismissible].
 */
@Composable
internal fun IdOverlay(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    showClose: Boolean = dismissible,
    tapOutsideToClose: Boolean = dismissible,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val cs = MaterialTheme.colorScheme
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(cs.background)
                    drawRect(
                        Brush.radialGradient(
                            0f to lerp(cs.background, cs.primary, 0.1f), 0.7f to cs.background,
                            center = Offset(size.width / 2f, size.height * 0.3f),
                            radius = maxOf(size.width, size.height) * 0.75f,
                        ),
                    )
                }
                // Не clickable: тот слил бы весь оверлей в один узел для TalkBack
                .pointerInput(dismissible, tapOutsideToClose) {
                    detectTapGestures { if (dismissible && tapOutsideToClose) onDismiss() }
                },
        ) {
            background()
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        // Тап по панели оверлей не закрывает
                        .pointerInput(Unit) { detectTapGestures { } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
            if (showClose) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .systemBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(VlTheme.tokens.shapes.indicator)
                        .background(cs.surfaceContainerHighest)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Надзаголовок оверлея: моно, разрядка, primary (`.idi-kicker`). */
@Composable
internal fun OverlayKicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) = Text(
    text.uppercase(),
    modifier = modifier,
    color = color,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 3.6.sp,
    textAlign = TextAlign.Center,
)

@Composable
internal fun OverlayTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
)

@Composable
internal fun OverlayLead(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 420.dp),
)

@Composable
internal fun SkinError(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
    textAlign = TextAlign.Center,
)

/** Второстепенная строка (`.idt-muted`). */
@Composable
internal fun MutedText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) = Text(
    text,
    modifier = modifier,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = textAlign,
)

/** Кнопка оверлея по контенту, а не во всю ширину ([VlButton] растягивается). */
@Composable
internal fun SkinActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    destructive: Boolean = false,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val bg = when {
        destructive -> cs.error
        primary -> cs.primary
        else -> cs.surfaceContainerHighest
    }
    val fg = when {
        destructive -> cs.onError
        primary -> cs.onPrimary
        else -> cs.onSurface
    }
    val shape = tokens.shapes.button
    Row(
        modifier
            .height(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(17.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}
