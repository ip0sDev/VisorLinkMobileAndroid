package by.iposdev.visorlink.ui.screens.saved

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import by.iposdev.visorlink.ui.screens.chat.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticHelper
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
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
    val isOneUi  = appTheme == AppTheme.ONE_UI
    val isExthru = appTheme == AppTheme.EXTHRU
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

    // Автоматический запуск биометрии, если экран блокировки активен
    var autoBioTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.showPinInput) {
        if (uiState.showPinInput && !autoBioTriggered && viewModel.hasBiometricPinSaved()) {
            autoBioTriggered = true
            (context as? FragmentActivity)?.let { activity ->
                viewModel.launchBiometricUnlock(activity) {}
            }
        } else if (!uiState.showPinInput) {
            autoBioTriggered = false // сброс флага, если мы разблокировали чат
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

    val scaffoldBg = when {
        isExthru -> ExthruChat.pageBg(isDark)
        isOneUi  -> if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg
        else     -> MaterialTheme.colorScheme.background
    }

    Scaffold(
        snackbarHost    = { SnackbarHost(snackbar) },
        containerColor  = scaffoldBg,
        topBar = {
            SavedTopBar(
                isExthru        = isExthru,
                isOneUi         = isOneUi,
                isDark          = isDark,
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
                    isExthru       = isExthru,
                    isOneUi        = isOneUi,
                    isDark         = isDark,
                    isEncrypted    = uiState.isEncryptionEnabled,
                    isRecording    = uiState.isRecording,
                    hapticEnabled  = hapticEnabled,
                    audioPermission = audioPermission,
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
        val listBg = when {
            isExthru -> Modifier.background(ExthruChat.pageBg(isDark))
            isOneUi  -> Modifier.background(if (isDark) OneUiChat.PageBgDark else OneUiChat.PageBg)
            else     -> Modifier
        }

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
                    modifier      = Modifier.fillMaxSize().padding(padding).then(listBg),
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
                                onLongPress    = {
                                    haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                    actionMsg = msg
                                },
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
    isExthru: Boolean, isOneUi: Boolean, isDark: Boolean, isEncrypted: Boolean,
    isPinEnabled: Boolean, onNavigateBack: () -> Unit, onLock: () -> Unit, onOpenSettings: () -> Unit
) {
    val containerColor = when {
        isExthru -> ExthruChat.barBg(isDark)
        isOneUi  -> if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
        else     -> MaterialTheme.colorScheme.surface
    }
    val titleColor = when {
        isExthru -> ExthruChat.textPrimary(isDark)
        isOneUi  -> if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary
        else     -> MaterialTheme.colorScheme.onSurface
    }
    val accentColor = when {
        isExthru -> ExthruChat.Accent
        isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
        else     -> MaterialTheme.colorScheme.primary
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                if (isExthru) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ExthruChat.barBg(isDark)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = titleColor)
                }
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
            if (isPinEnabled) IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock", tint = accentColor) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings", tint = if (isExthru) ExthruChat.textSecondary(isDark) else titleColor) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor)
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SavedBottomBar(
    inputText: String, isExthru: Boolean, isOneUi: Boolean, isDark: Boolean, isEncrypted: Boolean,
    isRecording: Boolean, hapticEnabled: Boolean, audioPermission: com.google.accompanist.permissions.PermissionState,
    haptic: HapticHelper, onTextChange: (String) -> Unit, onPickImage: () -> Unit, onSend: () -> Unit,
    onStartRecord: () -> Unit, onSendRecord: () -> Unit, onCancelRecord: () -> Unit, onRequestAudioPerm: () -> Unit
) {
    val syncedState = remember(isRecording) { ChatUiState(isRecording = isRecording, isCooldown = false, isUploading = false) }

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
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⭐", fontSize = 56.sp, modifier = Modifier.clip(CircleShape).background(accentColor.copy(alpha = 0.12f)).padding(20.dp))
            Text("Здесь будут ваши сохранённые сообщения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
            if (isEncrypted) {
                Surface(shape = RoundedCornerShape(12.dp), color = accentColor.copy(alpha = 0.1f), modifier = Modifier.padding(top = 16.dp, start = 32.dp, end = 32.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Тексты зашифрованы на устройстве", fontSize = 12.sp, color = accentColor)
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
                        // Разрешаем вводить только цифры и до 8 символов
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

                // Если биометрия еще не настроена, показываем чекбокс
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
                        Checkbox(
                            checked = useBiometrics,
                            onCheckedChange = { useBiometrics = it }
                        )
                        Text("Разрешить вход по биометрии", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPinEntered(pin, useBiometrics) },
                enabled = pin.length in 4..8
            ) {
                Text("Разблокировать")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Если биометрия настроена, показываем кнопку отпечатка внизу
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