package by.iposdev.visorlink.ui.screens.saved

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.screens.chat.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticHelper
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private fun SavedMessage.toMessage(currentUid: String): Message = Message(
    id         = id,
    senderId   = currentUid,
    senderUsername = "",
    type       = type,
    text       = text,
    url        = url,
    fileName   = fileName,
    duration   = duration,
    caption    = caption,
    images     = emptyList(),
    spoiler    = spoiler,
    stickerId  = stickerId,
    packId     = packId,
    packName   = packName,
    packEmoji  = packEmoji,
    deleted    = deleted,
    deletedAt  = deletedAt,
    createdAt  = createdAt,
    forwardFrom = forwardFrom,
    tg_forwarded = tg_forwarded,
    tg_forwarded_from = tg_forwarded_from,
    tg_forwarded_from_fallback = tg_forwarded_from_fallback,
    isUnofficialClient = isUnofficialClient,
    readBy     = emptyList(),
    reactions  = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SavedMessagesScreen(
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val viewModel: SavedMessagesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val haptic   = rememberHaptic()

    val themeVm: ThemeViewModel = koinViewModel()
    val appTheme by themeVm.appTheme.collectAsState()
    val dynamicInput by themeVm.dynamicChatInput.collectAsState()
    val isOneUi  = appTheme == AppTheme.ONE_UI
    val isExthru = appTheme == AppTheme.EXTHRU || appTheme == AppTheme.BIOLUME
    val isDark   = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val listState   = rememberLazyListState()
    val snackbar    = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var editorUri by remember { mutableStateOf<Uri?>(null) }
    var actionMsg by remember { mutableStateOf<Message?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) editorUri = uri
    }

    var autoBioTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.showPinInput) {
        if (uiState.showPinInput && !autoBioTriggered && viewModel.hasBiometricPinSaved()) {
            autoBioTriggered = true
            (context as? FragmentActivity)?.let { activity ->
                viewModel.launchBiometricUnlock(activity) {}
            }
        } else if (!uiState.showPinInput) {
            autoBioTriggered = false
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.lockIfConfigured() } }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            snackbar.showSnackbar(err, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    if (uiState.showPinInput) {
        PinInputDialog(
            pinError     = uiState.pinError,
            hasBiometric = viewModel.hasBiometricPinSaved(),
            onPinEntered = { pin, useBio -> viewModel.onPinEntered(pin, useBio) },
            onDismiss    = onNavigateBack,
            onBiometric  = {
                (context as? FragmentActivity)?.let { activity ->
                    viewModel.launchBiometricUnlock(activity) {}
                }
            },
            clearError   = { viewModel.clearPinError() }
        )
        return
    }

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize().background(scaffoldBg)) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                VlAmbientGlow(appTheme = appTheme)

                Scaffold(
                    snackbarHost    = { SnackbarHost(snackbar) },
                    containerColor  = Color.Transparent,
                    topBar = {
                        SavedTopBar(
                            isExthru        = isExthru,
                            isOneUi         = isOneUi,
                            isDark          = isDark,
                            hapticEnabled   = hapticEnabled,
                            isEncrypted     = uiState.isEncryptionEnabled,
                            isPinEnabled    = uiState.settings?.pinEnabled == true,
                            onNavigateBack  = onNavigateBack,
                            onLock          = { viewModel.lock() },
                            onOpenSettings  = onOpenSettings
                        )
                    },
                    bottomBar = {
                        if (uiState.isUnlocked) {
                            SavedBottomBar(
                                inputText      = inputText,
                                appTheme       = appTheme,
                                isExthru       = isExthru,
                                isOneUi        = isOneUi,
                                isDark         = isDark,
                                isEncrypted    = uiState.isEncryptionEnabled,
                                isRecording    = uiState.isRecording,
                                hapticEnabled  = hapticEnabled,
                                audioPermission = audioPermission,
                                dynamicInput   = dynamicInput,
                                haptic         = haptic,
                                onTextChange   = { inputText = it },
                                onPickImage    = { imagePicker.launch("image/*") },
                                onSend         = {
                                    val t = inputText.trim()
                                    if (t.isNotBlank()) {
                                        viewModel.saveText(t)
                                        inputText = ""
                                    }
                                },
                                onStartRecord  = { viewModel.startRecording() },
                                onSendRecord   = { viewModel.stopRecordingAndSend() },
                                onCancelRecord = { viewModel.cancelRecording() },
                                onRequestAudioPerm = { audioPermission.launchPermissionRequest() }
                            )
                        }
                    }
                ) { padding ->
                    when {
                        uiState.isLoading -> {
                            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.messages.isEmpty() -> {
                            SavedEmptyPlaceholder(
                                modifier    = Modifier.fillMaxSize().padding(padding),
                                isEncrypted = uiState.isEncryptionEnabled,
                                isExthru    = isExthru,
                                isDark      = isDark
                            )
                        }
                        else -> {
                            LazyColumn(
                                state         = listState,
                                reverseLayout = true,
                                modifier      = Modifier.fillMaxSize().padding(padding),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(items = uiState.messages.asReversed(), key = { it.id }) { saved ->
                                    val msg = saved.toMessage(viewModel.currentUid)
                                    SwipeableMessage(
                                        message       = msg,
                                        isMine        = true,
                                        hapticEnabled = hapticEnabled,
                                        isOneUi       = isOneUi,
                                        isExthru      = isExthru,
                                        isDark        = isDark,
                                        onReply       = { }
                                    ) {
                                        MessageBubble(
                                            message        = msg,
                                            isMine         = true,
                                            otherUid       = viewModel.currentUid,
                                            currentUid     = viewModel.currentUid,
                                            chatType       = ChatType.DIRECT,
                                            hapticEnabled  = hapticEnabled,
                                            showSenderName = false,
                                            voicePlayback  = uiState.voicePlayback,
                                            isOneUi        = isOneUi,
                                            isExthru       = isExthru,
                                            isDark         = isDark,
                                            hasWallpaper   = false,
                                            onPlayVoice    = { url, dur -> viewModel.playVoice(msg.id, url, dur) },
                                            onSeekVoice    = { viewModel.seekVoice(it) },
                                            onLongPressStart = { offset ->
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                actionMsg = msg
                                            },
                                            onLongPressDrag  = { },
                                            onLongPressEnd   = { },
                                            onImageTap     = { },
                                            onAlbumTap     = { _, _ -> },
                                            onReact        = { },
                                            onReplyClick   = { },
                                            onMentionClick = { },
                                            chat           = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionMsg?.let { msg ->
        SavedMessageActionSheet(
            onDismiss = { actionMsg = null },
            onDelete  = {
                viewModel.deleteMessage(msg.id)
                actionMsg = null
            }
        )
    }

    editorUri?.let { uri ->
        ImageEditorScreen(
            uri            = uri,
            onNavigateBack = { editorUri = null },
            onSend         = { editedUri, isSpoiler ->
                editorUri = null
                viewModel.saveImage(editedUri, isSpoiler = isSpoiler)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI КОМПОНЕНТЫ И ЭКРАНЫ ВНУТРИ
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedTopBar(
    isExthru: Boolean, isOneUi: Boolean, isDark: Boolean, hapticEnabled: Boolean, isEncrypted: Boolean,
    isPinEnabled: Boolean, onNavigateBack: () -> Unit, onLock: () -> Unit, onOpenSettings: () -> Unit
) {
    val haptic = rememberHaptic()
    val hazeState = LocalHazeState.current

    if (isExthru) {
        TopAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .hazeEffect(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)),
            navigationIcon = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "back_scale")
                val shadowMod = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 4.dp)
                        .size(42.dp)
                        .scale(scale)
                        .then(shadowMod)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                        .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            onNavigateBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Избранное", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                        if (isEncrypted) Text("🔐 Зашифровано", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            actions = {
                val interactionSourceSettings = remember { MutableInteractionSource() }
                val isPressedSettings by interactionSourceSettings.collectIsPressedAsState()
                val scaleSettings by animateFloatAsState(if (isPressedSettings) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "set_scale")
                val shadowModSettings = if (isPressedSettings) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

                if (isPinEnabled) {
                    val interactionSourceLock = remember { MutableInteractionSource() }
                    val isPressedLock by interactionSourceLock.collectIsPressedAsState()
                    val scaleLock by animateFloatAsState(if (isPressedLock) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "lock_scale")
                    val shadowModLock = if (isPressedLock) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(scaleLock)
                            .then(shadowModLock)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                            .border(1.dp, if (isPressedLock) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
                            .clip(CircleShape)
                            .clickable(interactionSource = interactionSourceLock, indication = null) {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onLock()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, "Lock", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }

                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(42.dp)
                        .scale(scaleSettings)
                        .then(shadowModSettings)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                        .border(1.dp, if (isPressedSettings) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSourceSettings, indication = null) {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
        )
    } else {
        val containerColor = if (isOneUi) (if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar) else MaterialTheme.colorScheme.surface
        val titleColor = if (isOneUi) (if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary) else MaterialTheme.colorScheme.onSurface
        val accentColor = if (isOneUi) (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue) else MaterialTheme.colorScheme.primary

        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = titleColor)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Избранное", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = titleColor)
                        if (isEncrypted) Text("🔐 Зашифровано", fontSize = 10.sp, color = accentColor)
                    }
                }
            },
            actions = {
                if (isPinEnabled) IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onLock() }) { Icon(Icons.Default.Lock, "Lock", tint = accentColor) }
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSettings() }) { Icon(Icons.Default.Settings, "Settings", tint = titleColor) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor)
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SavedBottomBar(
    inputText: String, appTheme: AppTheme, isExthru: Boolean, isOneUi: Boolean, isDark: Boolean, isEncrypted: Boolean,
    isRecording: Boolean, hapticEnabled: Boolean, audioPermission: com.google.accompanist.permissions.PermissionState,
    dynamicInput: Boolean, haptic: HapticHelper, onTextChange: (String) -> Unit, onPickImage: () -> Unit, onSend: () -> Unit,
    onStartRecord: () -> Unit, onSendRecord: () -> Unit, onCancelRecord: () -> Unit, onRequestAudioPerm: () -> Unit
) {
    val syncedState = remember(isRecording) { ChatUiState(isRecording = isRecording, isCooldown = false, isUploading = false) }

    if (dynamicInput) {
        DynamicChatInputBar(
            uiState = syncedState, inputText = inputText, isDark = isDark, appTheme = appTheme,
            canSendMessage = true, canSendMedia = true, hapticEnabled = hapticEnabled, showStickerSheet = false, audioPermission = audioPermission,
            onInputChange = onTextChange, onAttach = onPickImage, onStickerClick = { }, onSend = onSend,
            onStartRecord = onStartRecord, onRequestAudioPerm = onRequestAudioPerm, onCancelRecord = onCancelRecord,
            onSendRecord = onSendRecord, onClearReply = { }, haptic = haptic
        )
    } else {
        when {
            isExthru -> ExthruChatBottomBar(
                uiState = syncedState, inputText = inputText, isDark = isDark, canSendMessage = true, canSendMedia = true,
                hapticEnabled = hapticEnabled, showStickerSheet = false, audioPermission = audioPermission,
                onInputChange = onTextChange, onAttach = onPickImage, onStickerClick = { }, onSend = onSend,
                onStartRecord = onStartRecord, onRequestAudioPerm = onRequestAudioPerm, onCancel = onCancelRecord,
                onSendRecord = onSendRecord, onClearReply = { }, haptic = haptic
            )
            isOneUi -> OneUiChatBottomBar(
                uiState = syncedState, inputText = inputText, isDark = isDark, canSendMessage = true, canSendMedia = true,
                hapticEnabled = hapticEnabled, showStickerSheet = false, audioPermission = audioPermission,
                onInputChange = onTextChange, onAttach = onPickImage, onStickerClick = { }, onSend = onSend,
                onStartRecord = onStartRecord, onRequestAudioPerm = onRequestAudioPerm, onCancel = onCancelRecord,
                onSendRecord = onSendRecord, onClearReply = { }, haptic = haptic
            )
            else -> DefaultChatBottomBar(
                uiState = syncedState, inputText = inputText, canSendMessage = true, canSendMedia = true,
                hapticEnabled = hapticEnabled, showStickerSheet = false, audioPermission = audioPermission,
                onInputChange = onTextChange, onAttach = onPickImage, onStickerClick = { }, onSend = onSend,
                onStartRecord = onStartRecord, onRequestAudioPerm = onRequestAudioPerm, onCancel = onCancelRecord,
                onSendRecord = onSendRecord, onClearReply = { }, haptic = haptic
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMessageActionSheet(onDismiss: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            ListItem(
                headlineContent = { Text("Удалить из Избранного", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun SavedEmptyPlaceholder(modifier: Modifier, isEncrypted: Boolean, isExthru: Boolean, isDark: Boolean) {
    val accentColor = if (isExthru) ExthruChat.Accent else MaterialTheme.colorScheme.primary
    val textColor = if (isExthru) ExthruChat.textPrimary(isDark) else MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "empty_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (isExthru) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(breathScale)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.6f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(breathScale)
                            .background(accentColor.copy(alpha = 0.2f), CircleShape)
                    )
                }
                Text("⭐", fontSize = 48.sp)
            }
            Text("Здесь будут ваши сохранённые сообщения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
            if (isEncrypted) {
                Surface(shape = RoundedCornerShape(16.dp), color = accentColor.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Тексты зашифрованы на устройстве", fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ДИАЛОГ ВВОДА PIN-КОДА
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PinInputDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    onPinEntered: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBiometric: () -> Unit,
    clearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Введите PIN-код") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            clearError()
                        }
                    },
                    label = { Text("PIN (от 4 до 8 цифр)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    singleLine = true
                )
                if (pinError) {
                    Text("Неверный PIN-код", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                if (!hasBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { useBiometrics = !useBiometrics }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
                        Text("Разрешить вход по биометрии", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPinEntered(pin, useBiometrics) },
                enabled = pin.length in 4..8
            ) { Text("Разблокировать") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasBiometric) {
                    IconButton(onClick = onBiometric) {
                        Icon(Icons.Default.Fingerprint, tint = MaterialTheme.colorScheme.primary, contentDescription = "Биометрия")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}