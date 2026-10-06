package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinRules
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.idcard.RollState
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdSkinsState
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset
import kotlin.math.max

private val GRID_GAP = 12.dp
private val TILE_MIN = 140.dp

/**
 * Инвентарь скинов в настройках «ID-карта» (спека 1.6, веб: IdSkinInventory.jsx): баланс и слоты,
 * прокрутка раз в неделю за 200 Bits (нужен свободный слот), сетка слотов — скины, пустые
 * «карманы», «+ Ещё слот». Скин → лист с «Надеть» / «Продать». Надетый тоже занимает слот.
 */
@Composable
fun IdSkinInventory(card: IdCard, profile: UserProfile, modifier: Modifier = Modifier) {
    val repo: IdCardRepository = koinInject()
    val skinsState by remember(profile.uid) { repo.skinsFlow(profile.uid) }.collectAsState(initial = IdSkinsState.Loading)
    val raw = (skinsState as? IdSkinsState.Ready)?.skins.orEmpty()
    val loading = skinsState is IdSkinsState.Loading
    val scope = rememberCoroutineScope()
    val haptics = rememberCardHaptics()

    var rolling by remember { mutableStateOf(false) }
    var confirmRoll by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    var confirmSlot by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var unlocked by remember { mutableIntStateOf(0) }

    // Новые скины (прокрутка, обмен) коротко подсвечиваются
    var known by remember { mutableStateOf<Set<String>?>(null) }
    var fresh by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(skinsState) {
        if (skinsState !is IdSkinsState.Ready) return@LaunchedEffect
        val ids = raw.map { it.id }.toSet()
        known?.let { prev -> (ids - prev).takeIf { it.isNotEmpty() }?.let { fresh = it } }
        known = ids
    }
    LaunchedEffect(fresh) {
        if (fresh.isEmpty()) return@LaunchedEffect
        delay(3_600)
        fresh = emptySet()
    }

    val bits = profile.bits
    val items = IdSkinRules.inventoryOf(card, raw)
    val slots = IdSkinRules.slotsOf(card)
    val free = max(0, slots - items.size)
    val equippedId = IdSkinRules.equippedId(card, items)
    val rollLeft = rememberCountdown(IdSkinRules.rollAvailableAt(card))
    val rollState = if (rollLeft > 0) RollState.COOLDOWN else IdSkinRules.rollState(card, bits, items.size, System.currentTimeMillis())
    val wearer = card.wearer(profile)
    val person = profile.idCardPerson()
    val open = items.firstOrNull { it.id == openId }

    fun buySlot() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                repo.buySkinSlot()
                haptics.success()
                unlocked++
                confirmSlot = false
            } catch (e: Exception) {
                error = e
            } finally {
                busy = false
            }
        }
    }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InventoryHeader(bits = bits, used = items.size, slots = slots)

        RollPanel(
            state = rollState,
            left = rollLeft,
            confirming = confirmRoll,
            onAsk = { confirmRoll = true },
            onCancel = { confirmRoll = false },
            onGo = { confirmRoll = false; rolling = true },
        )

        if (loading && items.isEmpty()) {
            MutedText(stringResource(R.string.idcard_tab_loading))
        } else {
            SlotGrid(
                items = items,
                equippedId = equippedId,
                fresh = fresh,
                free = free,
                unlocked = unlocked,
                slots = slots,
                bits = bits,
                wearer = wearer,
                person = person,
                confirmSlot = confirmSlot,
                busy = busy,
                onOpen = { openId = it },
                onAskSlot = { confirmSlot = true },
                onCancelSlot = { confirmSlot = false },
                onBuySlot = ::buySlot,
            )
        }
        error?.let { SkinError(skinErrorText(it, IdSkinRules.SLOT_PRICE_BITS)) }
        MutedText(stringResource(R.string.idskin_footer))
    }

    if (rolling) {
        IdSkinRoll(wearer = wearer, person = person, onClose = { rolling = false })
    }
    open?.let { skin ->
        IdSkinSheet(skin = skin, isEquipped = skin.id == equippedId, wearer = wearer, person = person, onClose = { openId = null })
    }
}

@Composable
private fun InventoryHeader(bits: Int, used: Int, slots: Int) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val pill = tokens.shapes.pill
    val balanceDesc = stringResource(R.string.idskin_balance_aria, bits)
    val countDesc = stringResource(R.string.idskin_count_aria, used, slots)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = balanceDesc }
                .clip(pill)
                .background(cs.surfaceContainerLow, pill)
                .vlInset(tokens.structure, pill)
                .height(28.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Paid, null, tint = tokens.status.warning, modifier = Modifier.size(14.dp))
            Text("%,d".format(bits), style = tokens.data.dataMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
            Text("Bits", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.semantics(mergeDescendants = true) { contentDescription = countDesc }, verticalAlignment = Alignment.Bottom) {
            Text("$used", style = tokens.data.dataMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("/$slots", style = tokens.data.dataMedium, color = cs.onSurfaceVariant, fontSize = 15.sp)
        }
    }
}

/** Панель прокрутки: кулдаун с кольцом недели, подтверждение или кнопка (неактивна без слота / Bits). */
@Composable
private fun RollPanel(state: RollState, left: Long, confirming: Boolean, onAsk: () -> Unit, onCancel: () -> Unit, onGo: () -> Unit) {
    VlSurface(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state == RollState.COOLDOWN -> {
                    CooldownRing(left)
                    PanelText(stringResource(R.string.idskin_roll_next), stringResource(R.string.idskin_roll_wait, formatWait(left)), Modifier.weight(1f))
                }
                confirming -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelText(stringResource(R.string.idskin_roll_confirm_title), stringResource(R.string.idskin_roll_confirm_text, IdSkinRules.ROLL_COST_BITS))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkinActionButton(stringResource(R.string.action_cancel), onCancel, primary = false)
                            SkinActionButton(stringResource(R.string.idskin_roll_go), onGo, icon = Icons.Default.Casino)
                        }
                    }
                }
                else -> {
                    // Текст сверху, кнопка под ним — как flex-wrap веба на узком экране
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelText(
                            stringResource(R.string.idskin_roll_title),
                            when (state) {
                                RollState.FULL -> stringResource(R.string.idskin_roll_full)
                                RollState.BITS -> stringResource(R.string.idcard_tab_err_bits, IdSkinRules.ROLL_COST_BITS)
                                else -> stringResource(R.string.idskin_roll_desc, IdSkinRules.ROLL_COST_BITS)
                            },
                        )
                        RollButton(enabled = state == RollState.READY, onClick = onAsk, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelText(title: String, text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
        Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Кольцо ожидания прокрутки: сколько недели уже прошло. */
@Composable
private fun CooldownRing(left: Long) {
    val cs = MaterialTheme.colorScheme
    val progress = (1f - left.toFloat() / IdSkinRules.ROLL_COOLDOWN_MS).coerceIn(0f, 1f)
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(cs.surfaceContainerHighest, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(cs.primary, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(Icons.Default.Casino, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/**
 * «Прокрутить · 200 Bits»: заметная кнопка с бегущей каймой цветов тиражей и бликом — единственный
 * «живой» элемент вкладки. Неактивная — плоская, без эффектов.
 */
@Composable
private fun RollButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape: Shape = tokens.shapes.pill
    val live = enabled && !tokens.reduceMotion
    val transition = rememberInfiniteTransition(label = "rollBtn")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "rim")
    val sweep by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 2800; 0f at 0; 1f at 1120; 1f at 2800 }, initialStartOffset = StartOffset(600)),
        label = "sweep",
    )
    val fg = if (enabled) cs.onPrimary else cs.onSurfaceVariant
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .drawBehind {
                if (!enabled) {
                    drawRect(cs.surfaceContainerHighest)
                    return@drawBehind
                }
                // Кайма: вращающийся conic-gradient под заливкой с отступом 2dp
                if (live) {
                    rotate(angle) {
                        val r = size.maxDimension
                        drawCircle(Brush.sweepGradient(listOf(cs.primary) + IdCardMaterials.RollRim + cs.primary, center), r)
                    }
                } else {
                    drawRect(cs.primary)
                }
                val inset = 2.dp.toPx()
                val inner = shape.createOutline(Size(size.width - inset * 2, size.height - inset * 2), layoutDirection, this)
                translate(inset, inset) { drawOutline(inner, cs.primary) }
                if (live && sweep in 0.001f..0.999f) {
                    val x = -size.width + sweep * size.width * 2f
                    drawRect(
                        Brush.linearGradient(
                            0.3f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.35f), 0.7f to Color.Transparent,
                            start = Offset(x, 0f), end = Offset(x + size.width, size.height * 0.4f),
                        ),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Casino, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.idskin_roll_btn), color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        PricePill(IdSkinRules.ROLL_COST_BITS, fg, Color.Black.copy(alpha = 0.18f), Modifier.height(32.dp))
    }
}

@Composable
internal fun PricePill(amount: Int, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    val shape = VlTheme.tokens.shapes.pill
    Row(
        modifier.clip(shape).background(bg, shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$amount", color = fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("Bits", color = fg.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

/** Сетка: скины, пустые слоты, «+ Ещё слот» или «Максимум». Высота ячеек — по плитке скина. */
@Composable
private fun SlotGrid(
    items: List<IdSkin>,
    equippedId: String?,
    fresh: Set<String>,
    free: Int,
    unlocked: Int,
    slots: Int,
    bits: Int,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    confirmSlot: Boolean,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onAskSlot: () -> Unit,
    onCancelSlot: () -> Unit,
    onBuySlot: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = max(2, ((maxWidth + GRID_GAP) / (TILE_MIN + GRID_GAP)).toInt())
        val cell = (maxWidth - GRID_GAP * (cols - 1)) / cols
        val thumbW = cell - 20.dp
        val tileH = thumbW * (54f / 85.6f) + 46.dp
        val cells = buildList {
            items.forEachIndexed { i, skin -> add(GridCell.Skin(skin, i)) }
            repeat(free) { i -> add(GridCell.Empty(items.size + i, unlocked = i >= free - unlocked)) }
            add(if (slots < IdSkinRules.MAX_SLOTS) GridCell.Buy(items.size + free) else GridCell.Max)
        }
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            cells.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    row.forEach { c ->
                        val m = Modifier.width(cell).height(tileH)
                        when (c) {
                            is GridCell.Skin -> key(c.skin.id) {
                                SkinTile(c.skin, c.skin.id == equippedId, c.skin.id in fresh, c.index, wearer, person, thumbW, m) { onOpen(c.skin.id) }
                            }
                            is GridCell.Empty -> EmptySlot(c.index, c.unlocked, m)
                            is GridCell.Buy -> BuySlotTile(confirmSlot, busy, bits >= IdSkinRules.SLOT_PRICE_BITS, c.index, onAskSlot, onCancelSlot, onBuySlot, m)
                            GridCell.Max -> MaxedTile(m)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface GridCell {
    data class Skin(val skin: IdSkin, val index: Int) : GridCell
    data class Empty(val index: Int, val unlocked: Boolean) : GridCell
    data class Buy(val index: Int) : GridCell
    data object Max : GridCell
}

/** Появление плитки: снизу с лёгким перелётом, по очереди (`idsk-tile-in`). */
@Composable
private fun rememberTileIn(index: Int): Animatable<Float, *> {
    val reduce = VlTheme.tokens.reduceMotion
    val anim = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index * 45L)
        anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    return anim
}

private fun Modifier.tileIn(p: Float): Modifier = graphicsLayer {
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * 14.dp.toPx()
    val s = 0.9f + 0.1f * p
    scaleX = s
    scaleY = s
}

@Composable
private fun SkinTile(
    skin: IdSkin,
    equipped: Boolean,
    fresh: Boolean,
    index: Int,
    wearer: IdSkinWearer,
    person: IdCardPerson,
    thumbW: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tokens = VlTheme.tokens
    val appear = rememberTileIn(index)
    val ed = IdCardMaterials.edition(skin.edition)
    // Новый скин: вспышка цвета тиража дважды
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(fresh) {
        if (!fresh || tokens.reduceMotion) return@LaunchedEffect
        delay(300)
        repeat(2) {
            pulse.animateTo(1f, tween(640))
            pulse.animateTo(0f, tween(960))
        }
    }
    val shape = tokens.shapes.card
    val label = stringResource(R.string.idcard_edition_label) + ": " +
        org.visorlink.app.ui.components.idcard.editionName(skin.edition) + ", " + skin.serial +
        if (equipped) ", " + stringResource(R.string.idskin_equipped) else ""
    VlSurface(
        modifier = modifier
            .tileIn(appear.value)
            .semantics { contentDescription = label }
            .drawWithContent {
                drawContent()
                if (pulse.value > 0f) {
                    val o = shape.createOutline(size, layoutDirection, this)
                    drawOutline(o, ed.copy(alpha = pulse.value), style = Stroke(4.dp.toPx() * pulse.value))
                    drawOutline(o, ed.copy(alpha = 0.25f * pulse.value), style = Stroke(14.dp.toPx() * pulse.value))
                }
            },
        overrideColor = if (equipped) tokens.selectionFill else null,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IdSkinThumb(skin.look, wearer, person, mintedAt = skin.mintedAt, width = thumbW)
                Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { EditionChip(skin.edition) }
                    if (equipped) SkinBadge(equipped = true)
                    if (skin.listedIn != null) SkinBadge(equipped = false)
                }
            }
            EditionStrip(skin.edition)
        }
    }
}

/** Пунктирный контур карты внутри слота. */
@Composable
internal fun SlotOutline(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val shape = VlTheme.tokens.shapes.rounded(10.dp)
    Box(
        modifier
            .aspectRatio(85.6f / 54f)
            .drawBehind { dashedOutline(shape, color, 1.5.dp.toPx()) },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.dashedOutline(shape: Shape, color: Color, width: Float) {
    val o = shape.createOutline(size, layoutDirection, this)
    drawOutline(o, color, style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/** Пустой слот — вдавленный «карман», принимает карту. Только что купленный «открывается». */
@Composable
private fun EmptySlot(index: Int, unlocked: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val appear = rememberTileIn(index)
    val unlock = remember { Animatable(if (unlocked && !tokens.reduceMotion) 0f else 1f) }
    LaunchedEffect(unlocked) { if (unlocked && !tokens.reduceMotion) unlock.animateTo(1f, tween(900)) }
    val shape = tokens.shapes.card
    val u = unlock.value
    val desc = stringResource(R.string.idskin_empty_slot)
    Box(
        modifier
            .tileIn(appear.value)
            .graphicsLayer {
                // idsk-unlock: 0.6 и −4° → 1.05 → 1
                val s = if (u < 0.6f) 0.6f + (1.05f - 0.6f) * (u / 0.6f) else 1.05f - 0.05f * ((u - 0.6f) / 0.4f)
                scaleX = s
                scaleY = s
                rotationZ = if (u < 0.6f) -4f * (1f - u / 0.6f) else 0f
                alpha = (u * 2f).coerceAtMost(1f)
            }
            .semantics { contentDescription = desc }
            .clip(shape)
            .background(cs.surfaceContainerLow, shape)
            .vlInset(tokens.structure, shape)
            .drawWithContent {
                drawContent()
                if (u < 1f) {
                    val o = shape.createOutline(size, layoutDirection, this)
                    drawOutline(o, cs.primary.copy(alpha = 1f - u), style = Stroke(2.dp.toPx()))
                }
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        SlotOutline(Modifier.fillMaxWidth(0.78f))
    }
}

@Composable
private fun BuySlotTile(
    confirm: Boolean,
    busy: Boolean,
    canPay: Boolean,
    index: Int,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val appear = rememberTileIn(index)
    val shape = tokens.shapes.card
    Box(
        modifier
            .tileIn(appear.value)
            .clip(shape)
            .drawBehind { dashedOutline(shape, cs.outline.copy(alpha = 0.6f), 1.5.dp.toPx()) }
            .then(if (confirm) Modifier else Modifier.clickable(onClick = onAsk)),
        contentAlignment = Alignment.Center,
    ) {
        if (confirm) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.idskin_slot_confirm, IdSkinRules.SLOT_PRICE_BITS),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkinActionButton(stringResource(R.string.idskin_no), onCancel, primary = false, enabled = !busy, modifier = Modifier.height(36.dp))
                    SkinActionButton(stringResource(R.string.idskin_yes), onBuy, enabled = !busy && canPay, modifier = Modifier.height(36.dp))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Add, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.idskin_slot_buy), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                PricePill(IdSkinRules.SLOT_PRICE_BITS, cs.onSurface, cs.surfaceContainerHigh, Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MaxedTile(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = VlTheme.tokens.shapes.card
    Column(
        modifier
            .clip(shape)
            .drawBehind { dashedOutline(shape, cs.outline.copy(alpha = 0.6f), 1.5.dp.toPx()) }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.Lock, null, tint = cs.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.idskin_slot_max, IdSkinRules.MAX_SLOTS),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
