package by.iposdev.visorlink.ui.screens.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenStickers: (onSelect: (Sticker) -> Unit) -> Unit,
    hapticEnabled: Boolean = true
) {
    val viewModel: ChatViewModel = koinViewModel(parameters = { parametersOf(chatId, otherUid) })
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.sendImage(it) } }

    // Прокрутка к последнему сообщению при новых
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Haptic при получении нового сообщения
    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && hapticEnabled) {
            val lastMsg = uiState.messages.lastOrNull()
            if (lastMsg?.senderId != viewModel.currentUid) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenOtherProfile(otherUid) }
                    ) {
                        AvatarWithPresence(
                            avatarUrl = uiState.otherUser?.avatarUrl,
                            displayName = uiState.otherUser?.displayName ?: "",
                            isOnline = uiState.isOnline,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                uiState.otherUser?.displayName ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            AnimatedContent(
                                targetState = uiState.isOnline,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "online_status"
                            ) { isOnline ->
                                Text(
                                    text = if (isOnline) "Online"
                                    else uiState.lastSeen?.let {
                                        "Last seen ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())}"
                                    } ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnline) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Reply banner
                AnimatedVisibility(
                    visible = uiState.replyingTo != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    uiState.replyingTo?.let { ReplyBanner(it) { viewModel.clearReply() } }
                }

                // Upload progress
                AnimatedVisibility(visible = uiState.isUploading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!uiState.isRecording) {
                        // Attachment
                        IconButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(
                                Icons.Default.AttachFile, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Stickers
                        IconButton(onClick = {
                            onOpenStickers { sticker -> viewModel.sendSticker(sticker) }
                        }) {
                            Icon(
                                Icons.Default.EmojiEmotions, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Text input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = MaterialTheme.shapes.extraLarge
                        )

                        Spacer(Modifier.width(4.dp))

                        // Send / Mic button
                        AnimatedContent(
                            targetState = inputText.isNotBlank(),
                            transitionSpec = {
                                scaleIn(initialScale = 0.8f) + fadeIn() togetherWith
                                        scaleOut(targetScale = 0.8f) + fadeOut()
                            },
                            label = "send_mic"
                        ) { hasText ->
                            if (hasText) {
                                IconButton(
                                    onClick = {
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.sendText(inputText)
                                        inputText = ""
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        "Send",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (audioPermission.status.isGranted) {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.startRecording()
                                        } else {
                                            audioPermission.launchPermissionRequest()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Mic, "Record",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        RecordingBar(
                            hapticEnabled = hapticEnabled,
                            onCancel = { viewModel.cancelRecording() },
                            onSend = { viewModel.stopRecordingAndSend() }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Группировка по датам
        val grouped = remember(uiState.messages) {
            uiState.messages.groupBy { msg ->
                msg.createdAt?.toDate()?.let {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                } ?: ""
            }
        }

        if (uiState.messages.isEmpty() && !uiState.isUploading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                    )
                    Text(
                        "Say hi! 👋",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                grouped.forEach { (date, msgs) ->
                    if (date.isNotEmpty()) {
                        item(key = "date_$date") { DateSeparator(date) }
                    }
                    items(msgs, key = { it.id }) { message ->
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                initialOffsetY = { 40 },
                                animationSpec = tween(200)
                            ) + fadeIn(animationSpec = tween(200))
                        ) {
                            MessageBubble(
                                message = message,
                                isMine = message.senderId == viewModel.currentUid,
                                currentUid = viewModel.currentUid,
                                hapticEnabled = hapticEnabled,
                                onLongPress = {
                                    if (message.senderId == viewModel.currentUid && !message.deleted) {
                                        showDeleteConfirm = message.id
                                    }
                                },
                                onReply = { viewModel.setReplyTo(message) },
                                onReact = { emoji ->
                                    viewModel.toggleReaction(message.id, emoji, message.parsedReactions)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete dialog
    showDeleteConfirm?.let { msgId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete message?") },
            text = { Text("This will be marked as deleted for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msgId)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            delay(3000)
            viewModel.clearError()
        }
    }
}

// ─── Date separator ───────────────────────────────────────────────────────────

@Composable
private fun DateSeparator(date: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ─── Message bubble ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    hapticEnabled: Boolean,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showActions by remember { mutableStateOf(false) }

    // Анимация появления bubble
    val scale by animateFloatAsState(
        targetValue = if (showActions) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubble_scale"
    )

    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = if (isMine)
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    else
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .scale(scale)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = { showActions = !showActions },
                    onLongClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                        showActions = true
                    }
                )
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                // Reply preview
                message.replyData?.let {
                    ReplyPreview(it, isMine)
                    Spacer(Modifier.height(4.dp))
                }

                // Content
                if (message.deleted) {
                    Text(
                        "🚫 Message deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.6f)
                    )
                } else when (message.type) {
                    MessageType.TEXT -> Text(
                        message.text ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                    MessageType.IMAGE -> AsyncImage(
                        model = message.url,
                        contentDescription = null,
                        modifier = Modifier
                            .width(220.dp)
                            .heightIn(max = 280.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                    MessageType.VOICE -> VoiceBubble(message.duration ?: 0, textColor)
                    MessageType.STICKER -> AsyncImage(
                        model = message.url,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp)
                    )
                }

                // Timestamp
                Text(
                    message.createdAt?.toDate()?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        // Reactions
        if (message.parsedReactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.parsedReactions.forEach { reaction ->
                    val iReacted = currentUid in reaction.uids
                    Surface(
                        onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReact(reaction.emoji)
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (iReacted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (iReacted) 2.dp else 0.dp
                    ) {
                        Text(
                            "${reaction.emoji} ${reaction.count}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Quick actions bar (появляется по тапу)
        AnimatedVisibility(
            visible = showActions && !message.deleted,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = tween(150)
            ) + fadeOut(tween(150))
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
                        ReactionButton(emoji = emoji, onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReact(emoji)
                            showActions = false
                        })
                    }
                    IconButton(
                        onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onReply()
                            showActions = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Reply, "Reply",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionButton(emoji: String, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    TextButton(
        onClick = {
            scope.launch {
                scale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                scale.animateTo(1f, tween(100))
            }
            onClick()
        },
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier
            .size(36.dp)
            .scale(scale.value)
    ) {
        Text(emoji, fontSize = 18.sp)
    }
}

// ─── Reply preview inside bubble ─────────────────────────────────────────────

@Composable
private fun ReplyPreview(reply: ReplyData, isMine: Boolean) {
    val accentColor = if (isMine) Color.White.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val nameColor = if (isMine) Color.White.copy(alpha = 0.9f)
    else MaterialTheme.colorScheme.primary
    val textColor = if (isMine) Color.White.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(accentColor)
            .padding(6.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .background(nameColor, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "@${reply.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = nameColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                reply.text ?: when (reply.type) {
                    MessageType.IMAGE -> "📷 Image"
                    MessageType.VOICE -> "🎤 Voice message"
                    MessageType.STICKER -> "🎭 Sticker"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Reply banner (bottom bar) ────────────────────────────────────────────────

@Composable
private fun ReplyBanner(message: Message, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Reply, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "@${message.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                message.text ?: "Media",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close, "Cancel reply",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─── Voice bubble ─────────────────────────────────────────────────────────────

@Composable
private fun VoiceBubble(duration: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.PlayArrow, "Play",
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(4.dp))
        // Waveform
        Row(
            modifier = Modifier.width(100.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(12, 20, 16, 24, 18, 14, 22, 10, 18, 16).forEach { h ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .background(tint.copy(0.7f), RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${duration / 60}:${(duration % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

// ─── Recording bar ────────────────────────────────────────────────────────────

@Composable
private fun RecordingBar(
    hapticEnabled: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var elapsed by remember { mutableIntStateOf(0) }

    // Пульсирующая точка
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color.Red.copy(alpha = dotAlpha), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Recording...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, "Send",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}