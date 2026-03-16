package by.iposdev.visorlink.ui.screens.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, otherUid: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val profileCache by viewModel.profileCache.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VisorLink", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                    Box(
                        modifier = Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape)
                            .clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentUser?.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(model = currentUser!!.avatarUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = currentUser?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenSearch,
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text("New Chat") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("No chats yet", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Search for users to start chatting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(chats, key = { it.id }) { chat ->
                    val otherUid = chat.otherParticipantId(viewModel.currentUid)
                    ChatListItem(
                        chat = chat,
                        currentUid = viewModel.currentUid,
                        otherProfile = profileCache[otherUid],
                        onClick = { onOpenChat(chat.id, otherUid) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 80.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    chat: Chat,
    currentUid: String,
    otherProfile: UserProfile?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarWithPresence(
            avatarUrl = otherProfile?.avatarUrl,
            displayName = chat.otherDisplayName(currentUid),
            isOnline = otherProfile?.online ?: false,
            size = 52.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                chat.lastMessageAt?.let {
                    Text(formatTime(it.toDate()), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(chat.lastMessage ?: "No messages yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    }
}