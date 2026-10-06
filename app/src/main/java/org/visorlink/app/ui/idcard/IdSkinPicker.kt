package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinRules
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.idcard.IdTrade
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.IdSkinsState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.editionName
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset
import kotlin.math.max

/** Зачем выбирают скин: выставить в чат или предложить взамен выставленного. */
internal sealed interface SkinPickPurpose {
    data class Post(val chatId: String) : SkinPickPurpose

    /** [wearer] — та же «примерка», что в карточке обмена (с учётом маски). */
    data class Offer(val trade: IdTrade, val wearer: IdSkinWearer) : SkinPickPurpose
}

/**
 * Выбор своего скина для обмена (спека 2.4, веб: IdSkinPicker.jsx). Надетый выбрать нельзя,
 * выставленный в другом обмене нельзя выставить второй раз. После отправки выбранная карта
 * «улетает», окно закрывается.
 */
@Composable
internal fun IdSkinPicker(purpose: SkinPickPurpose, onClose: () -> Unit) {
    val repo: IdCardRepository = koinInject()
    val users: UserRepository = koinInject()
    val haptics = rememberCardHaptics()
    val scope = rememberCoroutineScope()
    val uid = LocalIdModeState.current.myUid
    val cardState by remember(uid) { repo.cardFlow(uid) }.collectAsState(initial = IdCardState.Loading)
    val profile by remember(uid) { if (uid == null) flowOf(null) else users.userProfileFlow(uid) }.collectAsState(initial = null)
    val card = (cardState as? IdCardState.Ready)?.card
    val skinsState by remember(uid, card != null) {
        if (card == null) flowOf(IdSkinsState.Loading) else repo.skinsFlow(uid)
    }.collectAsState(initial = IdSkinsState.Loading)

    var selected by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    val post = purpose is SkinPickPurpose.Post
    val items = IdSkinRules.inventoryOf(card, (skinsState as? IdSkinsState.Ready)?.skins.orEmpty())
    val equippedId = IdSkinRules.equippedId(card, items)
    fun blocked(s: IdSkin) = s.id == equippedId || s.virtual || (post && s.listedIn != null)
    val choices = items.filterNot(::blocked)
    val pick = items.firstOrNull { it.id == selected }
    val wearer = (purpose as? SkinPickPurpose.Offer)?.wearer ?: card.wearer(profile)
    val person = profile?.idCardPerson() ?: IdCardPerson(null, null, null)

    fun submit() {
        val skin = pick ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                when (purpose) {
                    is SkinPickPurpose.Post -> repo.postTrade(purpose.chatId, skin.id, note.trim())
                    is SkinPickPurpose.Offer -> repo.offerTrade(purpose.trade.id, skin.id)
                }
                haptics.success()
                sent = true
                delay(850)
                onClose()
            } catch (e: Exception) {
                error = e
                busy = false
            }
        }
    }

    IdOverlay(onDismiss = { if (!busy) onClose() }, dismissible = !busy) {
        OverlayKicker(stringResource(R.string.idskin_trade_kicker))
        OverlayTitle(stringResource(if (post) R.string.idskin_picker_title_post else R.string.idskin_picker_title_offer))
        OverlayLead(stringResource(if (post) R.string.idskin_picker_lead_post else R.string.idskin_picker_lead_offer))

        if (purpose is SkinPickPurpose.Offer) VersusBlock(purpose.trade, pick, wearer, person)

        when {
            cardState is IdCardState.Loading || (card != null && skinsState is IdSkinsState.Loading) ->
                MutedText(stringResource(R.string.idcard_tab_loading))
            card == null -> MutedText(stringResource(R.string.idskin_picker_no_card), textAlign = TextAlign.Center)
            choices.isEmpty() -> EmptyPicker(stringResource(if (post) R.string.idskin_picker_empty_post else R.string.idskin_picker_empty_offer))
            else -> {
                PickGrid(items, equippedId, ::blocked, selected, sent, busy, wearer, person) {
                    selected = it
                    haptics.tap()
                }
                if (post) {
                    VlTextField(
                        value = note,
                        onValueChange = { v -> note = v.replace('\n', ' ').take(IdSkinRules.TRADE_NOTE_MAX) },
                        label = stringResource(R.string.idskin_picker_note) + " " + stringResource(R.string.idcard_choose_optional),
                        placeholder = stringResource(R.string.idskin_picker_note_placeholder),
                        supportingText = "${note.length}/${IdSkinRules.TRADE_NOTE_MAX}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        error?.let { SkinError(skinErrorText(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinActionButton(stringResource(R.string.action_cancel), onClose, primary = false, enabled = !busy)
            SkinActionButton(
                when {
                    sent -> stringResource(R.string.idskin_picker_sent)
                    busy -> stringResource(R.string.idskin_picker_sending)
                    post -> stringResource(R.string.idskin_picker_submit_post)
                    else -> stringResource(R.string.idskin_picker_submit_offer)
                },
                ::submit,
                enabled = pick != null && !busy && !sent,
                icon = if (post) Icons.AutoMirrored.Filled.Send else Icons.Default.SwapHoriz,
            )
        }
    }
}

/** «Их карта ⇄ ваша»: выставленный скин и выбранный взамен — на карте смотрящего. */
@Composable
private fun VersusBlock(trade: IdTrade, pick: IdSkin?, wearer: IdSkinWearer, person: IdCardPerson) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val wobble = if (tokens.reduceMotion) 0f else {
        rememberInfiniteTransition(label = "vs").animateFloat(0f, 180f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "vsR").value
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VlSurface(Modifier.weight(1f)) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IdSkinThumb(trade.skin, wearer, person, Modifier.fillMaxWidth())
                VsLabel("@${trade.ownerName}")
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(tokens.shapes.indicator)
                .background(cs.surfaceContainer, tokens.shapes.indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SwapHoriz, null, tint = cs.primary, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = wobble })
        }
        if (pick != null) {
            VlSurface(Modifier.weight(1f)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IdSkinThumb(pick.look, wearer, person, Modifier.fillMaxWidth(), mintedAt = pick.mintedAt)
                    VsLabel(stringResource(R.string.idskin_picker_yours))
                }
            }
        } else {
            val shape = tokens.shapes.card
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(cs.surfaceContainerLow, shape)
                    .vlInset(tokens.structure, shape)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlotOutline(Modifier.fillMaxWidth())
                VsLabel(stringResource(R.string.idskin_picker_yours))
            }
        }
    }
}

@Composable
private fun VsLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

/** Сетка выбора: недоступные приглушены, выбранная приподнята с контуром primary и галочкой. */
@Composable
private fun PickGrid(
    items: List<IdSkin>,
    equippedId: String?,
    blocked: (IdSkin) -> Boolean,
    selected: String?,
    sent: Boolean,
    busy: Boolean,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    onPick: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val cols = max(2, ((maxWidth + gap) / (150.dp + gap)).toInt())
        val cell = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { skin ->
                        PickTile(
                            skin = skin,
                            equipped = skin.id == equippedId,
                            off = blocked(skin),
                            picked = selected == skin.id,
                            flying = sent && selected == skin.id,
                            dimmed = sent && selected != skin.id,
                            busy = busy,
                            wearer = wearer,
                            person = person,
                            width = cell,
                            onClick = { onPick(skin.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickTile(
    skin: IdSkin,
    equipped: Boolean,
    off: Boolean,
    picked: Boolean,
    flying: Boolean,
    dimmed: Boolean,
    busy: Boolean,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    width: Dp,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.card
    // idsk-fly: карта приседает и улетает вверх-вправо
    val fly = remember { Animatable(0f) }
    LaunchedEffect(flying) {
        if (flying && !tokens.reduceMotion) fly.animateTo(1f, tween(800, easing = CubicBezierEasing(0.5f, -0.4f, 0.7f, 0.6f)))
    }
    val check = remember { Animatable(0f) }
    LaunchedEffect(picked) { check.animateTo(if (picked) 1f else 0f, tween(400, easing = SkinEasing.Letter)) }
    val label = editionName(skin.edition) + ", " + skin.serial
    Box(
        Modifier
            .width(width)
            .graphicsLayer {
                val f = fly.value
                if (f > 0f) {
                    translationX = 160.dp.toPx() * f
                    translationY = (if (f < 0.3f) 8.dp.toPx() * (f / 0.3f) else -320.dp.toPx() * ((f - 0.3f) / 0.7f))
                    rotationZ = 18f * f
                    val s = 1f - 0.6f * f
                    scaleX = s
                    scaleY = s
                    alpha = 1f - f
                } else {
                    translationY = if (picked) -3.dp.toPx() else 0f
                    alpha = when {
                        off -> 0.45f
                        dimmed -> 0.3f
                        else -> 1f
                    }
                }
            }
            .semantics {
                contentDescription = label
                selected = picked
            },
    ) {
        VlSurface(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    if (picked) drawOutline(shape.createOutline(size, layoutDirection, this), cs.primary, style = Stroke(2.dp.toPx()))
                },
            overrideColor = if (picked) tokens.selectionFill else null,
            onClick = if (off || busy) null else onClick,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdSkinThumb(skin.look, wearer, person, mintedAt = skin.mintedAt, width = width - 20.dp)
                    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) { EditionChip(skin.edition) }
                        if (equipped) SkinBadge(equipped = true)
                        if (skin.listedIn != null) SkinBadge(equipped = false)
                    }
                }
                EditionStrip(skin.edition)
            }
        }
        if (check.value > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = check.value
                        scaleY = check.value
                    }
                    .clip(tokens.shapes.indicator)
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Пустое состояние: веер пунктирных карт. */
@Composable
private fun EmptyPicker(text: String) {
    val cs = MaterialTheme.colorScheme
    val reduce = VlTheme.tokens.reduceMotion
    val fan = if (reduce) 0f else {
        rememberInfiniteTransition(label = "fan").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "fanP").value
    }
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(90.dp, 60.dp)) {
            listOf(0f, 8f, -8f).forEachIndexed { i, r ->
                SlotOutline(
                    Modifier
                        .offset(x = 10.dp, y = 6.dp)
                        .width(64.dp)
                        .graphicsLayer {
                            val p = ((fan - i * 0.08f).coerceIn(0f, 1f))
                            rotationZ = r * p
                            translationY = -2.dp.toPx() * p
                        },
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
