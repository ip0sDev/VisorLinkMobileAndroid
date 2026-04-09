package by.iposdev.visorlink.ui.screens.chat

import android.Manifest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.ui.screens.stickers.AddStickerPackBanner
import by.iposdev.visorlink.ui.screens.stickers.StickerPickerBottomSheet
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
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.ActiveChatTracker

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
    onMentionClick: (String) -> Unit = {},
    onOpenComments: (messageId: String) -> Unit = {},
    hapticEnabled: Boolean = true,
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
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var actionSheetMessage by remember { mutableStateOf<Message?>(null) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    var lightboxImages by remember { mutableStateOf<List<AlbumImage>>(emptyList()) }
    var lightboxStartIndex by remember { mutableIntStateOf(0) }
    var showLightbox by remember { mutableStateOf(false) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> editorUri = uris.first()
            else           -> viewModel.onImagesPicked(uris)
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setWallpaper(it) }
    }

    val onScrollToMessage: (String) -> Unit = { targetMsgId ->
        val index = uiState.messageListItems.asReversed().indexOfFirst {
            it is MessageListItem.MessageItem && it.message.id == targetMsgId
        }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    // ── Определяем активную тему ──────────────────────────────────────────────
    val themeVm: ThemeViewModel = koinViewModel()
    val appTheme by themeVm.appTheme.collectAsState()
    val isOneUi  = appTheme == AppTheme.ONE_UI
    val isExthru = appTheme == AppTheme.EXTHRU
    val isDark   = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val isAdmin  = uiState.myMember?.isAdmin() == true
    val isOwner  = uiState.myMember?.isOwner() == true
    val canSetWallpaper = uiState.chatType == ChatType.DIRECT || isAdmin
    val canReact = uiState.chat?.settings?.allowReactions != false

    val canSendMessage = when (uiState.chatType) {
        ChatType.DIRECT  -> true
        ChatType.CHANNEL -> isAdmin || isOwner
        ChatType.GROUP   -> {
            val member = uiState.myMember
            if (member?.banned == true) false
            else if (member?.muted == true) {
                val until = member.mutedUntil?.toDate()
                until != null && Date().after(until)
            } else true
        }
    }
    val canSendMedia = canSendMessage && uiState.myMember?.mediaRestricted != true
    DisposableEffect(chatId) {
        ActiveChatTracker.activeChatId = chatId
        onDispose {
            ActiveChatTracker.activeChatId = null
        }
    }
    // ── Автопрокрутка и счётчик непрочитанных ────────────────────────────────
    LaunchedEffect(listState.firstVisibleItemIndex, uiState.messageListItems.size) {
        val layoutInfo = listState.layoutInfo
        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (uiState.messageListItems.isNotEmpty() &&
            lastVisibleItemIndex >= uiState.messageListItems.size - 5 &&
            uiState.hasMore && !uiState.isLoadingMore
        ) {
            viewModel.loadMore()
        }
    }

    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(showScrollDown) { if (!showScrollDown) unreadCount = 0 }

    val messageCount = uiState.messageListItems.size
    LaunchedEffect(messageCount) {
        if (messageCount == 0) return@LaunchedEffect
        val lastMsg = uiState.messages.lastOrNull() ?: return@LaunchedEffect
        val isMine = lastMsg.senderId == viewModel.currentUid
        when {
            isMine -> {
                // Всегда скроллим вниз при отправке своего сообщения
                listState.animateScrollToItem(0)
            }
            listState.firstVisibleItemIndex <= 1 -> {
                // Входящее сообщение и мы уже внизу — скроллим
                if (hapticEnabled) haptic.perform(HapticType.MESSAGE_RECEIVED, hapticEnabled)
                listState.animateScrollToItem(0)
            }
            else -> {
                unreadCount++
            }
        }
    }

    val scaffoldBg = when {
        isExthru -> ExthruChat.pageBg(isDark)   // ← было: ExthruChat.PageBg
        isOneUi  -> if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg
        else     -> MaterialTheme.colorScheme.background
    }

    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            when {
                isExthru -> ExthruChatTopBar(
                    uiState = uiState,
                    otherUid = otherUid,
                    chatId = chatId,
                    isDark = isDark, // Передаем параметр темной темы
                    canSetWallpaper = canSetWallpaper,
                    isAdmin = isAdmin,
                    isOwner = isOwner,
                    onWallpaperClick = { showWallpaperSheet = true },
                    onNavigateBack = onNavigateBack,
                    onOpenOtherProfile = onOpenOtherProfile,
                    onOpenChatSettings = onOpenChatSettings,
                    onLeaveClick = { showLeaveDialog = true }
                )
                isOneUi -> OneUiChatTopBar(
                    uiState = uiState,
                    otherUid = otherUid,
                    chatId = chatId,
                    isDark = isDark,
                    canSetWallpaper = canSetWallpaper,
                    isAdmin = isAdmin,
                    isOwner = isOwner,
                    onWallpaperClick = { showWallpaperSheet = true },
                    onNavigateBack = onNavigateBack,
                    onOpenOtherProfile = onOpenOtherProfile,
                    onOpenChatSettings = onOpenChatSettings,
                    onLeaveClick = { showLeaveDialog = true }
                )
                else -> DefaultChatTopBar(
                    uiState = uiState,
                    otherUid = otherUid,
                    chatId = chatId,
                    canSetWallpaper = canSetWallpaper,
                    isAdmin = isAdmin,
                    isOwner = isOwner,
                    onWallpaperClick = { showWallpaperSheet = true },
                    onNavigateBack = onNavigateBack,
                    onOpenOtherProfile = onOpenOtherProfile,
                    onOpenChatSettings = onOpenChatSettings,
                    onLeaveClick = { showLeaveDialog = true }
                )
            }
        },
        bottomBar = {
            when {
                isExthru -> ExthruChatBottomBar(
                    uiState, inputText,
                    isDark,          // ← НОВЫЙ параметр после inputText
                    canSendMessage, canSendMedia, hapticEnabled,
                    showStickerSheet, audioPermission,
                    { inputText = it; viewModel.onTextChanged(it) },
                    { imagePicker.launch("image/*") },
                    { showStickerSheet = true },
                    { val t = inputText; inputText = ""; viewModel.sendText(t) },
                    { viewModel.startRecording() },
                    { audioPermission.launchPermissionRequest() },
                    { viewModel.cancelRecording() },
                    { viewModel.stopRecordingAndSend() },
                    { viewModel.clearReply() },
                    haptic,
                )
                isOneUi -> OneUiChatBottomBar(
                    uiState, inputText, isDark, canSendMessage, canSendMedia, hapticEnabled,
                    showStickerSheet, audioPermission,
                    { inputText = it; viewModel.onTextChanged(it) },
                    { imagePicker.launch("image/*") },
                    { showStickerSheet = true },
                    { val t = inputText; inputText = ""; viewModel.sendText(t) },
                    { viewModel.startRecording() },
                    { audioPermission.launchPermissionRequest() },
                    { viewModel.cancelRecording() },
                    { viewModel.stopRecordingAndSend() },
                    { viewModel.clearReply() },
                    haptic,
                )
                else -> DefaultChatBottomBar(
                    uiState, inputText, canSendMessage, canSendMedia, hapticEnabled,
                    showStickerSheet, audioPermission,
                    { inputText = it; viewModel.onTextChanged(it) },
                    { imagePicker.launch("image/*") },
                    { showStickerSheet = true },
                    { val t = inputText; inputText = ""; viewModel.sendText(t) },
                    { viewModel.startRecording() },
                    { audioPermission.launchPermissionRequest() },
                    { viewModel.cancelRecording() },
                    { viewModel.stopRecordingAndSend() },
                    { viewModel.clearReply() },
                    haptic,
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Unofficial client warning ──────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.showUnofficialClientWarning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_client_unsafe_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissUnofficialWarning() },
                            modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Wallpaper
                uiState.wallpaperUrl?.let { url ->
                    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(), alpha = if (isDark) 0.35f else 0.7f)
                }

                if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
                    EmptyChatPlaceholder(modifier = Modifier.fillMaxSize())
                } else {
                    val listBg = when {
                        uiState.wallpaperUrl != null -> Modifier
                        isExthru -> Modifier.background(ExthruChat.pageBg(isDark))  // ← было: ExthruChat.PageBg
                        isOneUi  -> Modifier.background(if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg)
                        else     -> Modifier
                    }
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize().then(listBg),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(
                            items = uiState.messageListItems.asReversed(),
                            key = { item ->
                                when (item) {
                                    is MessageListItem.DateHeader  -> "date_${item.label}"
                                    is MessageListItem.MessageItem -> item.message.id
                                }
                            },
                        ) { item ->
                            when (item) {
                                is MessageListItem.DateHeader -> DateSeparator(
                                    item.label, isOneUi, isExthru, isDark, uiState.wallpaperUrl != null)
                                is MessageListItem.MessageItem -> {
                                    val isMine = item.message.senderId == viewModel.currentUid
                                    SwipeableMessage(
                                        message = item.message, isMine = isMine,
                                        hapticEnabled = hapticEnabled,
                                        isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                                        onReply = {
                                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                                            viewModel.setReplyTo(item.message)
                                        },
                                    ) {
                                        MessageBubble(
                                            message = item.message, isMine = isMine,
                                            otherUid = otherUid, currentUid = viewModel.currentUid,
                                            chatType = uiState.chatType, hapticEnabled = hapticEnabled,
                                            showSenderName = uiState.chatType != ChatType.DIRECT,
                                            voicePlayback = uiState.voicePlayback,
                                            isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                                            hasWallpaper = uiState.wallpaperUrl != null,   // ← ДОБАВИТЬ
                                            onPlayVoice = { url, dur -> viewModel.playVoice(item.message.id, url, dur) },
                                            onSeekVoice = { viewModel.seekVoice(it) },
                                            onLongPress = {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                actionSheetMessage = item.message
                                            },
                                            onImageTap = onOpenImageViewer,
                                            onAlbumTap = { imgs, idx ->
                                                lightboxImages = imgs; lightboxStartIndex = idx; showLightbox = true
                                            },
                                            onReact = { emoji ->
                                                viewModel.toggleReaction(item.message.id, emoji, item.message.parsedReactions)
                                            },
                                            onReplyClick = onScrollToMessage,
                                            onMentionClick = onMentionClick,
                                            onOpenComments = { onOpenComments(item.message.id) },
                                            chat = uiState.chat,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading more indicator
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                    AnimatedVisibility(
                        visible = uiState.isLoadingMore,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    ) {
                        val indicatorColor = when {
                            isExthru -> ExthruChat.Accent
                            isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
                            else     -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isOneUi && isDark) OneUiChat.CardBgDark else MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp, modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp, color = indicatorColor)
                            }
                        }
                    }
                }
                // Scroll-down FAB
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollDown,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                ) {
                    val fabColor = when {
                        isExthru -> ExthruChat.Accent
                        isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
                        else     -> MaterialTheme.colorScheme.primary
                    }
                    Box {
                        FloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier.size(44.dp),
                            containerColor = fabColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)
                                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                    .background(Color.Red, CircleShape).padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Диалоги и bottom sheets ───────────────────────────────────────────────

    if (showWallpaperSheet) {
        WallpaperBottomSheet(
            hasWallpaper = uiState.wallpaperUrl != null,
            isGroupOrChannel = uiState.chatType != ChatType.DIRECT,
            onDismiss = { showWallpaperSheet = false },
            onPickWallpaper = { showWallpaperSheet = false; wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = { showWallpaperSheet = false; viewModel.removeWallpaper() },
        )
    }

    if (uiState.showAlbumPreview) {
        AlbumPreviewSheet(
            images = uiState.albumDraft, caption = uiState.albumCaption,
            onSpoilerToggle = { viewModel.onAlbumSpoilerToggle(it) },
            onCaptionChange = { viewModel.onAlbumCaptionChange(it) },
            onDismiss = { viewModel.dismissAlbumPreview() },
            onSend = { viewModel.sendAlbum() },
        )
    }

    if (showLightbox && lightboxImages.isNotEmpty()) {
        AlbumLightbox(images = lightboxImages, startIndex = lightboxStartIndex,
            onDismiss = { showLightbox = false })
    }

    actionSheetMessage?.let { msg ->
        MessageActionSheet(
            message = msg, isMine = msg.senderId == viewModel.currentUid,
            canReact = canReact, currentUid = viewModel.currentUid,
            onDismiss = { actionSheetMessage = null },
            onReply = { viewModel.setReplyTo(msg); actionSheetMessage = null },
            onDelete = { showDeleteConfirm = msg.id; actionSheetMessage = null },
            onSaveImage = {
                scope.launch {
                    val success = saveImageToGallery(context, msg.url ?: "")
                    Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveVoice = {
                scope.launch {
                    val success = saveVoiceToDownloads(context, msg.url ?: "")
                    Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenImage = { msg.url?.let { onOpenImageViewer(it) } },
            onForward = null,
            onReact = { emoji -> viewModel.toggleReaction(msg.id, emoji, msg.parsedReactions) },
        )
    }

    if (showStickerSheet) {
        StickerPickerBottomSheet(
            onDismiss = { showStickerSheet = false },
            onStickerSelected = { packId, sticker ->
                viewModel.sendSticker(sticker = sticker, packId = packId, packName = "", packEmoji = "")
                showStickerSheet = false
                haptic.perform(HapticType.SUCCESS, hapticEnabled)
            },
        )
    }

    showDeleteConfirm?.let { msgId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_delete_message_title)) },
            text  = { Text(stringResource(R.string.dialog_delete_message_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMessage(msgId); showDeleteConfirm = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text(if (uiState.chatType == ChatType.CHANNEL)
                    stringResource(R.string.dialog_leave_channel_title)
                else stringResource(R.string.dialog_leave_group_title))
            },
            text = { Text(stringResource(R.string.dialog_leave_body)) },
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false; viewModel.leaveChat { onNavigateBack() } }) {
                    Text(stringResource(R.string.action_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { delay(3000); viewModel.clearError() }
    }

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri = uri,
            onNavigateBack = { editorUri = null },
            onSend = { editedUri, isSpoiler -> editorUri = null; viewModel.sendImage(editedUri, isSpoiler) },
        )
    }
}

//HELPERS
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperBottomSheet(
    hasWallpaper: Boolean,
    isGroupOrChannel: Boolean,
    onDismiss: () -> Unit,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            Text(
                text = "Chat Wallpaper",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (isGroupOrChannel) {
                Text(
                    text = "Applied to all members in this chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                )
            }
            ListItem(
                headlineContent = { Text("Choose from Gallery") },
                leadingContent = { Icon(Icons.Default.Image, null) },
                modifier = Modifier.clickable { onPickWallpaper() }
            )
            if (hasWallpaper) {
                ListItem(
                    headlineContent = { Text("Remove Wallpaper", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onRemoveWallpaper() }
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPreviewSheet(
    images: List<AlbumImageLocal>,
    caption: String,
    onSpoilerToggle: (Int) -> Unit,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                Text(
                    "${images.size} фото",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onSend, enabled = images.isNotEmpty()) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = stringResource(R.string.action_send),
                        tint = if (images.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(images) { index, item ->
                    AlbumThumbnailCell(
                        uri = item.uri,
                        spoiler = item.spoiler,
                        onToggleSpoiler = { onSpoilerToggle(index) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Добавить подпись…") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Text(
                        "${caption.length}/500",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = images.isNotEmpty()
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отправить ${images.size} фото")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumLightbox(
    images: List<AlbumImage>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val img = images[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x; offsetY += pan.y
                                } else {
                                    offsetX = 0f; offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = img.url, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offsetX; translationY = offsetY
                            }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 8.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close),
                    tint = Color.White)
            }

            if (images.size > 1) {
                val stripListState = rememberLazyListState()
                LaunchedEffect(pagerState.currentPage) {
                    stripListState.animateScrollToItem(pagerState.currentPage)
                }
                LazyRow(
                    state = stripListState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(images) { idx, img ->
                        val isActive = idx == pagerState.currentPage
                        val scope = rememberCoroutineScope()
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    width = if (isActive) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                        ) {
                            AsyncImage(
                                model = img.url, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    chatType: ChatType,
    isOneUi: Boolean = false,
    isDark: Boolean = false,
    isExthru: Boolean = false,
    onAlbumTap: (images: List<AlbumImage>, startIndex: Int) -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null
) {
    val images = message.images
    if (images.isEmpty()) return

    val revealedIndices = remember(message.id) { mutableStateOf(setOf<Int>()) }

    val bubbleColor = when {
        isMine && isOneUi && isDark -> Color(0xFF4D90F0)
        isMine && isOneUi           -> Color(0xFF1259C3)
        isMine                      -> MaterialTheme.colorScheme.primary
        isOneUi && isDark           -> Color(0xFF2C2C2C)
        isOneUi                     -> Color.White
        else                        -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isMine) Color.White else
        if (isOneUi && isDark) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)

    val bubbleShape = if (isMine)
        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape, color = bubbleColor,
            shadowElevation = if (!isMine) 1.dp else 0.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
            ) {
                message.replyData?.let { reply ->
                    ReplyPreview(
                        reply = reply, isMine = isMine,
                        onClick = { reply.id?.let { id -> onReplyClick(id) } }
                    )
                }

                AlbumGrid(
                    images = images,
                    revealedIndices = revealedIndices.value,
                    onReveal = { idx -> revealedIndices.value = revealedIndices.value + idx },
                    onTap = { idx -> onAlbumTap(images, idx) },
                    onLongPress = onLongPress
                )

                if (!message.caption.isNullOrBlank()) {
                    Text(
                        text = message.caption, color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(
                            end = 8.dp, bottom = 4.dp,
                            top = if (message.caption.isNullOrBlank()) 4.dp else 2.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        message.createdAt?.toDate()?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else
                            if (isDark) Color(0xFF999999) else Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }

                // FIX: AnimatedVisibility inside Column — ColumnScope is available here
                AnimatedVisibility(
                    visible = message.parsedReactions.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it / 2 },
                        animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                            scaleIn(initialScale = 0.7f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
                ) {
                    InlinedReactionRow(
                        reactions = message.parsedReactions, currentUid = currentUid,
                        isMine = isMine, isOneUi = isOneUi, isDark = isDark,
                        hapticEnabled = true,
                        onReact = onReact, onShowPicker = { onLongPress() },
                        isExthru = isExthru
                    )
                }
            }
        }

        if (chatType == ChatType.CHANNEL && !message.deleted) {
            // CommentsButton( ... )
        }
    }
}
@Composable
private fun AlbumThumbnailCell(
    uri: android.net.Uri,
    spoiler: Boolean,
    onToggleSpoiler: () -> Unit
) {
    val blurRadius by animateDpAsState(
        targetValue = if (spoiler) 12.dp else 0.dp,
        animationSpec = tween(200), label = "thumb_blur"
    )
    Box(modifier = Modifier
        .size(140.dp)
        .clip(RoundedCornerShape(12.dp))) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        )
        if (spoiler) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text("SPOILER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(if (spoiler) Color(0xFF1259C3) else Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onToggleSpoiler),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (spoiler) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (spoiler) "Убрать spoiler" else "Пометить spoiler",
                tint = Color.White, modifier = Modifier.size(16.dp)
            )
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGrid(
    images: List<AlbumImage>,
    revealedIndices: Set<Int>,
    onReveal: (Int) -> Unit,
    onTap: (Int) -> Unit,
    onLongPress: () -> Unit
) {
    val gap = 2.dp
    val maxWidth = 280.dp

    when (images.size) {
        1 -> AlbumCell(
            image = images[0], revealed = 0 in revealedIndices,
            modifier = Modifier
                .width(maxWidth)
                .height(220.dp),
            onTap = { onTap(0) }, onReveal = { onReveal(0) }, onLongPress = onLongPress
        )
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            images.forEachIndexed { i, img ->
                AlbumCell(
                    image = img, revealed = i in revealedIndices,
                    modifier = Modifier
                        .width((maxWidth - gap) / 2)
                        .height(160.dp),
                    onTap = { onTap(i) }, onReveal = { onReveal(i) }, onLongPress = onLongPress
                )
            }
        }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            AlbumCell(
                image = images[0], revealed = 0 in revealedIndices,
                modifier = Modifier
                    .width((maxWidth - gap) / 2)
                    .height(200.dp),
                onTap = { onTap(0) }, onReveal = { onReveal(0) }, onLongPress = onLongPress
            )
            Column(
                modifier = Modifier.width((maxWidth - gap) / 2),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                for (i in 1..2) AlbumCell(
                    image = images[i], revealed = i in revealedIndices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((200.dp - gap) / 2),
                    onTap = { onTap(i) }, onReveal = { onReveal(i) }, onLongPress = onLongPress
                )
            }
        }
        4 -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    AlbumCell(
                        image = images[i], revealed = i in revealedIndices,
                        modifier = Modifier
                            .width((maxWidth - gap) / 2)
                            .height(130.dp),
                        onTap = { onTap(i) }, onReveal = { onReveal(i) }, onLongPress = onLongPress
                    )
                }
            }
        }
        else -> {
            val visibleCount = minOf(images.size, 6)
            val extraCount = images.size - visibleCount
            val shown = images.take(visibleCount)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                shown.chunked(3).forEachIndexed { rowIdx, rowImages ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowImages.forEachIndexed { colIdx, img ->
                            val globalIdx = rowIdx * 3 + colIdx
                            val isLast = rowIdx == 1 && colIdx == rowImages.size - 1 && extraCount > 0
                            Box(modifier = Modifier
                                .width((maxWidth - gap * 2) / 3)
                                .height(110.dp)) {
                                AlbumCell(
                                    image = img, revealed = globalIdx in revealedIndices,
                                    modifier = Modifier.fillMaxSize(),
                                    onTap = { onTap(globalIdx) },
                                    onReveal = { onReveal(globalIdx) }, onLongPress = onLongPress
                                )
                                if (isLast && extraCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .clickable { onTap(globalIdx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+$extraCount", color = Color.White,
                                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCell(
    image: AlbumImage,
    revealed: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onReveal: () -> Unit,
    onLongPress: () -> Unit
) {
    val isSpoiler = image.spoiler && !revealed
    val blurRadius by animateDpAsState(
        targetValue = if (isSpoiler) 10.dp else 0.dp,
        animationSpec = tween(300), label = "cell_blur"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .combinedClickable(
                onClick = { if (isSpoiler) onReveal() else onTap() },
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = image.url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        )
        // FIX: AnimatedVisibility inside Box — use explicit non-receiver call
        androidx.compose.animation.AnimatedVisibility(
            visible = isSpoiler,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🙈", fontSize = 20.sp)
                    Text("SPOILER", color = Color.White, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        if (isSpoiler) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0xFF1259C3).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("Spoiler", color = Color.White, fontSize = 8.sp)
            }
        }
    }
}