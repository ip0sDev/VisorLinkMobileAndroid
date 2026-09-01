package by.iposdev.visorlink.ui.screens.comments

import android.Manifest
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.chat.*
import by.iposdev.visorlink.ui.screens.chat.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun Comment.toMessage(): Message = Message(
    id = id,
    senderId = senderId,
    senderUsername = senderUsername,
    type = type,
    text = text,
    url = url,
    fileName = fileName,
    duration = duration,
    spoiler = spoiler,
    createdAt = createdAt,
    deleted = deleted,
    deletedAt = deletedAt,
    reactions = reactions,
    replyTo = replyTo?.let { mapOf("id" to it.id, "type" to it.type, "text" to it.text, "url" to it.url, "senderUsername" to it.senderUsername) },
    status = status
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CommentsScreen(
    chatId: String,
    messageId: String,
    channel: Chat?,
    onNavigateBack: () -> Unit,
    onOpenImageViewer: (url: String, type: String) -> Unit = { _, _ -> },
    hapticEnabled: Boolean = true
) {
    val viewModel: CommentsViewModel = koinViewModel(parameters = { parametersOf(chatId, messageId) })
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { editorUri = it }
    }

    val commentsAllowed = uiState.commentsAllowed(channel)
    val focusRequester = remember { FocusRequester() }

    val allComments = remember(uiState.comments, uiState.tempComments) {
        (uiState.comments + uiState.tempComments).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
    }

    val commentCount = uiState.comments.size
    LaunchedEffect(commentCount) {
        if (commentCount > 0) listState.scrollToItem(commentCount - 1)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val count = uiState.commentCount
                        if (count > 0) {
                            Text("$count comment${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                },
                actions = {
                    if (uiState.isAdmin) {
                        val post = uiState.post
                        val enabled = post?.commentsEnabled != false
                        
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            viewModel.toggleComments(enabled)
                        }) {
                            Icon(
                                imageVector = if (enabled) Icons.Default.SpeakerNotesOff else Icons.Default.SpeakerNotes,
                                contentDescription = if (enabled) "Disable comments" else "Enable comments",
                                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            val adaptedChatUiState = ChatUiState(
                replyingTo = uiState.replyingTo?.let { it.toMessage() },
                isUploading = uiState.isUploading,
                isRecording = uiState.isRecording,
                myMember = uiState.myMember,
                chatType = ChatType.CHANNEL
            )

            ChatBottomBar(
                uiState = adaptedChatUiState,
                inputText = inputText,
                canSendMessage = commentsAllowed,
                canSendMedia = commentsAllowed,
                canSendStickers = false,
                hapticEnabled = hapticEnabled,
                showStickerSheet = false,
                audioPermission = audioPermission,
                focusRequester = focusRequester,
                onInputChange = { inputText = it },
                onAttach = { imagePicker.launch("image/*") },
                onStickerClick = { },
                onSend = { val t = inputText; inputText = ""; viewModel.sendText(t) },
                onStartRecord = { viewModel.startRecording() },
                onRequestAudioPerm = { audioPermission.launchPermissionRequest() },
                onCancelRecord = { viewModel.cancelRecording() },
                onSendRecord = { viewModel.stopRecordingAndSend() },
                onClearReply = { viewModel.clearReply() }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            VlAmbientGlow()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                uiState.post?.let { post ->
                    item(key = "post_preview") {
                        MessageBubble(
                            message = post,
                            isMine = post.senderId == viewModel.currentUid,
                            otherUid = "",
                            currentUid = viewModel.currentUid,
                            chatType = ChatType.GROUP, // Hide comments button recursive
                            hapticEnabled = hapticEnabled,
                            showSenderName = true,
                            voicePlayback = uiState.voicePlayback,
                            onPlayVoice = { url, dur -> viewModel.playVoice(post.id, url, dur) },
                            onSeekVoice = { viewModel.seekVoice(it) },
                            onLongPressStart = { },
                            onLongPressDrag = { },
                            onLongPressEnd = { },
                            onMediaTap = { url, type -> onOpenImageViewer(url, type) },
                            onAlbumTap = { _, _ -> },
                            onReact = { emoji -> viewModel.toggleReaction(post.id, emoji, post.parsedReactions) },
                            onReplyClick = { },
                            onMentionClick = { },
                            onOpenComments = { },
                            chat = channel,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                if (uiState.comments.isEmpty()) {
                    item(key = "empty_comments") {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                                Spacer(Modifier.height(8.dp))
                                Text("No comments yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                                if (commentsAllowed) {
                                    Text("Be the first to comment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                                }
                            }
                        }
                    }
                }

                items(allComments, key = { it.id }) { comment ->
                    val isMine = comment.senderId == viewModel.currentUid

                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { 48 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180))
                    ) {
                        MessageBubble(
                            message = comment.toMessage(),
                            isMine = isMine,
                            otherUid = "",
                            currentUid = viewModel.currentUid,
                            chatType = ChatType.GROUP, // Show sender name, hide comments button
                            hapticEnabled = hapticEnabled,
                            showSenderName = !isMine,
                            voicePlayback = uiState.voicePlayback,
                            onPlayVoice = { url, dur -> viewModel.playVoice(comment.id, url, dur) },
                            onSeekVoice = { viewModel.seekVoice(it) },
                            onLongPressStart = { },
                            onLongPressDrag = { },
                            onLongPressEnd = { },
                            onMediaTap = { url, type -> onOpenImageViewer(url, type) },
                            onAlbumTap = { _, _ -> },
                            onReact = { emoji -> viewModel.toggleReaction(comment.id, emoji, comment.parsedReactions) },
                            onReplyClick = { replyId ->
                                val index = uiState.comments.indexOfFirst { it.id == replyId }
                                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                            },
                            onMentionClick = { },
                            onOpenComments = { },
                            chat = channel,
                        )
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { commentId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete comment?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComment(commentId)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
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

    uiState.error?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            delay(3000); viewModel.clearError()
        }
    }
}
