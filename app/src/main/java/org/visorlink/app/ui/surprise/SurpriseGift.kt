package org.visorlink.app.ui.surprise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.Surprise
import org.visorlink.app.data.model.SurpriseGiftResult
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardException
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.VlIdCard
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.components.rememberLoopFloat
import org.visorlink.app.ui.components.terminalData
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.sin
import kotlin.random.Random

private enum class GiftPhase { SEALED, OPENING, CARD, REWARDS, ERROR }

/** Сколько коробка дрожит минимум, даже если сервер ответил мгновенно. */
private const val OPENING_MIN_MS = 1700L

/**
 * Подарок в конце показа. До вскрытия — только запечатанная коробка: что внутри, клиент и сам
 * не знает. «Открыть» вызывает `claimSurpriseGift` (сервер выдаёт один раз), коробка дрожит,
 * вспышка — и под золотыми лучами влетает карта; дальше — биты и PRO.
 */
@Composable
internal fun GiftStep(
    surprise: Surprise,
    wearer: IdSkinWearer,
    profile: UserProfile?,
    claim: suspend () -> SurpriseGiftResult,
    onCelebrate: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
) {
    var phase by remember { mutableStateOf(GiftPhase.SEALED) }
    var result by remember { mutableStateOf<SurpriseGiftResult?>(null) }
    var errorReason by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberCardHaptics()
    val celebrate by rememberUpdatedState(onCelebrate)
    val flash = remember { Animatable(0f) }

    fun open() {
        if (phase == GiftPhase.OPENING) return
        phase = GiftPhase.OPENING
        errorReason = null
        scope.launch {
            val started = System.currentTimeMillis()
            val outcome = runCatching { claim() }
            val left = OPENING_MIN_MS - (System.currentTimeMillis() - started)
            if (left > 0) delay(left)
            outcome
                .onSuccess {
                    result = it
                    phase = if (it.skin != null) GiftPhase.CARD else GiftPhase.REWARDS
                    haptics.stamp()
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
                }
                .onFailure {
                    errorReason = (it as? IdCardException)?.reason ?: "error"
                    phase = GiftPhase.ERROR
                }
        }
    }

    val cs = MaterialTheme.colorScheme
    val gold = IdCardMaterials.edition(IdEdition.LEGENDARY)
    Box(Modifier.fillMaxSize()) {
        Rays(
            color = if (phase == GiftPhase.CARD || phase == GiftPhase.REWARDS) gold else cs.primary,
            intensity = when (phase) {
                GiftPhase.SEALED -> 0.55f
                GiftPhase.OPENING -> 1f
                GiftPhase.CARD -> 1f
                GiftPhase.REWARDS -> 0.45f
                GiftPhase.ERROR -> 0f
            },
            modifier = Modifier.fillMaxSize(),
            center = 0.38f,
        )
        when (phase) {
            GiftPhase.SEALED, GiftPhase.OPENING, GiftPhase.ERROR -> CenteredColumn {
                Kicker(stringResource(R.string.surprise_gift_kicker))
                GiftBox(size = 150.dp, shake = if (phase == GiftPhase.OPENING) 1f else 0f, onClick = if (phase == GiftPhase.SEALED) ::open else null)
                when (phase) {
                    GiftPhase.OPENING -> Lead(stringResource(R.string.surprise_gift_opening))
                    GiftPhase.ERROR -> {
                        Lead(stringResource(giftErrorText(errorReason)))
                        VlButton(onClick = ::open, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_retry)) }
                        QuietText(stringResource(R.string.surprise_later), onLater)
                    }
                    else -> {
                        Title(stringResource(R.string.surprise_gift_title))
                        Lead(stringResource(R.string.surprise_gift_lead))
                        VlButton(onClick = ::open, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_gift_open)) }
                    }
                }
            }
            GiftPhase.CARD -> result?.let { r ->
                GiftCardReveal(r, wearer, profile, onLanded = { celebrate() }, onNext = { phase = GiftPhase.REWARDS })
            }
            GiftPhase.REWARDS -> result?.let { r ->
                GiftRewards(surprise, r, onEnter = { celebrate() }, onDone = onDone)
            }
        }
        // Вспышка вскрытия
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
    }
}

private fun giftErrorText(reason: String?): Int = when (reason) {
    "not-yet" -> R.string.surprise_gift_err_not_yet
    "no-slot" -> R.string.surprise_gift_err_no_slot
    else -> R.string.surprise_gift_err_generic
}

@Composable
private fun GiftCardReveal(result: SurpriseGiftResult, wearer: IdSkinWearer, profile: UserProfile?, onLanded: () -> Unit, onNext: () -> Unit) {
    val skin = result.skin ?: return
    val reduceMotion = VlTheme.tokens.reduceMotion
    val haptics = rememberCardHaptics()
    val landedNow by rememberUpdatedState(onLanded)
    val spin = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var landed by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        spin.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        landed = true
        haptics.holo()
        landedNow()
    }
    val textsIn by animateFloatAsState(if (landed) 1f else 0f, tween(600), label = "texts")
    val card = remember(skin, wearer) { skin.asCard(wearer, issuedAt = result.skinMintedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) }
    val appearance = profile?.let { ProfileAppearance.resolve(owner = it, viewer = it) }
    val cs = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = min(340.dp, maxWidth * 0.86f)
        CenteredColumn {
            Box(Modifier.graphicsLayer { alpha = textsIn }) { Kicker(stringResource(R.string.surprise_gift_card_kicker)) }
            VlIdCard(
                card = card,
                person = IdCardPerson(profile?.displayName, profile?.username, profile?.avatarUrl),
                width = cardWidth,
                accent = appearance?.let { it.accentHexColor ?: it.accent?.seedColor },
                emojis = appearance?.emojis.orEmpty(),
                interactive = landed,
                modifier = Modifier.graphicsLayer {
                    // Из глубины, два с половиной оборота, с перелётом по масштабу на посадке
                    val p = spin.value
                    rotationY = 900f * (1f - p)
                    rotationZ = 8f * (1f - p)
                    translationY = 140.dp.toPx() * (1f - p)
                    cameraDistance = 16f * density
                    val sc = 0.2f + 0.8f * p + 0.06f * sin(p * Math.PI.toFloat())
                    scaleX = sc
                    scaleY = sc
                    alpha = (p * 3f).coerceAtMost(1f)
                },
            )
            Column(
                Modifier.graphicsLayer {
                    alpha = textsIn
                    translationY = 16.dp.toPx() * (1f - textsIn)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.surprise_gift_edition, skin.serial),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        brush = Brush.horizontalGradient(IdCardMaterials.LegendaryShimmer),
                    ),
                    textAlign = TextAlign.Center,
                )
                Title(stringResource(R.string.surprise_gift_card_title))
                Lead(stringResource(if (result.equipped) R.string.surprise_gift_card_equipped else R.string.surprise_gift_card_inventory))
                VlButton(onClick = onNext, enabled = landed, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_next)) }
            }
        }
    }
}

@Composable
private fun GiftRewards(surprise: Surprise, result: SurpriseGiftResult, onEnter: () -> Unit, onDone: () -> Unit) {
    val haptics = rememberCardHaptics()
    val enter by rememberUpdatedState(onEnter)
    val reduceMotion = VlTheme.tokens.reduceMotion
    var shown by remember { mutableIntStateOf(if (reduceMotion) 3 else 0) }
    LaunchedEffect(Unit) {
        enter()
        haptics.success()
        while (shown < 3) {
            delay(450)
            shown++
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val until = remember(result.proUntil, locale) { result.proUntil?.let { SimpleDateFormat("d MMMM yyyy", locale).format(Date(it)) } }
    val cs = MaterialTheme.colorScheme
    CenteredColumn(spacing = 14.dp) {
        Kicker(stringResource(R.string.surprise_gift_rewards_kicker))
        if (result.bits > 0) {
            Appear(shown >= 1) {
                RewardTile(
                    icon = { Icon(Icons.Default.Toll, contentDescription = null, tint = IdCardMaterials.Coin, modifier = Modifier.size(30.dp)) },
                    value = "+" + formatNumber(result.bits.toLong(), grouped = result.bits >= 1000),
                    label = stringResource(R.string.surprise_gift_bits),
                    countUp = true,
                )
            }
        }
        if (result.proDays > 0) {
            Appear(shown >= 2) {
                RewardTile(
                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = cs.primary, modifier = Modifier.size(30.dp)) },
                    value = stringResource(if (result.proDays >= 365) R.string.surprise_gift_pro_year else R.string.surprise_gift_pro),
                    label = until?.let { stringResource(R.string.surprise_gift_pro_until, it) } ?: "",
                    countUp = false,
                )
            }
        }
        Appear(shown >= 3) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Title(surprise.giftTitle ?: stringResource(R.string.surprise_final_default))
                surprise.giftText?.let { Lead(it) }
                surprise.fromName?.let {
                    Text(stringResource(R.string.surprise_signature, it), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                VlButton(onClick = onDone, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_thanks)) }
            }
        }
    }
}

@Composable
private fun Appear(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 0.85f) +
            slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 3 },
    ) { content() }
}

@Composable
private fun RewardTile(icon: @Composable () -> Unit, value: String, label: String, countUp: Boolean) {
    val cs = MaterialTheme.colorScheme
    VlSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(52.dp).clip(VlTheme.tokens.shapes.indicator).background(VlTheme.tokens.selectionFill),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CountUpText(
                    value = value,
                    countUp = countUp,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold).terminalData(),
                    color = cs.onSurface,
                    align = TextAlign.Start,
                )
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuietText(text: String, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
        .clip(VlTheme.tokens.shapes.chip)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp),
)

// ── Декор ─────────────────────────────────────────────────────────────────────

/** Подарочная коробка: дышит в покое, [shake] = 1 — дрожит перед вскрытием. */
@Composable
internal fun GiftBox(size: Dp, shake: Float, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val reduceMotion = VlTheme.tokens.reduceMotion
    val pulse by rememberLoopFloat(enabled = !reduceMotion) {
        rememberInfiniteTransition(label = "gift").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gift",
        )
    }
    val jitter by rememberLoopFloat(enabled = !reduceMotion && shake > 0f) {
        rememberInfiniteTransition(label = "shake").animateFloat(
            -1f, 1f, infiniteRepeatable(tween(60, easing = LinearEasing), RepeatMode.Reverse), label = "shake",
        )
    }
    val shakeIn by animateFloatAsState(shake, tween(500), label = "shakeIn")
    VlSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val sc = 1f + 0.06f * pulse + 0.1f * shakeIn
                scaleX = sc
                scaleY = sc
                rotationZ = (pulse - 0.5f) * 6f * (1f - shakeIn) + jitter * 8f * shakeIn
                translationX = jitter * 5.dp.toPx() * shakeIn
            },
        onClick = onClick,
    ) {
        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = cs.primary, modifier = Modifier.size(size * 0.47f))
    }
}

/**
 * Медленно вращающиеся лучи и ореол из точки на высоте [center] (доля высоты). [intensity] 0…1
 * плавно гасит и зажигает их; без анимаций системы лучи стоят на месте.
 */
@Composable
internal fun Rays(color: Color, intensity: Float, modifier: Modifier = Modifier, center: Float = 0.5f) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    val spin by rememberLoopFloat(enabled = !reduceMotion) {
        rememberInfiniteTransition(label = "rays").animateFloat(
            0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "rays",
        )
    }
    val alpha by animateFloatAsState(intensity, tween(900), label = "raysAlpha")
    Canvas(modifier) {
        if (alpha <= 0.01f) return@Canvas
        val c = Offset(size.width / 2f, size.height * center)
        val r = size.maxDimension
        val beams = Brush.radialGradient(
            0f to color.copy(alpha = 0.32f * alpha), 0.6f to color.copy(alpha = 0.08f * alpha), 1f to Color.Transparent,
            center = c, radius = r * 0.75f,
        )
        rotate(spin, c) {
            val n = 14
            for (i in 0 until n) {
                drawArc(beams, startAngle = i * 360f / n, sweepAngle = 360f / n / 2.2f, useCenter = true, topLeft = c - Offset(r, r), size = Size(2 * r, 2 * r))
            }
        }
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = 0.45f * alpha), 1f to Color.Transparent, center = c, radius = r * 0.32f),
            radius = r * 0.32f, center = c,
        )
    }
}

/** Ночное небо: мерцающие звёзды под всем показом (позиции фиксированы, мерцание — по кругу). */
@Composable
internal fun NightSky(modifier: Modifier = Modifier) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    val color = MaterialTheme.colorScheme.onSurface
    val stars = remember {
        val r = Random(2210)
        List(80) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.5f, r.nextFloat() * 6.2832f, (1 + r.nextInt(3)).toFloat()) }
    }
    val t by rememberLoopFloat(enabled = !reduceMotion, rest = 1f) {
        rememberInfiniteTransition(label = "sky").animateFloat(
            0f, 6.2832f, infiniteRepeatable(tween(8_000, easing = LinearEasing)), label = "sky",
        )
    }
    Canvas(modifier) {
        for (s in stars) {
            val a = 0.1f + 0.4f * (0.5f + 0.5f * sin(t * s[4] + s[3]))
            drawCircle(color.copy(alpha = a), s[2] * density, Offset(s[0] * size.width, s[1] * size.height))
        }
    }
}

/** Метка демо и выход из него. */
@Composable
internal fun DemoBadge(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(12.dp)
            .clip(VlTheme.tokens.shapes.chip)
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClose)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.surprise_demo_badge), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.surprise_demo_close), tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}
