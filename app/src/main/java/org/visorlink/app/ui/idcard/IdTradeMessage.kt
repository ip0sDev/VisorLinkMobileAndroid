package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdOfferStatus
import org.visorlink.app.data.idcard.IdSkinLook
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.idcard.IdTrade
import org.visorlink.app.data.idcard.IdTradeOffer
import org.visorlink.app.data.idcard.IdTradeStatus
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.IdTradeState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset

/**
 * Сообщение «ID-карта на обмен» (`type: id_trade`, спека 2.2–2.4, веб: IdTradeMessage.jsx).
 * Сообщение — только ссылка на `idTrades/{tradeId}`: вид скина и статус берутся оттуда, и обмен
 * должен принадлежать этому чату и этому сообщению — подделанное сообщение ничего не покажет.
 *
 * Скин рисуется на карте смотрящего («примерка»): режим и данные — его (в маске — Standard),
 * так чужой особый режим не раскрывается.
 */
@Composable
fun IdTradeMessage(tradeId: String?, messageId: String, chatId: String, modifier: Modifier = Modifier) {
    val state = LocalIdModeState.current
    if (!state.enabled || tradeId.isNullOrEmpty()) {
        Unavailable(modifier)
        return
    }
    val repo: IdCardRepository = koinInject()
    val users: UserRepository = koinInject()
    val uid = state.myUid
    val tradeState by remember(tradeId) { repo.tradeFlow(tradeId) }.collectAsState(initial = IdTradeState.Loading)
    val cardState by remember(uid) { repo.cardFlow(uid) }.collectAsState(initial = IdCardState.Loading)
    val profile by remember(uid) { if (uid == null) flowOf(null) else users.userProfileFlow(uid) }.collectAsState(initial = null)

    if (tradeState is IdTradeState.Loading) {
        Skeleton(modifier)
        return
    }
    val trade = (tradeState as? IdTradeState.Ready)?.trade?.takeIf { it.belongsTo(chatId, messageId) }
    if (trade == null) {
        Unavailable(modifier)
        return
    }
    val wearer = (cardState as? IdCardState.Ready)?.card.wearer(profile, masked = state.mask.active)
    val person = profile?.idCardPerson() ?: IdCardPerson(null, null, null)
    TradeCard(trade, uid, wearer, person, modifier)
}

@Composable
private fun TradeCard(trade: IdTrade, uid: String?, wearer: IdSkinWearer, person: IdCardPerson, modifier: Modifier) {
    val repo: IdCardRepository = koinInject()
    val haptics = rememberCardHaptics()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val isOwner = trade.ownerUid == uid
    val open = trade.status == IdTradeStatus.OPEN
    val myOffer by remember(trade.id, uid, open && !isOwner) {
        if (open && !isOwner && uid != null) repo.myOfferFlow(trade.id, uid) else flowOf(null)
    }.collectAsState(initial = null)
    var modal by remember { mutableStateOf<TradeModal?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    // Обмен состоялся на глазах — праздник (у уже закрытого при загрузке — нет)
    var prevStatus by remember { mutableStateOf<IdTradeStatus?>(null) }
    var justDone by remember { mutableStateOf(false) }
    LaunchedEffect(trade.status) {
        val was = prevStatus
        prevStatus = trade.status
        if (was == IdTradeStatus.OPEN && trade.status == IdTradeStatus.DONE) {
            haptics.swap()
            justDone = true
            delay(2600)
            justDone = false
        }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
                haptics.tap()
            } catch (e: Exception) {
                error = e
            } finally {
                busy = false
            }
        }
    }

    val edition = trade.skin.edition
    val pending = myOffer?.takeIf { it.status == IdOfferStatus.PENDING }
    // Цвет текста — от пузыря (свой / чужой), а не от поверхности экрана
    val ink = LocalContentColor.current
    val muted = ink.copy(alpha = 0.7f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.SwapHoriz, null, tint = muted, modifier = Modifier.size(13.dp))
                Text(
                    stringResource(R.string.idskin_trade_kicker).uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = muted,
                    maxLines = 1,
                )
            }
            EditionChip(edition)
        }

        if (trade.status != IdTradeStatus.DONE) {
            TradeSkin(trade.skin, wearer, person, closed = trade.status == IdTradeStatus.CLOSED)
        }
        if (trade.note.isNotBlank()) {
            Text(trade.note, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
        if (open) {
            Text(stringResource(R.string.idskin_trade_try_on), fontSize = 11.sp, lineHeight = 15.sp, color = muted)
        }

        when {
            trade.status == IdTradeStatus.DONE -> Deal(trade, wearer, person, justDone)
            trade.status == IdTradeStatus.CLOSED -> StatusLine(
                stringResource(if (trade.closedByOwner) R.string.idskin_trade_closed else R.string.idskin_trade_gone),
                muted,
            )
            isOwner -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OffersButton(trade.offerCount) { modal = TradeModal.OFFERS }
                QuietAction(stringResource(R.string.idskin_trade_close), enabled = !busy) { run { repo.closeTrade(trade.id) } }
            }
            pending != null -> MyPendingOffer(pending, wearer, person, busy) { run { repo.withdrawOffer(trade.id) } }
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (myOffer?.status) {
                    IdOfferStatus.DECLINED -> StatusLine(stringResource(R.string.idskin_trade_declined), muted, small = true)
                    IdOfferStatus.VOID -> StatusLine(stringResource(R.string.idskin_trade_void), muted, small = true)
                    else -> Unit
                }
                OfferButton { modal = TradeModal.OFFER }
            }
        }
        error?.let { SkinError(skinErrorText(it)) }
    }

    when (modal) {
        TradeModal.OFFER -> IdSkinPicker(SkinPickPurpose.Offer(trade, wearer), onClose = { modal = null })
        TradeModal.OFFERS -> IdTradeOffers(trade, wearer, person, onClose = { modal = null })
        null -> Unit
    }
}

private enum class TradeModal { OFFER, OFFERS }

/** Скин на обмене: ореол тиража, лёгкое покачивание; снятый с обмена — обесцвечен. */
@Composable
private fun TradeSkin(look: IdSkinLook, wearer: IdSkinWearer, person: IdCardPerson, closed: Boolean) {
    val ed = IdCardMaterials.edition(look.edition)
    val float = if (closed || VlTheme.tokens.reduceMotion) 0f else {
        rememberInfiniteTransition(label = "tradeFloat").animateFloat(0f, 1f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "f").value
    }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (closed) 0f else if (look.edition == IdEdition.COMMON) 0.4f else 1f }
                .drawBehind {
                    drawOval(
                        Brush.radialGradient(listOf(ed.copy(alpha = 0.22f), ed.copy(alpha = 0f)), Offset(size.width / 2f, size.height / 2f), size.maxDimension * 0.55f),
                    )
                },
        )
        IdSkinThumb(
            look, wearer, person,
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -4.dp.toPx() * float
                    rotationZ = 0.4f * float
                    if (closed) {
                        alpha = 0.6f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
                .then(
                    if (closed) Modifier.drawWithContent {
                        drawContent()
                        // grayscale(0.85) brightness(0.8): насыщенность в ноль, чуть темнее
                        drawRect(Color.Gray, blendMode = BlendMode.Saturation)
                        drawRect(Color.Black.copy(alpha = 0.2f))
                    } else Modifier,
                ),
        )
    }
}

/** Состоялся: две мини-карты с ⇄; если на глазах — карты меняются местами, искры. */
@Composable
private fun Deal(trade: IdTrade, wearer: IdSkinWearer, person: IdCardPerson, justDone: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val anim = remember { Animatable(1f) }
    LaunchedEffect(justDone) {
        if (!justDone || tokens.reduceMotion) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(1200, easing = SkinEasing.Swap))
    }
    val p = anim.value
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).graphicsLayer {
                        // idsk-deal-a: с места B поверху через дугу на своё
                        translationX = threeStop(p, size.width + 38.dp.toPx(), size.width * 0.6f, 0f)
                        translationY = threeStop(p, 0f, -18.dp.toPx(), 0f)
                        rotationZ = threeStop(p, 0f, -6f, 0f)
                    },
                ) { IdSkinThumb(trade.skin, wearer, person, Modifier.fillMaxWidth()) }
                Box(
                    Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            rotationZ = -360f * (1f - p)
                            val s = 0.6f + 0.4f * p
                            scaleX = s
                            scaleY = s
                        }
                        .clip(tokens.shapes.indicator)
                        .background(tokens.selectionFill),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.SwapHoriz, null, tint = cs.primary, modifier = Modifier.size(16.dp)) }
                Box(
                    Modifier.weight(1f).graphicsLayer {
                        // idsk-deal-b: с места A понизу
                        translationX = threeStop(p, -(size.width + 38.dp.toPx()), -size.width * 0.6f, 0f)
                        translationY = threeStop(p, 0f, 18.dp.toPx(), 0f)
                        rotationZ = threeStop(p, 0f, 6f, 0f)
                    },
                ) { trade.deal?.skin?.let { IdSkinThumb(it, wearer, person, Modifier.fillMaxWidth()) } }
            }
            if (justDone) Sparks(26, 140.dp, cs.primary, Modifier.matchParentSize())
        }
        StatusLine(
            stringResource(R.string.idskin_trade_done, trade.ownerName.ifBlank { "—" }, trade.deal?.fromName?.ifBlank { null } ?: "—"),
            tokens.status.success,
        )
    }
}

/** Своё ожидающее предложение: мини-карта, «ждёт ответа», «Отозвать». */
@Composable
private fun MyPendingOffer(offer: IdTradeOffer, wearer: IdSkinWearer, person: IdCardPerson, busy: Boolean, onWithdraw: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.rounded(12.dp)
    val flip = if (tokens.reduceMotion) 0f else {
        rememberInfiniteTransition(label = "hourglass").animateFloat(
            0f, 360f,
            infiniteRepeatable(keyframes { durationMillis = 2000; 0f at 0; 0f at 800; 180f at 1000; 180f at 1800; 360f at 2000 }),
            label = "hg",
        ).value
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceContainerLow, shape)
            .vlInset(tokens.structure, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IdSkinThumb(offer.skin, wearer, person, width = 96.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.HourglassTop, null, tint = LocalContentColor.current, modifier = Modifier.size(13.dp).graphicsLayer { rotationZ = flip })
                Text(stringResource(R.string.idskin_trade_waiting), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current)
            }
            QuietAction(stringResource(R.string.idskin_trade_withdraw), enabled = !busy, icon = Icons.AutoMirrored.Filled.Undo, onClick = onWithdraw)
        }
    }
}

/** «Предложить обмен»: на всю ширину, с пробегающим бликом. */
@Composable
private fun OfferButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.button
    val sweep = if (tokens.reduceMotion) 0f else {
        rememberInfiniteTransition(label = "offerSweep").animateFloat(
            0f, 1f,
            infiniteRepeatable(keyframes { durationMillis = 3400; 0f at 0; 1f at 1360; 1f at 3400 }, initialStartOffset = StartOffset(1000)),
            label = "s",
        ).value
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(cs.primary, shape)
            .drawWithContent {
                drawContent()
                if (sweep in 0.001f..0.999f) {
                    val x = -size.width + sweep * size.width * 2f
                    drawRect(
                        Brush.linearGradient(
                            0.3f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.3f), 0.7f to Color.Transparent,
                            start = Offset(x, 0f), end = Offset(x + size.width, size.height * 0.4f),
                        ),
                    )
                }
            }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Default.SwapHoriz, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.idskin_trade_offer), color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** «Предложения (N)» — у автора открытого обмена. */
@Composable
private fun OffersButton(count: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.button
    // Новое предложение — значок подпрыгивает
    val bump = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count <= 0 || tokens.reduceMotion) return@LaunchedEffect
        bump.snapTo(0.4f)
        bump.animateTo(1f, tween(500, easing = SkinEasing.Letter))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(cs.primary, shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Default.Inbox, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.idskin_trade_offers), color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (count > 0) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = bump.value
                        scaleY = bump.value
                    }
                    .height(22.dp)
                    .clip(tokens.shapes.pill)
                    .background(cs.onPrimary)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = cs.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/** Неприметное действие ссылкой (`.idi-quiet-link`). */
@Composable
private fun QuietAction(text: String, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val color = LocalContentColor.current.copy(alpha = if (enabled) 0.75f else 0.35f)
    Row(
        Modifier
            .clip(VlTheme.tokens.shapes.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(13.dp)) }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline), color = color)
    }
}

@Composable
private fun StatusLine(text: String, color: Color, small: Boolean = false) = Text(
    text,
    modifier = Modifier.fillMaxWidth(),
    style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelLarge,
    color = color,
    textAlign = TextAlign.Center,
)

/** «Обмен недоступен»: нет флага, нет доступа или сообщение не совпадает с обменом. */
@Composable
private fun Unavailable(modifier: Modifier) {
    val color = LocalContentColor.current.copy(alpha = 0.7f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.HighlightOff, null, tint = color, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.idskin_trade_unavailable), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Заглушка на время загрузки обмена — силуэт карты с бегущим бликом. */
@Composable
private fun Skeleton(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val x = if (VlTheme.tokens.reduceMotion) 0.5f else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x").value
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(85.6f / 54f)
            .clip(VlTheme.tokens.shapes.rounded(12.dp))
            .drawBehind {
                drawRect(cs.surfaceContainerHigh)
                val cx = size.width * (2f - 3f * x)
                drawRect(
                    Brush.linearGradient(
                        0f to cs.surfaceContainerHigh, 0.5f to cs.surfaceContainerHighest, 1f to cs.surfaceContainerHigh,
                        start = Offset(cx - size.width, 0f), end = Offset(cx, size.height * 0.3f),
                    ),
                )
            },
    )
}

/** Ключевые кадры 0% → 50% → 100%. */
private fun threeStop(p: Float, a: Float, b: Float, c: Float): Float =
    if (p < 0.5f) a + (b - a) * (p / 0.5f) else b + (c - b) * ((p - 0.5f) / 0.5f)
