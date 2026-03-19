package by.iposdev.visorlink.ui.screens.chatlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.ChatType
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
    onCreateChat: () -> Unit,
    onFindChannel: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val profileCache by viewModel.profileCache.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()

    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chatlist_title), fontWeight = FontWeight.Bold) },
                actions = {
                    BadgedBox(badge = {
                        if (unreadNotifications > 0) Badge { Text("$unreadNotifications") }
                    }) {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Default.Notifications,
                                stringResource(R.string.notifications_title))
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, stringResource(R.string.action_search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                    }
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
                                        currentUser?.displayName?.firstOrNull()?.uppercase() ?: "?",
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
            ChatFab(
                showMenu = showFabMenu,
                onToggle = { showFabMenu = !showFabMenu },
                onNewChat = { showFabMenu = false; onOpenSearch() },
                onNewGroup = { showFabMenu = false; onCreateChat() },
                onFindChannel = { showFabMenu = false; onFindChannel() }
            )
        },
    ) { padding ->
        if (showFabMenu) {
            Box(modifier = Modifier.fillMaxSize().clickable { showFabMenu = false })
        }

        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.chatlist_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.chatlist_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(chats, key = { it.id }) { chat ->
                    val chatType = chat.chatType()
                    val otherUid = when (chatType) {
                        ChatType.DIRECT -> chat.otherParticipantId(viewModel.currentUid)
                        else -> chat.id
                    }
                    ChatListItem(
                        chat = chat,
                        chatType = chatType,
                        currentUid = viewModel.currentUid,
                        otherProfile = if (chatType == ChatType.DIRECT) profileCache[otherUid] else null,
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
private fun SmallFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 2.dp
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium)
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ChatListItem(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (chatType) {
            ChatType.DIRECT -> AvatarWithPresence(
                avatarUrl = otherProfile?.avatarUrl,
                displayName = chat.otherDisplayName(currentUid),
                isOnline = otherProfile?.online ?: false,
                size = 52.dp
            )
            ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                avatarUrl = chat.avatarUrl,
                name = chat.name,
                isChannel = chatType == ChatType.CHANNEL,
                size = 52.dp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chatType != ChatType.DIRECT) {
                    Text(
                        if (chatType == ChatType.CHANNEL) "📢" else "👥",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = when (chatType) {
                        ChatType.DIRECT -> chat.otherDisplayName(currentUid)
                            .ifEmpty { "@${chat.otherUsername(currentUid)}" }
                        else -> chat.name
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                chat.lastMessageAt?.let {
                    Text(formatTime(it.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                chat.lastMessage ?: stringResource(R.string.chatlist_no_messages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GroupChannelAvatar(
    avatarUrl: String?,
    name: String,
    isChannel: Boolean,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(
                if (isChannel) Color(0xFF6366F1)
                else MaterialTheme.colorScheme.secondaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(model = avatarUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = if (isChannel) "📢" else (name.firstOrNull()?.uppercase() ?: "G"),
                fontSize = (size.value * 0.38f).sp,
                color = if (isChannel) Color.White
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
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

@Composable
private fun ChatFab(
    showMenu: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onFindChannel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit  = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFab(Icons.Default.Tag,
                    stringResource(R.string.chatlist_fab_find_channel), onFindChannel)
                SmallFab(Icons.Default.Group,
                    stringResource(R.string.chatlist_fab_new_group), onNewGroup)
                SmallFab(Icons.Default.PersonAdd,
                    stringResource(R.string.chatlist_fab_new_chat), onNewChat)
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary
        ) {
            AnimatedContent(
                targetState = showMenu,
                transitionSpec = {
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith
                            scaleOut(tween(100))
                },
                label = "fab_icon"
            ) { isOpen ->
                Icon(if (isOpen) Icons.Default.Close else Icons.Default.Edit, null)
            }
        }
    }
}