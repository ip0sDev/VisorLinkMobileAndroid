package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdSkinLook
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.idcard.IdTrade
import org.visorlink.app.data.idcard.IdTradeOffer
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.finishName
import org.visorlink.app.ui.components.idcard.foilName
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme

private const val SWAP_MS = 2600L

/**
 * Предложения по обмену — для автора (спека 2.4, веб: IdTradeOffers.jsx): свой скин сверху,
 * список ожидающих предложений, «Отклонить» и «Обменять» → «Точно меняем». После обмена —
 * анимация: карты меняются местами по дугам.
 */
@Composable
internal fun IdTradeOffers(trade: IdTrade, wearer: IdSkinWearer, person: IdCardPerson, onClose: () -> Unit) {
    val repo: IdCardRepository = koinInject()
    val haptics = rememberCardHaptics()
    val scope = rememberCoroutineScope()
    val offers by remember(trade.id) { repo.pendingOffersFlow(trade.id) }.collectAsState(initial = null)
    var confirmId by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var swap by remember { mutableStateOf<Pair<IdSkinLook, IdSkinLook>?>(null) }

    fun act(offer: IdTradeOffer, accept: Boolean) {
        if (busyId != null) return
        busyId = offer.id
        error = null
        scope.launch {
            try {
                if (accept) {
                    repo.acceptOffer(trade.id, offer.id)
                    swap = trade.skin to offer.skin
                } else {
                    repo.declineOffer(trade.id, offer.id)
                    haptics.tap()
                }
            } catch (e: Exception) {
                error = e
            } finally {
                busyId = null
                confirmId = null
            }
        }
    }

    val locked = busyId != null || swap != null
    IdOverlay(onDismiss = onClose, dismissible = !locked) {
        val current = swap
        if (current != null) {
            IdSwapFx(current.first, current.second, wearer, person, onDone = onClose)
            return@IdOverlay
        }
        OverlayKicker(stringResource(R.string.idskin_trade_kicker))
        OverlayTitle(stringResource(R.string.idskin_offers_title))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val thumbW = min(220.dp, maxWidth * 0.6f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IdSkinThumb(trade.skin, wearer, person, width = thumbW)
                MutedText(stringResource(R.string.idskin_offers_your_card))
            }
        }
        val list = offers
        when {
            list == null -> MutedText(stringResource(R.string.idcard_tab_loading))
            list.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                MutedText(stringResource(R.string.idskin_offers_empty))
            }
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEachIndexed { i, offer ->
                    OfferRow(
                        offer = offer,
                        index = i,
                        wearer = wearer,
                        person = person,
                        confirming = confirmId == offer.id,
                        busy = busyId == offer.id,
                        locked = busyId != null,
                        onDecline = { act(offer, accept = false) },
                        onAccept = { confirmId = offer.id },
                        onConfirm = { act(offer, accept = true) },
                        onCancel = { confirmId = null },
                    )
                }
            }
        }
        error?.let { SkinError(skinErrorText(it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfferRow(
    offer: IdTradeOffer,
    index: Int,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    confirming: Boolean,
    busy: Boolean,
    locked: Boolean,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val reduce = VlTheme.tokens.reduceMotion
    val ed = IdCardMaterials.edition(offer.skin.edition)
    // idsk-slide-in: справа, по очереди
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index * 70L)
        enter.animateTo(1f, tween(450, easing = SkinEasing.CardIn))
    }
    VlSurface(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                translationX = (1f - enter.value) * 24.dp.toPx()
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 360.dp
            // Полоса цвета тиража слева
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 10.dp)
                    .width(3.dp)
                    .height(if (narrow) 160.dp else 90.dp)
                    .drawBehind { drawRect(ed) },
            )
            val info: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("@${offer.fromName.ifBlank { "—" }}", style = MaterialTheme.typography.titleSmall, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    EditionChip(offer.skin.edition)
                    Text(
                        finishName(offer.skin.traits.finish) + " · " + foilName(offer.skin.traits.foil),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (confirming) {
                            SkinActionButton(stringResource(R.string.action_cancel), onCancel, primary = false, enabled = !locked, modifier = Modifier.height(36.dp))
                            SkinActionButton(
                                stringResource(if (busy) R.string.idskin_picker_sending else R.string.idskin_offers_confirm),
                                onConfirm, enabled = !locked, icon = Icons.Default.SwapHoriz, modifier = Modifier.height(36.dp),
                            )
                        } else {
                            SkinActionButton(stringResource(R.string.idskin_offers_decline), onDecline, primary = false, enabled = !locked, icon = Icons.Default.Block, modifier = Modifier.height(36.dp))
                            SkinActionButton(stringResource(R.string.idskin_offers_accept), onAccept, enabled = !locked, icon = Icons.Default.SwapHoriz, modifier = Modifier.height(36.dp))
                        }
                    }
                }
            }
            if (narrow) {
                Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdSkinThumb(offer.skin, wearer, person, Modifier.fillMaxWidth())
                    info()
                }
            } else {
                Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IdSkinThumb(offer.skin, wearer, person, width = 150.dp)
                    Box(Modifier.weight(1f)) { info() }
                }
            }
        }
    }
}

/**
 * Обмен состоялся: две карты сходятся и меняются местами по дугам (A — поверху, B — понизу),
 * вспышка в центре, искры, «Обмен состоялся!». ~2,6 с, при «уменьшить анимацию» — сразу итог.
 */
@Composable
internal fun IdSwapFx(mine: IdSkinLook, theirs: IdSkinLook, wearer: IdSkinWearer, person: IdCardPerson, onDone: () -> Unit) {
    val haptics = rememberCardHaptics()
    val reduce = VlTheme.tokens.reduceMotion
    val cs = MaterialTheme.colorScheme
    val done by rememberUpdatedState(onDone)
    val time = remember { Animatable(if (reduce) 3f else 0f) }
    LaunchedEffect(Unit) {
        haptics.swap()
        if (reduce) {
            delay(900)
        } else {
            launch { time.animateTo(3f, tween(3000, easing = LinearEasing)) }
            delay(SWAP_MS)
        }
        done()
    }
    val t = time.value
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stageW = min(520.dp, maxWidth)
            val cardW = min(200.dp, stageW * 0.4f)
            Box(Modifier.width(stageW).height(200.dp).align(Alignment.Center)) {
                // Карта A уходит вправо поверху, B — влево понизу
                val p = SkinEasing.Swap.transform(seg(t, 0.2f, 1.5f))
                val travel = stageW - cardW
                SwapCard(mine, wearer, person, cardW, Modifier.offset(y = 40.dp).zIndex(2f), progress = p, travel = travel, arc = -70f, tilt = -8f, peakScale = 1.08f)
                SwapCard(theirs, wearer, person, cardW, Modifier.offset(x = travel, y = 40.dp), progress = p, travel = -travel, arc = 70f, tilt = 8f, peakScale = 0.92f)
                // Вспышка в центре
                val f = seg(t, 0.85f, 0.7f)
                if (f > 0f && f < 1f) {
                    Canvas(Modifier.size(160.dp).align(Alignment.Center)) {
                        val s = 0.2f + 2.2f * f
                        val a = if (f < 0.3f) f / 0.3f else 1f - (f - 0.3f) / 0.7f
                        val r = size.minDimension / 2f * s
                        drawCircle(Brush.radialGradient(listOf(Color.White, cs.primary.copy(alpha = 0.6f), Color.Transparent), center, r), r, center, alpha = a)
                    }
                }
                Sparks(30, 200.dp, cs.primary, Modifier.fillMaxSize(), startDelay = 0.9f)
            }
        }
        val title = seg(t, 1.3f, 0.6f)
        val tp = SkinEasing.Letter.transform(title)
        Text(
            stringResource(R.string.idskin_trade_done_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = cs.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = (title * 2f).coerceAtMost(1f)
                translationY = (1f - tp) * 18.dp.toPx()
                val s = 0.4f + 0.6f * tp
                scaleX = s
                scaleY = s
                rotationZ = -12f * (1f - tp)
            },
        )
    }
}

/** Карта на дуге: к 45% пути — середина сцены, смещена по вертикали на [arc] dp и наклонена. */
@Composable
private fun SwapCard(
    look: IdSkinLook,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    progress: Float,
    travel: androidx.compose.ui.unit.Dp,
    arc: Float,
    tilt: Float,
    peakScale: Float,
) {
    val ed = IdCardMaterials.edition(look.edition)
    Box(
        modifier
            .graphicsLayer {
                val p = progress
                val k = if (p < 0.45f) p / 0.45f else 1f - (p - 0.45f) / 0.55f
                translationX = travel.toPx() * p
                translationY = arc * density * k
                rotationZ = tilt * k
                val s = 1f + (peakScale - 1f) * k
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                drawRoundRect(ed.copy(alpha = 0.25f), Offset(-6.dp.toPx(), -6.dp.toPx()), androidx.compose.ui.geometry.Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
            },
    ) {
        IdSkinThumb(look, wearer, person, width = width)
    }
}
