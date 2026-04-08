package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.theme.Biolume
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmDividerTop
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticHelper
import by.iposdev.visorlink.utils.HapticType
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════════
//  Exthru Chat Bottom Bar
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ExthruChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    isDark: Boolean = false,                    // ← добавлен параметр тёмной темы
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    val inputShape = RoundedCornerShape(22.dp)

    // Адаптивные цвета на основе isDark
    val barBg   = ExthruChat.barBg(isDark)
    val inputBg = ExthruChat.inputBg(isDark)
    val textPrimary   = ExthruChat.textPrimary(isDark)
    val textSecondary = ExthruChat.textSecondary(isDark)
    val textHint      = ExthruChat.textHint(isDark)
    val replyBg       = if (isDark) Biolume.DarkAbyssSurface else ExthruChat.InputBg

    Column(
        modifier = Modifier
            .background(barBg)
            .nmDividerTop(isDark = isDark)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Restriction banner
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage ->
                stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(
                color = ExthruChat.Destructive.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    restriction ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExthruChat.Destructive,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Reply banner
        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(replyBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.type == MessageType.ALBUM && msg.images.isNotEmpty()) {
                        AsyncImage(
                            model = msg.images[0].url, contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.width(3.dp).height(32.dp)
                            .background(ExthruChat.Accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "@${msg.senderUsername}", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = ExthruChat.Accent,
                        )
                        Text(
                            when (msg.type) {
                                MessageType.IMAGE   -> stringResource(R.string.photo)
                                MessageType.VOICE   -> stringResource(R.string.voice_message)
                                MessageType.STICKER -> stringResource(R.string.sticker)
                                MessageType.ALBUM   -> msg.caption ?: "📷 ${msg.images.size} фото"
                                else -> msg.text ?: stringResource(R.string.media)
                            },
                            fontSize = 12.sp, color = textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = textHint, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Upload progress
        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(),
                color      = ExthruChat.Accent,
                trackColor = inputBg,
            )
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .exthruSmallRaisedShadow(isDark = isDark)
                                .background(barBg, CircleShape)
                                .clickable(enabled = !uiState.isCooldown) { onAttach() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AttachFile, null,
                                tint = textSecondary, modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // Поле ввода — inset неоморфизм
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(inputShape)
                            .background(inputBg, inputShape)
                            .nmInsetShadow(isDark = isDark, cornerRadius = 22.dp)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value         = inputText,
                                onValueChange = onInputChange,
                                modifier      = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                maxLines  = 4,
                                textStyle = TextStyle(
                                    color    = textPrimary,
                                    fontSize = 15.sp,
                                ),
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            stringResource(R.string.chat_input_placeholder),
                                            fontSize = 15.sp, color = textHint,
                                        )
                                    }
                                    inner()
                                },
                            )
                            if (inputText.isNotEmpty()) {
                                Text(
                                    "${inputText.length}/2000", fontSize = 11.sp,
                                    color = if (inputText.length >= 2000) ExthruChat.Destructive
                                    else textHint,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            // Стикер-кнопка
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !uiState.isCooldown) { onStickerClick() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.EmojiEmotions, null,
                                    tint = if (showStickerSheet) ExthruChat.Accent else textHint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Кнопка отправки / микрофона
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)) togetherWith
                                    scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80))
                        },
                        label = "exthru_send_mic",
                    ) { hasText ->
                        if (hasText) {
                            ExthruSendButton(
                                enabled       = !uiState.isCooldown,
                                isDark        = isDark,
                                hapticEnabled = hapticEnabled,
                                haptic        = haptic,
                                onClick       = onSend,
                            )
                        } else {
                            if (canSendMedia) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .exthruSmallRaisedShadow(isDark = isDark)
                                        .background(barBg, CircleShape)
                                        .clickable(enabled = !uiState.isCooldown) {
                                            if (audioPermission.status.isGranted) {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                onStartRecord()
                                            } else onRequestAudioPerm()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Mic, null,
                                        tint = ExthruChat.Accent, modifier = Modifier.size(22.dp),
                                    )
                                }
                            } else Spacer(Modifier.size(44.dp))
                        }
                    }
                } else {
                    RecordingBar(
                        isDark        = isDark,
                        hapticEnabled = hapticEnabled,
                        onCancel      = onCancel,
                        onSend        = onSendRecord,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Exthru Send Button — градиент + raised shadow
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExthruSendButton(
    enabled: Boolean,
    isDark: Boolean = false,
    hapticEnabled: Boolean,
    haptic: HapticHelper,
    onClick: () -> Unit,
) {
    val sendScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(sendScale.value)
            .clip(CircleShape)
            .exthruSmallRaisedShadow(isDark = isDark)
            .background(
                brush = if (enabled)
                    Brush.radialGradient(listOf(Biolume.TealLight, Biolume.TealPulse))
                else
                    Brush.radialGradient(
                        listOf(
                            Biolume.TealLight.copy(alpha = 0.5f),
                            Biolume.TealPulse.copy(alpha = 0.5f),
                        )
                    ),
                shape = CircleShape,
            )
            .clickable(
                enabled            = enabled,
                interactionSource  = remember { MutableInteractionSource() },
                indication         = null,
            ) {
                scope.launch {
                    sendScale.animateTo(0.84f, spring(stiffness = Spring.StiffnessHigh))
                    sendScale.animateTo(1.06f, spring(Spring.DampingRatioLowBouncy))
                    sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send, null,
            tint = Color.White, modifier = Modifier.size(20.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  OneUI Bottom Bar (без изменений)
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun OneUiChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    isDark: Boolean,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    val barBg       = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val accentColor = if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
    val inputBg     = if (isDark) OneUiChat.InputBgDark else OneUiChat.InputBg
    val iconTint    = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary

    Column(modifier = Modifier.background(barBg).navigationBarsPadding().imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage ->
                stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    restriction ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF232B3A) else Color(0xFFEBF1FD))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.type == MessageType.ALBUM && msg.images.isNotEmpty()) {
                        AsyncImage(
                            model = msg.images[0].url, contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(Modifier.width(3.dp).height(32.dp).background(accentColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "@${msg.senderUsername}", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = accentColor,
                        )
                        Text(
                            when (msg.type) {
                                MessageType.IMAGE   -> stringResource(R.string.photo)
                                MessageType.VOICE   -> stringResource(R.string.voice_message)
                                MessageType.STICKER -> stringResource(R.string.sticker)
                                MessageType.ALBUM   -> msg.caption ?: "📷 ${msg.images.size} фото"
                                else -> msg.text ?: stringResource(R.string.media)
                            },
                            fontSize = 12.sp,
                            color = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(),
                color      = accentColor,
                trackColor = if (isDark) Color(0xFF232B3A) else Color(0xFFEBF1FD),
            )
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        IconButton(onClick = onAttach, enabled = !uiState.isCooldown) {
                            Icon(Icons.Default.AttachFile, null, tint = iconTint, modifier = Modifier.size(22.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBg)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = inputText, onValueChange = onInputChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                maxLines  = 4,
                                textStyle = TextStyle(
                                    color    = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary,
                                    fontSize = 15.sp,
                                ),
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            stringResource(R.string.chat_input_placeholder), fontSize = 15.sp,
                                            color = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary,
                                        )
                                    }
                                    inner()
                                },
                            )
                            if (inputText.isNotEmpty()) {
                                Text(
                                    "${inputText.length}/2000", fontSize = 11.sp,
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error else iconTint,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            IconButton(
                                onClick  = onStickerClick,
                                modifier = Modifier.size(32.dp),
                                enabled  = !uiState.isCooldown,
                            ) {
                                Icon(
                                    Icons.Default.EmojiEmotions, null,
                                    tint     = if (showStickerSheet) accentColor else iconTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)) togetherWith
                                    scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80))
                        },
                        label = "oui_send_mic",
                    ) { hasText ->
                        if (hasText) {
                            val sendScale = remember { Animatable(1f) }
                            val sendScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(sendScale.value)
                                    .background(
                                        if (uiState.isCooldown) accentColor.copy(alpha = 0.5f) else accentColor,
                                        CircleShape,
                                    )
                                    .clickable(
                                        enabled           = !uiState.isCooldown,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null,
                                    ) {
                                        sendScope.launch {
                                            sendScale.animateTo(0.80f, spring(stiffness = Spring.StiffnessHigh))
                                            sendScale.animateTo(1.10f, spring(Spring.DampingRatioLowBouncy))
                                            sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                        haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            if (canSendMedia) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                        .clickable(enabled = !uiState.isCooldown) {
                                            if (audioPermission.status.isGranted) {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                onStartRecord()
                                            } else onRequestAudioPerm()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Mic, null, tint = accentColor, modifier = Modifier.size(22.dp))
                                }
                            } else Spacer(Modifier.size(44.dp))
                        }
                    }
                } else {
                    RecordingBar(hapticEnabled = hapticEnabled, onCancel = onCancel, onSend = onSendRecord)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Default (M3) Bottom Bar (без изменений)
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun DefaultChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage ->
                stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    restriction ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(180, easing = FastOutLinearInEasing),
            ) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                if (msg.type == MessageType.ALBUM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (msg.images.isNotEmpty()) {
                            AsyncImage(
                                model = msg.images[0].url, contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            Icons.Default.Reply, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "@${msg.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                msg.caption ?: "📷 ${msg.images.size} фото",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close, stringResource(R.string.chat_cancel_reply),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                } else {
                    ReplyBanner(msg) { onClearReply() }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        val attachScale = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            attachScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                        }
                        IconButton(
                            onClick  = onAttach,
                            modifier = Modifier.scale(attachScale.value),
                            enabled  = !uiState.isCooldown,
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val emojiScale = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(40)
                        emojiScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                    }
                    IconButton(
                        onClick  = { haptic.perform(HapticType.CLICK, hapticEnabled); onStickerClick() },
                        modifier = Modifier.scale(emojiScale.value),
                        enabled  = !uiState.isCooldown,
                    ) {
                        Icon(
                            Icons.Default.EmojiEmotions, null,
                            tint = if (showStickerSheet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = onInputChange,
                        placeholder   = { Text(stringResource(R.string.chat_input_placeholder)) },
                        modifier      = Modifier.weight(1f).animateContentSize(
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        ),
                        maxLines  = 4,
                        shape     = MaterialTheme.shapes.extraLarge,
                        supportingText = if (inputText.isNotEmpty()) {
                            {
                                Text(
                                    "${inputText.length}/2000",
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            }
                        } else null,
                    )
                    Spacer(Modifier.width(4.dp))
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            (scaleIn(
                                initialScale  = 0.6f,
                                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                            ) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(
                                        targetScale   = 0.6f,
                                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                                    ) + fadeOut(tween(80)))
                        },
                        label = "send_mic",
                    ) { hasText ->
                        if (hasText) {
                            val sendScale = remember { Animatable(1f) }
                            val sendScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(sendScale.value)
                                    .background(
                                        if (uiState.isCooldown) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    )
                                    .clickable(
                                        enabled           = !uiState.isCooldown,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null,
                                    ) {
                                        sendScope.launch {
                                            sendScale.animateTo(0.82f, spring(stiffness = Spring.StiffnessHigh))
                                            sendScale.animateTo(1.12f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                                            sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                        haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send),
                                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp),
                                )
                            }
                        } else {
                            if (canSendMedia) {
                                IconButton(
                                    onClick  = {
                                        if (audioPermission.status.isGranted) {
                                            haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                            onStartRecord()
                                        } else onRequestAudioPerm()
                                    },
                                    modifier = Modifier.size(48.dp),
                                    enabled  = !uiState.isCooldown,
                                ) {
                                    Icon(
                                        Icons.Default.Mic, stringResource(R.string.chat_input_record),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else Spacer(Modifier.size(48.dp))
                        }
                    }
                } else {
                    RecordingBar(hapticEnabled = hapticEnabled, onCancel = onCancel, onSend = onSendRecord)
                }
            }
        }
    }
}