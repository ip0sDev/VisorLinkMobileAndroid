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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenStickers: (onSelect: (Sticker) -> Unit) -> Unit,
    onOpenChatSettings: (chatId: String) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val viewModel: ChatViewModel = koinViewModel(parameters = { parametersOf(chatId, otherUid) })
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var reactionsOpenForId by remember { mutableStateOf<String?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.sendImage(it) } }

    // ── Скролл вниз при новых сообщениях ─────────────────────────────────────
    val messageCount = uiState.messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0 && !uiState.isLoadingMore) {
            listState.animateScrollToItem(
                (uiState.messageListItems.size - 1).coerceAtLeast(0)
            )
        }
    }

    // ── Скролл после отправки своего сообщения ────────────────────────────────
    val lastOwnMessageId = uiState.messages.lastOrNull { it.senderId == viewModel.currentUid }?.id
    LaunchedEffect(lastOwnMessageId) {
        if (lastOwnMessageId != null) {
            listState.animateScrollToItem(
                (uiState.messageListItems.size - 1).coerceAtLeast(0)
            )
        }
    }

    // ── Haptic при новом входящем ─────────────────────────────────────────────
    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && hapticEnabled) {
            val last = uiState.messages.lastOrNull()
            if (last?.senderId != viewModel.currentUid) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // ── Пагинация ─────────────────────────────────────────────────────────────
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 3 && uiState.hasMore && !uiState.isLoadingMore) {
            viewModel.loadMore()
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
                        modifier = Modifier.clickable {
                            when (uiState.chatType) {
                                ChatType.DIRECT -> onOpenOtherProfile(otherUid)
                                else -> onOpenChatSettings(chatId)
                            }
                        }
                    ) {
                        // Аватар зависит от типа чата
                        when (uiState.chatType) {
                            ChatType.DIRECT -> AvatarWithPresence(
                                avatarUrl = uiState.otherUser?.avatarUrl,
                                displayName = uiState.otherUser?.displayName ?: "",
                                isOnline = uiState.topbarStatus is TopbarStatus.Online ||
                                        uiState.topbarStatus is TopbarStatus.Typing,
                                size = 36.dp
                            )
                            ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                                avatarUrl = uiState.chat?.avatarUrl,
                                name = uiState.chat?.name ?: "",
                                isChannel = uiState.chatType == ChatType.CHANNEL,
                                size = 36.dp
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column {
                            // Название чата
                            Text(
                                when (uiState.chatType) {
                                    ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                                    else -> uiState.chat?.name ?: ""
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Subtitle
                            when (uiState.chatType) {
                                ChatType.DIRECT -> AnimatedContent(
                                    targetState = uiState.topbarStatus,
                                    transitionSpec = {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                    },
                                    label = "topbar_status"
                                ) { status ->
                                    when (status) {
                                        is TopbarStatus.Typing -> TypingDots()
                                        is TopbarStatus.Online -> Text(
                                            "Online",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF22C55E), fontSize = 11.sp
                                        )
                                        is TopbarStatus.LastSeen -> Text(
                                            status.ts?.let { ts ->
                                                "Last seen ${
                                                    SimpleDateFormat("HH:mm", Locale.getDefault())
                                                        .format(Date(ts))
                                                }"
                                            } ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                        else -> Text("", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                ChatType.GROUP -> {
                                    val memberCount = uiState.chat?.memberCount
                                        ?: uiState.members.size
                                    val online = uiState.onlineCount
                                    Text(
                                        buildString {
                                            append("$memberCount members")
                                            if (online > 0) append(" · $online online")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }

                                ChatType.CHANNEL -> Text(
                                    "${uiState.chat?.memberCount ?: 0} subscribers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Настройки — для admin групп/каналов
                    if (uiState.chatType != ChatType.DIRECT && uiState.isAdmin) {
                        IconButton(onClick = { onOpenChatSettings(chatId) }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                    // Выйти — для не-owner'ов в группах/каналах
                    if (uiState.chatType != ChatType.DIRECT && !uiState.isOwner) {
                        IconButton(onClick = { showLeaveDialog = true }) {
                            Icon(Icons.Default.ExitToApp, "Leave",
                                tint = MaterialTheme.colorScheme.error)
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
                // Баннер ограничений
                val restriction = when {
                    uiState.myMember?.banned == true ->
                        "You are banned from this chat"
                    uiState.myMember?.muted == true ->
                        "You are muted"
                    uiState.chatType == ChatType.CHANNEL && !uiState.isAdmin ->
                        "📢 Only admins can post in channels"
                    else -> null
                }
                AnimatedVisibility(visible = restriction != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            restriction ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Reply banner
                AnimatedVisibility(
                    visible = uiState.replyingTo != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    uiState.replyingTo?.let { msg ->
                        ReplyBanner(msg) { viewModel.clearReply() }
                    }
                }

                // Upload progress
                AnimatedVisibility(visible = uiState.isUploading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Input row — только если разрешено отправлять
                if (uiState.canSendMessage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!uiState.isRecording) {
                            // Прикрепить файл — только если не mediaRestricted
                            if (uiState.canSendMedia) {
                                IconButton(onClick = { imagePicker.launch("image/*") }) {
                                    Icon(Icons.Default.AttachFile, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Стикеры
                            IconButton(onClick = {
                                if (hapticEnabled)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showStickerSheet = true
                            }) {
                                Icon(Icons.Default.EmojiEmotions, null,
                                    tint = if (showStickerSheet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = {
                                    inputText = it
                                    viewModel.onTextChanged(it)
                                },
                                placeholder = { Text("Message") },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = MaterialTheme.shapes.extraLarge
                            )

                            Spacer(Modifier.width(4.dp))

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
                                            if (hapticEnabled)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val text = inputText
                                            inputText = ""
                                            viewModel.sendText(text)
                                            scope.launch {
                                                delay(100)
                                                listState.animateScrollToItem(
                                                    (uiState.messageListItems.size - 1).coerceAtLeast(0)
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, "Send",
                                            tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else {
                                    // Микрофон — только если можно отправлять медиа
                                    if (uiState.canSendMedia) {
                                        IconButton(
                                            onClick = {
                                                if (audioPermission.status.isGranted) {
                                                    if (hapticEnabled)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.startRecording()
                                                } else {
                                                    audioPermission.launchPermissionRequest()
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Mic, "Record",
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        // Только текст — нет кнопки медиа
                                        Spacer(Modifier.size(48.dp))
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
        }
    ) { padding ->

        // Пустое состояние
        if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    Text("Say hi! 👋",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Индикатор загрузки старых
                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(
                    items = uiState.messageListItems,
                    key = { item ->
                        when (item) {
                            is MessageListItem.DateHeader -> "date_${item.label}"
                            is MessageListItem.MessageItem -> item.message.id
                        }
                    }
                ) { item ->
                    when (item) {
                        is MessageListItem.DateHeader -> DateSeparator(label = item.label)
                        is MessageListItem.MessageItem -> {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { 40 },
                                    animationSpec = tween(200)
                                ) + fadeIn(tween(200))
                            ) {
                                SwipeableMessage(
                                    message = item.message,
                                    isMine = item.message.senderId == viewModel.currentUid,
                                    hapticEnabled = hapticEnabled,
                                    onReply = {
                                        if (hapticEnabled)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setReplyTo(item.message)
                                    }
                                ) {
                                    MessageBubble(
                                        message = item.message,
                                        isMine = item.message.senderId == viewModel.currentUid,
                                        otherUid = otherUid,
                                        currentUid = viewModel.currentUid,
                                        chatType = uiState.chatType,
                                        hapticEnabled = hapticEnabled,
                                        reactionsOpenForId = reactionsOpenForId,
                                        canReact = uiState.canReact,
                                        showSenderName = uiState.chatType != ChatType.DIRECT,
                                        onOpenReactions = { msgId ->
                                            reactionsOpenForId =
                                                if (reactionsOpenForId == msgId) null else msgId
                                        },
                                        onLongPress = {
                                            reactionsOpenForId = item.message.id
                                            if (item.message.senderId == viewModel.currentUid
                                                && !item.message.deleted
                                            ) {
                                                showDeleteConfirm = item.message.id
                                            }
                                        },
                                        onReply = { viewModel.setReplyTo(item.message) },
                                        onReact = { emoji ->
                                            viewModel.toggleReaction(
                                                item.message.id, emoji,
                                                item.message.parsedReactions
                                            )
                                            reactionsOpenForId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Sticker bottom sheet ──────────────────────────────────────────────────
    if (showStickerSheet) {
        StickerBottomSheet(
            stickers = uiState.stickers,
            hapticEnabled = hapticEnabled,
            onDismiss = { showStickerSheet = false },
            onStickerSelected = { sticker ->
                viewModel.sendSticker(sticker)
                showStickerSheet = false
                if (hapticEnabled)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────────────
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

    // ── Leave dialog ──────────────────────────────────────────────────────────
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text("Leave ${if (uiState.chatType == ChatType.CHANNEL) "channel" else "group"}?")
            },
            text = { Text("You will need an invite or link to rejoin.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveChat { onNavigateBack() }
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Error auto-dismiss ────────────────────────────────────────────────────
    uiState.error?.let {
        LaunchedEffect(it) {
            delay(3000)
            viewModel.clearError()
        }
    }
}

// ─── Swipeable message wrapper ────────────────────────────────────────────────

@Composable
private fun SwipeableMessage(
    message: Message,
    isMine: Boolean,
    hapticEnabled: Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val triggerThreshold = 80f
    val maxOffset = 100f
    var didTrigger by remember { mutableStateOf(false) }

    val replyIconAlpha by animateFloatAsState(
        targetValue = if (kotlin.math.abs(offsetX.value) > 20f)
            (kotlin.math.abs(offsetX.value) / triggerThreshold).coerceIn(0f, 1f)
        else 0f,
        label = "reply_icon_alpha"
    )
    val replyIconScale by animateFloatAsState(
        targetValue = if (kotlin.math.abs(offsetX.value) > 20f)
            (0.6f + 0.4f * (kotlin.math.abs(offsetX.value) / triggerThreshold)).coerceIn(0.6f, 1f)
        else 0.6f,
        label = "reply_icon_scale"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        // Иконка реплая
        Box(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 20.dp)
                .size(36.dp)
                .scale(replyIconScale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Reply,
                contentDescription = "Reply",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = replyIconAlpha),
                modifier = Modifier.size(24.dp)
            )
        }

        // Сообщение
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { didTrigger = false },
                        onDragEnd = {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val target = if (isMine) {
                                    (offsetX.value + dragAmount).coerceIn(-maxOffset, 0f)
                                } else {
                                    (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                                }
                                offsetX.snapTo(target)

                                if (kotlin.math.abs(offsetX.value) >= triggerThreshold && !didTrigger) {
                                    didTrigger = true
                                    if (hapticEnabled)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReply()
                                    offsetX.animateTo(
                                        if (isMine) -triggerThreshold * 0.6f
                                        else triggerThreshold * 0.6f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                    delay(150)
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

// ─── Sticker bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerBottomSheet(
    stickers: List<Sticker>,
    hapticEnabled: Boolean,
    onDismiss: () -> Unit,
    onStickerSelected: (Sticker) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                        RoundedCornerShape(2.dp)
                    )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stickers", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (stickers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEmotions, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text("No stickers yet", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add stickers in your profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            } else {
                val rows = stickers.chunked(4)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { rowStickers ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowStickers.forEach { sticker ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            if (hapticEnabled)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onStickerSelected(sticker)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = sticker.url,
                                        contentDescription = sticker.name,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            repeat(4 - rowStickers.size) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Typing dots ──────────────────────────────────────────────────────────────

@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("typing", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            Box(
                modifier = Modifier.size(4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

// ─── Date separator ───────────────────────────────────────────────────────────

@Composable
private fun DateSeparator(label: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

// ─── Message bubble ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    otherUid: String,
    currentUid: String,
    chatType: ChatType,
    hapticEnabled: Boolean,
    reactionsOpenForId: String?,
    canReact: Boolean,
    showSenderName: Boolean,
    onOpenReactions: (String) -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val showActions = reactionsOpenForId == message.id
    val isReadByOther = otherUid in message.readBy

    val scale by animateFloatAsState(
        targetValue = if (showActions) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubble_scale"
    )

    // Изображения — отдельный composable
    if (message.type == MessageType.IMAGE && !message.deleted) {
        ImageBubble(
            message = message,
            isMine = isMine,
            isReadByOther = isReadByOther,
            chatType = chatType,
            hapticEnabled = hapticEnabled,
            showActions = showActions,
            currentUid = currentUid,
            canReact = canReact,
            onTap = { onOpenReactions(message.id) },
            onLongPress = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            },
            onReply = { onReply(); onOpenReactions("") },
            onReact = onReact
        )
        return
    }

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
                    onClick = { onOpenReactions(message.id) },
                    onLongClick = {
                        if (hapticEnabled)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp
                )
            ) {
                // Имя отправителя в группах/каналах (только для чужих сообщений)
                if (showSenderName && !isMine) {
                    Text(
                        "@${message.senderUsername}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                message.replyData?.let {
                    ReplyPreview(it, isMine)
                    Spacer(Modifier.height(4.dp))
                }

                if (message.deleted) {
                    Text("🚫 Message deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.6f))
                } else when (message.type) {
                    MessageType.TEXT -> Text(message.text ?: "",
                        style = MaterialTheme.typography.bodyMedium, color = textColor)
                    MessageType.VOICE -> VoiceBubble(message.duration ?: 0, textColor)
                    MessageType.STICKER -> AsyncImage(model = message.url,
                        contentDescription = null, modifier = Modifier.size(120.dp))
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        message.createdAt?.toDate()?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f), fontSize = 10.sp
                    )
                    // Read receipt только в DIRECT
                    if (isMine && !message.deleted && chatType == ChatType.DIRECT) {
                        ReadReceipt(isRead = isReadByOther)
                    }
                }
            }
        }

        // Reactions
        if (message.parsedReactions.isNotEmpty()) {
            ReactionRow(
                reactions = message.parsedReactions,
                currentUid = currentUid,
                hapticEnabled = hapticEnabled,
                onReact = onReact
            )
        }

        // Quick actions
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
            QuickActionsBar(
                hapticEnabled = hapticEnabled,
                canReact = canReact,
                onReact = { emoji ->
                    if (hapticEnabled)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReact(emoji)
                },
                onReply = {
                    if (hapticEnabled)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReply()
                    onOpenReactions("")
                }
            )
        }
    }
}

// ─── Quick actions bar ────────────────────────────────────────────────────────

@Composable
private fun QuickActionsBar(
    hapticEnabled: Boolean,
    canReact: Boolean,
    onReact: (String) -> Unit,
    onReply: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            if (canReact) {
                listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
                    ReactionButton(emoji = emoji, onClick = { onReact(emoji) })
                }
            }
            IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Reply, "Reply",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Reaction row ─────────────────────────────────────────────────────────────

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    currentUid: String,
    hapticEnabled: Boolean,
    onReact: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            val iReacted = currentUid in reaction.uids
            Surface(
                onClick = {
                    if (hapticEnabled)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReact(reaction.emoji)
                },
                shape = MaterialTheme.shapes.extraSmall,
                color = if (iReacted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (iReacted) 2.dp else 0.dp
            ) {
                Text("${reaction.emoji} ${reaction.count}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─── Image bubble (TG-style) ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    message: Message,
    isMine: Boolean,
    isReadByOther: Boolean,
    chatType: ChatType,
    hapticEnabled: Boolean,
    showActions: Boolean,
    currentUid: String,
    canReact: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val imageShape = if (isMine)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .clip(imageShape)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
        ) {
            AsyncImage(
                model = message.url, contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
                contentScale = ContentScale.Crop
            )

            // Reply preview поверх (сверху)
            message.replyData?.let { reply ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(28.dp)
                            .background(Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("@${reply.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(reply.text ?: "📷 Photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.8f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Время + read receipt поверх (снизу справа)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        message.createdAt?.toDate()?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontSize = 10.sp
                    )
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(
                            imageVector = if (isReadByOther) Icons.Default.DoneAll
                            else Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isReadByOther) Color(0xFF7DD3FC)
                            else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Reactions
        if (message.parsedReactions.isNotEmpty()) {
            ReactionRow(
                reactions = message.parsedReactions,
                currentUid = currentUid,
                hapticEnabled = hapticEnabled,
                onReact = onReact
            )
        }

        // Quick actions
        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = tween(150)
            ) + fadeOut(tween(150))
        ) {
            QuickActionsBar(
                hapticEnabled = hapticEnabled,
                canReact = canReact,
                onReact = onReact,
                onReply = onReply
            )
        }
    }
}

// ─── Read receipt ─────────────────────────────────────────────────────────────

@Composable
private fun ReadReceipt(isRead: Boolean) {
    Icon(
        imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = if (isRead) "Read" else "Sent",
        modifier = Modifier.size(14.dp),
        tint = if (isRead) Color(0xFF00D4FF) else Color(0xFF6B7280)
    )
}

// ─── Reaction button ──────────────────────────────────────────────────────────

@Composable
private fun ReactionButton(emoji: String, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    TextButton(
        onClick = {
            scope.launch {
                scale.animateTo(1.35f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                scale.animateTo(1f, tween(100))
            }
            onClick()
        },
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.size(36.dp).scale(scale.value)
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
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(accentColor)
            .padding(6.dp)
    ) {
        Box(Modifier.width(3.dp).height(32.dp).background(nameColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("@${reply.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = nameColor, fontWeight = FontWeight.SemiBold)
            Text(
                reply.text ?: when (reply.type) {
                    MessageType.IMAGE -> "📷 Image"
                    MessageType.VOICE -> "🎤 Voice message"
                    MessageType.STICKER -> "🎭 Sticker"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Reply banner ─────────────────────────────────────────────────────────────

@Composable
private fun ReplyBanner(message: Message, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Reply, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${message.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                when (message.type) {
                    MessageType.IMAGE -> "📷 Photo"
                    MessageType.VOICE -> "🎤 Voice message"
                    MessageType.STICKER -> "🎭 Sticker"
                    else -> message.text ?: "Media"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "Cancel reply",
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ─── Voice bubble ─────────────────────────────────────────────────────────────

@Composable
private fun VoiceBubble(duration: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PlayArrow, "Play", tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(4.dp))
        Row(modifier = Modifier.width(100.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf(12, 20, 16, 24, 18, 14, 22, 10, 18, 16).forEach { h ->
                Box(Modifier.width(3.dp).height(h.dp)
                    .background(tint.copy(0.7f), RoundedCornerShape(2.dp)))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("${duration / 60}:${(duration % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall, color = tint)
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

    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
            if (hapticEnabled)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text("Recording...", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send",
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}