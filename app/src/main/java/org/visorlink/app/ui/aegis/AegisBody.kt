// ui/aegis/AegisBody.kt
package org.visorlink.app.ui.aegis

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.data.model.aegis.LinkAction
import org.visorlink.app.data.model.aegis.LinkEmotion
import org.visorlink.app.data.model.aegis.VisorIcon
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun AnimatedProtogen(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val targetColor = when (emotion) {
        LinkEmotion.OFFENDED -> Color(0xFFFF3B30)
        LinkEmotion.HAPPY, LinkEmotion.PARTY -> Color(0xFF34C759)
        LinkEmotion.CURIOUS -> Color(0xFF32ADE6)
        LinkEmotion.SAD -> Color(0xFF007AFF)
        LinkEmotion.SLEEPY -> Color(0xFFAF52DE)
        else -> Color(0xFF32ADE6)
    }
    val visorColor by animateColorAsState(targetValue = targetColor, tween(500), label = "color")

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2000, 6000))
            isBlinking = true
            delay(150)
            isBlinking = false
        }
    }

    val targetEarRotation = when (emotion) {
        LinkEmotion.SAD, LinkEmotion.SLEEPY -> 60f
        LinkEmotion.HAPPY, LinkEmotion.CURIOUS -> -10f
        LinkEmotion.OFFENDED -> 45f
        else -> 15f
    }
    val earRotation by animateFloatAsState(targetValue = targetEarRotation, spring(dampingRatio = 0.6f), label = "ears")

    val squishScaleX by animateFloatAsState(if (isPressed) 1.1f else 1f, spring(dampingRatio = 0.4f), label = "squishX")
    val squishScaleY by animateFloatAsState(if (isPressed) 0.85f else breathScale, spring(dampingRatio = 0.4f), label = "squishY")

    Box(
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = squishScaleX
                scaleY = squishScaleY
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        // Уши
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 16.dp)
                .size(24.dp, 40.dp)
                .graphicsLayer { rotationZ = -earRotation }
                .clip(VlTheme.tokens.shapes.chip)
                .background(Color(0xFF2A2A2A))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = 16.dp)
                .size(24.dp, 40.dp)
                .graphicsLayer { rotationZ = earRotation }
                .clip(VlTheme.tokens.shapes.chip)
                .background(Color(0xFF2A2A2A))
        )

        // Тело
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(76.dp, 45.dp)
                .clip(RoundedCornerShape(topStart = VlTheme.tokens.shapes.cardRadius, topEnd = VlTheme.tokens.shapes.cardRadius))
                .background(Color(0xFF1E1E1E))
        )

        // Голова
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-5).dp)
                .size(96.dp, 64.dp)
                .clip(VlTheme.tokens.shapes.card)
                .background(Color(0xFF333333))
                .padding(4.dp)
                .clip(VlTheme.tokens.shapes.card)
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = emotion == LinkEmotion.HAPPY,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 8.dp, y = 8.dp)
            ) { Box(Modifier.size(12.dp, 6.dp).background(Color(0xFFFF4B4B).copy(alpha = 0.6f), VlTheme.tokens.shapes.indicator)) }

            AnimatedVisibility(
                visible = emotion == LinkEmotion.HAPPY,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp, y = 8.dp)
            ) { Box(Modifier.size(12.dp, 6.dp).background(Color(0xFFFF4B4B).copy(alpha = 0.6f), VlTheme.tokens.shapes.indicator)) }

            Text(
                text = if (isBlinking) "-  -" else (visorIcon?.toVisual() ?: "^_^"),
                color = visorColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun VisorIcon.toVisual(): String = when(this) {
    VisorIcon.HEART -> "♥_♥"
    VisorIcon.EXCLAMATION -> "0_0"
    VisorIcon.DOTS -> "•_•"
    VisorIcon.CROSS -> "X_X"
    VisorIcon.CHECKMARK -> "^_^"
    VisorIcon.ZZZ -> "-_-"
    VisorIcon.QUESTION -> "?_?"
    VisorIcon.SMILE -> "^_^"
    VisorIcon.ANGRY -> ">_<"
}

@Composable
fun AegisAura(
    action: LinkAction,
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    message: String = "",
    onDismiss: (wasOffended: Boolean) -> Unit,
    onBoop: () -> Unit = {},
    onPet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var petAccumulator by remember { mutableFloatStateOf(0f) }

    // Кастомная позиция, куда юзер прикрепил Линка
    var customPosition by remember { mutableStateOf<IntOffset?>(null) }

    val haptic = rememberHaptic()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "hover")
    val hoverY by infiniteTransition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover_y"
    )

    // Если движок прислал новую команду движения (не IDLE), сбрасываем кастомную позицию
    LaunchedEffect(action) {
        if (action != LinkAction.IDLE) {
            customPosition = null
        }
    }

    // Базовые позиции от движка
    val engineBaseOffset = remember(action, screenWidthPx, screenHeightPx) {
        when (action) {
            LinkAction.HUG_EDGE -> IntOffset(
                (-25 * density.density).toInt(),
                (screenHeightPx * 0.4f).toInt()
            )
            LinkAction.PEEK -> IntOffset(
                (screenWidthPx - 80 * density.density).toInt(),
                (screenHeightPx * 0.15f).toInt()
            )
            LinkAction.SIT -> IntOffset(
                (screenWidthPx * 0.6f).toInt(),
                (screenHeightPx - 160 * density.density).toInt()
            )
            LinkAction.WAVE -> IntOffset(
                (screenWidthPx * 0.3f).toInt(),
                (screenHeightPx * 0.4f).toInt()
            )
            else -> IntOffset(
                (screenWidthPx * 0.1f).toInt(),
                (screenHeightPx * 0.6f).toInt()
            )
        }
    }

    // Активная позиция: либо куда юзер прилепил, либо куда сказал движок
    val activeBaseOffset = customPosition ?: engineBaseOffset

    val animatedX by animateFloatAsState(
        targetValue = if (isDragging) activeBaseOffset.x + offsetX else activeBaseOffset.x.toFloat(),
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "x"
    )
    val animatedY by animateFloatAsState(
        targetValue = if (isDragging) activeBaseOffset.y + offsetY else activeBaseOffset.y.toFloat() + hoverY,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "y"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isDragging) (offsetX * 0.05f).coerceIn(-15f, 15f) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "aegis_rotation"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .graphicsLayer { rotationZ = rotation }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = message.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = VlTheme.tokens.shapes.button,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 8.dp).widthIn(max = 220.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(modifier = Modifier.size(120.dp)) {
                AnimatedProtogen(
                    emotion = emotion,
                    visorIcon = visorIcon,
                    isPressed = isPressed || isDragging,
                    modifier = Modifier.fillMaxSize()
                )

                // ── ЗОНА ГОЛОВЫ (Тапы и Глажка) ──
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(70.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = {
                                    haptic.perform(HapticType.REACTION, true)
                                    onBoop()
                                },
                                onLongPress = {
                                    haptic.perform(HapticType.LONG_PRESS, true)
                                    onDismiss(false) // Убрать долгим нажатием
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { petAccumulator = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    petAccumulator += abs(dragAmount)
                                    // Порог глажки снижен со 150 до 60
                                    if (petAccumulator > 60f) {
                                        onPet()
                                        haptic.perform(HapticType.SELECTION, true)
                                        petAccumulator = 0f
                                    }
                                }
                            )
                        }
                )

                // ── ЗОНА ТЕЛА (Перетаскивание и Снаппинг) ──
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = {
                                    haptic.perform(HapticType.REACTION, true)
                                    onBoop()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    haptic.perform(HapticType.SELECTION, true)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    isPressed = false

                                    // ── ЛОГИКА МАГНИТНОГО СНАППИНГА ──
                                    val dropX = activeBaseOffset.x + offsetX
                                    val dropY = activeBaseOffset.y + offsetY

                                    val aegisSize = 120 * density.density
                                    val bottomBarOffset = 160 * density.density // Высота бара ввода + отступ

                                    // Вычисляем расстояния до 3-х магнитных зон
                                    val distLeft = dropX
                                    val distRight = screenWidthPx - dropX - aegisSize
                                    val distBottom = screenHeightPx - dropY - bottomBarOffset

                                    val minDist = minOf(distLeft, distRight, distBottom)

                                    val snapX: Float
                                    val snapY: Float

                                    when (minDist) {
                                        distBottom -> {
                                            // Прилипает к боттом бару (можно двигать по X)
                                            snapX = dropX.coerceIn(0f, screenWidthPx - aegisSize)
                                            snapY = screenHeightPx - bottomBarOffset
                                        }
                                        distLeft -> {
                                            // Прилипает к сообщениям слева (можно двигать по Y)
                                            snapX = -25f * density.density
                                            snapY = dropY.coerceIn(0f, screenHeightPx - bottomBarOffset)
                                        }
                                        else -> {
                                            // Прилипает к своим сообщениям справа
                                            snapX = screenWidthPx - 80f * density.density
                                            snapY = dropY.coerceIn(0f, screenHeightPx - bottomBarOffset)
                                        }
                                    }

                                    customPosition = IntOffset(snapX.roundToInt(), snapY.roundToInt())
                                    offsetX = 0f
                                    offsetY = 0f

                                    haptic.perform(HapticType.SUCCESS, true)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            )
                        }
                )
            }
        }
    }
}