package by.iposdev.visorlink.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import by.iposdev.visorlink.data.model.Message
import androidx.compose.ui.res.stringResource
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.MessageType
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch

private val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: Message,
    isMine: Boolean,
    canReact: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onReact: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            // ── Реакции — ряд эмодзи-кнопок вверху ───────────────────────────
            if (canReact && !message.deleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QUICK_EMOJIS.forEach { emoji ->
                        val alreadyReacted = message.parsedReactions
                            .find { it.emoji == emoji }
                            ?.uids?.contains(currentUid) == true

                        EmojiReactionButton(
                            emoji = emoji,
                            isSelected = alreadyReacted,
                            onClick = {
                                haptic.perform(HapticType.REACTION, true)
                                onReact(emoji)
                                onDismiss()
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // ── Превью сообщения ──────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = when (message.type) {
                        MessageType.IMAGE -> stringResource(R.string.action_preview_photo)
                        MessageType.VOICE -> stringResource(R.string.action_preview_voice)
                        MessageType.STICKER -> stringResource(R.string.action_preview_sticker)
                        else -> message.text?.take(80)?.let {
                            if ((message.text?.length ?: 0) > 80) "$it…" else it
                        } ?: "Message"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 2
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))

            // ── Действия по типу ──────────────────────────────────────────────
            if (!message.deleted) {
                when (message.type) {
                    MessageType.TEXT -> {
                        ActionItem(icon = Icons.Default.ContentCopy, label = stringResource(R.string.action_copy_text)) {
                            haptic.perform(HapticType.CLICK, true)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text ?: ""))
                            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    MessageType.IMAGE -> {
                        ActionItem(icon = Icons.Default.ZoomIn, label = stringResource(R.string.action_view_image)) {
                            haptic.perform(HapticType.CLICK, true)
                            onDismiss()
                            onOpenImage()
                        }
                        ActionItem(icon = Icons.Default.Download, label = stringResource(R.string.action_save_gallery)) {
                            haptic.perform(HapticType.CLICK, true)
                            onDismiss()
                            onSaveImage()
                        }
                    }
                    MessageType.VOICE -> {
                        ActionItem(icon = Icons.Default.Download, label = stringResource(R.string.action_save_voice)) {
                            haptic.perform(HapticType.CLICK, true)
                            onDismiss()
                            onSaveVoice()
                        }
                    }
                    else -> {}
                }

                ActionItem(icon = Icons.Default.Reply, label = stringResource(R.string.action_reply)) {
                    haptic.perform(HapticType.CLICK, true)
                    onDismiss()
                    onReply()
                }
            }

            if (isMine && !message.deleted) {
                ActionItem(icon = Icons.Default.Delete, label = stringResource(R.string.action_delete_message), destructive = true) {
                    haptic.perform(HapticType.LONG_PRESS, true)
                    onDismiss()
                    onDelete()
                }
            }
        }
    }
}

// ── Кнопка эмодзи с пружинящей анимацией ─────────────────────────────────────

@Composable
private fun EmojiReactionButton(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale.value)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
            .clickable {
                scope.launch {
                    // Резкий сжим → пружинящий отскок
                    scale.animateTo(0.75f, spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh
                    ))
                    scale.animateTo(1.25f, spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ))
                    scale.animateTo(1f, spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    ))
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            emoji,
            fontSize = 22.sp,
            modifier = Modifier.scale(if (isSelected) 1.1f else 1f)
        )
    }
}

// ── Пункт меню с анимацией нажатия ───────────────────────────────────────────

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale.value)
            .clickable {
                scope.launch {
                    scale.animateTo(0.96f, spring(stiffness = Spring.StiffnessHigh))
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = color
        )
    }
}