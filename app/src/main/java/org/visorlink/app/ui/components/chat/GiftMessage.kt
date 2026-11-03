package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdSkinLook
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.repository.UserGiftRepository
import org.visorlink.app.data.repository.UserGifts
import org.visorlink.app.data.repository.giftErrorText
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.idcard.EditionChip
import org.visorlink.app.ui.idcard.IdSkinThumb
import org.visorlink.app.ui.idcard.SkinError
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Сообщение-подарок в чате.
 * Если message.userGift == true, отображается подарок пользователя (UserGiftMessage).
 * Иначе — подарок администрации (AdminGiftMessage) с различием 100 Bits ("gold_star") и PRO.
 */
@Composable
fun GiftMessage(
    message: Message,
    chatId: String,
    modifier: Modifier = Modifier
) {
    if (message.userGift) {
        UserGiftMessage(message = message, modifier = modifier)
    } else {
        AdminGiftMessage(message = message, chatId = chatId, modifier = modifier)
    }
}

// ── Подарок от пользователя ──

@Composable
fun UserGiftMessage(
    message: Message,
    modifier: Modifier = Modifier,
    userGiftRepository: UserGiftRepository = koinInject()
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val currentUid = remember { Firebase.auth.currentUser?.uid.orEmpty() }
    val isMine = message.senderId == currentUid
    val status = message.giftStatus ?: "sent"

    val isFresh = remember(message.createdAt) {
        val created = message.createdAt?.toDate()?.time ?: 0L
        System.currentTimeMillis() - created < 30_000L
    }

    if (message.giftType == "postcard") {
        Postcard(
            design = message.design ?: "spark",
            text = message.text ?: "",
            from = message.senderUsername ?: "",
            play = isFresh,
            width = 260.dp,
            modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
        return
    }

    val skin = remember(message.giftSkin) {
        message.giftSkin?.let { IdSkinLook.parse(it) }
    }

    val isRedeemed = status == "redeemed"
    val isCancelled = status == "cancelled"
    val gold = VlTheme.tokens.status.warning

    Box(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .width(260.dp)
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
                else gold.copy(alpha = 0.35f),
                VlTheme.tokens.shapes.card
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (skin != null) {
                val wearer = remember { IdSkinWearer() }
                val person = remember(message.senderUsername) {
                    IdCardPerson(message.senderUsername, message.senderUsername, null)
                }
                IdSkinThumb(
                    look = skin,
                    wearer = wearer,
                    person = person,
                    width = 200.dp
                )
                Spacer(Modifier.height(8.dp))
                EditionChip(skin.edition)
                Spacer(Modifier.height(6.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(gold.copy(alpha = 0.16f), VlTheme.tokens.shapes.indicator),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💎", fontSize = 28.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = if (isMine) stringResource(R.string.gift_your_gift) else stringResource(R.string.gift_from_user, message.senderUsername ?: ""),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))

            val title = UserGifts.kindTitles[message.giftType] ?: stringResource(R.string.gift_user_title)
            Text(
                text = title,
                color = if (isRedeemed) Color(0xFF3DBFB0) else gold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            if (!message.text.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                isRedeemed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF3DBFB0), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gift_opened_by, message.redeemedByUsername ?: "—"),
                            color = Color(0xFF3DBFB0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                isCancelled -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.gift_cancelled),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                isMine -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏳", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.gift_waiting),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            enabled = !isBusy && !message.giftId.isNullOrEmpty(),
                            onClick = {
                                val giftId = message.giftId ?: return@TextButton
                                isBusy = true
                                errorText = null
                                scope.launch {
                                    try {
                                        userGiftRepository.cancel(giftId)
                                        haptic.perform(HapticType.CLICK, true)
                                    } catch (e: Exception) {
                                        errorText = giftErrorText(e)
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.gift_action_cancel), fontSize = 13.sp)
                            }
                        }
                    }
                }
                else -> {
                    // Получатель: кнопка «Открыть подарок»
                    Box(
                        modifier = Modifier
                            .clip(VlTheme.tokens.shapes.card)
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFF39C12))))
                            .clickable(enabled = !isBusy && !message.giftId.isNullOrEmpty()) {
                                val giftId = message.giftId ?: return@clickable
                                isBusy = true
                                errorText = null
                                scope.launch {
                                    try {
                                        userGiftRepository.redeem(giftId)
                                        haptic.perform(HapticType.SUCCESS, true)
                                    } catch (e: Exception) {
                                        errorText = giftErrorText(e)
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBusy) stringResource(R.string.gift_action_opening_gift) else stringResource(R.string.gift_action_open_gift),
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(8.dp))
                SkinError(errorText!!)
            }
        }
    }
}

// ── Подарок администрации ──

@Composable
fun AdminGiftMessage(
    message: Message,
    chatId: String,
    modifier: Modifier = Modifier
) {
    var isOpening by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val isRedeemed = message.redeemed
    val isBits = message.giftType == "gold_star"

    if (showOverlay) {
        FullscreenGiftOverlay(
            chatId = chatId,
            messageId = message.id,
            isBits = isBits,
            onDismiss = {
                showOverlay = false
                isOpening = false
            }
        )
    }

    Box(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
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
            val title = if (isBits) stringResource(R.string.gift_bits_title) else stringResource(R.string.gift_pro_title)
            if (isRedeemed) {
                val bounce = rememberInfiniteTransition(label = "").animateFloat(
                    initialValue = -6f, targetValue = 6f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse), label = ""
                )
                val icon = if (isBits) "⭐" else "💎"
                Text(icon, fontSize = 56.sp, modifier = Modifier.offset(y = bounce.value.dp))
                Spacer(Modifier.height(16.dp))
                Text(title, color = Color(0xFF3DBFB0), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.gift_redeemed_by, message.redeemedByUsername ?: ""), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            } else {
                val icon = if (isBits) "⭐" else "🎁"
                Text(icon, fontSize = 56.sp)
                Spacer(Modifier.height(16.dp))
                Text(title, color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 18.sp)
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
fun FullscreenGiftOverlay(
    chatId: String,
    messageId: String,
    isBits: Boolean = false,
    onDismiss: () -> Unit
) {
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
                    val icon = if (isBits) "⭐" else "🎁"
                    Text(icon, fontSize = 160.sp, modifier = Modifier.offset(x = shake.value.dp))
                }
                FullscreenState.Exploded -> {
                    Box(modifier = Modifier.size(300.dp).background(Brush.radialGradient(listOf(Color(0x99FFD700), Color.Transparent))), contentAlignment = Alignment.Center) {
                        sparks.forEach { spark -> Particle(spark) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val icon = if (isBits) "⭐" else "💎"
                            val title = if (isBits) stringResource(R.string.gift_bits_received) else stringResource(R.string.gift_pro_received)
                            Text(icon, fontSize = 180.sp)
                            Spacer(Modifier.height(32.dp))
                            Text(title, color = Color(0xFFFFD700), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
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
