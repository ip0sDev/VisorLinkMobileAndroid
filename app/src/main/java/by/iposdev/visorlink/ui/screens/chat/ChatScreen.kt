package by.iposdev.visorlink.ui.screens.chat

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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
    onOpenImageViewer: (url: String) -> Unit = {},
    onMentionClick: (String) -> Unit = {}, // <-- Добавлен коллбэк для @тегов
    hapticEnabled: Boolean = true
) {
    val viewModel: ChatViewModel = koinViewModel(parameters = { parametersOf(chatId, otherUid) })
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var actionSheetMessage by remember { mutableStateOf<Message?>(null) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { editorUri = it } }

    val onScrollToMessage: (String) -> Unit = { targetMsgId ->
        val index = uiState.messageListItems.indexOfFirst {
            it is MessageListItem.MessageItem && it.message.id == targetMsgId
        }
        if (index >= 0) {
            scope.launch {
                listState.animateScrollToItem(index)
            }
        }
    }

    val itemCount = uiState.messageListItems.size
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && !uiState.isLoadingMore) {
            listState.scrollToItem(itemCount - 1)
        }
    }

    val lastOwnMessageId = uiState.messages.lastOrNull { it.senderId == viewModel.currentUid }?.id
    LaunchedEffect(lastOwnMessageId) {
        if (lastOwnMessageId != null) {
            delay(50)
            listState.scrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0))
        }
    }

    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && hapticEnabled) {
            if (uiState.messages.lastOrNull()?.senderId != viewModel.currentUid) {
                haptic.perform(HapticType.MESSAGE_RECEIVED, hapticEnabled)
            }
        }
    }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
                            when (uiState.chatType) {
                                ChatType.DIRECT -> AnimatedContent(
                                    targetState = uiState.topbarStatus,
                                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                    label = "topbar_status"
                                ) { status ->
                                    when (status) {
                                        is TopbarStatus.Typing -> TypingDots()
                                        is TopbarStatus.Online -> Text(
                                            stringResource(R.string.chat_status_online),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF22C55E), fontSize = 11.sp
                                        )
                                        is TopbarStatus.LastSeen -> Text(
                                            status.ts?.let { ts ->
                                                "Last seen ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))}"
                                            } ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                        else -> Text("", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                ChatType.GROUP -> {
                                    val memberCount = uiState.chat?.memberCount ?: uiState.members.size
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
                    if (uiState.chatType != ChatType.DIRECT && uiState.isAdmin) {
                        IconButton(onClick = { onOpenChatSettings(chatId) }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
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
                val restriction = when {
                    uiState.myMember?.banned == true -> "You are banned from this chat"
                    uiState.myMember?.muted == true -> "You are muted"
                    uiState.chatType == ChatType.CHANNEL && !uiState.isAdmin ->
                        "📢 Only admins can post in channels"
                    else -> null
                }
                AnimatedVisibility(visible = restriction != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(restriction ?: "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                    }
                }

                AnimatedVisibility(
                    visible = uiState.replyingTo != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(180, easing = FastOutLinearInEasing)
                    ) + fadeOut(tween(150))
                ) {
                    uiState.replyingTo?.let { msg -> ReplyBanner(msg) { viewModel.clearReply() } }
                }

                AnimatedVisibility(
                    visible = uiState.isUploading,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                if (uiState.canSendMessage) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!uiState.isRecording) {
                            if (uiState.canSendMedia) {
                                val attachScale = remember { Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    attachScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                                }
                                IconButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    modifier = Modifier.scale(attachScale.value)
                                ) {
                                    Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val emojiScale = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(40)
                                emojiScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                            }
                            IconButton(
                                onClick = {
                                    if (hapticEnabled) haptic.perform(HapticType.CLICK, hapticEnabled)
                                    showStickerSheet = true
                                },
                                modifier = Modifier.scale(emojiScale.value)
                            ) {
                                Icon(Icons.Default.EmojiEmotions, null,
                                    tint = if (showStickerSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it; viewModel.onTextChanged(it) },
                                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                    ),
                                maxLines = 4,
                                shape = MaterialTheme.shapes.extraLarge
                            )
                            Spacer(Modifier.width(4.dp))
                            AnimatedContent(
                                targetState = inputText.isNotBlank(),
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150))) togetherWith
                                            (scaleOut(targetScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80)))
                                },
                                label = "send_mic"
                            ) { hasText ->
                                if (hasText) {
                                    val sendScale = remember { Animatable(1f) }
                                    val sendScope = rememberCoroutineScope()
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(sendScale.value)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                sendScope.launch {
                                                    sendScale.animateTo(0.82f, spring(stiffness = Spring.StiffnessHigh))
                                                    sendScale.animateTo(1.12f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                                                    sendScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                }
                                                if (hapticEnabled)
                                                    haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                                val text = inputText
                                                inputText = ""
                                                viewModel.sendText(text)
                                                scope.launch {
                                                    delay(80)
                                                    listState.scrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0))
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send),
                                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                                    }
                                } else {
                                    if (uiState.canSendMedia) {
                                        IconButton(
                                            onClick = {
                                                if (audioPermission.status.isGranted) {
                                                    if (hapticEnabled) haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                    viewModel.startRecording()
                                                } else {
                                                    audioPermission.launchPermissionRequest()
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Mic, stringResource(R.string.chat_input_record), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        Spacer(Modifier.size(48.dp))
                                    }
                                }
                            }
                        } else {
                            RecordingBar(
                                hapticEnabled = hapticEnabled,
                                onCancel = { viewModel.cancelRecording() },
                                onSend = {
                                    viewModel.stopRecordingAndSend()
                                    scope.launch {
                                        delay(200)
                                        listState.scrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
            val emptyTransition = rememberInfiniteTransition(label = "empty")
            val emptyScale by emptyTransition.animateFloat(
                initialValue = 0.92f, targetValue = 1.08f,
                animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "empty_scale"
            )
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline, null,
                        modifier = Modifier.size(64.dp).scale(emptyScale),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.chat_empty_subtitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
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
                            val isMine = item.message.senderId == viewModel.currentUid
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { 72 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                ) + slideInHorizontally(
                                    initialOffsetX = { if (isMine) it / 6 else -it / 6 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeIn(tween(180)) + scaleIn(
                                    initialScale = 0.88f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                            ) {
                                SwipeableMessage(
                                    message = item.message,
                                    isMine = item.message.senderId == viewModel.currentUid,
                                    hapticEnabled = hapticEnabled,
                                    onReply = {
                                        haptic.perform(HapticType.SELECTION, hapticEnabled)
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
                                        showSenderName = uiState.chatType != ChatType.DIRECT,
                                        voicePlayback = uiState.voicePlayback,
                                        onPlayVoice = { url, durationSec -> viewModel.playVoice(item.message.id, url, durationSec) },
                                        onSeekVoice = { viewModel.seekVoice(it) },
                                        onLongPress = {
                                            haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                            actionSheetMessage = item.message
                                        },
                                        onImageTap = { url -> onOpenImageViewer(url) },
                                        onReact = { emoji ->
                                            viewModel.toggleReaction(item.message.id, emoji, item.message.parsedReactions)
                                        },
                                        onReplyClick = onScrollToMessage,
                                        onMentionClick = onMentionClick // <-- Прокидываем коллбэк
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionSheetMessage?.let { msg ->
        MessageActionSheet(
            message = msg,
            isMine = msg.senderId == viewModel.currentUid,
            canReact = uiState.canReact,
            currentUid = viewModel.currentUid,
            onDismiss = { actionSheetMessage = null },
            onReply = { viewModel.setReplyTo(msg); actionSheetMessage = null },
            onDelete = { showDeleteConfirm = msg.id; actionSheetMessage = null },
            onSaveImage = {
                scope.launch {
                    val success = saveImageToGallery(context, msg.url ?: "")
                    Toast.makeText(context, if (success) context.getString(R.string.toast_saved_gallery) else context.getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                }
            },
            onSaveVoice = {
                scope.launch {
                    val success = saveVoiceToDownloads(context, msg.url ?: "")
                    Toast.makeText(context, if (success) context.getString(R.string.toast_saved_downloads) else context.getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                }
            },
            onOpenImage = { msg.url?.let { onOpenImageViewer(it) } },
            onReact = { emoji -> viewModel.toggleReaction(msg.id, emoji, msg.parsedReactions) }
        )
    }

    if (showStickerSheet) {
        StickerBottomSheet(
            stickers = uiState.stickers, hapticEnabled = hapticEnabled,
            onDismiss = { showStickerSheet = false },
            onStickerSelected = { sticker ->
                viewModel.sendSticker(sticker)
                showStickerSheet = false
                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                scope.launch {
                    delay(150)
                    listState.scrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0))
                }
            }
        )
    }

    showDeleteConfirm?.let { msgId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_delete_message_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msgId)
                    showDeleteConfirm = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(if (uiState.chatType == ChatType.CHANNEL) stringResource(R.string.dialog_leave_channel_title) else stringResource(R.string.dialog_leave_group_title)) },
            text = { Text(stringResource(R.string.dialog_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveChat { onNavigateBack() }
                }) { Text(stringResource(R.string.action_leave), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { delay(3000); viewModel.clearError() }
    }

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri = uri, onNavigateBack = { editorUri = null },
            onSend = { editedUri, isSpoiler ->
                editorUri = null
                viewModel.sendImage(editedUri, isSpoiler)
                scope.launch { delay(150); listState.scrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0)) }
            }
        )
    }
}

// ─── Компонент для кликабельных ссылок и @тегов ──────────────────────────────────

@Composable
fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    onLongPress: () -> Unit,
    onMentionClick: (String) -> Unit // <-- Новый коллбэк
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val (urlList, mentionList) = remember(text) {
        val urls = mutableListOf<Triple<String, Int, Int>>() // url, start, end
        val urlMatcher = android.util.Patterns.WEB_URL.matcher(text)
        while (urlMatcher.find()) {
            var url = urlMatcher.group() ?: continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            urls.add(Triple(url, urlMatcher.start(), urlMatcher.end()))
        }

        val mentions = mutableListOf<Triple<String, Int, Int>>() // mention, start, end
        // Регулярное выражение: @, перед которым нет букв/цифр (не внутри email)
        val mentionRegex = Regex("(?<!\\w)@[a-zA-Z0-9_]+")
        mentionRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            // Исключаем совпадения, которые попали внутрь URL
            val isInsideUrl = urls.any { start >= it.second && end <= it.third }
            if (!isInsideUrl) {
                mentions.add(Triple(matchResult.value, start, end))
            }
        }

        Pair(urls, mentions)
    }

    val annotatedString = remember(text, color, linkColor) {
        buildAnnotatedString {
            append(text)
            // Применяем стили к URL
            urlList.forEach { (url, start, end) ->
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ), start = start, end = end
                )
                addStringAnnotation(tag = "URL", annotation = url, start = start, end = end)
            }
            // Применяем стили к @тегам
            mentionList.forEach { (mention, start, end) ->
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold
                    ), start = start, end = end
                )
                addStringAnnotation(tag = "MENTION", annotation = mention, start = start, end = end)
            }
        }
    }

    Text(
        text = annotatedString,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)

                        // Сначала проверяем, не кликнули ли на ссылку
                        annotatedString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                try { uriHandler.openUri(annotation.item) } catch (e: Exception) {}
                                return@detectTapGestures
                            }

                        // Проверяем, не кликнули ли на @тег
                        annotatedString.getStringAnnotations("MENTION", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                // Убираем '@', чтобы передать чистый юзернейм/тег
                                val username = annotation.item.removePrefix("@")
                                onMentionClick(username)
                                return@detectTapGestures
                            }
                    }
                }
            )
        }
    )
}

// ─── Swipeable wrapper ────────────────────────────────────────────────────────

@Composable
private fun SwipeableMessage(
    message: Message,
    isMine: Boolean,
    hapticEnabled: Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = rememberHaptic()
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val triggerThreshold = 80f
    val maxOffset = 110f
    var didTrigger by remember { mutableStateOf(false) }

    val replyIconAlpha by animateFloatAsState(
        targetValue = if (kotlin.math.abs(offsetX.value) > 20f)
            (kotlin.math.abs(offsetX.value) / triggerThreshold).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(80),
        label = "reply_icon_alpha"
    )
    val replyIconScale by animateFloatAsState(
        targetValue = if (kotlin.math.abs(offsetX.value) >= triggerThreshold) 1.15f
        else if (kotlin.math.abs(offsetX.value) > 20f)
            (0.6f + 0.4f * (kotlin.math.abs(offsetX.value) / triggerThreshold)).coerceIn(0.6f, 1.15f)
        else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "reply_icon_scale"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 16.dp)
                .size(36.dp)
                .scale(replyIconScale)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = replyIconAlpha * 0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Reply, stringResource(R.string.chat_reply),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = replyIconAlpha),
                modifier = Modifier.size(20.dp)
            )
        }

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
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
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
                                val target = if (isMine)
                                    (offsetX.value + dragAmount).coerceIn(-maxOffset, 0f)
                                else
                                    (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                                offsetX.snapTo(target)

                                if (kotlin.math.abs(offsetX.value) >= triggerThreshold && !didTrigger) {
                                    didTrigger = true
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    onReply()
                                    offsetX.animateTo(
                                        if (isMine) -triggerThreshold * 0.5f else triggerThreshold * 0.5f,
                                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh)
                                    )
                                    delay(100)
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                }
                            }
                        }
                    )
                }
        ) { content() }
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
    val haptic = rememberHaptic()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f), RoundedCornerShape(2.dp))
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stickers_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stickers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEmotions, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.stickers_empty_title), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.stickers_empty_subtitle), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stickers.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sticker ->
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        if (hapticEnabled) haptic.perform(HapticType.CLICK, hapticEnabled)
                                        onStickerSelected(sticker)
                                    }, contentAlignment = Alignment.Center) {
                                    AsyncImage(model = sticker.url, contentDescription = sticker.name,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit)
                                }
                            }
                            repeat(4 - row.size) { Box(modifier = Modifier.weight(1f).aspectRatio(1f)) }
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.chat_typing), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "dot_$i"
            )
            Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape))
        }
    }
}

// ─── Date separator ───────────────────────────────────────────────────────────

@Composable
private fun DateSeparator(label: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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
    showSenderName: Boolean,
    voicePlayback: by.iposdev.visorlink.utils.VoicePlaybackState,
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit,
    onLongPress: () -> Unit,
    onImageTap: (url: String) -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit // <-- Добавлен коллбэк для тегов
) {
    val haptic = rememberHaptic()
    val isReadByOther = otherUid in message.readBy
    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    if (message.type == MessageType.IMAGE && !message.deleted) {
        ImageBubble(
            message = message,
            isMine = isMine,
            isReadByOther = isReadByOther,
            chatType = chatType,
            currentUid = currentUid,
            hapticEnabled = hapticEnabled,
            onTap = { message.url?.let { onImageTap(it) } },
            onLongPress = {
                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                onLongPress()
            },
            onReact = onReact,
            onReplyClick = onReplyClick
        )
        return
    }

    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = if (isMine) Color.White else MaterialTheme.colorScheme.primary
    val bubbleShape = if (isMine) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .scale(pressScale.value)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        scope.launch {
                            pressScale.animateTo(0.91f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh))
                            pressScale.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                            pressScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        onLongPress()
                    }
                )
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                if (showSenderName && !isMine) {
                    Text("@${message.senderUsername}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp))
                }

                message.replyData?.let { reply ->
                    ReplyPreview(
                        reply = reply,
                        isMine = isMine,
                        onClick = { reply.id?.let { id -> onReplyClick(id) } }
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (message.deleted) {
                    Text(stringResource(R.string.chat_message_deleted), style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.6f))
                } else when (message.type) {
                    MessageType.TEXT -> LinkifiedText(
                        text = message.text ?: "",
                        color = textColor,
                        linkColor = linkColor,
                        onLongPress = {
                            haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                            onLongPress()
                        },
                        onMentionClick = onMentionClick // <-- Прокидываем коллбэк
                    )
                    MessageType.VOICE -> VoiceBubble(
                        messageId = message.id, url = message.url ?: "",
                        durationSec = message.duration ?: 0, tint = textColor,
                        playback = voicePlayback, onPlay = onPlayVoice, onSeek = onSeekVoice
                    )
                    MessageType.STICKER -> AsyncImage(model = message.url,
                        contentDescription = null, modifier = Modifier.size(120.dp))
                }

                Row(modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    AnimatedVisibility(
                        visible = message.createdAt != null,
                        enter = fadeIn(tween(300))
                    ) {
                        Text(
                            message.createdAt?.toDate()?.let {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                            } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f), fontSize = 10.sp
                        )
                    }
                    if (isMine && !message.deleted && chatType == ChatType.DIRECT) {
                        AnimatedContent(
                            targetState = isReadByOther,
                            transitionSpec = {
                                scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) +
                                        fadeIn(tween(200)) togetherWith
                                        scaleOut(targetScale = 0.5f) + fadeOut(tween(100))
                            },
                            label = "read_receipt"
                        ) { read ->
                            ReadReceipt(isRead = read)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
        ) {
            ReactionRow(reactions = message.parsedReactions, currentUid = currentUid,
                hapticEnabled = hapticEnabled, onReact = onReact)
        }
    }
}

// ─── Image bubble ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    message: Message,
    isMine: Boolean,
    isReadByOther: Boolean,
    chatType: ChatType,
    currentUid: String,
    hapticEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit
) {
    val imageShape = if (isMine)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()

    val isSpoiler = message.spoiler == true
    var spoilerRevealed by remember(message.id) { mutableStateOf(false) }
    val blurRadius by animateDpAsState(
        targetValue = if (isSpoiler && !spoilerRevealed) 20.dp else 0.dp,
        animationSpec = tween(300),
        label = "spoiler_blur"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .scale(pressScale.value)
                .clip(imageShape)
                .combinedClickable(
                    onClick = {
                        if (isSpoiler && !spoilerRevealed) {
                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                            spoilerRevealed = true
                        } else {
                            onTap()
                        }
                    },
                    onLongClick = {
                        scope.launch {
                            pressScale.animateTo(0.93f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh))
                            pressScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        onLongPress()
                    }
                )
        ) {
            AsyncImage(
                model = message.url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                contentScale = ContentScale.Crop
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isSpoiler && !spoilerRevealed,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(),
                exit = fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text("Tap to reveal", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            message.replyData?.let { reply ->
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { reply.id?.let { id -> onReplyClick(id) } }
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(reply.text ?: "📷 Photo", style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(message.createdAt?.toDate()?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(
                            imageVector = if (isReadByOther) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null, modifier = Modifier.size(13.dp),
                            tint = if (isReadByOther) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
        ) {
            ReactionRow(reactions = message.parsedReactions, currentUid = currentUid,
                hapticEnabled = hapticEnabled, onReact = onReact)
        }
    }
}

// ─── Read receipt ─────────────────────────────────────────────────────────────

@Composable
private fun ReadReceipt(isRead: Boolean) {
    Icon(imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = if (isRead) stringResource(R.string.chat_read) else stringResource(R.string.chat_sent),
        modifier = Modifier.size(14.dp),
        tint = if (isRead) Color(0xFF00D4FF) else Color(0xFF6B7280))
}

// ─── Reaction row ─────────────────────────────────────────────────────────────

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    currentUid: String,
    hapticEnabled: Boolean,
    onReact: (String) -> Unit
) {
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            key(reaction.emoji) {
                val iReacted = currentUid in reaction.uids
                val chipScale = remember { Animatable(1f) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    chipScale.snapTo(0f)
                    chipScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                }

                Surface(
                    onClick = {
                        scope.launch {
                            chipScale.animateTo(1.35f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh))
                            chipScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        haptic.perform(HapticType.REACTION, hapticEnabled)
                        onReact(reaction.emoji)
                    },
                    modifier = Modifier.scale(chipScale.value),
                    shape = RoundedCornerShape(12.dp),
                    color = if (iReacted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (iReacted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                    tonalElevation = if (iReacted) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(reaction.emoji, fontSize = 14.sp)
                        Text(
                            reaction.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (iReacted) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (iReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─── Reply preview ────────────────────────────────────────────────────────────

@Composable
private fun ReplyPreview(reply: ReplyData, isMine: Boolean, onClick: () -> Unit) {
    val accentColor = if (isMine) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val nameColor = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
    val textColor = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.extraSmall)
        .background(accentColor)
        .clickable(onClick = onClick)
        .padding(6.dp)) {
        Box(Modifier.width(3.dp).height(32.dp).background(nameColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall,
                color = nameColor, fontWeight = FontWeight.SemiBold)
            Text(reply.text ?: when (reply.type) {
                MessageType.IMAGE -> "📷 Image"
                MessageType.VOICE -> "🎤 Voice message"
                MessageType.STICKER -> "🎭 Sticker"
                else -> ""
            }, style = MaterialTheme.typography.bodySmall, color = textColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Reply banner ─────────────────────────────────────────────────────────────

@Composable
private fun ReplyBanner(message: Message, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.primaryContainer)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${message.senderUsername}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(when (message.type) {
                MessageType.IMAGE -> "📷 Photo"
                MessageType.VOICE -> "🎤 Voice message"
                MessageType.STICKER -> "🎭 Sticker"
                else -> message.text ?: "Media"
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.chat_cancel_reply), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ─── Voice bubble ─────────────────────────────────────────────────────────────

@Composable
private fun VoiceBubble(
    messageId: String,
    url: String,
    durationSec: Int,
    tint: Color,
    playback: by.iposdev.visorlink.utils.VoicePlaybackState,
    onPlay: (url: String, durationSec: Int) -> Unit,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val isThisMessage = playback.playingMessageId == messageId
    val isPlaying = isThisMessage && playback.isPlaying
    val isLoading = isThisMessage && playback.isLoading
    val progress = if (isThisMessage) playback.progress else 0f
    val currentSec = if (isThisMessage) playback.currentMs / 1000 else 0
    val totalSec = if (isThisMessage && playback.durationMs > 0) playback.durationMs / 1000 else durationSec

    var waveform by remember(url) { mutableStateOf(generateFallbackWaveform()) }
    LaunchedEffect(url) {
        val real = extractWaveform(context, url)
        waveform = real
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse_phase"
    )

    Column(modifier = Modifier.width(220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape)
                    .clickable { onPlay(url, durationSec) },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .pointerInput(messageId) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val press = event.changes.firstOrNull() ?: continue
                                if (press.pressed) {
                                    val fraction = (press.position.x / size.width).coerceIn(0f, 1f)
                                    press.consume()
                                    onSeek(fraction)
                                    onPlay(url, durationSec)
                                }
                            }
                        }
                    }
            ) {
                val barCount = waveform.size
                val totalWidth = size.width
                val totalHeight = size.height
                val gap = totalWidth * 0.018f
                val barWidth = (totalWidth - gap * (barCount - 1)) / barCount
                val centerY = totalHeight / 2f

                waveform.forEachIndexed { index, amp ->
                    val barProgress = (index + 0.5f) / barCount
                    val isPassed = barProgress <= progress && isThisMessage
                    val pulse = if (isPlaying && isPassed) {
                        1f + 0.12f * kotlin.math.sin(pulsePhase + index * 0.4f)
                    } else 1f

                    val barHeight = (amp * totalHeight * 0.88f * pulse).coerceAtLeast(3f)
                    val x = index * (barWidth + gap)

                    val color = when {
                        isPassed -> tint
                        else -> tint.copy(alpha = 0.30f)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }

                if (isThisMessage && progress > 0f) {
                    val cursorX = progress * totalWidth
                    drawLine(
                        color = tint.copy(alpha = 0.6f),
                        start = androidx.compose.ui.geometry.Offset(cursorX, 0f),
                        end = androidx.compose.ui.geometry.Offset(cursorX, totalHeight),
                        strokeWidth = 1.5f
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatVoiceTime(currentSec),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
            Text(
                formatVoiceTime(totalSec),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

private fun formatVoiceTime(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

// ─── Recording bar ────────────────────────────────────────────────────────────

@Composable
private fun RecordingBar(hapticEnabled: Boolean, onCancel: () -> Unit, onSend: () -> Unit) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha"
    )
    val sendScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
            haptic.perform(HapticType.CLICK, hapticEnabled)
        }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            haptic.perform(HapticType.ERROR, hapticEnabled)
            onCancel()
        }) {
            Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_recording_label), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = {
                scope.launch {
                    sendScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                    sendScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                onSend()
            },
            modifier = Modifier
                .size(48.dp)
                .scale(sendScale.value)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}