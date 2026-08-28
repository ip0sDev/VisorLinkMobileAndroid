package by.iposdev.visorlink.ui.screens.chatlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlBrandText
import by.iposdev.visorlink.ui.components.VlTopAppBar
import by.iposdev.visorlink.ui.components.chatlist.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

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
    onOpenFeed: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val profileCache by viewModel.profileCache.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()
    val drafts by viewModel.drafts.collectAsState()

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val compactList by themeViewModel.compactChatList.collectAsState()

    val haptic = rememberHaptic()

    var showFabMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            VlTopAppBar(
                title = {
                    val tokens = VlTheme.tokens
                    Column {
                        if (tokens.isBiolume) {
                            VlBrandText(
                                text = stringResource(R.string.chatlist_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp
                            )
                        } else {
                            Text(
                                stringResource(R.string.chatlist_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnimatedVisibility(
                            visible = !isScrolled,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
                        ) {
                            Text(
                                text = if (chats.isEmpty()) "" else {
                                    val count = chats.size
                                    pluralStringResource(
                                        R.plurals.chatlist_chats_count,
                                        count,
                                        count
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    val badgeScale = remember { Animatable(if (unreadNotifications > 0) 1f else 0f) }
                    LaunchedEffect(unreadNotifications > 0) {
                        if (unreadNotifications > 0) {
                            badgeScale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                            badgeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        } else {
                            badgeScale.animateTo(0f, tween(150))
                        }
                    }

                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(
                                    modifier = Modifier.scale(badgeScale.value)
                                ) { Text("$unreadNotifications") }
                            }
                        }
                    ) {
                        IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenNotifications() }) {
                            Icon(if (unreadNotifications > 0) Icons.Default.Notifications else Icons.Outlined.Notifications, stringResource(R.string.notifications_title))
                        }
                    }

                    IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSearch() }) {
                        Icon(Icons.Default.Search, stringResource(R.string.action_search))
                    }

                    IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSettings() }) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                    }

                    AvatarChip(
                        avatarUrl = currentUser?.avatarUrl,
                        displayName = currentUser?.displayName ?: "",
                        hapticEnabled = hapticEnabled,
                        onClick = { onOpenProfile() }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                ChatListFab(
                    showMenu = showFabMenu,
                    hapticEnabled = hapticEnabled,
                    onToggle = { showFabMenu = !showFabMenu },
                    onNewChat = { showFabMenu = false; onOpenSearch() },
                    onNewGroup = { showFabMenu = false; onCreateChat() },
                    onFindChannel = { showFabMenu = false; onFindChannel() }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            VlAmbientGlow()

            AnimatedContent(
                targetState = chats.isEmpty(),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "list_empty_toggle"
            ) { isEmpty ->
                if (isEmpty) {
                    ChatListEmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 88.dp
                        )
                    ) {
                        if (compactList) {
                            // ── КОМПАКТНЫЙ РЕЖИМ (Единая карточка: Избранное + все чаты) ─────────────
                            item(key = "compact_chats_card") {
                                val cs = MaterialTheme.colorScheme
                                val shape = RoundedCornerShape(24.dp)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 12.dp)
                                        .background(cs.surfaceContainerLow, shape)
                                        .clip(shape)
                                ) {
                                    // 📌 SAVED MESSAGES (первая строка компактного блока)
                                    val savedChat = viewModel.savedMessagesEntry
                                    ChatListItemCompact(
                                        chat = savedChat,
                                        chatType = ChatType.DIRECT,
                                        currentUid = viewModel.currentUid,
                                        otherProfile = null,
                                        draftText = drafts[savedChat.id],
                                        isSavedMessages = true,
                                        onClick = { onOpenChat(savedChat.id, viewModel.currentUid) }
                                    )

                                    if (chats.isNotEmpty()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                            color = cs.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }

                                    // 📌 ВСЕ ОСТАЛЬНЫЕ ЧАТЫ
                                    chats.forEachIndexed { index, chat ->
                                        val chatType = chat.chatType()
                                        val otherUid = when (chatType) {
                                            ChatType.DIRECT -> chat.otherParticipantId(viewModel.currentUid)
                                            else -> chat.id
                                        }

                                        ChatListItemCompact(
                                            chat = chat,
                                            chatType = chatType,
                                            currentUid = viewModel.currentUid,
                                            otherProfile = if (chatType == ChatType.DIRECT) profileCache[otherUid] else null,
                                            draftText = drafts[chat.id],
                                            onClick = { onOpenChat(chat.id, otherUid) }
                                        )

                                        if (index < chats.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                                thickness = 0.5.dp,
                                                color = cs.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── ОБЫЧНЫЙ РЕЖИМ (Отдельные карточки с отступами) ────────────────────────
                            // 📌 SAVED MESSAGES
                            item(key = "saved_messages") {
                                val savedChat = viewModel.savedMessagesEntry
                                ChatListItem(
                                    chat = savedChat,
                                    chatType = ChatType.DIRECT,
                                    currentUid = viewModel.currentUid,
                                    otherProfile = null,
                                    draftText = drafts[savedChat.id],
                                    isSavedMessages = true,
                                    isCompactList = false,
                                    onClick = { onOpenChat(savedChat.id, viewModel.currentUid) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
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
                                    draftText = drafts[chat.id],
                                    isCompactList = compactList,
                                    onClick = { onOpenChat(chat.id, otherUid) }
                                )
                            }
                        }
                    }
                }
            }

            // FAB Menu Overlay
            if (showFabMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            showFabMenu = false
                        }
                )
            }
        }
    }
}
