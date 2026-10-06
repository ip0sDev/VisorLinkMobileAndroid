package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinOrigin
import org.visorlink.app.data.idcard.IdSkinRules
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.finishName
import org.visorlink.app.ui.components.idcard.foilName
import org.visorlink.app.ui.components.idcard.formatCardDate
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme

private enum class SheetOutro { EQUIP, SELL }

/**
 * Скин крупно (спека 1.6 п.4, веб: IdSkinSheet.jsx): повертеть, признаки и история, «Надеть»,
 * «Продать за 100 Bits» с подтверждением. Надели — карта уходит вверх, вспыхивает галочка;
 * продали — карта рассыпается, разлетаются монеты. У надетого действий нет.
 */
@Composable
internal fun IdSkinSheet(skin: IdSkin, isEquipped: Boolean, wearer: IdSkinWearer, person: IdCardPerson, onClose: () -> Unit) {
    val repo: IdCardRepository = koinInject()
    val scope = rememberCoroutineScope()
    val haptics = rememberCardHaptics()
    val cs = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    var confirmSell by remember { mutableStateOf(false) }
    var outro by remember { mutableStateOf<SheetOutro?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val listed = skin.listedIn != null
    val actionable = !isEquipped && !skin.virtual

    fun run(kind: SheetOutro, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
                haptics.success()
                outro = kind
                delay(if (kind == SheetOutro.EQUIP) 1000 else 1300)
                onClose()
            } catch (e: Exception) {
                error = e
                busy = false
                confirmSell = false
            }
        }
    }

    IdOverlay(onDismiss = { if (!busy) onClose() }, dismissible = !busy) {
        Row(
            Modifier.graphicsLayer { alpha = if (outro != null) 0f else 1f },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditionChip(skin.edition, large = true)
            if (isEquipped) SkinBadge(equipped = true, label = stringResource(R.string.idskin_equipped))
            if (listed) SkinBadge(equipped = false, label = stringResource(R.string.idskin_listed))
        }

        SheetCard(skin, wearer, person, outro)

        if (outro == null) {
            SpecsTable(skin)
            error?.let { SkinError(skinErrorText(it, IdSkinRules.SELL_PRICE_BITS)) }
            if (actionable) {
                if (confirmSell) {
                    val shape = VlTheme.tokens.shapes.card
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(lerp(cs.surface, cs.error, 0.08f), shape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.idskin_sell_confirm_text, IdSkinRules.SELL_PRICE_BITS), style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkinActionButton(stringResource(R.string.action_cancel), { confirmSell = false }, primary = false, enabled = !busy)
                            SkinActionButton(
                                stringResource(R.string.idskin_sell_confirm, IdSkinRules.SELL_PRICE_BITS),
                                { run(SheetOutro.SELL) { repo.sellSkin(skin.id) } },
                                destructive = true, enabled = !busy, icon = Icons.Default.Paid,
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkinActionButton(
                            stringResource(R.string.idskin_sell_btn, IdSkinRules.SELL_PRICE_BITS),
                            { confirmSell = true }, primary = false, enabled = !busy, icon = Icons.Default.Paid,
                        )
                        SkinActionButton(
                            stringResource(if (busy) R.string.idskin_equipping else R.string.idskin_equip),
                            { run(SheetOutro.EQUIP) { repo.equipSkin(skin.id) } },
                            enabled = !busy, icon = Icons.Default.Check,
                        )
                    }
                }
                if (listed) MutedText(stringResource(R.string.idskin_listed_hint), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AttachFile, null, tint = cs.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Text(
                    stringResource(if (isEquipped) R.string.idskin_equipped_hint else R.string.idskin_trade_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Карта крупно с ореолом тиража; уход при надевании, растворение и монеты при продаже. */
@Composable
private fun SheetCard(skin: IdSkin, wearer: IdSkinWearer, person: IdCardPerson, outro: SheetOutro?) {
    val cs = MaterialTheme.colorScheme
    val reduce = VlTheme.tokens.reduceMotion
    val ed = IdCardMaterials.edition(skin.edition)
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) enter.animateTo(1f, tween(700, easing = CubicBezierEasing(0.18f, 1.2f, 0.4f, 1f))) }
    val out = remember { Animatable(0f) }
    LaunchedEffect(outro) {
        if (outro == null || reduce) return@LaunchedEffect
        if (outro == SheetOutro.EQUIP) {
            delay(150)
            out.animateTo(1f, tween(900, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.4f)))
        } else {
            out.animateTo(1f, tween(1000, easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)))
        }
    }
    val check = remember { Animatable(0f) }
    LaunchedEffect(outro) {
        if (outro != SheetOutro.EQUIP) return@LaunchedEffect
        delay(300)
        check.animateTo(1f, tween(800, easing = SkinEasing.Letter))
    }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = min(340.dp, maxWidth * 0.92f)
        // Ореол тиража под картой
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (skin.edition == IdEdition.COMMON) 0.4f else 1f }
                .drawBehind {
                    drawOval(
                        Brush.radialGradient(
                            listOf(ed.copy(alpha = 0.26f), ed.copy(alpha = 0f)),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 0.75f,
                        ),
                    )
                },
        )
        val p = out.value
        val e = enter.value
        IdSkinThumb(
            skin.look, wearer, person,
            mintedAt = skin.mintedAt,
            interactive = outro == null,
            width = width,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .graphicsLayer {
                    alpha = e.coerceIn(0f, 1f) * when (outro) {
                        SheetOutro.EQUIP -> 1f - p
                        SheetOutro.SELL -> 1f - p
                        null -> 1f
                    }
                    when (outro) {
                        // idsk-equip-away: чуть вниз, затем вверх и в точку
                        SheetOutro.EQUIP -> {
                            val y = if (p < 0.3f) 8f * (p / 0.3f) else 8f - 128f * ((p - 0.3f) / 0.7f)
                            translationY = y * density
                            val s = if (p < 0.3f) 1f - 0.02f * (p / 0.3f) else 0.98f - 0.63f * ((p - 0.3f) / 0.7f)
                            scaleX = s; scaleY = s
                        }
                        // idsk-dissolve: вспышка, затем вниз и в размытие
                        SheetOutro.SELL -> {
                            val s = if (p < 0.3f) 1f + 0.02f * (p / 0.3f) else 1.02f - 0.32f * ((p - 0.3f) / 0.7f)
                            scaleX = s; scaleY = s
                            rotationZ = if (p < 0.3f) -2f * (p / 0.3f) else -2f * (1f - (p - 0.3f) / 0.7f)
                            translationY = (if (p < 0.3f) 0f else 30f * ((p - 0.3f) / 0.7f)) * density
                        }
                        null -> {
                            translationY = 60f * (1f - e) * density
                            rotationY = -180f * (1f - e)
                            val s = 0.55f + 0.45f * e
                            scaleX = s; scaleY = s
                            cameraDistance = 16f * density
                        }
                    }
                }
                .then(if (outro == SheetOutro.SELL) Modifier.blur((10f * p).dp) else Modifier),
        )
        if (outro == SheetOutro.SELL) {
            Sparks(22, 150.dp, IdCardMaterials.Coin, Modifier.matchParentSize(), coins = true)
            BitsFloat(IdSkinRules.SELL_PRICE_BITS, Modifier.align(Alignment.Center), large = true)
        }
        if (outro == SheetOutro.EQUIP) {
            val c = check.value
            Box(
                Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        val s = if (c < 0.6f) 0.2f + 0.95f * (c / 0.6f) else 1.15f - 0.15f * ((c - 0.6f) / 0.4f)
                        scaleX = s; scaleY = s
                        alpha = (c / 0.6f).coerceAtMost(1f)
                    }
                    .clip(VlTheme.tokens.shapes.indicator)
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/** Таблица признаков: отделка, голограмма, ламинирование, потёртость, оттенок, серийник, история. */
@Composable
private fun SpecsTable(skin: IdSkin) {
    val cs = MaterialTheme.colorScheme
    val t = skin.traits
    val origin = stringResource(
        when (skin.origin) {
            IdSkinOrigin.ISSUE -> R.string.idskin_origin_issue
            IdSkinOrigin.ROLL -> R.string.idskin_origin_roll
            IdSkinOrigin.TRADE -> R.string.idskin_origin_trade
        },
    )
    // (подпись, значение, моноширинное)
    val rows = buildList {
        add(Triple(stringResource(R.string.idskin_spec_finish), finishName(t.finish), false))
        add(Triple(stringResource(R.string.idskin_spec_foil), foilName(t.foil), false))
        add(Triple(stringResource(R.string.idskin_spec_laminated), stringResource(if (t.laminated) R.string.idskin_yes else R.string.idskin_no), false))
        add(Triple(stringResource(R.string.idskin_spec_wear), wearName(t.wear), false))
        add(Triple(stringResource(R.string.idskin_spec_variant), "${(((t.variant % 12) + 12) % 12) + 1} / 12", false))
        add(Triple(stringResource(R.string.idcard_field_serial), skin.serial, true))
        add(Triple(stringResource(R.string.idskin_origin_label), "$origin · ${formatCardDate(skin.obtainedAt)}", false))
        if (skin.trades > 0) add(Triple(stringResource(R.string.idskin_trades), skin.trades.toString(), false))
    }
    val shape = VlTheme.tokens.shapes.card
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.outlineVariant.copy(alpha = 0.5f), shape),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        rows.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                pair.forEach { (k, v, mono) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(k.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = cs.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1)
                        Text(
                            v,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (mono) FontFamily.Monospace else null,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun wearName(wear: Int): String = stringResource(
    when (wear) {
        0 -> R.string.idskin_wear_0
        1 -> R.string.idskin_wear_1
        2 -> R.string.idskin_wear_2
        else -> R.string.idskin_wear_3
    },
)
