package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.R
import org.visorlink.app.data.model.Message
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.visorlink.app.ui.theme.VlTheme
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GiftMessage(
    message: Message,
    chatId: String
) {
    var isOpening by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val isRedeemed = message.redeemed

    if (showOverlay) {
        FullscreenGiftOverlay(
            chatId = chatId,
            messageId = message.id,
            onDismiss = {
                showOverlay = false
                isOpening = false
            }
        )
    }

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .width(260.dp)
            .heightIn(min = 180.dp)
            .clip(VlTheme.tokens.shapes.card)
            .background(
                Brush.linearGradient(
                    colors = if (isRedeemed) listOf(Color(0xFF162523), Color(0xFF1B302E))
                    else listOf(Color(0xFF1A1510), Color(0xFF2D2418))
                )
            )
            .border(
                1.dp,
                if (isRedeemed) Color(0xFF2DA89A).copy(alpha = 0.4f)
                else Color(0xFFFFD700).copy(alpha = 0.3f),
                VlTheme.tokens.shapes.card
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRedeemed) {
                val bounce = rememberInfiniteTransition(label = "").animateFloat(
                    initialValue = -6f, targetValue = 6f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse), label = ""
                )
                Text("💎", fontSize = 56.sp, modifier = Modifier.offset(y = bounce.value.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.gift_pro_title), color = Color(0xFF3DBFB0), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.gift_redeemed_by, message.redeemedByUsername ?: ""), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            } else {
                Text("🎁", fontSize = 56.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.gift_pro_title), color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.gift_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .clip(VlTheme.tokens.shapes.card)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFF39C12))))
                        .clickable(enabled = !isOpening && !isRedeemed) {
                            isOpening = true
                            haptic.perform(HapticType.CLICK, true)
                            showOverlay = true
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isOpening) stringResource(R.string.gift_action_opening) else stringResource(R.string.gift_action_open), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Полноэкранный взрыв ──
private enum class FullscreenState { Shaking, Exploded, Error }

private data class Spark(val tx: Float, val ty: Float, val scale: Float, val dur1: Int, val dur2: Int, val color: Color, val rot: Float, val isCircle: Boolean)

@Composable
fun FullscreenGiftOverlay(chatId: String, messageId: String, onDismiss: () -> Unit) {
    var state by remember { mutableStateOf(FullscreenState.Shaking) }
    val haptic = rememberHaptic()

    val colors = listOf(Color(0xFFFFD700), Color(0xFFFF0055), Color(0xFF00E5CC), Color(0xFF831AD4), Color(0xFF0EA5E9), Color.White)
    val sparks = remember {
        List(110) {
            val angle = Random.nextDouble() * 2 * Math.PI
            val velocity = 280 + Random.nextDouble() * 550
            Spark(
                tx = (cos(angle) * velocity).toFloat(),
                ty = (sin(angle) * velocity).toFloat(),
                scale = 0.8f + Random.nextFloat() * 1.6f,
                dur1 = 450 + Random.nextInt(250),
                dur2 = 2500 + Random.nextInt(1200),
                color = colors.random(),
                rot = Random.nextFloat() * 6f - 3f,
                isCircle = Random.nextBoolean()
            )
        }
    }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        try {
            Firebase.functions("europe-west1").getHttpsCallable("redeemGift")
                .call(mapOf("chatId" to chatId, "messageId" to messageId))
                .await()

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 5000) delay(5000 - elapsed)

            state = FullscreenState.Exploded
            haptic.perform(HapticType.ERROR, true) // Взрыв
            delay(150)
            haptic.perform(HapticType.SUCCESS, true) // Звон
            delay(3500)
            onDismiss()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 5000) delay(5000 - elapsed)

            state = FullscreenState.Error
            haptic.perform(HapticType.ERROR, true)
            delay(2500)
            onDismiss()
        }
    }

    // Haptics тряски
    LaunchedEffect(state) {
        while (state == FullscreenState.Shaking) {
            haptic.perform(HapticType.LONG_PRESS, true)
            delay(250)
            haptic.perform(HapticType.LONG_PRESS, true)
            delay(750)
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            when (state) {
                FullscreenState.Shaking -> {
                    val shake = rememberInfiniteTransition(label = "").animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(50), RepeatMode.Reverse), label = "")
                    Text("🎁", fontSize = 160.sp, modifier = Modifier.offset(x = shake.value.dp))
                }
                FullscreenState.Exploded -> {
                    Box(modifier = Modifier.size(300.dp).background(Brush.radialGradient(listOf(Color(0x99FFD700), Color.Transparent))), contentAlignment = Alignment.Center) {
                        sparks.forEach { spark -> Particle(spark) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💎", fontSize = 180.sp)
                            Spacer(Modifier.height(32.dp))
                            Text(stringResource(R.string.gift_success_title), color = Color(0xFFFFD700), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        }
                    }
                }
                FullscreenState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 140.sp)
                        Spacer(Modifier.height(32.dp))
                        Box(modifier = Modifier.background(Color.Red.copy(alpha = 0.2f), VlTheme.tokens.shapes.card).padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Text(stringResource(R.string.gift_error_taken), color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Particle(spark: Spark) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(spark.dur1, easing = FastOutSlowInEasing))
        progress.animateTo(2f, tween(spark.dur2, easing = LinearEasing))
    }
    val x = spark.tx * progress.value.coerceAtMost(1f)
    val y = spark.ty * progress.value.coerceAtMost(1f) + if (progress.value > 1f) (progress.value - 1f) * 1000f else 0f
    val alpha = if (progress.value > 1f) 1f - (progress.value - 1f) else 1f

    Box(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .graphicsLayer {
                rotationZ = spark.rot * progress.value * 360f
                scaleX = spark.scale
                scaleY = spark.scale
                this.alpha = alpha
            }
            .size(14.dp)
            .background(spark.color, if (spark.isCircle) VlTheme.tokens.shapes.indicator else RectangleShape)
    )
}
