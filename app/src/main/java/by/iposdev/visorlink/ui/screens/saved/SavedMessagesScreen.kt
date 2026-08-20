package by.iposdev.visorlink.ui.screens.saved

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.saved.*
import by.iposdev.visorlink.ui.screens.chat.*
import by.iposdev.visorlink.ui.components.chat.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

private fun SavedMessage.toMessage(currentUid: String, decryptedFile: File? = null): Message {
    val resolvedUrl = when {
        encrypted == true && decryptedFile != null -> Uri.fromFile(decryptedFile).toString()
        encrypted == true -> null
        else -> url
    }

    return Message(
        id         = id,
        senderId   = currentUid,
        senderUsername = "",
        type       = type,
        text       = text,
        url        = resolvedUrl,
        cdnMediaId = if (encrypted == true && decryptedFile == null) null else cdnMediaId,
        localFile  = decryptedFile,
        localBytes = localBytes,
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
}

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost    = { SnackbarHost(snackbar) },
        containerColor  = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.saved_messages_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (uiState.isEncryptionEnabled) Text(stringResource(R.string.saved_enc_active_short), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    if (uiState.settings?.pinEnabled == true) {
                        IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.lock() }) {
                            Icon(Icons.Default.Lock, stringResource(R.string.saved_pin_disable), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSettings() }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.chat_action_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            if (uiState.isUnlocked) {
                val adaptedChatUiState = ChatUiState(
                    isRecording = uiState.isRecording,
                    chatType = ChatType.DIRECT
                )

                ChatBottomBar(
                    uiState = adaptedChatUiState,
                    inputText = inputText,
                    canSendMessage = true,
                    canSendMedia = true,
                    hapticEnabled = hapticEnabled,
                    showStickerSheet = false,
                    audioPermission = audioPermission,
                    focusRequester = remember { FocusRequester() },
                    onInputChange = { inputText = it },
                    onAttach = { imagePicker.launch("image/*") },
                    onStickerClick = { },
                    onSend = {
                        val t = inputText.trim()
                        if (t.isNotBlank()) {
                            viewModel.saveText(t)
                            inputText = ""
                        }
                    },
                    onStartRecord = { viewModel.startRecording() },
                    onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                    onCancelRecord = { viewModel.cancelRecording() },
                    onSendRecord = { viewModel.stopRecordingAndSend() },
                    onClearReply = { }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            VlAmbientGlow()

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.messages.isEmpty() -> {
                    SavedEmptyPlaceholder(
                        modifier    = Modifier.fillMaxSize(),
                        isEncrypted = uiState.isEncryptionEnabled
                    )
                }
                else -> {
                    LazyColumn(
                        state         = listState,
                        reverseLayout = true,
                        modifier      = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = uiState.messages, key = { it.id }) { saved ->
                            var decryptedFile by remember(saved.id) { mutableStateOf<File?>(null) }

                            LaunchedEffect(saved) {
                                if (saved.encrypted == true && saved.cdnMediaId != null && decryptedFile == null) {
                                    val file = viewModel.getDecryptedFile(saved)
                                    if (file != null) {
                                        decryptedFile = file
                                    }
                                }
                            }

                            val msg = saved.toMessage(viewModel.currentUid, decryptedFile)

                            SwipeableMessage(
                                message       = msg,
                                isMine        = true,
                                hapticEnabled = hapticEnabled,
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
                                    onPlayVoice    = { url, dur -> viewModel.playVoice(msg.id, url, dur) },
                                    onSeekVoice    = { viewModel.seekVoice(it) },
                                    onLongPressStart = { offset ->
                                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                        actionMsg = msg
                                    },
                                    onLongPressDrag  = { },
                                    onLongPressEnd   = { },
                                    onMediaTap     = { _, _ -> },
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
