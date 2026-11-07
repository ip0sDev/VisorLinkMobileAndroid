package org.visorlink.app.ui.screens.chatlist

import androidx.compose.animation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppNotification
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.SyncState
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.vlHazeSource
import org.visorlink.app.ui.components.isVlMatte
import org.visorlink.app.ui.components.VlBarBrandText
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.components.progressiveEdgeBlur
import org.visorlink.app.ui.components.chatlist.*
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.NotificationHelper
import org.visorlink.app.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.visorlink.app.ui.components.LaunchedEffectAfterFirst
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.ui.components.calculateJellyScale
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.components.LiquidPullRefreshLayout
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, otherUid: String) -> Unit,
    onOpenTopicList: (chatId: String) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    /** `false` — профиль живёт во вкладке навбара (флаг enable_profile_navbar), аватар в шапке не нужен. */
    showProfileButton: Boolean = true,
    onCreateChat: () -> Unit,
    onFindChannel: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenFolders: () -> Unit = {},
    isActive: Boolean = true,
    viewModel: ChatListViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val profileCache by viewModel.profileCache.collectAsStateWithLifecycle()
    // Приглашения живут строкой в списке чатов (InvitesEntry.kt), а не кнопкой в топбаре
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val entries by viewModel.listEntries.collectAsStateWithLifecycle()
    val invitesPreview = invitesPreview(invites)
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val typingMap by viewModel.typingMap.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val activeFolderId by viewModel.activeFolderId.collectAsStateWithLifecycle()
    val unreadByFolder by viewModel.unreadByFolder.collectAsStateWithLifecycle()
    val totalFolderUnread by viewModel.totalUnread.collectAsStateWithLifecycle()

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsStateWithLifecycle()

    val haptic = rememberHaptic()
    val context = LocalContext.current

    var showFabMenu by remember { mutableStateOf(false) }
    var showIonReveal by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val triggerKey = remember(isActive, chats.isEmpty()) { "${isActive}_${chats.isNotEmpty()}" }

    val isLed = VlTheme.tokens.led != null
    val totalUnread = remember(chats, viewModel.currentUid) {
        chats.sumOf { it.unreadCountFor(viewModel.currentUid) }
    }
    val isOnline = syncState != SyncState.WAITING_FOR_NETWORK

    val idCardRepo: org.visorlink.app.data.repository.IdCardRepository = org.koin.compose.koinInject()
    var ionGiftAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.currentUid) {
        try {
            ionGiftAvailable = idCardRepo.ionGiftAvailable()
        } catch (_: Exception) {
            ionGiftAvailable = false
        }
    }

    LaunchedEffect(chats) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            chats.forEach { chat ->
                if (chat.unreadCountFor(viewModel.currentUid) == 0) {
                    NotificationHelper.clearNotification(context, chat.id)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)
            // Жидкие пульсы деформируют бар — с активным backdrop-блюром размытый фон
            // под ним «плывёт» и шапка выглядит прыгающей, поэтому при матовом режиме
            // бар стабилен; пульсы остаются в темах без блюра
            val matte = isVlMatte()
            LaunchedEffect(triggerKey) {
                if (isActive && !matte) {
                    topBarJelly.pulse(0.08f)
                }
            }
            LaunchedEffectAfterFirst(syncState) {
                if (!matte) {
                    topBarJelly.pulse(0.06f)
                }
            }
            LaunchedEffectAfterFirst(isScrolled) {
                if (!matte) {
                    topBarJelly.pulse(0.07f)
                }
            }

            VlTopAppBar(
                modifier = Modifier.liquidJelly(topBarJelly),
                title = {
                    Column {
                        VlBarBrandText(stringResource(R.string.chatlist_title))
                        AnimatedVisibility(
                            visible = !isScrolled,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
                        ) {
                            AnimatedContent(
                                targetState = syncState,
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label = "chatlist_sync_status"
                            ) { state ->
                                val (statusText, statusColor) = when (state) {
                                    SyncState.WAITING_FOR_NETWORK -> stringResource(R.string.status_waiting_for_network) to MaterialTheme.colorScheme.error
                                    SyncState.CONNECTING -> stringResource(R.string.status_connecting) to MaterialTheme.colorScheme.primary
                                    SyncState.UPDATING -> stringResource(R.string.status_updating) to MaterialTheme.colorScheme.primary
                                    SyncState.SYNCED -> {
                                        val text = if (chats.isEmpty()) "" else {
                                            val count = chats.size
                                            pluralStringResource(
                                                R.plurals.chatlist_chats_count,
                                                count,
                                                count
                                            )
                                        }
                                        text to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                }

                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Mask Mode: маска до выключения / индикатор «Маска до 18:30» (спека §8)
                    org.visorlink.app.ui.idcard.MaskToolbarButton()

                    VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSearch() }) {
                        Icon(Icons.Default.Search, stringResource(R.string.action_search), modifier = Modifier.size(20.dp))
                    }

                    VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenSettings() }) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title), modifier = Modifier.size(20.dp))
                    }

                    if (showProfileButton) {
                        AvatarChip(
                            avatarUrl = currentUser?.avatarUrl,
                            displayName = currentUser?.displayName ?: "",
                            hapticEnabled = hapticEnabled,
                            onClick = { onOpenProfile() }
                        )
                    }
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
                // Источник backdrop-блюра шапки: фон + свечение + список, заезжающий под бар
                .vlHazeSource()
        ) {
            VlAmbientGlow()

            LiquidPullRefreshLayout(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                hapticEnabled = hapticEnabled,
                topPadding = padding.calculateTopPadding(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (chats.isEmpty() && invites.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                    ) {
                        if (isLed) {
                            ChatBoard(
                                unread = 0,
                                online = isOnline,
                                sync = syncState,
                                compact = false,
                                gift = ionGiftAvailable,
                                onGift = { showIonReveal = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            org.visorlink.app.ui.idcard.IonGiftBanner(
                                uid = viewModel.currentUid,
                                onOpenReveal = { showIonReveal = true }
                            )
                        }
                        if (currentUser?.isPro == true && (folders.isNotEmpty() || activeFolderId != null)) {
                            org.visorlink.app.ui.components.chatlist.FolderTabs(
                                folders = folders,
                                activeFolderId = activeFolderId,
                                unreadByFolder = unreadByFolder,
                                totalUnread = totalFolderUnread,
                                onSelect = { viewModel.selectFolder(it) },
                                onManage = onOpenFolders,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                        ChatListEmptyState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .progressiveEdgeBlur(
                                topBlur = 10.dp,
                                bottomBlur = 10.dp,
                                enabled = !VlTheme.tokens.reduceMotion
                            ),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 88.dp
                        )
                    ) {
                        if (isLed) {
                            item(key = "forge_chat_board") {
                                ChatBoard(
                                    unread = totalUnread,
                                    online = isOnline,
                                    sync = syncState,
                                    compact = false,
                                    gift = ionGiftAvailable,
                                    onGift = { showIonReveal = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            item(key = "ion_gift_banner") {
                                org.visorlink.app.ui.idcard.IonGiftBanner(
                                    uid = viewModel.currentUid,
                                    onOpenReveal = { showIonReveal = true }
                                )
                            }
                        }
                        if (currentUser?.isPro == true && (folders.isNotEmpty() || activeFolderId != null)) {
                            item(key = "chat_folder_tabs") {
                                org.visorlink.app.ui.components.chatlist.FolderTabs(
                                    folders = folders,
                                    activeFolderId = activeFolderId,
                                    unreadByFolder = unreadByFolder,
                                    totalUnread = totalFolderUnread,
                                    onSelect = { viewModel.selectFolder(it) },
                                    onManage = onOpenFolders,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                            // 📌 SAVED MESSAGES
                            item(key = "saved_messages") {
                                val savedChat = viewModel.savedMessagesEntry
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ChatListItem(
                                        chat = savedChat,
                                        chatType = ChatType.DIRECT,
                                        currentUid = viewModel.currentUid,
                                        otherProfile = null,
                                        draftText = drafts[savedChat.id],
                                        unreadCount = 0,
                                        isSavedMessages = true,
                                        isTyping = false,
                                        onClick = { onOpenChat(savedChat.id, viewModel.currentUid) }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            itemsIndexed(
                                items = entries,
                                key = { _, chat -> chat.id },
                                contentType = { _, _ -> "chat_item" }
                            ) { index, chat ->
                                if (viewModel.isInvitesEntry(chat)) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ChatListItem(
                                            chat = chat,
                                            chatType = ChatType.DIRECT,
                                            currentUid = viewModel.currentUid,
                                            otherProfile = null,
                                            draftText = null,
                                            unreadCount = invites.size,
                                            isInvites = true,
                                            previewOverride = invitesPreview,
                                            onClick = onOpenNotifications
                                        )
                                    }
                                    return@itemsIndexed
                                }
                                val chatType = chat.chatType()
                                val otherUid = when (chatType) {
                                    ChatType.DIRECT, ChatType.EMERGENCY -> chat.otherParticipantId(viewModel.currentUid)
                                    else -> chat.id
                                }

                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ChatListItem(
                                        chat = chat,
                                        chatType = chatType,
                                        currentUid = viewModel.currentUid,
                                        otherProfile = if (chatType == ChatType.DIRECT || chatType == ChatType.EMERGENCY) profileCache[otherUid] else null,
                                        draftText = drafts[chat.id],
                                        unreadCount = maxOf(chat.unreadCountFor(viewModel.currentUid), NotificationHelper.getUnreadCount(context, chat.id)),
                                        isTyping = typingMap[chat.id] == true,
                                        onClick = {
                                            if (chat.isForumActive) onOpenTopicList(chat.id)
                                            else onOpenChat(chat.id, otherUid)
                                        }
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

    if (showIonReveal) {
        org.visorlink.app.ui.idcard.IonGiftReveal(
            uid = viewModel.currentUid,
            onDismiss = { showIonReveal = false }
        )
    }
}

/**
 * Подпись строки «Приглашения»: одно — от кого, несколько — сколько, ни одного — что их нет.
 * `invitedBy` — ник пригласившего, как на экране приглашений.
 */
@Composable
private fun invitesPreview(invites: List<AppNotification>): String = when {
    invites.isEmpty() -> stringResource(R.string.invites_preview_none)
    invites.size > 1 -> pluralStringResource(R.plurals.invites_preview_count, invites.size, invites.size)
    invites.first().invitedBy.isNotBlank() -> stringResource(R.string.invites_preview_from, invites.first().invitedBy)
    else -> stringResource(R.string.notification_group_invite_title)
}
