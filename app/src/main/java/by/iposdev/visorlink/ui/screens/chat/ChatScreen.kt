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

// ─── One UI color tokens ───────────────────────────────────────────────────────

private object OneUiChat {
    val Blue        = Color(0xFF1259C3)
    val BlueDark    = Color(0xFF4D90F0)
    val PageBg      = Color(0xFFF4F4F4)
    val PageBgDark  = Color(0xFF1A1A1A)
    val CardBg      = Color(0xFFFFFFFF)
    val CardBgDark  = Color(0xFF2C2C2C)
    val TextPrimary       = Color(0xFF1A1A1A)
    val TextPrimaryDark   = Color(0xFFEEEEEE)
    val TextSecondary     = Color(0xFF888888)
    val TextSecondaryDark = Color(0xFF999999)
    val BubbleMine      = Color(0xFF1259C3)
    val BubbleMineDark  = Color(0xFF4D90F0)
    val BubbleOther     = Color(0xFFFFFFFF)
    val BubbleOtherDark = Color(0xFF2C2C2C)
    val InputBg     = Color(0xFFF0F0F0)
    val InputBgDark = Color(0xFF333333)
    val TopBar      = Color(0xFFFFFFFF)
    val TopBarDark  = Color(0xFF1E1E1E)
}

private val QUICK_REACTIONS = listOf(
    "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
    "🥰", "😍", "🤩", "😭", "🤔", "👀", "💯", "✅",
    "🙏", "😎", "🤣", "😅", "😡", "💀", "🎊", "⚡"
)

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
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var actionSheetMessage by remember { mutableStateOf<Message?>(null) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    // ── Lightbox state ────────────────────────────────────────────────────────
    var lightboxImages by remember { mutableStateOf<List<AlbumImage>>(emptyList()) }
    var lightboxStartIndex by remember { mutableIntStateOf(0) }
    var showLightbox by remember { mutableStateOf(false) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // ── Мульти-пикер: 1 фото → editor, 2+ → album ────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> editorUri = uris.first()
            else           -> viewModel.onImagesPicked(uris)
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setWallpaper(it) }
    }

    val onScrollToMessage: (String) -> Unit = { targetMsgId ->
        val index = uiState.messageListItems.indexOfFirst {
            it is MessageListItem.MessageItem && it.message.id == targetMsgId
        }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    val themeVm: by.iposdev.visorlink.ui.theme.ThemeViewModel = koinViewModel()
    val isOneUi = themeVm.appTheme.collectAsState().value == AppTheme.ONE_UI
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val isAdmin = uiState.myMember?.isAdmin() == true
    val isOwner = uiState.myMember?.isOwner() == true
    val isAdminOrOwner = isAdmin || isOwner

    val canSetWallpaper = uiState.chatType == ChatType.DIRECT || isAdmin
    val canReact = uiState.chat?.settings?.allowReactions != false

    val canSendMessage = when (uiState.chatType) {
        ChatType.DIRECT -> true
        ChatType.CHANNEL -> isAdminOrOwner
        ChatType.GROUP -> {
            val member = uiState.myMember
            if (member?.banned == true) false
            else if (member?.muted == true) {
                val until = member.mutedUntil?.toDate()
                until != null && Date().after(until)
            } else true
        }
    }
    val canSendMedia = canSendMessage && uiState.myMember?.mediaRestricted != true

    LaunchedEffect(Unit) {
        val itemCount = uiState.messageListItems.size
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 3 && uiState.hasMore && !uiState.isLoadingMore) {
            viewModel.loadMore()
        }
    }

    val showScrollDown by remember { derivedStateOf { listState.canScrollForward } }
    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showScrollDown) {
        if (!showScrollDown) unreadCount = 0
    }

    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        val messages = uiState.messages
        if (messages.isNotEmpty()) {
            val lastMsg = messages.last()
            val isMine = lastMsg.senderId == viewModel.currentUid
            if (isMine) {
                delay(150)
                listState.animateScrollToItem((uiState.messageListItems.size - 1).coerceAtLeast(0))
            } else {
                if (hapticEnabled) haptic.perform(HapticType.MESSAGE_RECEIVED, hapticEnabled)
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = uiState.messageListItems.size
                val isNearBottom = lastVisibleIndex >= totalItems - 4
                if (isNearBottom) {
                    delay(150)
                    listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
                } else {
                    unreadCount++
                }
            }
        }
    }

    val scaffoldBg = if (isOneUi)
        if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg
    else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            if (isOneUi) {
                OneUiChatTopBar(
                    uiState = uiState, otherUid = otherUid, chatId = chatId,
                    isDark = isDark, canSetWallpaper = canSetWallpaper,
                    isAdmin = isAdmin, isOwner = isOwner,
                    onWallpaperClick = { showWallpaperSheet = true },
                    onNavigateBack = onNavigateBack,
                    onOpenOtherProfile = onOpenOtherProfile,
                    onOpenChatSettings = onOpenChatSettings,
                    onLeaveClick = { showLeaveDialog = true }
                )
            } else {
                DefaultChatTopBar(
                    uiState = uiState, otherUid = otherUid, chatId = chatId,
                    canSetWallpaper = canSetWallpaper,
                    isAdmin = isAdmin, isOwner = isOwner,
                    onWallpaperClick = { showWallpaperSheet = true },
                    onNavigateBack = onNavigateBack,
                    onOpenOtherProfile = onOpenOtherProfile,
                    onOpenChatSettings = onOpenChatSettings,
                    onLeaveClick = { showLeaveDialog = true }
                )
            }
        },
        bottomBar = {
            if (isOneUi) {
                OneUiChatBottomBar(
                    uiState = uiState, inputText = inputText, isDark = isDark,
                    canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                    hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                    audioPermission = audioPermission,
                    onInputChange = { newText ->
                        if (newText.length <= 2000) {
                            inputText = newText
                            viewModel.onTextChanged(newText)
                        }
                    },
                    onAttach = { imagePicker.launch("image/*") },
                    onStickerClick = { showStickerSheet = true },
                    onSend = { val text = inputText; inputText = ""; viewModel.sendText(text) },
                    onStartRecord = { viewModel.startRecording() },
                    onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                    onCancel = { viewModel.cancelRecording() },
                    onSendRecord = { viewModel.stopRecordingAndSend() },
                    onClearReply = { viewModel.clearReply() },
                    haptic = haptic
                )
            } else {
                DefaultChatBottomBar(
                    uiState = uiState, inputText = inputText,
                    canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                    hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                    audioPermission = audioPermission,
                    onInputChange = { newText ->
                        if (newText.length <= 2000) {
                            inputText = newText
                            viewModel.onTextChanged(newText)
                        }
                    },
                    onAttach = { imagePicker.launch("image/*") },
                    onStickerClick = { showStickerSheet = true },
                    onSend = { val text = inputText; inputText = ""; viewModel.sendText(text) },
                    onStartRecord = { viewModel.startRecording() },
                    onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                    onCancel = { viewModel.cancelRecording() },
                    onSendRecord = { viewModel.stopRecordingAndSend() },
                    onClearReply = { viewModel.clearReply() },
                    haptic = haptic
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            // ── Unofficial Client Banner ─────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.showUnofficialClientWarning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.chat_client_unsafe_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissUnofficialWarning() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.chat_client_unsafe_close),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Основной контент чата ──────────────────────────────────────────
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {

                uiState.wallpaperUrl?.let { url ->
                    AsyncImage(
                        model = url, contentDescription = "Chat Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = if (isDark) 0.35f else 0.7f
                    )
                }

                if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
                    EmptyChatPlaceholder(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (uiState.wallpaperUrl == null && isOneUi)
                                    Modifier.background(if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg)
                                else Modifier
                            ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
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
                                is MessageListItem.DateHeader -> DateSeparator(
                                    label = item.label, isOneUi = isOneUi, isDark = isDark,
                                    hasWallpaper = uiState.wallpaperUrl != null
                                )
                                is MessageListItem.MessageItem -> {
                                    val isMine = item.message.senderId == viewModel.currentUid
                                    SwipeableMessage(
                                        message = item.message, isMine = isMine,
                                        hapticEnabled = hapticEnabled, isOneUi = isOneUi, isDark = isDark,
                                        onReply = {
                                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                                            viewModel.setReplyTo(item.message)
                                        }
                                    ) {
                                        MessageBubble(
                                            message = item.message, isMine = isMine,
                                            otherUid = otherUid, currentUid = viewModel.currentUid,
                                            chatType = uiState.chatType, hapticEnabled = hapticEnabled,
                                            showSenderName = uiState.chatType != ChatType.DIRECT,
                                            voicePlayback = uiState.voicePlayback,
                                            isOneUi = isOneUi, isDark = isDark,
                                            onPlayVoice = { url, durationSec ->
                                                viewModel.playVoice(item.message.id, url, durationSec)
                                            },
                                            onSeekVoice = { viewModel.seekVoice(it) },
                                            onLongPress = {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                actionSheetMessage = item.message
                                            },
                                            onImageTap = { url -> onOpenImageViewer(url) },
                                            onAlbumTap = { imgs, idx ->
                                                lightboxImages = imgs
                                                lightboxStartIndex = idx
                                                showLightbox = true
                                            },
                                            onReact = { emoji ->
                                                viewModel.toggleReaction(
                                                    item.message.id, emoji,
                                                    item.message.parsedReactions
                                                )
                                            },
                                            onReplyClick = onScrollToMessage,
                                            onMentionClick = onMentionClick,
                                            onOpenComments = { onOpenComments(item.message.id) },
                                            chat = uiState.chat
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Loading indicator (pagination) ────────────────────────────────
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isLoadingMore && uiState.messageListItems.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isOneUi && isDark) OneUiChat.CardBgDark else MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp, modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp,
                                color = if (isOneUi && isDark) OneUiChat.BlueDark
                                else if (isOneUi) OneUiChat.Blue
                                else MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                // ── Scroll to bottom FAB ──────────────────────────────────────────
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollDown,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Box {
                        val fabColor = if (isOneUi)
                            (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
                        else MaterialTheme.colorScheme.primary
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (uiState.messageListItems.size - 1).coerceAtLeast(0)
                                    )
                                }
                                if (hapticEnabled) haptic.perform(HapticType.CLICK, hapticEnabled)
                            },
                            modifier = Modifier.size(44.dp), containerColor = fabColor,
                            contentColor = Color.White, shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp, 6.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(22.dp))
                        }
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, lineHeight = 9.sp
                                )
                            }
                        }
                    }
                }
            } // end Box(weight(1f))
        } // end Column
    }

    // ── Диалоги и bottom sheets ───────────────────────────────────────────────

    if (showWallpaperSheet) {
        WallpaperBottomSheet(
            hasWallpaper = uiState.wallpaperUrl != null,
            isGroupOrChannel = uiState.chatType != ChatType.DIRECT,
            onDismiss = { showWallpaperSheet = false },
            onPickWallpaper = { showWallpaperSheet = false; wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = { showWallpaperSheet = false; viewModel.removeWallpaper() }
        )
    }

    // ── Album preview sheet ───────────────────────────────────────────────────
    if (uiState.showAlbumPreview) {
        AlbumPreviewSheet(
            images = uiState.albumDraft,
            caption = uiState.albumCaption,
            onSpoilerToggle = { viewModel.onAlbumSpoilerToggle(it) },
            onCaptionChange = { viewModel.onAlbumCaptionChange(it) },
            onDismiss = { viewModel.dismissAlbumPreview() },
            onSend = { viewModel.sendAlbum() }
        )
    }

    // ── Album lightbox ────────────────────────────────────────────────────────
    if (showLightbox && lightboxImages.isNotEmpty()) {
        AlbumLightbox(
            images = lightboxImages,
            startIndex = lightboxStartIndex,
            onDismiss = { showLightbox = false }
        )
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
                    val success = saveImageToGallery(context, msg.url ?: "") // Предполагается что функция есть
                    Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveVoice = {
                scope.launch {
                    val success = saveVoiceToDownloads(context, msg.url ?: "") // Предполагается что функция есть
                    Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenImage = { msg.url?.let { onOpenImageViewer(it) } },
            onReact = { emoji -> viewModel.toggleReaction(msg.id, emoji, msg.parsedReactions) }
        )
    }

    if (showStickerSheet) {
        StickerPickerBottomSheet(
            onDismiss = { showStickerSheet = false },
            onStickerSelected = { packId, sticker ->
                viewModel.sendSticker(
                    sticker   = sticker,
                    packId    = packId,
                    packName  = "",   // StickerPackViewModel знает имя пака,
                    packEmoji = ""    // но сюда можно передать пустую строку пока
                )
                showStickerSheet = false
                haptic.perform(HapticType.SUCCESS, hapticEnabled)
            }
        )
    }

    showDeleteConfirm?.let { msgId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_delete_message_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMessage(msgId); showDeleteConfirm = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
            }
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { delay(3000); viewModel.clearError() }
    }

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri = uri,
            onNavigateBack = { editorUri = null },
            onSend = { editedUri, isSpoiler ->
                editorUri = null
                viewModel.sendImage(editedUri, isSpoiler)
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  ALBUM PREVIEW SHEET
// ════════════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════════════
//  ALBUM BUBBLE
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    chatType: ChatType,
    isOneUi: Boolean = false,
    isDark: Boolean = false,
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
                        onReact = onReact, onShowPicker = { onLongPress() }
                    )
                }
            }
        }

        if (chatType == ChatType.CHANNEL && !message.deleted) {
            // CommentsButton( ... )
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
        AnimatedVisibility(
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

// ════════════════════════════════════════════════════════════════════════════════
//  ALBUM LIGHTBOX
// ════════════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════════════
//  WALLPAPER BOTTOM SHEET
// ════════════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════════════
//  TOP BARS
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneUiChatTopBar(
    uiState: ChatUiState, otherUid: String, chatId: String, isDark: Boolean,
    canSetWallpaper: Boolean, isAdmin: Boolean, isOwner: Boolean,
    onWallpaperClick: () -> Unit, onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit, onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit
) {
    val bgColor = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val textPrimary = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary
    val textSecondary = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFECECEC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = textPrimary, modifier = Modifier.size(18.dp))
                }
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
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (uiState.chatType) {
                            ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                            else -> uiState.chat?.name ?: ""
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status"
                        ) { status ->
                            when (status) {
                                is TopbarStatus.Typing -> TypingDots(
                                    primaryColor = if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
                                is TopbarStatus.Online -> Text(stringResource(R.string.chat_status_online),
                                    fontSize = 11.sp, color = Color(0xFF22C55E))
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "", fontSize = 11.sp, color = textSecondary)
                                else -> Text("", fontSize = 11.sp, color = textSecondary)
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members, memberCount))
                                if (online > 0) append(stringResource(R.string.online, online))
                            }, fontSize = 11.sp, color = textSecondary)
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers, uiState.chat?.memberCount ?: 0),
                            fontSize = 11.sp, color = textSecondary)
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                IconButton(onClick = onWallpaperClick) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null)
                            (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue) else textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = onLeaveClick) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, scrolledContainerColor = bgColor)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultChatTopBar(
    uiState: ChatUiState, otherUid: String, chatId: String,
    canSetWallpaper: Boolean, isAdmin: Boolean, isOwner: Boolean,
    onWallpaperClick: () -> Unit, onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit, onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit
) {
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
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (uiState.chatType) {
                            ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                            else -> uiState.chat?.name ?: ""
                        },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status"
                        ) { status ->
                            when (status) {
                                is TopbarStatus.Typing -> TypingDots()
                                is TopbarStatus.Online -> Text(stringResource(R.string.chat_status_online),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF22C55E), fontSize = 11.sp)
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen_topbar,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                else -> Text("", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members_topbar, memberCount))
                                if (online > 0) append(stringResource(R.string.online_topbar, online))
                            }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers_topbar, uiState.chat?.memberCount ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                IconButton(onClick = onWallpaperClick) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = onLeaveClick) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  BOTTOM BARS
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun OneUiChatBottomBar(
    uiState: ChatUiState, inputText: String, isDark: Boolean,
    canSendMessage: Boolean, canSendMedia: Boolean, hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: com.google.accompanist.permissions.PermissionState,
    onInputChange: (String) -> Unit, onAttach: () -> Unit, onStickerClick: () -> Unit,
    onSend: () -> Unit, onStartRecord: () -> Unit, onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit, onSendRecord: () -> Unit, onClearReply: () -> Unit,
    haptic: by.iposdev.visorlink.utils.HapticHelper
) {
    val barBg = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val accentColor = if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
    val inputBg = if (isDark) OneUiChat.InputBgDark else OneUiChat.InputBg
    val iconTint = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary

    Column(modifier = Modifier
        .background(barBg)
        .navigationBarsPadding()
        .imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage ->
                stringResource(R.string.only_admins_can_post_in_channels)
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
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150))
        ) {
            uiState.replyingTo?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF232B3A) else Color(0xFFEBF1FD))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (msg.type == MessageType.ALBUM && msg.images.isNotEmpty()) {
                        AsyncImage(
                            model = msg.images[0].url, contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(accentColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${msg.senderUsername}", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = accentColor)
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
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = if (isDark) Color(0xFF232B3A) else Color(0xFFEBF1FD))
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        IconButton(onClick = onAttach, enabled = !uiState.isCooldown) {
                            Icon(Icons.Default.AttachFile, null, tint = iconTint,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBg)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputText, onValueChange = onInputChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                maxLines = 4,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary,
                                    fontSize = 15.sp
                                ),
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(stringResource(R.string.chat_input_placeholder),
                                            fontSize = 15.sp,
                                            color = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary)
                                    }
                                    inner()
                                }
                            )
                            if (inputText.isNotEmpty()) {
                                Text(
                                    text = "${inputText.length}/2000",
                                    fontSize = 11.sp,
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error else iconTint,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            IconButton(onClick = onStickerClick, modifier = Modifier.size(32.dp), enabled = !uiState.isCooldown) {
                                Icon(Icons.Default.EmojiEmotions, null,
                                    tint = if (showStickerSheet) accentColor else iconTint,
                                    modifier = Modifier.size(20.dp))
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
                        label = "oui_send_mic"
                    ) { hasText ->
                        if (hasText) {
                            val sendScale = remember { Animatable(1f) }
                            val sendScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(sendScale.value)
                                    .background(if (uiState.isCooldown) accentColor.copy(alpha = 0.5f) else accentColor, CircleShape)
                                    .clickable(
                                        enabled = !uiState.isCooldown,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        sendScope.launch {
                                            sendScale.animateTo(
                                                0.80f,
                                                spring(stiffness = Spring.StiffnessHigh)
                                            )
                                            sendScale.animateTo(
                                                1.10f,
                                                spring(Spring.DampingRatioLowBouncy)
                                            )
                                            sendScale.animateTo(
                                                1f,
                                                spring(Spring.DampingRatioMediumBouncy)
                                            )
                                        }
                                        if (hapticEnabled) haptic.perform(
                                            HapticType.MESSAGE_SENT,
                                            hapticEnabled
                                        )
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                        } else {
                            if (canSendMedia) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                        .clickable(enabled = !uiState.isCooldown) {
                                            if (audioPermission.status.isGranted) {
                                                if (hapticEnabled) haptic.perform(
                                                    HapticType.LONG_PRESS,
                                                    hapticEnabled
                                                )
                                                onStartRecord()
                                            } else onRequestAudioPerm()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, null, tint = accentColor,
                                        modifier = Modifier.size(22.dp))
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun DefaultChatBottomBar(
    uiState: ChatUiState, inputText: String,
    canSendMessage: Boolean, canSendMedia: Boolean, hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: com.google.accompanist.permissions.PermissionState,
    onInputChange: (String) -> Unit, onAttach: () -> Unit, onStickerClick: () -> Unit,
    onSend: () -> Unit, onStartRecord: () -> Unit, onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit, onSendRecord: () -> Unit, onClearReply: () -> Unit,
    haptic: by.iposdev.visorlink.utils.HapticHelper
) {
    Column(modifier = Modifier
        .background(MaterialTheme.colorScheme.surface)
        .navigationBarsPadding()
        .imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage ->
                stringResource(R.string.only_admins_can_post_in_channels)
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
            enter = slideInVertically(initialOffsetY = { it },
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) +
                    fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it },
                animationSpec = tween(180, easing = FastOutLinearInEasing)) + fadeOut(tween(150))
        ) {
            uiState.replyingTo?.let { msg ->
                if (msg.type == MessageType.ALBUM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (msg.images.isNotEmpty()) {
                            AsyncImage(
                                model = msg.images[0].url, contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Reply, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("@${msg.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(msg.caption ?: "📷 ${msg.images.size} фото",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.chat_cancel_reply),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                } else {
                    ReplyBanner(msg) { onClearReply() }
                }
            }
        }

        AnimatedVisibility(visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer)
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        val attachScale = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            attachScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                        }
                        IconButton(onClick = onAttach, modifier = Modifier.scale(attachScale.value), enabled = !uiState.isCooldown) {
                            Icon(Icons.Default.AttachFile, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val emojiScale = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        delay(40)
                        emojiScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                    }
                    IconButton(
                        onClick = { if (hapticEnabled) haptic.perform(HapticType.CLICK, hapticEnabled); onStickerClick() },
                        modifier = Modifier.scale(emojiScale.value),
                        enabled = !uiState.isCooldown
                    ) {
                        Icon(Icons.Default.EmojiEmotions, null,
                            tint = if (showStickerSheet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = inputText, onValueChange = onInputChange,
                        placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(
                                animationSpec = spring(
                                    Spring.DampingRatioMediumBouncy,
                                    Spring.StiffnessMedium
                                )
                            ),
                        maxLines = 4, shape = MaterialTheme.shapes.extraLarge,
                        supportingText = if (inputText.isNotEmpty()) {
                            {
                                Text(
                                    text = "${inputText.length}/2000",
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        } else null
                    )
                    Spacer(Modifier.width(4.dp))
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            (scaleIn(initialScale = 0.6f,
                                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) +
                                    fadeIn(tween(150))) togetherWith
                                    (scaleOut(targetScale = 0.6f,
                                        animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                                            fadeOut(tween(80)))
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
                                    .background(if (uiState.isCooldown) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable(
                                        enabled = !uiState.isCooldown,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        sendScope.launch {
                                            sendScale.animateTo(
                                                0.82f,
                                                spring(stiffness = Spring.StiffnessHigh)
                                            )
                                            sendScale.animateTo(
                                                1.12f,
                                                spring(
                                                    Spring.DampingRatioLowBouncy,
                                                    Spring.StiffnessMedium
                                                )
                                            )
                                            sendScale.animateTo(
                                                1f,
                                                spring(Spring.DampingRatioMediumBouncy)
                                            )
                                        }
                                        if (hapticEnabled) haptic.perform(
                                            HapticType.MESSAGE_SENT,
                                            hapticEnabled
                                        )
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send,
                                    stringResource(R.string.action_send),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp))
                            }
                        } else {
                            if (canSendMedia) {
                                IconButton(
                                    onClick = {
                                        if (audioPermission.status.isGranted) {
                                            if (hapticEnabled) haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                            onStartRecord()
                                        } else onRequestAudioPerm()
                                    },
                                    modifier = Modifier.size(48.dp),
                                    enabled = !uiState.isCooldown
                                ) {
                                    Icon(Icons.Default.Mic, stringResource(R.string.chat_input_record),
                                        tint = MaterialTheme.colorScheme.primary)
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

// ════════════════════════════════════════════════════════════════════════════════
//  MESSAGE BUBBLE
// ════════════════════════════════════════════════════════════════════════════════

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
    isOneUi: Boolean = false,
    isDark: Boolean = false,
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit,
    onLongPress: () -> Unit,
    onImageTap: (url: String) -> Unit,
    onAlbumTap: (images: List<AlbumImage>, startIndex: Int) -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null
) {
    val haptic = rememberHaptic()
    val isReadByOther = otherUid in message.readBy
    var showPackBanner by remember(message.id) { mutableStateOf(false) }

    if (message.type == MessageType.ALBUM && !message.deleted) {
        AlbumBubble(
            message = message, isMine = isMine, currentUid = currentUid,
            chatType = chatType, isOneUi = isOneUi, isDark = isDark,
            onAlbumTap = onAlbumTap,
            onLongPress = {
                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                onLongPress()
            },
            onReact = onReact, onReplyClick = onReplyClick,
            onOpenComments = onOpenComments, chat = chat
        )
        return
    }

    if (message.type == MessageType.IMAGE && !message.deleted) {
        ImageBubble(
            message = message, isMine = isMine, isReadByOther = isReadByOther,
            chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
            isOneUi = isOneUi, isDark = isDark,
            onTap = { message.url?.let { onImageTap(it) } },
            onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
            onReact = onReact, onReplyClick = onReplyClick,
            onOpenComments = onOpenComments, chat = chat
        )
        return
    }

    if (message.type == MessageType.STICKER && !message.deleted) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            if (!message.packId.isNullOrBlank()) showPackBanner = !showPackBanner
                        },
                        onLongClick = {
                            haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                            onLongPress()
                        }
                    )
            ) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    message.replyData?.let { reply ->
                        ReplyPreview(
                            reply = reply, isMine = isMine,
                            onClick = { reply.id?.let { id -> onReplyClick(id) } }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    AsyncImage(
                        model = message.url,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        message.createdAt?.toDate()?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (!message.packId.isNullOrBlank() && showPackBanner) {
                        AddStickerPackBanner(
                            packId    = message.packId,
                            packName  = message.packName ?: "",
                            packEmoji = message.packEmoji ?: "🎭"
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = message.parsedReactions.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { -it / 2 },
                    animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                        scaleIn(initialScale = 0.7f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
            ) {
                InlinedReactionRow(
                    reactions = message.parsedReactions,
                    currentUid = currentUid,
                    isMine = isMine,
                    isOneUi = isOneUi,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    onReact = onReact,
                    onShowPicker = { onLongPress() }
                )
            }
        }
        return
    }

    val bubbleColor = when {
        isOneUi && isMine && isDark -> OneUiChat.BubbleMineDark
        isOneUi && isMine           -> OneUiChat.BubbleMine
        isOneUi && !isMine && isDark -> OneUiChat.BubbleOtherDark
        isOneUi && !isMine          -> OneUiChat.BubbleOther
        isMine -> MaterialTheme.colorScheme.primary
        else   -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isOneUi && isMine -> Color.White
        isOneUi && !isMine && isDark -> OneUiChat.TextPrimaryDark
        isOneUi && !isMine -> OneUiChat.TextPrimary
        isMine -> MaterialTheme.colorScheme.onPrimary
        else   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val linkColor = if (isMine) Color.White else
        (if (isOneUi && isDark) OneUiChat.BlueDark
        else if (isOneUi) OneUiChat.Blue
        else MaterialTheme.colorScheme.primary)

    val bubbleShape = if (isOneUi) {
        if (isMine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
    } else {
        if (isMine) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        else        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    }

    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .scale(pressScale.value),
            shape = bubbleShape, color = bubbleColor,
            shadowElevation = if (isOneUi && !isMine) 1.dp else 0.dp, tonalElevation = 0.dp
        ) {
            Box(modifier = Modifier.combinedClickable(
                onClick = { },
                onLongClick = {
                    scope.launch {
                        pressScale.animateTo(0.91f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                        pressScale.animateTo(1.04f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                        pressScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                    onLongPress()
                }
            )) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                    if (showSenderName && !isMine) {
                        Text("@${message.senderUsername}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOneUi) (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 2.dp))
                    }

                    message.replyData?.let { reply ->
                        ReplyPreview(reply = reply, isMine = isMine,
                            onClick = { reply.id?.let { id -> onReplyClick(id) } })
                        Spacer(Modifier.height(4.dp))
                    }

                    if (message.deleted) {
                        Text(stringResource(R.string.chat_message_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.6f))
                    } else when (message.type) {
                        MessageType.TEXT -> LinkifiedText(
                            text = message.text ?: "", color = textColor, linkColor = linkColor,
                            onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
                            onMentionClick = onMentionClick)
                        MessageType.VOICE -> VoiceBubble(
                            messageId = message.id, url = message.url ?: "",
                            durationSec = message.duration ?: 0, tint = textColor,
                            playback = voicePlayback, onPlay = onPlayVoice, onSeek = onSeekVoice)
                        MessageType.STICKER -> AsyncImage(model = message.url, contentDescription = null,
                            modifier = Modifier.size(120.dp))
                        else -> {}
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AnimatedVisibility(visible = message.createdAt != null,
                            enter = fadeIn(tween(300))) {
                            Text(message.createdAt?.toDate()?.let {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                            } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        if (isMine && !message.deleted && chatType == ChatType.DIRECT) {
                            AnimatedContent(targetState = isReadByOther,
                                transitionSpec = {
                                    scaleIn(initialScale = 0.5f,
                                        animationSpec = spring(Spring.DampingRatioLowBouncy)) +
                                            fadeIn(tween(200)) togetherWith
                                            scaleOut(targetScale = 0.5f) + fadeOut(tween(100))
                                }, label = "read_receipt"
                            ) { read -> ReadReceipt(isRead = read) }
                        }
                    }

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
                            hapticEnabled = hapticEnabled,
                            onReact = onReact, onShowPicker = { onLongPress() }
                        )
                    }
                }
            }
        }

        if (chatType == ChatType.CHANNEL && !message.deleted) {
            // CommentsButton
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  HELPER BUBBLES
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    message: Message, isMine: Boolean, isReadByOther: Boolean,
    chatType: ChatType, currentUid: String, hapticEnabled: Boolean,
    isOneUi: Boolean = false, isDark: Boolean = false,
    onTap: () -> Unit, onLongPress: () -> Unit, onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null
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
        animationSpec = tween(300), label = "spoiler_blur"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .scale(pressScale.value)
                .clip(imageShape)
                .combinedClickable(
                    onClick = {
                        if (isSpoiler && !spoilerRevealed) spoilerRevealed = true
                        else onTap()
                    },
                    onLongClick = {
                        scope.launch {
                            pressScale.animateTo(
                                0.93f,
                                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh)
                            )
                            pressScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        onLongPress()
                    }
                )
        ) {
            AsyncImage(model = message.url, contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                contentScale = ContentScale.Crop)

            val overlayAlpha by animateFloatAsState(
                targetValue = if (isSpoiler && !spoilerRevealed) 1f else 0f,
                animationSpec = tween(300), label = "spoiler_alpha")

            if (overlayAlpha > 0f) {
                Box(modifier = Modifier
                    .matchParentSize()
                    .alpha(overlayAlpha)
                    .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.tap_to_reveal), color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            message.replyData?.let { reply ->
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { reply.id?.let { id -> onReplyClick(id) } }
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(reply.text ?: stringResource(R.string.photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Box(modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(message.createdAt?.toDate()?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "", style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontSize = 10.sp)
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(imageVector = if (isReadByOther) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null, modifier = Modifier.size(13.dp),
                            tint = if (isReadByOther) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        AnimatedVisibility(visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 },
                animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
        ) {
            Row(modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.parsedReactions.forEach { reaction ->
                    key(reaction.emoji) {
                        val iReacted = currentUid in reaction.uids
                        val chipScale = remember { Animatable(1f) }
                        val chipScope = rememberCoroutineScope()
                        val accentColor = if (isOneUi)
                            (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
                        else MaterialTheme.colorScheme.primary
                        Box(modifier = Modifier
                            .scale(chipScale.value)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (iReacted) accentColor.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                chipScope.launch {
                                    chipScale.animateTo(
                                        1.3f,
                                        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh)
                                    )
                                    chipScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                }
                                onReact(reaction.emoji)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(reaction.emoji, fontSize = 13.sp)
                                Text(reaction.count.toString(), fontSize = 11.sp,
                                    color = if (iReacted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (message.parsedReactions.size < 3) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onLongPress() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center) {
                        Text("＋", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlinedReactionRow(
    reactions: List<Reaction>, currentUid: String, isMine: Boolean,
    isOneUi: Boolean, isDark: Boolean, hapticEnabled: Boolean,
    onReact: (String) -> Unit, onShowPicker: () -> Unit
) {
    val haptic = rememberHaptic()
    val accentColor = if (isOneUi) (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
    else MaterialTheme.colorScheme.primary
    val chipBgSelected = if (isMine) Color.White.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.15f)
    val chipBgDefault = if (isMine) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val chipTextSelected = if (isMine) Color.White else accentColor
    val chipTextDefault = if (isMine) Color.White.copy(0.8f) else
        (if (isDark) OneUiChat.TextPrimaryDark else Color(0xFF444444))

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.forEach { reaction ->
            key(reaction.emoji) {
                val iReacted = currentUid in reaction.uids
                val chipScale = remember { Animatable(1f) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    chipScale.snapTo(0f)
                    chipScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                }
                Box(modifier = Modifier
                    .scale(chipScale.value)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (iReacted) chipBgSelected else chipBgDefault)
                    .clickable {
                        scope.launch {
                            chipScale.animateTo(
                                1.3f,
                                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh)
                            )
                            chipScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        haptic.perform(HapticType.REACTION, hapticEnabled)
                        onReact(reaction.emoji)
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(reaction.emoji, fontSize = 13.sp)
                        Text(reaction.count.toString(), fontSize = 11.sp,
                            fontWeight = if (iReacted) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (iReacted) chipTextSelected else chipTextDefault)
                    }
                }
            }
        }
        if (reactions.size < 3) {
            Box(modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(chipBgDefault)
                .clickable { onShowPicker() }
                .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center) {
                Text("＋", fontSize = 12.sp,
                    color = if (isMine) Color.White.copy(0.7f)
                    else (if (isDark) OneUiChat.TextSecondaryDark else Color(0xFF888888)))
            }
        }
    }
}

@Composable
private fun ReadReceipt(isRead: Boolean) {
    Icon(imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = if (isRead) stringResource(R.string.chat_read)
        else stringResource(R.string.chat_sent),
        modifier = Modifier.size(14.dp),
        tint = if (isRead) Color(0xFF00D4FF) else Color(0xFF6B7280))
}

@Composable
private fun ReplyPreview(reply: ReplyData, isMine: Boolean, onClick: () -> Unit) {
    val accentColor = if (isMine) Color.White.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val nameColor = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
    val textColor = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.extraSmall)
        .background(accentColor)
        .clickable(onClick = onClick)
        .padding(6.dp)) {
        Box(Modifier
            .width(3.dp)
            .height(32.dp)
            .background(nameColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall,
                color = nameColor, fontWeight = FontWeight.SemiBold)
            Text(
                reply.text ?: when (reply.type) {
                    MessageType.IMAGE   -> stringResource(R.string.image)
                    MessageType.VOICE   -> stringResource(R.string.voice_message)
                    MessageType.STICKER -> stringResource(R.string.sticker)
                    MessageType.ALBUM   -> "📷 Фото"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall, color = textColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReplyBanner(message: Message, onDismiss: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.primaryContainer)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${message.senderUsername}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(when (message.type) {
                MessageType.IMAGE   -> stringResource(R.string.image)
                MessageType.VOICE   -> stringResource(R.string.voice_message)
                MessageType.STICKER -> stringResource(R.string.sticker)
                MessageType.ALBUM   -> message.caption ?: "📷 ${message.images.size} фото"
                else -> message.text ?: "Media"
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.chat_cancel_reply),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun DateSeparator(
    label: String, isOneUi: Boolean = false, isDark: Boolean = false, hasWallpaper: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.85f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))) {
        Box(Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (isOneUi) {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (hasWallpaper) Color.Black.copy(alpha = 0.4f)
                        else if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.W500,
                        color = if (hasWallpaper) Color.White
                        else (if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary))
                }
            } else {
                Surface(color = if (hasWallpaper) Color.Black.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.extraSmall) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TypingDots(primaryColor: Color = Color.Unspecified) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val color = if (primaryColor == Color.Unspecified) MaterialTheme.colorScheme.primary else primaryColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.chat_typing), style = MaterialTheme.typography.labelSmall,
            color = color, fontSize = 11.sp)
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse), label = "dot_$i")
            Box(Modifier
                .size(4.dp)
                .background(color.copy(alpha = alpha), CircleShape))
        }
    }
}

@Composable
fun LinkifiedText(
    text: String, color: Color, linkColor: Color,
    onLongPress: () -> Unit, onMentionClick: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val (urlList, mentionList) = remember(text) {
        val urls = mutableListOf<Triple<String, Int, Int>>()
        val urlMatcher = android.util.Patterns.WEB_URL.matcher(text)
        while (urlMatcher.find()) {
            var url = urlMatcher.group() ?: continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            urls.add(Triple(url, urlMatcher.start(), urlMatcher.end()))
        }
        val mentions = mutableListOf<Triple<String, Int, Int>>()
        val mentionRegex = Regex("(?<!\\w)@[a-zA-Z0-9_]+")
        mentionRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val isInsideUrl = urls.any { start >= it.second && end <= it.third }
            if (!isInsideUrl) mentions.add(Triple(matchResult.value, start, end))
        }
        Pair(urls, mentions)
    }
    val annotatedString = remember(text, color, linkColor) {
        buildAnnotatedString {
            append(text)
            urlList.forEach { (url, start, end) ->
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("URL", url, start, end)
            }
            mentionList.forEach { (mention, start, end) ->
                addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), start, end)
                addStringAnnotation("MENTION", mention, start, end)
            }
        }
    }
    Text(text = annotatedString, color = color, style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        annotatedString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                                return@detectTapGestures
                            }
                        annotatedString.getStringAnnotations("MENTION", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                onMentionClick(annotation.item.removePrefix("@"))
                                return@detectTapGestures
                            }
                    }
                }
            )
        })
}

@Composable
private fun SwipeableMessage(
    message: Message, isMine: Boolean, hapticEnabled: Boolean,
    isOneUi: Boolean = false, isDark: Boolean = false,
    onReply: () -> Unit, content: @Composable () -> Unit
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
        animationSpec = tween(80), label = "reply_icon_alpha")
    val replyIconScale by animateFloatAsState(
        targetValue = if (kotlin.math.abs(offsetX.value) >= triggerThreshold) 1.15f
        else if (kotlin.math.abs(offsetX.value) > 20f)
            (0.6f + 0.4f * (kotlin.math.abs(offsetX.value) / triggerThreshold)).coerceIn(0.6f, 1.15f)
        else 0.6f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy), label = "reply_icon_scale")

    val replyIconColor = if (isOneUi) (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
    else MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 16.dp)
                .size(36.dp)
                .scale(replyIconScale)
                .background(replyIconColor.copy(alpha = replyIconAlpha * 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Reply, stringResource(R.string.chat_reply),
                tint = replyIconColor.copy(alpha = replyIconAlpha), modifier = Modifier.size(20.dp))
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    var totalDrag = 0f
                    var totalDragY = 0f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        totalDrag = 0f
                        totalDragY = 0f
                        didTrigger = false
                        var isDragging = false
                        var isVertical = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) break

                            val dragDeltaX = change.position.x - change.previousPosition.x
                            val dragDeltaY = change.position.y - change.previousPosition.y
                            totalDrag += dragDeltaX
                            totalDragY += dragDeltaY

                            if (!isDragging && kotlin.math.abs(totalDragY) > kotlin.math.abs(
                                    totalDrag
                                )
                            ) {
                                isVertical = true
                                break
                            }

                            if (isVertical) break

                            if (kotlin.math.abs(totalDrag) > 10f) {
                                isDragging = true
                                change.consume()

                                val target = if (isMine)
                                    (offsetX.value + dragDeltaX).coerceIn(-maxOffset, 0f)
                                else (offsetX.value + dragDeltaX).coerceIn(0f, maxOffset)

                                scope.launch { offsetX.snapTo(target) }

                                if (kotlin.math.abs(offsetX.value) >= triggerThreshold && !didTrigger) {
                                    didTrigger = true
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    onReply()
                                    scope.launch {
                                        offsetX.animateTo(
                                            if (isMine) -triggerThreshold * 0.5f else triggerThreshold * 0.5f,
                                            spring(
                                                Spring.DampingRatioLowBouncy,
                                                Spring.StiffnessHigh
                                            )
                                        )
                                        delay(100)
                                        offsetX.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy)
                                        )
                                    }
                                }
                            }
                        }

                        if (isDragging) {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                }
        ) { content() }
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    val emptyTransition = rememberInfiniteTransition(label = "empty")
    val emptyScale by emptyTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty_scale")
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null,
                modifier = Modifier
                    .size(64.dp)
                    .scale(emptyScale),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.chat_empty_subtitle), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerBottomSheet(
    stickers: List<Sticker>, hapticEnabled: Boolean,
    onDismiss: () -> Unit, onStickerSelected: (Sticker) -> Unit
) {
    val haptic = rememberHaptic()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .width(36.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                    RoundedCornerShape(2.dp)
                ))
        },
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stickers_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stickers.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEmotions, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.stickers_empty_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.stickers_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            } else {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stickers.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sticker ->
                                Box(modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        if (hapticEnabled) haptic.perform(
                                            HapticType.CLICK,
                                            hapticEnabled
                                        )
                                        onStickerSelected(sticker)
                                    }, contentAlignment = Alignment.Center) {
                                    AsyncImage(model = sticker.url, contentDescription = sticker.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentScale = ContentScale.Fit)
                                }
                            }
                            repeat(4 - row.size) {
                                Box(modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun VoiceBubble(
    messageId: String, url: String, durationSec: Int, tint: Color,
    playback: by.iposdev.visorlink.utils.VoicePlaybackState,
    onPlay: (url: String, durationSec: Int) -> Unit, onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val isThisMessage = playback.playingMessageId == messageId
    val isPlaying = isThisMessage && playback.isPlaying
    val isLoading = isThisMessage && playback.isLoading
    val progress = if (isThisMessage) playback.progress else 0f
    val currentSec = if (isThisMessage) playback.currentMs / 1000 else 0
    val totalSec = if (isThisMessage && playback.durationMs > 0) playback.durationMs / 1000 else durationSec

    var waveform by remember(url) {
        mutableStateOf(List(40) { kotlin.random.Random.nextFloat() * 0.8f + 0.2f })
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "pulse_phase")

    Column(modifier = Modifier.width(220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape)
                .clickable { onPlay(url, durationSec) }, contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(modifier = Modifier
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
                }) {
                val barCount = waveform.size
                val totalWidth = size.width
                val totalHeight = size.height
                val gap = totalWidth * 0.018f
                val barWidth = (totalWidth - gap * (barCount - 1)) / barCount
                val centerY = totalHeight / 2f
                waveform.forEachIndexed { index, amp ->
                    val barProgress = (index + 0.5f) / barCount
                    val isPassed = barProgress <= progress && isThisMessage
                    val pulse = if (isPlaying && isPassed)
                        1f + 0.12f * kotlin.math.sin(pulsePhase + index * 0.4f) else 1f
                    val barHeight = (amp * totalHeight * 0.88f * pulse).coerceAtLeast(3f)
                    val x = index * (barWidth + gap)
                    drawRoundRect(
                        color = if (isPassed) tint else tint.copy(alpha = 0.30f),
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
                }
                if (isThisMessage && progress > 0f) {
                    drawLine(color = tint.copy(alpha = 0.6f),
                        start = androidx.compose.ui.geometry.Offset(progress * totalWidth, 0f),
                        end = androidx.compose.ui.geometry.Offset(progress * totalWidth, totalHeight),
                        strokeWidth = 1.5f)
                }
            }
        }
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatVoiceTime(currentSec), style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(formatVoiceTime(totalSec), style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

private fun formatVoiceTime(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
private fun RecordingBar(hapticEnabled: Boolean, onCancel: () -> Unit, onSend: () -> Unit) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha")
    val sendScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsed++; haptic.perform(HapticType.CLICK, hapticEnabled) }
    }
    Row(Modifier
        .fillMaxWidth()
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() }) {
            Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier
                .size(10.dp)
                .background(Color.Red.copy(alpha = dotAlpha), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_recording_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = {
                scope.launch {
                    sendScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                    sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                onSend()
            },
            modifier = Modifier
                .size(48.dp)
                .scale(sendScale.value)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send),
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}