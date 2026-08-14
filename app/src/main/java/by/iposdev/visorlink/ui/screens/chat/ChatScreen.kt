package by.iposdev.visorlink.ui.screens.chat

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.aegis.AegisAura
import by.iposdev.visorlink.ui.aegis.AegisLifeViewModel
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.screens.stickers.StickerPickerBottomSheet
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.ActiveChatTracker
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenStickers: (onSelect: (Sticker) -> Unit) -> Unit,
    onOpenChatSettings: (chatId: String) -> Unit = {},
    onOpenImageViewer: (url: String, type: String) -> Unit = { _, _ -> },
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
    val focusRequester = remember { FocusRequester() }

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val aegisViewModel: AegisLifeViewModel = koinViewModel()
    val aegisUiState by aegisViewModel.uiState.collectAsState()
    val isAegisEnabled by aegisViewModel.isAegisEnabled.collectAsState()

    // Анализ сообщений для Aegis
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

    LaunchedEffect(uiState.replyingTo) {
        if (uiState.replyingTo != null && !uiState.isRecording) {
            delay(100)
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    var showStickerSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    // Контекстное меню сообщений
    var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

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

    val themeVm: ThemeViewModel = koinViewModel()
    val appTheme by themeVm.appTheme.collectAsState()
    val dynamicInput by themeVm.dynamicChatInput.collectAsState()
    val isOneUi  = appTheme == AppTheme.ONE_UI
    val isExthru = appTheme.isExthruFamily
    val isForge  = appTheme == AppTheme.FORGE
    val isDark   = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val useGestureMenu = isExthru

    val isAdmin  = uiState.myMember?.isAdmin() == true
    val isOwner  = uiState.myMember?.isOwner() == true
    val canSetWallpaper = uiState.chatType == ChatType.DIRECT || isAdmin
    val canReact = uiState.chat?.settings?.allowReactions != false
    val canSendMessage = uiState.canSendMessage
    val canSendMedia = uiState.canSendMedia

    DisposableEffect(chatId) {
        ActiveChatTracker.activeChatId = chatId
        NotificationHelper.clearNotification(context, chatId)
        onDispose { ActiveChatTracker.activeChatId = null }
    }

    val newestMessage = (uiState.messageListItems.lastOrNull() as? MessageListItem.MessageItem)?.message
    val newestMessageId = newestMessage?.id

    var isInitialLoad by remember { mutableStateOf(true) }
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }

    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showScrollDown) {
        if (!showScrollDown) unreadCount = 0
    }

    LaunchedEffect(newestMessageId) {
        if (newestMessageId == null) return@LaunchedEffect

        if (isInitialLoad) {
            isInitialLoad = false
            return@LaunchedEffect
        }

        val isMine = newestMessage?.senderId == viewModel.currentUid
        if (isMine) {
            listState.animateScrollToItem(0)
        } else if (isAtBottom) {
            if (hapticEnabled) haptic.perform(HapticType.MESSAGE_RECEIVED, hapticEnabled)
            listState.animateScrollToItem(0)
        } else {
            unreadCount++
        }
    }

    val hazeState = remember { HazeState() }
    
    val otherUser = uiState.otherUser
    val currentUser = uiState.currentUser
    val isOtherPro = otherUser?.isProActive() == true
    val applyCustom = isOtherPro && (currentUser?.ignoreCustomizations != true) && (uiState.chatType == ChatType.DIRECT)
    val cust = if (applyCustom) otherUser?.customization ?: emptyMap() else emptyMap()
    val chatBgUrl = cust["bgUrl"] as? String ?: uiState.wallpaperUrl

    UserProfileTheme(profile = otherUser, currentUser = currentUser) {
        val effectiveTheme = LocalAppThemeOverride.current ?: appTheme
        val isExthruEff = effectiveTheme.isExthruFamily
        val isOneUiEff  = effectiveTheme == AppTheme.ONE_UI
        val isForgeEff  = effectiveTheme == AppTheme.FORGE
        
        val cs = MaterialTheme.colorScheme
        val isDarkTheme = cs.surface.luminance() < 0.5f
        val style = rememberExthruStyle(effectiveTheme)
        
        val chatScaffoldBg = when {
            isExthruEff -> ExthruChat.pageBg(isDarkTheme)
            isOneUiEff  -> if (isDarkTheme) OneUiChat.PageBgDark else OneUiChat.PageBg
            else        -> cs.background
        }

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(modifier = Modifier.fillMaxSize().background(chatScaffoldBg)) {
                if (chatBgUrl != null) {
                    AsyncImage(
                        model = chatBgUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = if (isDarkTheme) 0.6f else 0.8f
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        if (isExthruEff) {
                            ExthruChatTopBar(
                                uiState = uiState, otherUid = otherUid, chatId = chatId, isDark = isDarkTheme, isForge = isForgeEff,
                                canSetWallpaper = canSetWallpaper, isAdmin = isAdmin, isOwner = isOwner,
                                hapticEnabled = hapticEnabled,
                                onWallpaperClick = { showWallpaperSheet = true },
                                onNavigateBack = onNavigateBack,
                                onOpenOtherProfile = onOpenOtherProfile,
                                onOpenChatSettings = onOpenChatSettings,
                                onLeaveClick = { showLeaveDialog = true },
                                onAegisClick = { aegisViewModel.onInteract() },
                                isAegisEnabled = isAegisEnabled
                            )
                        } else if (isOneUiEff) {
                            OneUiChatTopBar(
                                uiState = uiState, otherUid = otherUid, chatId = chatId, isDark = isDarkTheme,
                                canSetWallpaper = canSetWallpaper, isAdmin = isAdmin, isOwner = isOwner,
                                hapticEnabled = hapticEnabled,
                                onWallpaperClick = { showWallpaperSheet = true }, onNavigateBack = onNavigateBack,
                                onOpenOtherProfile = onOpenOtherProfile, onOpenChatSettings = onOpenChatSettings,
                                onLeaveClick = { showLeaveDialog = true },
                                onAegisClick = { aegisViewModel.onInteract() },
                                isAegisEnabled = isAegisEnabled
                            )
                        } else {
                            DefaultChatTopBar(
                                uiState = uiState, otherUid = otherUid, chatId = chatId,
                                canSetWallpaper = canSetWallpaper, isAdmin = isAdmin, isOwner = isOwner,
                                hapticEnabled = hapticEnabled,
                                onWallpaperClick = { showWallpaperSheet = true }, onNavigateBack = onNavigateBack,
                                onOpenOtherProfile = onOpenOtherProfile, onOpenChatSettings = onOpenChatSettings,
                                onLeaveClick = { showLeaveDialog = true },
                                onAegisClick = { aegisViewModel.onInteract() },
                                isAegisEnabled = isAegisEnabled
                            )
                        }
                    },
                    bottomBar = {
                        if (dynamicInput) {
                            DynamicChatInputBar(
                                uiState = uiState, inputText = inputText, isDark = isDarkTheme, appTheme = effectiveTheme,
                                canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                                hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                                audioPermission = audioPermission, focusRequester = focusRequester,
                                onInputChange = { inputText = it; viewModel.onTextChanged(it) },
                                onAttach = { imagePicker.launch("image/*") },
                                onStickerClick = { showStickerSheet = true },
                                onSend = { val t = inputText; inputText = ""; viewModel.sendText(t) },
                                onStartRecord = { viewModel.startRecording() },
                                onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                                onCancelRecord = { viewModel.cancelRecording() },
                                onSendRecord = { viewModel.stopRecordingAndSend() },
                                onClearReply = { viewModel.clearReply() },
                                haptic = haptic
                            )
                        } else {
                            if (isExthruEff) {
                                ExthruChatBottomBar(
                                    uiState = uiState, inputText = inputText, isDark = isDarkTheme, isForge = isForgeEff,
                                    canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                                    hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                                    audioPermission = audioPermission, focusRequester = focusRequester,
                                    onInputChange = { inputText = it; viewModel.onTextChanged(it) },
                                    onAttach = { imagePicker.launch("image/*") },
                                    onStickerClick = { showStickerSheet = true },
                                    onSend = { val t = inputText; inputText = ""; viewModel.sendText(t) },
                                    onStartRecord = { viewModel.startRecording() },
                                    onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                                    onCancel = { viewModel.cancelRecording() },
                                    onSendRecord = { viewModel.stopRecordingAndSend() },
                                    onClearReply = { viewModel.clearReply() },
                                    haptic = haptic
                                )
                            } else if (isOneUiEff) {
                                OneUiChatBottomBar(
                                    uiState = uiState, inputText = inputText, isDark = isDarkTheme,
                                    canSendMessage = canSendMessage, canSendMedia = canSendMedia,
                                    hapticEnabled = hapticEnabled, showStickerSheet = showStickerSheet,
                                    audioPermission = audioPermission, focusRequester = focusRequester,
                                    onInputChange = { inputText = it; viewModel.onTextChanged(it) },
                                    onAttach = { imagePicker.launch("image/*") },
                                    onStickerClick = { showStickerSheet = true },
                                    onSend = { val t = inputText; inputText = ""; viewModel.sendText(t) },
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
                                    audioPermission = audioPermission, focusRequester = focusRequester,
                                    onInputChange = { inputText = it; viewModel.onTextChanged(it) },
                                    onAttach = { imagePicker.launch("image/*") },
                                    onStickerClick = { showStickerSheet = true },
                                    onSend = { val t = inputText; inputText = ""; viewModel.sendText(t) },
                                    onStartRecord = { viewModel.startRecording() },
                                    onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                                    onCancel = { viewModel.cancelRecording() },
                                    onSendRecord = { viewModel.stopRecordingAndSend() },
                                    onClearReply = { viewModel.clearReply() },
                                    haptic = haptic
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                        if (uiState.wallpaperUrl != null && !applyCustom) {
                            AsyncImage(model = uiState.wallpaperUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(), alpha = if (isDarkTheme) 0.35f else 0.7f)
                        } else if (!applyCustom) {
                            VlAmbientGlow(appTheme = effectiveTheme)
                        }

                        if (uiState.messageListItems.isEmpty() && !uiState.isLoadingMore) {
                            EmptyChatPlaceholder(modifier = Modifier.fillMaxSize(), isExthru = isExthruEff, isForge = isForgeEff, isDark = isDarkTheme)
                        } else {
                            val listBg = when {
                                (uiState.wallpaperUrl != null || applyCustom) -> Modifier
                                isExthruEff -> Modifier.background(ExthruChat.pageBg(isDarkTheme))
                                isOneUiEff  -> Modifier.background(if (isDarkTheme) OneUiChat.PageBgDark else OneUiChat.PageBg)
                                else        -> Modifier
                            }
                            LazyColumn(
                                state = listState, reverseLayout = true,
                                userScrollEnabled = contextMenuData == null,
                                modifier = Modifier.fillMaxSize().then(listBg),
                                contentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding() + 12.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 12.dp
                                ),
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
                                        LaunchedEffect(index) {
                                            viewModel.loadMore()
                                        }
                                    }

                                    when (item) {
                                        is MessageListItem.DateHeader -> DateSeparator(
                                            item.label, isOneUiEff, isExthruEff, isForgeEff, isDarkTheme, (uiState.wallpaperUrl != null || applyCustom))
                                        is MessageListItem.MessageItem -> {
                                            val isMine = item.message.senderId == viewModel.currentUid
                                            SwipeableMessage(
                                                message = item.message, isMine = isMine,
                                                hapticEnabled = hapticEnabled,
                                                isOneUi = isOneUiEff, isExthru = isExthruEff, isDark = isDarkTheme,
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
                                                    isOneUi = isOneUiEff, isExthru = isExthruEff, isForge = isForgeEff, isDark = isDarkTheme,
                                                    hasWallpaper = (uiState.wallpaperUrl != null || applyCustom),
                                                    onPlayVoice = { url, dur -> viewModel.playVoice(item.message.id, url, dur) },
                                                    onSeekVoice = { viewModel.seekVoice(it) },
                                                    onLongPressStart = { offset ->
                                                        contextMenuData = ContextMenuData(item.message, isMine, offset)
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onLongPressDrag = { delta -> dragOffset += delta },
                                                    onLongPressEnd = {
                                                        contextMenuData = null
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onMediaTap = onOpenImageViewer,
                                                    onAlbumTap = { imgs, idx -> lightboxImages = imgs; lightboxStartIndex = idx; showLightbox = true },
                                                    onReact = { emoji -> viewModel.toggleReaction(item.message.id, emoji, item.message.parsedReactions) },
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

                        // Top overlays
                        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = innerPadding.calculateTopPadding() + 16.dp)) {
                            AnimatedVisibility(
                                visible = uiState.showUnofficialClientWarning,
                                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(R.string.chat_client_unsafe_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.dismissUnofficialWarning() }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

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
                                    shape = if (isForge) RectangleShape else CircleShape,
                                    color = if (isOneUi && isDark) OneUiChat.CardBgDark else MaterialTheme.colorScheme.surface,
                                    shadowElevation = if (isForge) 0.dp else 4.dp, modifier = Modifier.size(36.dp),
                                    border = if (isForge) BorderStroke(2.dp, if(isDark) Color(0xFF333333) else Color.Black) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = indicatorColor)
                                    }
                                }
                            }
                        }

                        // ── КРАСИВАЯ КНОПКА СКРОЛЛА ВНИЗ ──
                        AnimatedVisibility(
                            visible = showScrollDown,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp),
                            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200)),
                            exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                        ) {
                            if (isExthru) {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "fab_scale")
                                val shape = if (isForge) RectangleShape else CircleShape

                                val shadowMod = if (isForge) {
                                    Modifier.forgeNeuBrutalism(isPressed, isDark, 4.dp)
                                } else if (isPressed) {
                                    Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
                                } else {
                                    Modifier.exthruSmallRaisedShadow(isDark)
                                }

                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .scale(if(isForge) 1f else scale)
                                            .then(shadowMod)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
                                            .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                            .clip(shape)
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                scope.launch { listState.animateScrollToItem(0) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)
                                                .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                                .background(Color.Red, if (isForge) RectangleShape else CircleShape).padding(horizontal = 4.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                val fabColor = when {
                                    isOneUi  -> if (isDark) Color(0xFF4D90F0) else Color(0xFF1259C3)
                                    else     -> MaterialTheme.colorScheme.primary
                                }
                                Box {
                                    FloatingActionButton(
                                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                        modifier = Modifier.size(44.dp),
                                        containerColor = fabColor,
                                        contentColor = Color.White,
                                        shape = CircleShape,
                                        elevation = FloatingActionButtonDefaults.elevation()
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, null)
                                    }
                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)
                                                .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                                .background(Color.Red, CircleShape).padding(horizontal = 4.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Оверлей меню сообщения (поверх всего Scaffold) ───
            contextMenuData?.let { menuData ->
                MessageActionOverlay(
                    contextMenuData = menuData,
                    currentDragOffset = dragOffset,
                    isGestureMode = useGestureMenu,
                    canReact = canReact,
                    currentUid = viewModel.currentUid,
                    onDismiss = {
                        contextMenuData = null
                        dragOffset = Offset.Zero
                    },
                    onReply = {
                        viewModel.setReplyTo(menuData.message)
                        contextMenuData = null
                    },
                    onDelete = {
                        showDeleteConfirm = menuData.message.id
                        contextMenuData = null
                    },
                    onCancelSending = {
                        viewModel.cancelSending(menuData.message.id)
                        contextMenuData = null
                    },
                    onSaveImage = {
                        scope.launch {
                            val success = saveImageToGallery(context, menuData.message.url ?: "")
                            Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveVoice = {
                        scope.launch {
                            val success = saveVoiceToDownloads(context, menuData.message.url ?: "")
                            Toast.makeText(context, if (success) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenImage = { menuData.message.url?.let { onOpenImageViewer(it, menuData.message.type) } },
                    onForward = null, // В разработке
                    onReact = { emoji ->
                        viewModel.toggleReaction(menuData.message.id, emoji, menuData.message.parsedReactions)
                    }
                )
            }
        }
    }

    if (showWallpaperSheet) {
        WallpaperBottomSheet(
            hasWallpaper = uiState.wallpaperUrl != null,
            isGroupOrChannel = uiState.chatType != ChatType.DIRECT,
            isExthru = isExthru,
            isForge = isForge,
            isDark = isDark,
            onDismiss = { showWallpaperSheet = false },
            onPickWallpaper = { showWallpaperSheet = false; wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = { showWallpaperSheet = false; viewModel.removeWallpaper() },
        )
    }

    if (uiState.showAlbumPreview) {
        AlbumPreviewSheet(
            images = uiState.albumDraft, caption = uiState.albumCaption,
            isExthru = isExthru, isForge = isForge, isDark = isDark,
            hapticEnabled = hapticEnabled,
            onSpoilerToggle = { viewModel.onAlbumSpoilerToggle(it) },
            onCaptionChange = { viewModel.onAlbumCaptionChange(it) },
            onDismiss = { viewModel.dismissAlbumPreview() },
            onSend = { viewModel.sendAlbum() },
        )
    }

    if (showLightbox && lightboxImages.isNotEmpty()) {
        AlbumLightbox(images = lightboxImages, startIndex = lightboxStartIndex, onDismiss = { showLightbox = false })
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
            shape = if (isForge) RectangleShape else AlertDialogDefaults.shape
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text(if (uiState.chatType == ChatType.CHANNEL) stringResource(R.string.dialog_leave_channel_title) else stringResource(R.string.dialog_leave_group_title))
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
            shape = if (isForge) RectangleShape else AlertDialogDefaults.shape
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { delay(3000); viewModel.clearError() }
    }

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri            = uri,
            onNavigateBack = { editorUri = null },
            onSend         = { editedUri, isSpoiler -> editorUri = null; viewModel.sendImage(editedUri, isSpoiler) },
        )
    }

    // Aegis Project Overlay
    if (aegisUiState.isVisible) {
        AegisAura(
            action = aegisUiState.action,
            emotion = aegisUiState.emotion,
            visorIcon = aegisUiState.visorIcon,
            message = aegisUiState.message,
            onDismiss = { wasOffended -> aegisViewModel.onDismiss(wasOffended) },
            onBoop = { aegisViewModel.processIntent(by.iposdev.visorlink.data.model.aegis.LinkIntent.Boop) },
            onPet = { aegisViewModel.processIntent(by.iposdev.visorlink.data.model.aegis.LinkIntent.Pet) }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
//  Вспомогательные Bottom Sheets для ChatScreen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperBottomSheet(
    hasWallpaper: Boolean,
    isGroupOrChannel: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = if (isForge) RectangleShape else BottomSheetDefaults.ExpandedShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            Text(
                text = "Обои чата",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (isGroupOrChannel) {
                Text(
                    text = "Применяются для всех участников чата.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                )
            }

            val itemMod = if (isForge) {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, RectangleShape).background(MaterialTheme.colorScheme.surface)
            } else if (isExthru) {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).nmInsetShadow(isDark, cornerRadius = 16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            } else Modifier

            ListItem(
                headlineContent = { Text("Выбрать из галереи") },
                leadingContent = { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = itemMod.clickable { onPickWallpaper() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (hasWallpaper) {
                ListItem(
                    headlineContent = { Text("Удалить обои", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = itemMod.clickable { onRemoveWallpaper() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onSpoilerToggle: (Int) -> Unit,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    val haptic = rememberHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = if (isForge) RectangleShape else BottomSheetDefaults.ExpandedShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                IconButton(onClick = { haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled); onSend() }, enabled = images.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send),
                        tint = if (images.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(images) { index, item ->
                    AlbumThumbnailCell(
                        uri = item.uri,
                        spoiler = item.spoiler,
                        isForge = isForge,
                        onToggleSpoiler = { haptic.perform(HapticType.SELECTION, hapticEnabled); onSpoilerToggle(index) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val tfShape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)
            val tfModifier = if (isForge) {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).forgeNeuBrutalism(false, isDark, 2.dp).background(MaterialTheme.colorScheme.surface)
            } else if (isExthru) {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
                    .background(MaterialTheme.colorScheme.surface, tfShape)
            } else Modifier.fillMaxWidth().padding(horizontal = 16.dp)

            OutlinedTextField(
                value = caption,
                onValueChange = { onCaptionChange(it) },
                modifier = tfModifier,
                placeholder = { Text("Добавить подпись…") },
                maxLines = 3,
                shape = tfShape,
                colors = if (isExthru) OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                ) else OutlinedTextFieldDefaults.colors(),
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

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed && images.isNotEmpty()) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "btn_scale")

            val btnShape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)
            val btnMod = if (isExthru) {
                val shadow = if (isForge) Modifier.forgeNeuBrutalism(isPressed && images.isNotEmpty(), isDark, 4.dp) else if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.8f else 0.5f) else Modifier.exthruSmallRaisedShadow(isDark)
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).scale(if (isForge) 1f else scale).then(shadow)
                    .background(MaterialTheme.colorScheme.primary, btnShape).then(if (isForge) Modifier else Modifier.border(1.dp, if(isPressed) Color.Transparent else Color.White.copy(0.1f), btnShape)).clip(btnShape)
                    .clickable(interactionSource = interactionSource, indication = null, enabled = images.isNotEmpty()) {
                        haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                        onSend()
                    }
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).scale(scale)
            }

            if (isExthru) {
                Box(modifier = btnMod, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить ${images.size} фото", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled); onSend() },
                    modifier = btnMod,
                    shape = RoundedCornerShape(16.dp),
                    enabled = images.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отправить ${images.size} фото")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AlbumThumbnailCell(uri: Uri, spoiler: Boolean, isForge: Boolean, onToggleSpoiler: () -> Unit) {
    val blurRadius by animateDpAsState(targetValue = if (spoiler) 12.dp else 0.dp, animationSpec = tween(200), label = "thumb_blur")
    Box(modifier = Modifier.size(140.dp).clip(if (isForge) RectangleShape else RoundedCornerShape(12.dp))) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        )
        if (spoiler) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("SPOILER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(if(isForge) RectangleShape else CircleShape)
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

@Composable
fun EmptyChatPlaceholder(modifier: Modifier = Modifier, isExthru: Boolean = false, isForge: Boolean = false, isDark: Boolean = false) {
    val emptyTransition = rememberInfiniteTransition(label = "empty")
    val emptyScale by emptyTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty_scale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp).scale(emptyScale), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.chat_empty_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumLightbox(images: List<AlbumImage>, startIndex: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val img = images[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = img.url, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offsetX; translationY = offsetY
                        }
                    )
                }
            }

            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text("${pagerState.currentPage + 1} / ${images.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 8.dp).size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }

            if (images.size > 1) {
                val stripListState = rememberLazyListState()
                LaunchedEffect(pagerState.currentPage) { stripListState.animateScrollToItem(pagerState.currentPage) }
                LazyRow(
                    state = stripListState,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(images) { idx, img ->
                        val isActive = idx == pagerState.currentPage
                        val scope = rememberCoroutineScope()
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
                                .border(width = if (isActive) 2.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(4.dp))
                                .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                        ) {
                            AsyncImage(model = img.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingBar(
    isExthru: Boolean = false,
    isForge: Boolean = false,
    isDark: Boolean = false,
    hapticEnabled: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha",
    )

    val timerColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsed++; haptic.perform(HapticType.CLICK, hapticEnabled) }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isExthru) {
            val intCancel = remember { MutableInteractionSource() }
            val isCancelPressed by intCancel.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isCancelPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "")

            val shape = if (isForge) RectangleShape else CircleShape
            val shadowMod = if (isForge) {
                Modifier.forgeNeuBrutalism(isCancelPressed, isDark, 3.dp)
            } else if (isCancelPressed) {
                Modifier.nmInsetShadow(isDark, cornerRadius = 24.dp)
            } else {
                Modifier.exthruSmallRaisedShadow(isDark)
            }

            Box(
                modifier = Modifier.size(48.dp).scale(if(isForge) 1f else scale).then(shadowMod).background(MaterialTheme.colorScheme.surface, shape).clip(shape)
                    .then(if(isForge) Modifier else Modifier.border(1.dp, if(isCancelPressed) Color.Transparent else Color.White.copy(0.1f), shape))
                    .clickable(interactionSource = intCancel, indication = null) { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error) }
        } else {
            IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() }) {
                Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), if(isForge) RectangleShape else CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium, color = timerColor)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_recording_label), style = MaterialTheme.typography.bodySmall, color = labelColor)
        }

        if (isExthru) {
            val intSend = remember { MutableInteractionSource() }
            val isSendPressed by intSend.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isSendPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "")

            val shape = if (isForge) RectangleShape else CircleShape
            val shadowMod = if (isForge) {
                Modifier.forgeNeuBrutalism(isSendPressed, isDark, 3.dp)
            } else if (isSendPressed) {
                Modifier.nmInsetShadow(isDark, cornerRadius = 24.dp, darkAlpha = if(isDark)0.8f else 0.5f)
            } else {
                Modifier.exthruSmallRaisedShadow(isDark)
            }

            Box(
                modifier = Modifier.size(48.dp).scale(if(isForge) 1f else scale).then(shadowMod)
                    .background(Brush.radialGradient(listOf(Biolume.TealLight, Biolume.TealPulse)), shape).clip(shape)
                    .then(if(isForge) Modifier else Modifier.border(1.dp, if(isSendPressed) Color.Transparent else Color.White.copy(0.1f), shape))
                    .clickable(interactionSource = intSend, indication = null) { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        } else {
            IconButton(
                onClick = { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
