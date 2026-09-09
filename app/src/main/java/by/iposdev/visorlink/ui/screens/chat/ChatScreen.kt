package by.iposdev.visorlink.ui.screens.chat

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.model.aegis.LinkIntent
import by.iposdev.visorlink.ui.aegis.AegisAura
import by.iposdev.visorlink.ui.aegis.AegisLifeViewModel
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlFab
import by.iposdev.visorlink.ui.components.chat.*
import by.iposdev.visorlink.ui.components.mediapicker.VlMediaPickerSheet
import by.iposdev.visorlink.ui.screens.stickers.StickerPickerBottomSheet
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.ActiveChatTracker
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.ImageCache
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    topicId: String? = null,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenStickers: (onSelect: (Sticker) -> Unit) -> Unit = {},
    onOpenChatSettings: (chatId: String) -> Unit = {},
    onOpenImageViewer: (url: String, type: String) -> Unit = { _, _ -> },
    onMentionClick: (String) -> Unit = {},
    onOpenComments: (messageId: String) -> Unit = {},
    onForward: ((Message) -> Unit)? = null,
    onOpenTopicList: ((String) -> Unit)? = null,
    hapticEnabled: Boolean = true,
) {
    val viewModel: ChatViewModel = koinViewModel(parameters = { parametersOf(chatId, otherUid, topicId) })
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }

    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var selectedStickerPack by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }

    val aegisViewModel: AegisLifeViewModel = koinViewModel()
    val aegisUiState by aegisViewModel.uiState.collectAsState()
    val isAegisEnabled by aegisViewModel.isAegisEnabled.collectAsState()

    LaunchedEffect(uiState.messageListItems, isAegisEnabled) {
        if (!isAegisEnabled) return@LaunchedEffect
        val messages = uiState.messageListItems
            .filterIsInstance<MessageListItem.MessageItem>()
            .map { it.message }
        aegisViewModel.analyzeMessages(messages)
    }

    LaunchedEffect(uiState.initialDraft) {
        if (uiState.initialDraft.isNotEmpty() && inputText.isEmpty()) {
            inputText = uiState.initialDraft
        }
    }

    LaunchedEffect(uiState.chat?.isForumActive, topicId) {
        if (topicId == null && uiState.chat?.isForumActive == true && onOpenTopicList != null) {
            onOpenTopicList(chatId)
        }
    }

    LaunchedEffect(uiState.replyingTo) {
        if (uiState.replyingTo != null) {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.editingMessage) {
        if (uiState.editingMessage != null) {
            inputText = uiState.initialDraft
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var showStickerSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var forwardingMessage by remember { mutableStateOf<ForwardableMessage?>(null) }

    var lightboxImages by remember { mutableStateOf<List<AlbumImage>>(emptyList()) }
    var lightboxStartIndex by remember { mutableIntStateOf(0) }
    var showLightbox by remember { mutableStateOf(false) }

    var showAlbumPreview by remember { mutableStateOf(false) }
    var albumDraft by remember { mutableStateOf<List<AlbumImage>>(emptyList()) }
    var albumCaption by remember { mutableStateOf("") }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var showMediaPicker by remember { mutableStateOf(false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setWallpaper(it) }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tempFile = File(context.cacheDir, "audio_send_${System.currentTimeMillis()}.mp3")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                val mmr = MediaMetadataRetriever()
                var title = ""
                var artist = ""
                var durationSec = 0
                var coverFile: File? = null
                try {
                    mmr.setDataSource(tempFile.absolutePath)
                    title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                    artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                    val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationSec = (durStr?.toIntOrNull() ?: 0) / 1000
                    val pic = mmr.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        val cf = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(cf).use { it.write(pic) }
                        coverFile = cf
                    }
                } catch (_: Exception) {}
                finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
                if (title.isBlank()) {
                    title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Аудиозапись"
                }
                if (artist.isBlank()) {
                    artist = "Неизвестный исполнитель"
                }
                viewModel.sendAudio(tempFile, title, artist, durationSec, coverFile)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to prepare audio", e)
            }
        }
    }

    val onScrollToMessage: (String) -> Unit = { targetMsgId ->
        val index = uiState.messageListItems.asReversed().indexOfFirst {
            it is MessageListItem.MessageItem && it.message.id == targetMsgId
        }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    val isAdmin  = uiState.myMember?.isAdmin() == true
    val isOwner  = uiState.myMember?.isOwner() == true
    val canSetWallpaper = uiState.chatType == ChatType.DIRECT || isAdmin
    val canReact = uiState.chat?.settings?.allowReactions != false
    val canSendMessage = uiState.canSendMessage
    val canSendMedia = uiState.canSendMedia

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chatId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ActiveChatTracker.activeChatId = chatId
                    NotificationHelper.clearNotification(context, chatId)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    ActiveChatTracker.activeChatId = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ActiveChatTracker.activeChatId = null
            NotificationHelper.clearNotification(context, chatId)
        }
    }

    val newestMessage = (uiState.messageListItems.lastOrNull() as? MessageListItem.MessageItem)?.message
    val newestMessageId = newestMessage?.id

    var isInitialLoad by remember { mutableStateOf(true) }
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }

    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showScrollDown) { if (!showScrollDown) unreadCount = 0 }

    LaunchedEffect(newestMessageId) {
        if (newestMessageId == null) return@LaunchedEffect
        if (isInitialLoad) { isInitialLoad = false; return@LaunchedEffect }
        val isMine = newestMessage?.senderId == viewModel.currentUid
        if (isMine) listState.animateScrollToItem(0)
        else if (isAtBottom) {
            if (hapticEnabled) haptic.perform(HapticType.MESSAGE_RECEIVED, hapticEnabled)
            listState.animateScrollToItem(0)
        } else unreadCount++
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val otherUser = uiState.otherUser
    val currentUser = uiState.currentUser
    val isOtherPro = otherUser?.isProActive() == true
    val applyCustom = isOtherPro && (currentUser?.ignoreCustomizations != true) && (uiState.chatType == ChatType.DIRECT)
    val cust = if (applyCustom) otherUser?.customization ?: emptyMap() else emptyMap()
    val chatBgUrl = cust["bgUrl"] as? String ?: uiState.wallpaperUrl

    UserProfileTheme(profile = otherUser, currentUser = currentUser) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (chatBgUrl != null) {
                AsyncImage(
                    model = chatBgUrl, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.6f else 0.8f
                )
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        ChatTopBar(
                            uiState = uiState, otherUid = viewModel.effectiveOtherUid, chatId = chatId,
                            canSetWallpaper = canSetWallpaper, isAdmin = isAdmin, isOwner = isOwner,
                            hapticEnabled = hapticEnabled,
                            onWallpaperClick = { showWallpaperSheet = true }, onNavigateBack = onNavigateBack,
                            onOpenOtherProfile = { onOpenOtherProfile(viewModel.effectiveOtherUid) },
                            onOpenChatSettings = onOpenChatSettings,
                            onLeaveClick = { showLeaveDialog = true },
                            onAegisClick = { aegisViewModel.onInteract() }, isAegisEnabled = isAegisEnabled,
                            onOpenTopicList = if (onOpenTopicList != null) { { onOpenTopicList(chatId) } } else null
                        )
                        AudioPlaybackDockBar(
                            musicPlayback = uiState.musicPlayback,
                            onTogglePlayPause = { viewModel.toggleAudioPlayback() },
                            onClose = { viewModel.stopAudio() },
                            onOpenFullscreen = { viewModel.openFullscreenAudio() }
                        )
                    }
                },
                bottomBar = {
                    ChatBottomBar(
                        uiState = uiState, inputText = inputText,
                        canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                        hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                        audioPermission = audioPermission, focusRequester = inputFocusRequester,
                        onInputChange = { inputText = it; viewModel.onTextChanged(it) },
                        onAttach = {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            showMediaPicker = true
                        },
                        onStickerClick = { showStickerSheet = true },
                        onSend = {
                            val t = inputText
                            inputText = ""
                            if (uiState.editingMessage != null) viewModel.saveEdit(t)
                            else viewModel.sendText(t)
                        },
                        onStartRecord = { viewModel.startRecording() },
                        onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                        onCancelRecord = { viewModel.cancelRecording() },
                        onSendRecord = { viewModel.stopRecordingAndSend() },
                        onClearReply = { viewModel.clearReply() },
                        onCancelEdit = { viewModel.cancelEditing(); inputText = "" },
                        onJoinChannel = { viewModel.joinChannel() }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (uiState.wallpaperUrl != null && !applyCustom) {
                        AsyncImage(model = uiState.wallpaperUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(), alpha = 0.5f)
                    } else if (!applyCustom) {
                        VlAmbientGlow()
                    }

                    if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
                        EmptyChatPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            state = listState, reverseLayout = true,
                            userScrollEnabled = contextMenuData == null,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            itemsIndexed(
                                items = uiState.messageListItems.asReversed(),
                                key = { _, item ->
                                    when (item) {
                                        is MessageListItem.DateHeader  -> "date_${item.label}"
                                        is MessageListItem.MessageItem -> item.message.id
                                    }
                                }
                            ) { index, item ->
                                if (index >= uiState.messageListItems.size - 5 && uiState.hasMore && !uiState.isLoadingMore) {
                                    LaunchedEffect(index) { viewModel.loadMore() }
                                }
                                when (item) {
                                    is MessageListItem.DateHeader -> DateSeparator(item.label)
                                    is MessageListItem.MessageItem -> {
                                        val isMine = item.message.senderId == viewModel.currentUid
                                        SwipeableMessage(
                                            message = item.message, isMine = isMine, hapticEnabled = hapticEnabled,
                                            onReply = {
                                                haptic.perform(HapticType.SELECTION, hapticEnabled)
                                                viewModel.setReplyTo(item.message)
                                                inputFocusRequester.requestFocus()
                                                keyboardController?.show()
                                            }
                                        ) {
                                            MessageBubble(
                                                message = item.message, isMine = isMine,
                                                otherUid = otherUid, currentUid = viewModel.currentUid,
                                                chatType = uiState.chatType, hapticEnabled = hapticEnabled,
                                                showSenderName = uiState.chatType != ChatType.DIRECT,
                                                voicePlayback = uiState.voicePlayback,
                                                musicPlayback = uiState.musicPlayback,
                                                musicDownloadProgress = uiState.musicDownloadProgress,
                                                onPlayVoice = { url, dur -> viewModel.playVoice(item.message.id, url, dur) },
                                                onSeekVoice = { viewModel.seekVoice(it) },
                                                onPlayAudio = { viewModel.playAudio(it) },
                                                onToggleAudioPlayback = { viewModel.toggleAudioPlayback() },
                                                onSeekAudio = { viewModel.seekAudio(it) },
                                                onCycleAudioSpeed = { viewModel.cycleAudioSpeed() },
                                                onSaveTrackToLibrary = { viewModel.saveTrackToLibrary(it) },
                                                onOpenFullscreenAudio = { viewModel.openFullscreenAudio() },
                                                onLongPressStart = { offset ->
                                                    keyboardController?.hide()
                                                    contextMenuData = ContextMenuData(item.message, isMine, offset)
                                                    dragOffset = Offset.Zero
                                                },
                                                onLongPressDrag = { delta -> dragOffset += delta },
                                                onLongPressEnd = { contextMenuData = null; dragOffset = Offset.Zero },
                                                onMediaTap = onOpenImageViewer,
                                                onAlbumTap = { imgs, idx -> lightboxImages = imgs; lightboxStartIndex = idx; showLightbox = true },
                                                onReact = { emoji -> viewModel.toggleReaction(item.message.id, emoji, item.message.parsedReactions) },
                                                onReplyClick = onScrollToMessage,
                                                onMentionClick = onMentionClick,
                                                onOpenComments = { onOpenComments(item.message.id) },
                                                chat = uiState.chat,
                                                onStickerClick = { packId, _ ->
                                                    if (!packId.isNullOrEmpty()) {
                                                        selectedStickerPack = Triple(packId, item.message.packName, item.message.packEmoji)
                                                    }
                                                },
                                                onCancelUpload = { viewModel.cancelSending(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showScrollDown) {
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                            VlFab(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                icon = Icons.Default.KeyboardArrowDown,
                                size = 44.dp
                            )
                            if (unreadCount > 0) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(unreadCount.toString()) }
                            }
                        }
                    }
                }
            }

            contextMenuData?.let { menuData ->
                MessageActionOverlay(
                    contextMenuData = menuData,
                    currentDragOffset = dragOffset,
                    isGestureMode = true,
                    canReact = canReact,
                    currentUid = viewModel.currentUid,
                    onDismiss = { contextMenuData = null; dragOffset = Offset.Zero },
                    onReply = { viewModel.setReplyTo(menuData.message); contextMenuData = null },
                    onEdit = { viewModel.startEditing(menuData.message); contextMenuData = null },
                    onDelete = { showDeleteConfirm = menuData.message.id; contextMenuData = null },
                    onCancelSending = { viewModel.cancelSending(menuData.message.id); contextMenuData = null },
                    onRetry = { viewModel.retryMessage(menuData.message.id); contextMenuData = null },
                    onSaveImage = { scope.launch { ImageCache.saveImageToGallery(context, menuData.message.url ?: "") } },
                    onSaveVoice = { /* implement save voice */ },
                    onOpenImage = { menuData.message.url?.let { onOpenImageViewer(it, menuData.message.type) } },
                    onForward = if (uiState.chat?.settings?.noForwards != true) {
                        {
                            val fwd = ForwardableMessage.fromMessage(
                                msg = menuData.message,
                                chatId = chatId,
                                chatName = uiState.chat?.name
                            )
                            forwardingMessage = fwd
                            contextMenuData = null
                        }
                    } else null,
                    onReact = { emoji -> viewModel.toggleReaction(menuData.message.id, emoji, menuData.message.parsedReactions) }
                )
            }
        }
    }

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
            hapticEnabled = hapticEnabled,
            onSpoilerToggle = { viewModel.onAlbumSpoilerToggle(it) },
            onCaptionChange = { viewModel.onAlbumCaptionChange(it) },
            onDismiss = { viewModel.dismissAlbumPreview() },
            onSend = { viewModel.sendAlbum() },
        )
    }

    if (showLightbox) AlbumLightbox(images = lightboxImages, startIndex = lightboxStartIndex, onDismiss = { showLightbox = false })

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri = uri,
            onNavigateBack = { editorUri = null },
            onSend = { editedUri, isSpoiler ->
                editorUri = null
                viewModel.sendImage(editedUri, isSpoiler = isSpoiler)
            }
        )
    }

    val singlePickedUri = uiState.singlePickedUri
    singlePickedUri?.let { uri ->
        ImageEditorScreen(
            uri = uri,
            onNavigateBack = { viewModel.clearSinglePickedUri() },
            onSend = { editedUri, isSpoiler ->
                viewModel.clearSinglePickedUri()
                viewModel.sendImage(editedUri, isSpoiler = isSpoiler)
            }
        )
    }

    if (showStickerSheet) {
        StickerPickerBottomSheet(
            onDismiss = { showStickerSheet = false },
            onStickerSelected = { packId, sticker ->
                viewModel.sendSticker(sticker = sticker, packId = packId, packName = "", packEmoji = "")
                showStickerSheet = false
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
                TextButton(onClick = { showLeaveDialog = false; viewModel.leaveChat { onNavigateBack() } }) {
                    Text(stringResource(R.string.action_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (aegisUiState.isVisible) {
        AegisAura(
            action = aegisUiState.action, emotion = aegisUiState.emotion, visorIcon = aegisUiState.visorIcon,
            message = aegisUiState.message, onDismiss = { aegisViewModel.onDismiss(it) },
            onBoop = { aegisViewModel.processIntent(LinkIntent.Boop) },
            onPet = { aegisViewModel.processIntent(LinkIntent.Pet) }
        )
    }

    forwardingMessage?.let { fwdMsg ->
        ForwardPickerDialog(
            message = fwdMsg,
            chats = uiState.availableChats,
            currentUid = viewModel.currentUid,
            onDismiss = { forwardingMessage = null },
            onForwarded = {
                forwardingMessage = null
                Toast.makeText(context, context.getString(R.string.toast_message_forwarded), Toast.LENGTH_SHORT).show()
            }
        )
    }

    selectedStickerPack?.let { (packId, name, emoji) ->
        by.iposdev.visorlink.ui.components.chat.StickerPackBottomSheet(
            packId = packId,
            fallbackPackName = name,
            fallbackPackEmoji = emoji,
            onDismiss = { selectedStickerPack = null }
        )
    }

    if (showMediaPicker) {
        VlMediaPickerSheet(
            onDismiss = {
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            },
            onOpenAudioPicker = {
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                audioPicker.launch("audio/*")
            },
            onOpenEditor = { uri ->
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                editorUri = uri
            },
            onMediaSelected = { items ->
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                if (items.isEmpty()) return@VlMediaPickerSheet
                if (items.size == 1) {
                    val item = items.first()
                    if (item.type == MediaType.VIDEO) {
                        viewModel.sendVideo(item.uri)
                    } else {
                        viewModel.sendImage(item.uri)
                    }
                } else {
                    val photosOnly = items.filter { it.type == MediaType.IMAGE }.map { it.uri }
                    if (photosOnly.isNotEmpty()) {
                        viewModel.onImagesPicked(photosOnly)
                    } else {
                        val firstVideo = items.firstOrNull { it.type == MediaType.VIDEO }
                        firstVideo?.let { viewModel.sendVideo(it.uri) }
                    }
                }
            },
            onPhotoTaken = { uri ->
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                editorUri = uri
            },
            onVideoRecorded = { uri ->
                showMediaPicker = false
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                viewModel.sendVideo(uri)
            }
        )
    }
}