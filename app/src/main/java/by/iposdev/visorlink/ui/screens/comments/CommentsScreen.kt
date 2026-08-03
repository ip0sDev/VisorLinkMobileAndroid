package by.iposdev.visorlink.ui.screens.comments

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.screens.chat.resolveCdnUrl
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

    val commentCount = uiState.comments.size
    LaunchedEffect(commentCount) {
        if (commentCount > 0) listState.scrollToItem(commentCount - 1)
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
                    Column {
                        Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (!commentsAllowed) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text("Comments are disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                    }
                } else {
                    AnimatedVisibility(
                        visible = uiState.replyingTo != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150))
                    ) {
                        uiState.replyingTo?.let { comment ->
                            CommentReplyBanner(comment = comment, onDismiss = { viewModel.clearReply() })
                        }
                    }

                    AnimatedVisibility(visible = uiState.isUploading, enter = expandVertically(), exit = shrinkVertically()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!uiState.isRecording) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Add a comment…") },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = MaterialTheme.shapes.extraLarge
                            )

                            Spacer(Modifier.width(4.dp))

                            AnimatedContent(
                                targetState = inputText.isNotBlank(),
                                transitionSpec = {
                                    scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) +
                                            fadeIn() togetherWith scaleOut(targetScale = 0.6f) + fadeOut()
                                },
                                label = "send_mic_comments"
                            ) { hasText ->
                                if (hasText) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .clickable {
                                                haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                                val text = inputText
                                                inputText = ""
                                                viewModel.sendText(text)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (audioPermission.status.isGranted) {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                viewModel.startRecording()
                                            } else {
                                                audioPermission.launchPermissionRequest()
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Mic, "Record", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        } else {
                            CommentsRecordingBar(
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            uiState.post?.let { post ->
                item(key = "post_preview") {
                    PostPreview(
                        post = post,
                        revealedSpoilers = uiState.revealedSpoilers,
                        onReveal = { viewModel.revealSpoiler(post.id) },
                        onImageTap = { url -> onOpenImageViewer(url, post.type) }
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

            items(uiState.comments, key = { it.id }) { comment ->
                val isMine = comment.senderId == viewModel.currentUid
                val isRevealed = comment.id in uiState.revealedSpoilers

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { 48 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180))
                ) {
                    CommentBubble(
                        comment = comment,
                        isMine = isMine,
                        isRevealed = isRevealed,
                        currentUid = viewModel.currentUid,
                        voicePlayback = uiState.voicePlayback,
                        hapticEnabled = hapticEnabled,
                        onReveal = { viewModel.revealSpoiler(comment.id) },
                        onReply = {
                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                            viewModel.setReplyTo(comment)
                        },
                        onReact = { emoji -> viewModel.toggleReaction(comment.id, emoji, comment.parsedReactions) },
                        onDelete = { showDeleteConfirm = comment.id },
                        onScrollToReply = { replyId ->
                            val index = uiState.comments.indexOfFirst { it.id == replyId }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                        },
                        onImageTap = { url -> onOpenImageViewer(url, comment.type) },
                        onPlayVoice = { url, dur -> viewModel.playVoice(comment.id, url, dur) },
                        onSeekVoice = { viewModel.seekVoice(it) }
                    )
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
        by.iposdev.visorlink.ui.screens.chat.ImageEditorScreen(
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

@Composable
private fun PostPreview(
    post: Message,
    revealedSpoilers: Set<String>,
    onReveal: () -> Unit,
    onImageTap: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        when {
            post.deleted -> Text("This post was deleted", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            post.type == MessageType.IMAGE || post.type == MessageType.VIDEO || post.type == MessageType.GIF -> {
                val resolvedUrl = resolveCdnUrl(post.cdnMediaId, post.url)
                if (resolvedUrl != null) {
                    SpoilerImage(
                        url = resolvedUrl,
                        spoiler = post.spoiler,
                        isRevealed = post.id in revealedSpoilers,
                        onReveal = onReveal,
                        onFullscreen = { onImageTap(resolvedUrl) }
                    )
                }
            }
            post.type == MessageType.TEXT && !post.text.isNullOrEmpty() -> {
                Text(text = post.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            post.createdAt?.toDate()?.let { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it) } ?: "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentBubble(
    comment: Comment,
    isMine: Boolean,
    isRevealed: Boolean,
    currentUid: String,
    voicePlayback: by.iposdev.visorlink.utils.VoicePlaybackState,
    hapticEnabled: Boolean,
    onReveal: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDelete: () -> Unit,
    onScrollToReply: (String) -> Unit,
    onImageTap: (String) -> Unit,
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit
) {
    val haptic = rememberHaptic()
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = if (isMine) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        onReply()
                    }
                )
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                if (!isMine) {
                    Text(
                        "@${comment.senderUsername}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                comment.replyTo?.let { reply ->
                    CommentReplyPreview(reply = reply, isMine = isMine, onClick = { onScrollToReply(reply.id) })
                    Spacer(Modifier.height(4.dp))
                }

                when {
                    comment.deleted -> Text("Message deleted", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.6f))
                    comment.type == MessageType.IMAGE || comment.type == MessageType.VIDEO || comment.type == MessageType.GIF -> {
                        val resolvedUrl = resolveCdnUrl(comment.fileName, comment.url) // В комментариях пока используется url, но логика остаётся для совместимости
                        if (resolvedUrl != null) {
                            SpoilerImage(
                                url = resolvedUrl,
                                spoiler = comment.spoiler,
                                isRevealed = isRevealed,
                                onReveal = onReveal,
                                onFullscreen = { onImageTap(resolvedUrl) }
                            )
                        }
                    }
                    comment.type == MessageType.VOICE && comment.url != null -> {
                        by.iposdev.visorlink.ui.screens.chat.VoiceBubbleCompact(
                            messageId = comment.id, url = comment.url, durationSec = comment.duration ?: 0,
                            tint = textColor, playback = voicePlayback, onPlay = onPlayVoice, onSeek = onSeekVoice
                        )
                    }
                    comment.type == MessageType.TEXT -> Text(comment.text ?: "", style = MaterialTheme.typography.bodyMedium, color = textColor)
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        comment.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp
                    )
                    if (isMine && !comment.deleted) {
                        Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(14.dp).clickable { onDelete() }, tint = textColor.copy(alpha = 0.5f))
                    }
                    if (!comment.deleted) {
                        Icon(Icons.Default.Reply, "Reply", modifier = Modifier.size(14.dp).clickable { onReply() }, tint = textColor.copy(alpha = 0.5f))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = comment.parsedReactions.isNotEmpty(),
            enter = scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
        ) {
            CommentReactionRow(reactions = comment.parsedReactions, currentUid = currentUid, hapticEnabled = hapticEnabled, onReact = onReact)
        }
    }
}

@Composable
private fun CommentReactionRow(reactions: List<Reaction>, currentUid: String, hapticEnabled: Boolean, onReact: (String) -> Unit) {
    val haptic = rememberHaptic()
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { reaction ->
            key(reaction.emoji) {
                val iReacted = currentUid in reaction.uids
                Surface(
                    onClick = { haptic.perform(HapticType.REACTION, hapticEnabled); onReact(reaction.emoji) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (iReacted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (iReacted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                    tonalElevation = if (iReacted) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(reaction.emoji, fontSize = 14.sp)
                        Text(reaction.count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = if (iReacted) FontWeight.SemiBold else FontWeight.Normal, color = if (iReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentReplyPreview(reply: CommentReplyData, isMine: Boolean, onClick: () -> Unit) {
    val accent = if (isMine) Color.White.copy(0.25f) else MaterialTheme.colorScheme.primary.copy(0.12f)
    val nameColor = if (isMine) Color.White.copy(0.9f) else MaterialTheme.colorScheme.primary
    val textColor = if (isMine) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .widthIn(min = 60.dp, max = 260.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(accent)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(Modifier.width(3.dp).height(28.dp).background(nameColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall, color = nameColor, fontWeight = FontWeight.SemiBold)
            Text(reply.text ?: when (reply.type) { MessageType.IMAGE -> "📷 Image"; MessageType.VOICE -> "🎤 Voice"; else -> "" }, style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CommentReplyBanner(comment: Comment, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${comment.senderUsername}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(when (comment.type) { MessageType.IMAGE -> "📷 Photo"; MessageType.VOICE -> "🎤 Voice"; else -> comment.text ?: "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Cancel reply", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}

@Composable
private fun CommentsRecordingBar(hapticEnabled: Boolean, onCancel: () -> Unit, onSend: () -> Unit) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(initialValue = 1f, targetValue = 0.2f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot_alpha")
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }

    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() }) { Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(dotAlpha), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary) }
    }
}

@Composable
fun SpoilerImage(
    url: String,
    spoiler: Boolean,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blurRadius by animateDpAsState(
        targetValue = if (!spoiler || isRevealed) 0.dp else 20.dp,
        animationSpec = tween(350),
        label = "spoiler_blur"
    )
    val alpha by animateFloatAsState(
        targetValue = if (!spoiler || isRevealed) 1f else 0.35f,
        animationSpec = tween(350),
        label = "spoiler_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (!spoiler || isRevealed) onFullscreen() else onReveal()
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .sizeIn(minWidth = 100.dp, minHeight = 100.dp, maxWidth = 280.dp, maxHeight = 500.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                .graphicsLayerAlpha(alpha),
            contentScale = ContentScale.Crop
        )

        AnimatedVisibility(
            visible = spoiler && !isRevealed,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut(tween(350))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🙈", fontSize = 28.sp)
                    Text("SPOILER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                    Text("tap to reveal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier =
    this.then(Modifier.graphicsLayer { this.alpha = alpha })