package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdCardTraits
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdFoil
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinRules
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.editionName
import org.visorlink.app.ui.components.idcard.finishName
import org.visorlink.app.ui.components.idcard.foilName
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.cardPalette
import org.visorlink.app.ui.theme.drawBase
import org.visorlink.app.ui.theme.vlInset
import kotlin.math.floor
import kotlin.random.Random

private enum class RollPhase { CHARGING, SPINNING, SETTLE, REVEAL, ERROR }

/** Лента: ~54 плашки, выпавшая — на 46-й (как в вебе). */
private const val REEL_COUNT = 54
private const val REEL_TARGET = 46
private const val SPIN_MS = 6400
private const val SETTLE_MS = 450
private val REEL_GAP = 12.dp

/**
 * Прокрутка скина (спека 1.7, веб: IdSkinRoll.jsx). Сначала `roll` («Печатаем бланк…») — сервер
 * выбирает скин и списывает Bits, — потом анимация с уже известным скином: барабан замедляется на
 * выпавшей плашке, затем раскрытие карты. При «уменьшить анимацию» — сразу результат.
 */
@Composable
internal fun IdSkinRoll(
    wearer: IdSkinWearer,
    person: IdCardPerson,
    onClose: () -> Unit,
    /** Лента и сдвиг остановки; в скриншот-тестах — с фиксированным seed. */
    random: Random = Random.Default,
) {
    val repo: IdCardRepository = koinInject()
    val haptics = rememberCardHaptics()
    val reduce = VlTheme.tokens.reduceMotion
    var phase by remember { mutableStateOf(RollPhase.CHARGING) }
    var skin by remember { mutableStateOf<IdSkin?>(null) }
    var reel by remember { mutableStateOf<List<IdCardTraits>>(emptyList()) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        haptics.rollStart()
        try {
            val s = repo.rollSkin()
            skin = s
            if (reduce) {
                phase = RollPhase.REVEAL
            } else {
                reel = buildReel(s, random)
                phase = RollPhase.SPINNING
            }
        } catch (e: Exception) {
            error = e
            phase = RollPhase.ERROR
        }
    }
    LaunchedEffect(phase) {
        if (phase != RollPhase.SETTLE) return@LaunchedEffect
        haptics.success()
        delay(SETTLE_MS + 650L)
        phase = RollPhase.REVEAL
    }

    val closable = phase == RollPhase.REVEAL || phase == RollPhase.ERROR
    val edition = skin?.edition ?: IdEdition.COMMON
    IdOverlay(
        onDismiss = onClose,
        dismissible = closable,
        tapOutsideToClose = false,
        background = { if (phase == RollPhase.REVEAL && !reduce) RevealFx(edition) },
    ) {
        when (phase) {
            RollPhase.CHARGING, RollPhase.ERROR -> Charging(failed = phase == RollPhase.ERROR, error = error)
            RollPhase.SPINNING, RollPhase.SETTLE -> Reel(
                reel = reel,
                mode = wearer.mode,
                settling = phase == RollPhase.SETTLE,
                random = random,
                onTick = { haptics.reelTick() },
                onStopped = { phase = RollPhase.SETTLE },
            )
            RollPhase.REVEAL -> skin?.let { Reveal(it, wearer, person, onClose) }
        }
    }
}

/** Лента барабана: случайные плашки, выпавшая — на [REEL_TARGET], рядом иногда дразнящие редкие. */
private fun buildReel(result: IdSkin, random: Random): List<IdCardTraits> {
    fun seed() = random.nextLong(1L, 0x1_0000_0000L)
    val items = MutableList(REEL_COUNT) { IdCardGenerator.generateTraits(seed()) }
    fun rare(): IdCardTraits {
        repeat(400) {
            val tr = IdCardGenerator.generateTraits(seed())
            if (tr.edition == IdEdition.EPIC || tr.edition == IdEdition.LEGENDARY) return tr
        }
        return items[0]
    }
    // «Чуть-чуть не хватило»
    if (result.edition != IdEdition.LEGENDARY) items[REEL_TARGET + 1] = rare()
    if (random.nextFloat() < 0.5f) items[REEL_TARGET - 1] = rare()
    items[REEL_TARGET] = result.traits
    return items
}

// ── Печать бланка ──

@Composable
private fun Charging(failed: Boolean, error: Throwable?) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val live = !failed && !tokens.reduceMotion
    val t = rememberInfiniteTransition(label = "capsule")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")
    val spin2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin2")
    val charge by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "charge")
    val breathe by t.animateFloat(1f, 0.5f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "breathe")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(failed) {
        if (!failed || tokens.reduceMotion) return@LaunchedEffect
        repeat(2) {
            shake.animateTo(-6f, tween(100))
            shake.animateTo(6f, tween(200))
            shake.animateTo(0f, tween(100))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ring = 2.dp.toPx()
                if (failed) {
                    drawCircle(cs.error.copy(alpha = 0.4f), size.minDimension / 2f - ring, style = Stroke(ring))
                    return@Canvas
                }
                val inner = 8.dp.toPx()
                rotate(if (live) spin else 0f) {
                    val tl = Offset(inner + ring / 2, inner + ring / 2)
                    val sz = Size(size.width - inner * 2 - ring, size.height - inner * 2 - ring)
                    drawArc(cs.primary, -135f, 90f, false, tl, sz, style = Stroke(ring))
                    drawArc(cs.primary.copy(alpha = 0.4f), -45f, 90f, false, tl, sz, style = Stroke(ring))
                }
                rotate(if (live) spin2 else 0f) {
                    val tl = Offset(ring / 2, ring / 2)
                    val sz = Size(size.width - ring, size.height - ring)
                    drawArc(IdCardMaterials.edition(IdEdition.EPIC), -135f, 90f, false, tl, sz, style = Stroke(ring))
                    drawArc(IdCardMaterials.edition(IdEdition.LEGENDARY).copy(alpha = 0.6f), 135f, 90f, false, tl, sz, style = Stroke(ring))
                }
            }
            val coreShape = tokens.shapes.rounded(8.dp)
            Box(
                Modifier
                    .graphicsLayer {
                        val c = if (live) charge else 0.5f
                        translationY = (2f - 6f * c) * density
                        val s = 0.96f + 0.08f * c
                        scaleX = s
                        scaleY = s
                        translationX = shake.value * density
                    }
                    .size(64.dp, 40.dp)
                    .clip(coreShape)
                    .background(Brush.linearGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerHigh)), coreShape),
            )
        }
        if (failed) {
            error?.let { SkinError(skinErrorText(it)) }
        } else {
            Text(
                stringResource(R.string.idskin_roll_charging),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = if (live) breathe else 1f },
            )
        }
    }
}

// ── Барабан ──

/** Палитры плашек считаются один раз на ленту — их ~50, и рисуются одним Canvas. */
private class ReelItem(val traits: IdCardTraits, val palette: org.visorlink.app.ui.theme.CardPalette)

@Composable
private fun Reel(
    reel: List<IdCardTraits>,
    mode: IdMode,
    settling: Boolean,
    random: Random,
    onTick: () -> Unit,
    onStopped: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val items = remember(reel, mode) { reel.map { ReelItem(it, cardPalette(mode, it.variant, it.finish)) } }
    val shimmer by rememberInfiniteTransition(label = "reel").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "glint")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OverlayKicker(stringResource(R.string.idskin_roll_kicker))
        BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
            val w = maxWidth
            val tile = (w / 3.4f).coerceIn(96.dp, 150.dp)
            val tileH = tile * 0.633f
            val windowH = tileH + 56.dp
            val px = with(density) { Triple(w.toPx(), tile.toPx(), REEL_GAP.toPx()) }
            val (wPx, tilePx, gapPx) = px
            val step = tilePx + gapPx
            val center = wPx / 2f - tilePx / 2f
            val geometry = remember(wPx, tilePx) {
                // Останов со случайным сдвигом внутри плашки, затем «доводка» к центру
                val jitter = (random.nextFloat() * 2f - 1f) * tilePx * 0.36f
                val settle = -(REEL_TARGET * step) + center
                Triple(center - step * 1.5f, settle + jitter, settle)
            }
            val (start, end, settle) = geometry
            val offset = remember { Animatable(start) }
            val spinTime = remember { Animatable(0f) }
            val needle = remember { Animatable(0f) }
            val winner = remember { Animatable(0f) }
            val dim = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                launch { spinTime.animateTo(1f, tween(SPIN_MS, easing = LinearEasing)) }
                offset.animateTo(end, tween(SPIN_MS, easing = SkinEasing.Reel))
                onStopped()
                offset.animateTo(settle, tween(SETTLE_MS, easing = SkinEasing.Settle))
            }
            LaunchedEffect(settling) {
                if (!settling) return@LaunchedEffect
                launch { dim.animateTo(0.35f, tween(500)) }
                winner.animateTo(1f, tween(900, easing = SkinEasing.Letter))
            }
            // Стрелка вздрагивает на каждой пересечённой плашке, с лёгкой вибрацией
            val settlingNow by androidx.compose.runtime.rememberUpdatedState(settling)
            LaunchedEffect(Unit) {
                var last: Int? = null
                snapshotFlow { floor((wPx / 2f - offset.value + gapPx / 2f) / step).toInt() }.collect { idx ->
                    if (last != null && idx != last && !settlingNow) {
                        onTick()
                        scope.launch {
                            needle.snapTo(0f)
                            needle.animateTo(-14f, tween(60))
                            needle.animateTo(0f, tween(80))
                        }
                    }
                    last = idx
                }
            }

            val windowShape = tokens.shapes.rounded(24.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(windowH)
                    .clip(windowShape)
                    .background(cs.surfaceContainerLow, windowShape)
                    .vlInset(tokens.structure, windowShape),
            ) {
                // Пока лента летит — лёгкое смазывание, к остановке пропадает (idsk-blur)
                val st = spinTime.value
                val blurPx = when {
                    st < 0.06f -> 1.4f * (st / 0.06f)
                    st < 0.45f -> 1.4f - 0.8f * ((st - 0.06f) / 0.39f)
                    st < 0.7f -> 0.6f * (1f - (st - 0.45f) / 0.25f)
                    else -> 0f
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .then(if (blurPx > 0.05f) Modifier.blur((blurPx * 0.6f).dp) else Modifier),
                ) {
                    val top = (size.height - tileH.toPx()) / 2f
                    val first = floor((-offset.value - tilePx) / step).toInt().coerceAtLeast(0)
                    val last = floor((-offset.value + size.width) / step).toInt().coerceAtMost(items.lastIndex)
                    for (i in first..last) {
                        if (i == REEL_TARGET && settling) continue
                        val x = offset.value + i * step
                        drawReelTile(items[i], mode, Offset(x, top), Size(tilePx, tileH.toPx()), shimmer, if (settling) dim.value else 1f, cs.surfaceContainerLow, 1f, 0f)
                    }
                    if (settling) {
                        // Выигрышная поверх остальных: увеличивается и светится цветом тиража
                        val x = offset.value + REEL_TARGET * step
                        val v = winner.value
                        val s = if (v < 0.4f) 1f + 0.14f * (v / 0.4f) else 1.14f - 0.06f * ((v - 0.4f) / 0.6f)
                        drawReelTile(items[REEL_TARGET], mode, Offset(x, top), Size(tilePx, tileH.toPx()), shimmer, 1f, cs.surfaceContainerLow, s, v.coerceIn(0f, 1f))
                    }
                    // Края гаснут (mask-image: linear-gradient 14% / 86%)
                    val fade = size.width * 0.14f
                    drawRect(Brush.horizontalGradient(listOf(cs.surfaceContainerLow, cs.surfaceContainerLow.copy(alpha = 0f)), 0f, fade), size = Size(fade, size.height))
                    drawRect(
                        Brush.horizontalGradient(listOf(cs.surfaceContainerLow.copy(alpha = 0f), cs.surfaceContainerLow), size.width - fade, size.width),
                        topLeft = Offset(size.width - fade, 0f), size = Size(fade, size.height),
                    )
                    // Линия по центру
                    drawLine(
                        Brush.verticalGradient(
                            0f to Color.Transparent, 0.2f to cs.primary.copy(alpha = 0.7f), 0.8f to cs.primary.copy(alpha = 0.7f), 1f to Color.Transparent,
                        ),
                        Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx(), alpha = 0.55f,
                    )
                }
                Needle(Modifier.align(Alignment.TopCenter).offset(y = (-2).dp), needle.value, cs.primary, down = true)
                Needle(Modifier.align(Alignment.BottomCenter).offset(y = 2.dp), 0f, cs.primary, down = false)
            }
            BitsFloat(-IdSkinRules.ROLL_COST_BITS, Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = (-28).dp))
        }
        val breathe by rememberInfiniteTransition(label = "spinText").animateFloat(1f, 0.5f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "b")
        Text(
            stringResource(R.string.idskin_roll_spinning),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = breathe },
        )
    }
}

/** Стрелка барабана — треугольник цвета primary с отсветом. */
@Composable
private fun Needle(modifier: Modifier, rotation: Float, color: Color, down: Boolean) {
    Canvas(
        modifier
            .size(18.dp, 22.dp)
            .graphicsLayer {
                rotationZ = rotation
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (down) 0f else 1f)
            },
    ) {
        val path = Path().apply {
            if (down) {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height)
            } else {
                moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2f, 0f)
            }
            close()
        }
        drawPath(path, color.copy(alpha = 0.35f), style = Stroke(6.dp.toPx()))
        drawPath(path, color)
    }
}

/**
 * Плашка барабана (ReelTile): материал карты, полоса шапки, место фото, строки, кружок голограммы
 * и полоса тиража — без полной отрисовки карты.
 */
private fun DrawScope.drawReelTile(
    item: ReelItem,
    mode: IdMode,
    topLeft: Offset,
    tileSize: Size,
    shimmer: Float,
    /** Непрозрачность: остальные плашки тускнеют до 0.35 поверх фона окна [fadeColor]. */
    alpha: Float,
    fadeColor: Color,
    scale: Float,
    glow: Float,
) {
    val t = item.traits
    val pal = item.palette
    val ed = IdCardMaterials.edition(t.edition)
    val r = 8.dp.toPx()
    val center = topLeft + Offset(tileSize.width / 2f, tileSize.height / 2f)
    scale(scale, scale, pivot = center) {
        if (glow > 0f) {
            for (i in 1..5) {
                val g = i * 4.dp.toPx()
                drawRoundRect(
                    ed.copy(alpha = 0.12f * glow),
                    topLeft - Offset(g, g), Size(tileSize.width + g * 2, tileSize.height + g * 2), CornerRadius(r + g),
                )
            }
        }
        // Тень плашки
        drawRoundRect(Color.Black.copy(alpha = 0.25f * alpha), topLeft + Offset(0f, 4.dp.toPx()), tileSize, CornerRadius(r))
        val clip = if (mode == IdMode.PROTOGEN) {
            Path().apply {
                val (x, y) = topLeft
                val (w, h) = tileSize
                moveTo(x + w * 0.06f, y); lineTo(x + w, y); lineTo(x + w, y + h * 0.88f)
                lineTo(x + w * 0.93f, y + h); lineTo(x, y + h); lineTo(x, y + h * 0.12f); close()
            }
        } else {
            Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + tileSize.width, topLeft.y + tileSize.height, CornerRadius(r))) }
        }
        clipPath(clip) {
            inset(topLeft.x, topLeft.y, size.width - topLeft.x - tileSize.width, size.height - topLeft.y - tileSize.height) {
                val w = size.width
                val h = size.height
                drawBase(pal.base)
                // Шапка
                when (mode) {
                    IdMode.PROTOGEN -> drawLine(pal.neon.copy(alpha = 0.8f), Offset(0f, h * 0.19f), Offset(w, h * 0.19f), 1.dp.toPx())
                    IdMode.BEAST -> drawRect(Brush.horizontalGradient(listOf(pal.band.copy(alpha = 0.35f), Color.Transparent)), size = Size(w, h * 0.19f))
                    IdMode.STANDARD -> drawRect(pal.band.copy(alpha = 0.9f), size = Size(w, h * 0.19f))
                }
                // Фото
                drawRoundRect(pal.photoBg, Offset(w * 0.07f, h * 0.27f), Size(w * 0.22f, h * 0.45f), CornerRadius(3.dp.toPx()))
                // Строки
                val lineH = 4.dp.toPx()
                val lx = w * 0.35f
                val lw = w * (1f - 0.35f - 0.08f)
                val ly = h * 0.31f
                val span = h * 0.34f - lineH
                listOf(0.8f to 0.7f, 1f to 0.45f, 0.55f to 0.45f).forEachIndexed { i, (k, a) ->
                    drawRoundRect(pal.muted.copy(alpha = a), Offset(lx, ly + span * i / 2f), Size(lw * k, lineH), CornerRadius(lineH / 2f))
                }
                // Голограмма
                if (t.foil != IdFoil.NONE) {
                    val d = w * 0.15f
                    val c = Offset(w * (1f - 0.06f) - d / 2f, h * (1f - 0.12f) - d / 2f)
                    val colors = if (t.foil == IdFoil.GALAXY) IdCardMaterials.ReelHoloGalaxy else IdCardMaterials.ReelHolo
                    rotate(30f, c) { drawCircle(Brush.sweepGradient(colors, c), d / 2f, c, alpha = 0.85f) }
                }
                // Полоса тиража
                val edH = 5.dp.toPx()
                if (t.edition == IdEdition.EPIC || t.edition == IdEdition.LEGENDARY) {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, ed.copy(alpha = 0.45f)), h - edH * 3, h - edH), Offset(0f, h - edH * 3), Size(w, edH * 2))
                }
                drawRect(ed, Offset(0f, h - edH), Size(w, edH))
                // Блик legendary
                if (t.edition == IdEdition.LEGENDARY) {
                    val x = -w + shimmer * w * 3f
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.4f to Color.Transparent, 0.5f to IdCardMaterials.ReelLegendaryGlint, 0.6f to Color.Transparent, 1f to Color.Transparent,
                            start = Offset(x - w * 0.5f, 0f), end = Offset(x + w * 0.5f, h * 0.6f),
                        ),
                    )
                }
                // Край
                drawRoundRect(pal.edge, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
                if (glow > 0f) drawRoundRect(ed.copy(alpha = glow), cornerRadius = CornerRadius(r), style = Stroke(2.dp.toPx()))
            }
        }
        if (alpha < 1f) drawPath(clip, fadeColor.copy(alpha = 1f - alpha))
    }
}

// ── Раскрытие ──

/** Вспышка, лучи и ударная волна цвета тиража — за картой, на 42% высоты окна. */
@Composable
private fun RevealFx(edition: IdEdition) {
    val ed = IdCardMaterials.edition(edition)
    val soft = IdCardMaterials.editionSoft(edition)
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1.6f, tween(1600, easing = LinearEasing)) }
    val rays by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "raysR")
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val c = Offset(size.width / 2f, size.height * 0.42f)
            // Лучи: repeating-conic 7° цвета через 20°, гаснут к краю
            val raysAlpha = seg(t, 0.1f, 0.8f) * (if (edition == IdEdition.COMMON) 0.5f else 1f)
            if (raysAlpha > 0f) {
                val radius = 380.dp.toPx()
                val brush = Brush.radialGradient(0f to soft, 0.18f to soft, 0.68f to soft.copy(alpha = 0f), center = c, radius = radius)
                rotate(rays, c) {
                    for (k in 0 until 18) {
                        drawArc(brush, k * 20f, 7f, true, c - Offset(radius, radius), Size(radius * 2, radius * 2), alpha = raysAlpha)
                    }
                }
            }
            // Свечение: 0 → 1.15 → 1, прозрачность 0 → 1 → 0.8
            val g = seg(t, 0f, 1.1f)
            if (g > 0f) {
                val s = if (g < 0.3f) 0.3f + 0.85f * (g / 0.3f) else 1.15f - 0.15f * ((g - 0.3f) / 0.7f)
                val a = if (g < 0.3f) g / 0.3f else 1f - 0.2f * ((g - 0.3f) / 0.7f)
                val rx = 260.dp.toPx() * s
                val ry = 200.dp.toPx() * s
                scale(1f, ry / rx, pivot = c) {
                    drawCircle(Brush.radialGradient(listOf(ed.copy(alpha = 0.45f * a), ed.copy(alpha = 0f)), c, rx), rx, c)
                }
            }
            // Ударная волна (у legendary — две)
            val waves = if (edition == IdEdition.LEGENDARY) listOf(0.15f to 3f, 0.45f to 2f) else listOf(0.15f to 3f)
            for ((delay, width) in waves) {
                val p = seg(t, delay, 1f)
                if (p <= 0f || p >= 1f) continue
                val e = SkinEasing.Shock.transform(p)
                val r = 60.dp.toPx() * (0.2f + 5.3f * e)
                drawCircle(ed.copy(alpha = 0.9f * (1f - e)), r, c, style = Stroke(width.dp.toPx()))
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Sparks(
                sparkCount(edition), if (edition == IdEdition.LEGENDARY) 260.dp else 190.dp, ed,
                Modifier.offset(y = maxHeight * 0.42f - maxHeight / 2f).fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reveal(skin: IdSkin, wearer: IdSkinWearer, person: IdCardPerson, onClose: () -> Unit) {
    val repo: IdCardRepository = koinInject()
    val haptics = rememberCardHaptics()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val reduce = VlTheme.tokens.reduceMotion
    val edition = skin.edition
    val time = remember { Animatable(if (reduce) 3f else 0f) }
    var equipping by remember { mutableStateOf(false) }
    var equipped by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val away = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.reveal(edition.ordinal)
        if (!reduce) time.animateTo(3f, tween(3000, easing = LinearEasing))
    }
    LaunchedEffect(equipped) {
        if (!equipped) return@LaunchedEffect
        if (!reduce) away.animateTo(1f, tween(800))
    }
    val t = time.value

    fun equipNow() {
        if (equipping) return
        equipping = true
        error = null
        scope.launch {
            try {
                repo.equipSkin(skin.id)
                equipped = true
                haptics.success()
                delay(900)
                onClose()
            } catch (e: Exception) {
                error = e
                equipping = false
            }
        }
    }

    fun Modifier.fadeUp(delay: Float, dur: Float) = graphicsLayer {
        val p = seg(t, delay, dur)
        alpha = p
        translationY = (1f - p) * 10.dp.toPx()
    }

    OverlayKicker(stringResource(R.string.idskin_roll_yours), Modifier.fadeUp(0.2f, 0.5f))

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(360.dp, maxWidth * 0.92f)
        if (edition == IdEdition.LEGENDARY && !reduce) {
            // Ореол только вокруг карты: кнопка «Оборот» под ней остаётся читаемой
            val halo by rememberInfiniteTransition(label = "halo").animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "haloR")
            Box(
                Modifier
                    .offset(y = (-14).dp)
                    .size(width + 28.dp, width * (54f / 85.6f) + 28.dp)
                    .graphicsLayer { alpha = 0.55f * seg(t, 0.6f, 1f) }
                    .blur(18.dp)
                    .drawBehind {
                        rotate(halo) { drawCircle(Brush.sweepGradient(IdCardMaterials.LegendaryHalo), size.maxDimension) }
                    },
            )
        }
        val p = seg(t, 0f, 1.1f)
        // idsk-card-in: снизу, из обратной стороны, с перелётом
        val e = if (reduce) 1f else SkinEasing.CardIn.transform(p)
        val a = away.value
        IdSkinThumb(
            skin.look, wearer, person,
            mintedAt = skin.mintedAt,
            interactive = !equipped,
            width = width,
            modifier = Modifier.graphicsLayer {
                alpha = (p / 0.55f).coerceIn(0f, 1f) * (1f - a)
                translationY = (60f * (1f - e) - 120f * a) * density
                rotationY = -180f * (1f - e)
                val s = (0.55f + 0.45f * e) * (1f - 0.65f * a)
                scaleX = s
                scaleY = s
                cameraDistance = 16f * density
            },
        )
    }

    EditionTitle(edition, t)

    val traits = skin.traits
    val chips = buildList {
        add(finishName(traits.finish) to false)
        add(foilName(traits.foil) to false)
        if (traits.laminated) add(stringResource(R.string.idskin_spec_laminated) to false)
        add(wearName(traits.wear) to false)
        add(skin.serial to true)
    }
    FlowRow(
        Modifier.widthIn(max = 460.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { i, (text, mono) ->
            val shape = VlTheme.tokens.shapes.pill
            Text(
                text,
                fontSize = 12.sp,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .fadeUp(1.1f + i * 0.08f, 0.45f)
                    .clip(shape)
                    .background(cs.surfaceContainer, shape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }

    error?.let { SkinError(skinErrorText(it)) }

    Row(Modifier.fadeUp(1.5f, 0.5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (equipped) {
            val shape = VlTheme.tokens.shapes.pill
            Row(
                Modifier.height(40.dp).clip(shape).background(VlTheme.tokens.selectionFill, shape).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Check, null, tint = cs.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.idskin_equipped), color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
        } else {
            SkinActionButton(stringResource(R.string.idskin_roll_keep), onClose, primary = false, enabled = !equipping, icon = Icons.Default.Inventory2)
            SkinActionButton(
                stringResource(if (equipping) R.string.idskin_equipping else R.string.idskin_roll_equip),
                ::equipNow, enabled = !equipping, icon = Icons.Default.AutoAwesome,
            )
        }
    }
}

/** Название тиража по буквам; у legendary — переливающееся золото. */
@Composable
private fun EditionTitle(edition: IdEdition, t: Float) {
    val name = editionName(edition).uppercase()
    val ink = editionInk(edition, darken = 0.7f)
    val legendary = edition == IdEdition.LEGENDARY && !VlTheme.tokens.reduceMotion
    val shimmer = if (legendary) {
        rememberInfiniteTransition(label = "title").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "titleShimmer").value
    } else 0f
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, textAlign = TextAlign.Center)
    Row(Modifier.padding(top = 8.dp)) {
        name.forEachIndexed { i, ch ->
            val raw = seg(t, 0.75f + i * 0.045f, 0.55f)
            val p = SkinEasing.Letter.transform(raw)
            Text(
                ch.toString(),
                style = if (edition == IdEdition.LEGENDARY) {
                    style.copy(
                        brush = Brush.linearGradient(
                            IdCardMaterials.LegendaryShimmer,
                            start = Offset(-200f + 400f * shimmer - i * 20f, 0f),
                            end = Offset(200f + 400f * shimmer - i * 20f, 60f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                        ),
                    )
                } else style.copy(color = ink),
                modifier = Modifier.graphicsLayer {
                    alpha = (raw * 2f).coerceAtMost(1f)
                    translationY = (1f - p) * 18.dp.toPx()
                    val s = 0.4f + 0.6f * p
                    scaleX = s
                    scaleY = s
                    rotationZ = -12f * (1f - p)
                },
            )
        }
    }
}
