package org.visorlink.app.ui.idcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.data.repository.card
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ION_COLOR = Color(0xFFFF5A1F)
private val ION_SOFT = Color(0xFFFFB38A)
private val LED_COLOR = Color(0xFF3DF2FF)

/**
 * Баннер над списком чатов: пульсирует оранжевым, проверяет ionGiftAvailable раз в 5 минут и на ON_RESUME.
 */
@Composable
fun IonGiftBanner(
    uid: String,
    onOpenReveal: () -> Unit,
    modifier: Modifier = Modifier,
    repo: IdCardRepository = koinInject()
) {
    var available by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun checkAvailability() {
        scope.launch {
            try {
                available = repo.ionGiftAvailable()
            } catch (_: Exception) {
                available = false
            }
        }
    }

    LaunchedEffect(uid) {
        checkAvailability()
        while (true) {
            delay(300_000L) // 5 минут
            checkAvailability()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!available) return

    val infiniteTransition = rememberInfiniteTransition(label = "ion_glow")
    val glowFrac by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val shape = RoundedCornerShape(18.dp)
    val haptic = rememberHaptic()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.2.dp,
                color = ION_COLOR.copy(alpha = 0.35f + 0.4f * glowFrac),
                shape = shape
            )
            .clickable {
                haptic.perform(HapticType.CLICK, true)
                onOpenReveal()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(modifier = Modifier.size(40.dp)) {
                drawGiftBox(size, lid = 0f, glow = 0.25f, wobble = 0f)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.idcard_ion_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.idcard_ion_banner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ION_COLOR.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * Диалог раскрытия подарка VL Ion:
 * 1. Коробка трясётся (мин. 1.4 с), пока идёт сетевой запрос claimIonGift.
 * 2. Взрыв искр и кольца, крышка слетает.
 * 3. 3D-разворот ID-карты.
 * 4. Кнопки «Оставить» и «Надеть».
 */
@Composable
fun IonGiftReveal(
    uid: String,
    onDismiss: () -> Unit,
    repo: IdCardRepository = koinInject(),
    users: UserRepository = koinInject()
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    var skin by remember { mutableStateOf<IdSkin?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var isEquipped by remember { mutableStateOf(false) }

    val burstAnim = remember { Animatable(0f) }

    val infiniteShake = rememberInfiniteTransition(label = "box_shake")
    val shakeVal by infiniteShake.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    val profile by remember(uid) { users.userProfileFlow(uid) }.collectAsStateWithLifecycle(initialValue = null)
    val cardState by remember(uid) { repo.cardFlow(uid) }.collectAsStateWithLifecycle(initialValue = IdCardState.Loading)
    val card = cardState.card

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        try {
            val claimed = repo.claimIonGift()
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1400L) delay(1400L - elapsed)

            skin = claimed
            haptic.perform(HapticType.SUCCESS, true)
            burstAnim.animateTo(1f, tween(1900, easing = LinearEasing))
        } catch (e: Throwable) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1400L) delay(1400L - elapsed)
            error = e
        }
    }

    fun equip() {
        val s = skin ?: return
        if (isBusy) return
        isBusy = true
        scope.launch {
            try {
                repo.equipSkin(s.id)
                haptic.perform(HapticType.CLICK, true)
                isEquipped = true
                delay(500)
                onDismiss()
            } catch (e: Throwable) {
                error = e
                isBusy = false
            }
        }
    }

    val wearer = card?.wearer(profile) ?: IdSkinWearer()
    val person = profile?.idCardPerson() ?: IdCardPerson(null, null, null)

    IdOverlay(onDismiss = { if (!isBusy) onDismiss() }, dismissible = !isBusy) {
        OverlayKicker(stringResource(R.string.idcard_ion_title).uppercase())
        OverlayTitle(stringResource(R.string.idcard_ion_title))

        val b = burstAnim.value
        val stageWidth = 280.dp
        val stageHeight = 220.dp

        Box(
            modifier = Modifier
                .width(stageWidth)
                .height(stageHeight),
            contentAlignment = Alignment.Center
        ) {
            // Вспышка и искры
            if (skin != null && b > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawBurst(size, b)
                }
            }

            // Коробка
            val boxFade = if (skin == null) 1f else (1f - ((b - 0.25f) / 0.3f)).coerceIn(0f, 1f)
            if (boxFade > 0f) {
                val wobble = if (skin == null) shakeVal * 0.12f else 0f
                val lid = ((b / 0.3f)).coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer { alpha = boxFade }
                ) {
                    drawGiftBox(size, lid = EaseOut.transform(lid), glow = if (skin == null) 0.15f else b.coerceIn(0f, 1f), wobble = wobble)
                }
            }

            // Карта
            if (skin != null) {
                val cardT = EaseOutBack.transform(((b - 0.28f) / 0.55f).coerceIn(0f, 1f))
                if (cardT > 0f) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                rotationY = (1f - cardT) * 180f
                                scaleX = 0.55f + 0.45f * cardT
                                scaleY = 0.55f + 0.45f * cardT
                                alpha = cardT
                            }
                    ) {
                        IdSkinThumb(
                            look = skin!!.look,
                            wearer = wearer,
                            person = person,
                            mintedAt = skin!!.mintedAt,
                            width = stageWidth,
                            interactive = b >= 1f
                        )
                    }
                }
            }
        }

        if (skin == null && error == null) {
            MutedText(stringResource(R.string.idcard_ion_opening))
        }

        if (skin != null) {
            EditionChip(skin!!.edition, large = true)
        }

        if (error != null) {
            SkinError(skinErrorText(error!!))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            if (skin == null && error != null) {
                SkinActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
            }
            if (skin != null) {
                SkinActionButton(stringResource(R.string.idskin_roll_keep), onDismiss, primary = false, enabled = !isBusy)
                SkinActionButton(
                    text = when {
                        isEquipped -> stringResource(R.string.idskin_equipped)
                        isBusy -> stringResource(R.string.idskin_equipping)
                        else -> stringResource(R.string.idskin_roll_equip)
                    },
                    onClick = ::equip,
                    enabled = !isBusy && !isEquipped,
                    icon = Icons.Default.Check
                )
            }
        }
    }
}

// ── Рисование коробки подарка ──

private fun DrawScope.drawGiftBox(size: Size, lid: Float, glow: Float, wobble: Float) {
    val w = size.width
    val h = size.height

    translate(w / 2f, h * 0.9f) {
        rotate(wobble * 180f / PI.toFloat()) {
            translate(-w / 2f, -h * 0.9f) {
                val bodyTop = h * 0.42f
                val bodyRect = Rect(w * 0.14f, bodyTop, w * 0.86f, bodyTop + h * 0.48f)

                // Свет изнутри
                if (glow > 0f) {
                    val radius = w * (0.35f + 0.5f * glow)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ION_SOFT.copy(alpha = 0.9f * glow), Color.Transparent),
                            center = Offset(w / 2f, bodyTop),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(w / 2f, bodyTop)
                    )
                }

                // Корпус
                val body = Path().apply {
                    addRoundRect(RoundRect(bodyRect, androidx.compose.ui.geometry.CornerRadius(w * 0.07f)))
                }
                drawPath(
                    body,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF7A3D), Color(0xFFD9420D)),
                        start = bodyRect.topLeft,
                        end = bodyRect.bottomRight
                    )
                )

                // Лента вертикальная
                val ribbonW = w * 0.14f
                drawRect(
                    color = Color(0xFFFFF1E6),
                    topLeft = Offset(w / 2f - ribbonW / 2f, bodyTop),
                    size = Size(ribbonW, bodyRect.height)
                )

                // Крышка
                val lift = lid * h * 0.55f
                translate(w / 2f, h * 0.36f) {
                    rotate(-lid * 28f) {
                        translate(-w / 2f, -h * 0.36f - lift) {
                            val lidRect = Rect(w * 0.08f, h * 0.28f, w * 0.92f, h * 0.44f)
                            val lidPath = Path().apply {
                                addRoundRect(RoundRect(lidRect, androidx.compose.ui.geometry.CornerRadius(w * 0.06f)))
                            }
                            drawPath(
                                lidPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF8A55), Color(0xFFE5501A)),
                                    start = lidRect.topLeft,
                                    end = lidRect.bottomRight
                                )
                            )
                            drawRect(
                                color = Color(0xFFFFF1E6),
                                topLeft = Offset(w / 2f - ribbonW / 2f, lidRect.top),
                                size = Size(ribbonW, lidRect.height)
                            )

                            // Бант
                            drawOval(
                                color = Color(0xFFFFF1E6),
                                topLeft = Offset(w * 0.29f, h * 0.145f),
                                size = Size(w * 0.22f, h * 0.15f),
                                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                            )
                            drawOval(
                                color = Color(0xFFFFF1E6),
                                topLeft = Offset(w * 0.49f, h * 0.145f),
                                size = Size(w * 0.22f, h * 0.15f),
                                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // LED точка на корпусе
                drawCircle(LED_COLOR, radius = w * 0.035f, center = Offset(w * 0.3f, bodyTop + h * 0.24f))
            }
        }
    }
}

// ── Рисование взрыва искр ──

private data class BurstPart(val angle: Float, val speed: Float, val size: Float, val hue: Int, val star: Boolean)

private val BURST_PARTS = List(46) { i ->
    val r = Random(i * 7919 + 3)
    BurstPart(
        angle = r.nextFloat() * 2f * PI.toFloat(),
        speed = 0.35f + r.nextFloat() * 0.75f,
        size = 1.5f + r.nextFloat() * 3.5f,
        hue = r.nextInt(3),
        star = r.nextFloat() < 0.3f
    )
}

private fun DrawScope.drawBurst(size: Size, t: Float) {
    if (t <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val out = EaseOutCubic.transform(t.coerceIn(0f, 1f))

    // Ударное кольцо
    val ringAlpha = (1f - t).coerceIn(0f, 1f)
    drawCircle(
        color = ION_COLOR.copy(alpha = ringAlpha),
        radius = r * (0.1f + 0.8f * out),
        center = c,
        style = Stroke(width = 3f * (1f - t) * density)
    )

    // Искры
    val colors = listOf(ION_COLOR, ION_SOFT, LED_COLOR)
    for (p in BURST_PARTS) {
        val d = out * p.speed * r * 1.8f
        val pos = c + Offset(cos(p.angle) * d, sin(p.angle) * d + t * t * r * 0.36f)
        val a = (1f - t).coerceIn(0f, 1f)
        val color = colors[p.hue].copy(alpha = a)

        if (p.star) {
            val s = p.size * 2.2f * density
            val path = Path().apply {
                moveTo(pos.x, pos.y - s)
                quadraticBezierTo(pos.x + s * 0.16f, pos.y - s * 0.16f, pos.x + s, pos.y)
                quadraticBezierTo(pos.x + s * 0.16f, pos.y + s * 0.16f, pos.x, pos.y + s)
                quadraticBezierTo(pos.x - s * 0.16f, pos.y + s * 0.16f, pos.x - s, pos.y)
                quadraticBezierTo(pos.x - s * 0.16f, pos.y - s * 0.16f, pos.x, pos.y - s)
                close()
            }
            drawPath(path, color)
        } else {
            drawCircle(color, radius = p.size * density, center = pos)
        }
    }
}
