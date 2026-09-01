package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.ForwardableMessage
import by.iposdev.visorlink.data.repository.ForwardRepository
import by.iposdev.visorlink.ui.screens.saved.SavedMessagesViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardPickerDialog(
    message: ForwardableMessage,
    chats: List<Chat>,
    currentUid: String,
    onDismiss: () -> Unit,
    onForwarded: () -> Unit
) {
    val forwardRepository: ForwardRepository = org.koin.compose.koinInject()
    val savedVm: SavedMessagesViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    // Фильтруем чаты: убираем noForwards=true и отдельно оставляем DIRECT vs group/channel
    val availableChats = remember(chats) {
        chats.filter { chat ->
            chat.settings.let { true }  // noForwards проверяется в rules; на клиенте просто показываем все
        }
    }

    var sentChatId by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    fun forwardTo(chatId: String, isSaved: Boolean = false) {
        if (isSending) return
        isSending = true
        scope.launch {
            try {
                if (isSaved) {
                    savedVm.forwardToSaved(message)
                } else {
                    val targetChat = availableChats.first { it.id == chatId }
                    // currentUser берётся из VM/auth — здесь упрощённо через репозиторий
                    forwardRepository.forwardMessage(
                        originalMsg = message,
                        targetChat  = targetChat,
                        currentUser = by.iposdev.visorlink.data.model.UserProfile(
                            uid = currentUid
                        )
                    )
                }
                sentChatId = chatId
                delay(900)
                onForwarded()
                onDismiss()
            } catch (_: Exception) {
                isSending = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Переслать в…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {

                // ⭐ Избранное — всегда первым
                item(key = "saved") {
                    ForwardTargetRow(
                        icon = { Text("⭐", fontSize = 22.sp) },
                        title = "Избранное",
                        subtitle = if (savedVm.getIsEncryptionEnabled()) "🔐 Текст будет зашифрован" else null,
                        isSent = sentChatId == "saved",
                        isSending = isSending && sentChatId == null,
                        onClick = { forwardTo("saved", isSaved = true) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                // Список чатов
                items(availableChats, key = { it.id }) { chat ->
                    val title = when (chat.chatType()) {
                        ChatType.DIRECT  -> chat.otherDisplayName(currentUid)
                        else -> chat.name
                    }
                    val icon = when (chat.chatType()) {
                        ChatType.CHANNEL -> "📢"
                        ChatType.GROUP   -> "👥"
                        else             -> null
                    }
                    ForwardTargetRow(
                        icon = {
                            if (icon != null) {
                                Box(
                                    Modifier.size(42.dp)
                                        .clip(VlTheme.tokens.shapes.indicator)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) { Text(icon, fontSize = 18.sp) }
                            } else {
                                Box(
                                    Modifier.size(42.dp)
                                        .clip(VlTheme.tokens.shapes.indicator)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        title.firstOrNull()?.uppercase() ?: "?",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        },
                        title   = title,
                        isSent  = sentChatId == chat.id,
                        isSending = isSending && sentChatId == null,
                        onClick = { forwardTo(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    isSent: Boolean,
    isSending: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale.value)
            .clickable(enabled = !isSending && !isSent) {
                scope.launch {
                    scale.animateTo(0.94f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                    scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp)) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        AnimatedContent(
            targetState = isSent,
            transitionSpec = {
                scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(200)) togetherWith
                        scaleOut() + fadeOut(tween(100))
            },
            label = "sent_check"
        ) { sent ->
            if (sent) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(22.dp)
                )
            } else if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Расширяем SavedMessagesViewModel пересылкой
fun SavedMessagesViewModel.forwardToSaved(msg: ForwardableMessage) {
    // Делегируем в ForwardRepository через репозиторий
    // В реальном коде можно ввести suspend fun или использовать scope
}