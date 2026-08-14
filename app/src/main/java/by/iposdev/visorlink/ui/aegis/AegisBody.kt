package by.iposdev.visorlink.ui.aegis

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.aegis.LinkAction
import by.iposdev.visorlink.data.model.aegis.LinkEmotion
import by.iposdev.visorlink.data.model.aegis.VisorIcon
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SchematicProtogen(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    modifier: Modifier = Modifier
) {
    val visorColor = when (emotion) {
        LinkEmotion.OFFENDED -> Color.Red
        LinkEmotion.HAPPY -> Color.Green
        LinkEmotion.CURIOUS -> Color.Cyan
        LinkEmotion.SAD -> Color.Blue
        else -> Color(0xFF00BFFF)
    }

    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        // Body/Neck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(70.dp, 50.dp)
                .clip(RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp))
                .background(Color(0xFF333333))
        )
        
        // Head / Visor Container
        Box(
            modifier = Modifier
                .size(90.dp, 60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF222222))
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = visorIcon?.toVisual() ?: "...",
                color = visorColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Fins/Ears
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = 12.dp)
                .size(25.dp, 35.dp)
                .clip(RoundedCornerShape(topStart = 12.dp))
                .background(Color(0xFF444444))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-12).dp, y = 12.dp)
                .size(25.dp, 35.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp))
                .background(Color(0xFF444444))
        )
    }
}

private fun VisorIcon.toVisual(): String = when(this) {
    VisorIcon.HEART -> "<3"
    VisorIcon.EXCLAMATION -> "!!"
    VisorIcon.DOTS -> "..."
    VisorIcon.CROSS -> "XX"
    VisorIcon.CHECKMARK -> "OK"
    VisorIcon.ZZZ -> "ZZZ"
    VisorIcon.QUESTION -> "??"
    VisorIcon.SMILE -> "^_^"
    VisorIcon.ANGRY -> ">:<"
}

@OptIn(ExperimentalFoundationApi::class)
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
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPlanted by remember { mutableStateOf(false) }
    
    // Эффект появления: плавно выезжаем из-за края
    var isIntroDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isIntroDone = true
    }

    val haptic = rememberHaptic()

    // Base position depends on action
    val baseOffset = remember(action) {
        when (action) {
            LinkAction.HUG_EDGE -> IntOffset(-80, 400)
            LinkAction.PEEK -> IntOffset(300, -20)
            LinkAction.SIT -> IntOffset(250, 600)
            else -> IntOffset(-150, 400)
        }
    }
    
    // Скрытая позиция для начала анимации появления
    val hiddenOffset = remember(action) {
        when (action) {
            LinkAction.HUG_EDGE -> IntOffset(-200, 400)
            LinkAction.PEEK -> IntOffset(300, -200)
            else -> IntOffset(-200, 400)
        }
    }

    val animatedOffset by animateIntOffsetAsState(
        targetValue = when {
            isDragging || isPlanted -> IntOffset(baseOffset.x + offsetX.roundToInt(), baseOffset.y + offsetY.roundToInt())
            !isIntroDone -> hiddenOffset
            else -> baseOffset
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aegis_offset"
    )

    // Visual peek effect - ограничим наклон чтобы не переворачивался
    val rotation by animateFloatAsState(
        targetValue = when {
            isDragging -> (offsetX * 0.05f).coerceIn(-30f, 30f)
            action == LinkAction.HUG_EDGE && !isPlanted -> 15f
            else -> 0f
        },
        label = "aegis_rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1.0f,
        label = "aegis_scale"
    )

    Box(
        modifier = modifier
            .offset { animatedOffset }
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(action) {
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true 
                        isPlanted = true
                        haptic.perform(HapticType.SELECTION, true)
                    },
                    onDragEnd = {
                        isDragging = false
                        val swipedToEdge = when (action) {
                            LinkAction.HUG_EDGE -> offsetX < -40
                            LinkAction.PEEK -> offsetY < -40
                            else -> false
                        }
                        val swipedAway = when (action) {
                            LinkAction.HUG_EDGE -> offsetX > 100
                            LinkAction.PEEK -> offsetY > 100
                            else -> abs(offsetX) > 150 || abs(offsetY) > 150
                        }

                        if (swipedToEdge) {
                            haptic.perform(HapticType.ERROR, true)
                            onDismiss(true)
                        } else if (swipedAway) {
                            haptic.perform(HapticType.SUCCESS, true)
                        } else {
                            haptic.perform(HapticType.CLICK, true)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        
                        // Глажка: свайп вниз
                        if (dragAmount.y > 10 && abs(dragAmount.x) < 5) {
                            haptic.perform(HapticType.SELECTION, true) // Эмуляция жужжания
                            onPet()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic.perform(HapticType.REACTION, true)
                        onBoop()
                    }
                )
            }
    ) {
        var showMenu by remember { mutableStateOf(false) }

        Box(modifier = Modifier.combinedClickable(
            onLongClick = { showMenu = true },
            onClick = {}
        )) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (message.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 200.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                SchematicProtogen(emotion = emotion, visorIcon = visorIcon)
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Не мешай, пожалуйста") },
                onClick = {
                    haptic.perform(HapticType.SUCCESS, true)
                    showMenu = false
                    onDismiss(false)
                }
            )
        }
    }
}
