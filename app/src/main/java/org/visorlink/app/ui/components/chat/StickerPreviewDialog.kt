package org.visorlink.app.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.ui.components.VlAnimatedMedia
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/**
 * Полноэкранный модальный предпросмотр стикера при долгом нажатии.
 */
@Composable
fun StickerPreviewDialog(
    sticker: StickerItem,
    onDismiss: () -> Unit,
    onSend: (() -> Unit)? = null
) {
    val haptic = rememberHaptic()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val stickerSize = minOf(260.dp, screenWidth * 0.72f)

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        haptic.perform(HapticType.LONG_PRESS, true)
        visible = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Мягкое радиальное свечение за стикером
            Box(
                modifier = Modifier
                    .size(stickerSize + 80.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = 0.35f),
                                cs.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(
                    initialScale = 0.35f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = 380f
                    )
                ),
                exit = fadeOut(tween(130)) + scaleOut(
                    targetScale = 0.5f,
                    animationSpec = tween(130)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* предотвращаем закрытие при тапе по самому стикеру */ }
                ) {
                    // Эмодзи стикера в стеклянной плашке
                    if (sticker.emoji.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = cs.surface.copy(alpha = 0.88f),
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .then(
                                    if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                    else Modifier
                                )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sticker.emoji,
                                    fontSize = 28.sp
                                )
                            }
                        }
                    }

                    // Стикер крупным планом
                    Box(
                        modifier = Modifier
                            .size(stickerSize)
                            .then(
                                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, RoundedCornerShape(24.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        VlAnimatedMedia(
                            url = sticker.url,
                            contentDescription = sticker.emoji,
                            isLottie = sticker.isLottie,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Кнопка быстрой отправки стикера
                    if (onSend != null) {
                        VlButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, true)
                                onSend()
                                onDismiss()
                            },
                            modifier = Modifier
                                .height(46.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Отправить стикер",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Нажмите в любом месте, чтобы закрыть",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}
