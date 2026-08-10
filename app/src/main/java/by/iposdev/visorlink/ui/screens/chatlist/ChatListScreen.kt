package by.iposdev.visorlink.ui.screens.chatlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.CachedImage
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
//  One UI colour tokens
// ════════════════════════════════════════════════════════════════════════════

private object OneUi {
    val Blue        = Color(0xFF1259C3)
    val BlueDark    = Color(0xFF4D90F0)
    val PageBg      = Color(0xFFF4F4F4)
    val PageBgDark  = Color(0xFF1A1A1A)
    val CardBg      = Color(0xFFFFFFFF)
    val CardBgDark  = Color(0xFF2C2C2C)
    val TextPrimary       = Color(0xFF1A1A1A)
    val TextPrimaryDark   = Color(0xFFEEEEEE)
    val TextSecondary     = Color(0xFF888888)
    val TextSecondaryDark = Color(0xFF999999)
    val Divider     = Color(0xFFE8E8E8)
    val DividerDark = Color(0xFF3A3A3A)
    val IconRed     = Color(0xFFE53935)
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

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

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val compactList by themeViewModel.compactChatList.collectAsState()

    val isOneUi = currentTheme == AppTheme.ONE_UI
    val isExthru = currentTheme.isExthruFamily
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val haptic = rememberHaptic()

    var showFabMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val hazeState = remember { HazeState() }

    val scaffoldBg = when {
        isOneUi -> if (isDark) OneUi.PageBgDark else OneUi.PageBg
        isExthru -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.surface
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (isExthru) {
                    val topBarBg = if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)

                    val topBarMod = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isForge) Modifier.background(topBarBg)
                            else Modifier.hazeChild(
                                state = hazeState,
                                style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                            ).background(topBarBg)
                        )

                    TopAppBar(
                        modifier = topBarMod,
                        title = {
                            Text(
                                text = if (isForge) "> CHATS_" else stringResource(R.string.chatlist_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = if (isForge) 28.sp else 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isForge) FontFamily.Monospace else null
                                ),
                                color = if (isForge) style.accent else Color.Unspecified
                            )
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
                                            modifier = Modifier.scale(badgeScale.value).offset(x = (-10).dp, y = 10.dp),
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text("$unreadNotifications")
                                        }
                                    }
                                }
                            ) {
                                ExthruTopBarButton(
                                    icon = if (unreadNotifications > 0) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                    isDark = isDark,
                                    isForge = isForge,
                                    hapticEnabled = hapticEnabled
                                ) { onOpenNotifications() }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            ExthruTopBarButton(
                                icon = Icons.Default.Search,
                                isDark = isDark,
                                isForge = isForge,
                                hapticEnabled = hapticEnabled
                            ) { onOpenSearch() }

                            Spacer(modifier = Modifier.width(10.dp))

                            ExthruTopBarButton(
                                icon = Icons.Outlined.Settings,
                                isDark = isDark,
                                isForge = isForge,
                                hapticEnabled = hapticEnabled
                            ) { onOpenSettings() }

                            Spacer(modifier = Modifier.width(10.dp))

                            AvatarChip(
                                avatarUrl = currentUser?.avatarUrl,
                                displayName = currentUser?.displayName ?: "",
                                isOneUi = false,
                                isExthru = true,
                                isForge = isForge,
                                isDark = isDark,
                                hapticEnabled = hapticEnabled,
                                onClick = { onOpenProfile() }
                            )

                            Spacer(modifier = Modifier.width(6.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                } else {
                    Surface(
                        color = if (isOneUi) (if (isDark) OneUi.PageBgDark else OneUi.PageBg) else MaterialTheme.colorScheme.surface
                    ) {
                        TopAppBar(
                            title = {
                                if (isOneUi) {
                                    Text(
                                        stringResource(R.string.chatlist_title),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp,
                                        color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary
                                    )
                                } else {
                                    Column {
                                        Text(
                                            stringResource(R.string.chatlist_title),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        AnimatedVisibility(
                                            visible = !isScrolled,
                                            enter = expandVertically(
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                            ) + fadeIn(tween(200)),
                                            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
                                        ) {
                                            Text(
                                                text = if (chats.isEmpty()) "" else "${chats.size} chats",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                val iconTint = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else LocalContentColor.current
                                val badgeColor = if (isOneUi) OneUi.IconRed else BadgeDefaults.containerColor

                                CompositionLocalProvider(LocalContentColor provides iconTint) {
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
                                                    modifier = Modifier.scale(badgeScale.value),
                                                    containerColor = badgeColor
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
                                        isOneUi = isOneUi,
                                        isExthru = false,
                                        isForge = false,
                                        isDark = isDark,
                                        hapticEnabled = hapticEnabled,
                                        onClick = { onOpenProfile() }
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                Box(modifier = Modifier.padding(bottom = if (isForge) 64.dp else 80.dp)) {
                    M3eFab(
                        showMenu = showFabMenu,
                        isOneUi = isOneUi,
                        isExthru = isExthru,
                        isForge = isForge,
                        isDark = isDark,
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
                    .let { if (isExthru && !isForge) it.haze(state = hazeState) else it }
                    .background(scaffoldBg)
            ) {
                VlAmbientGlow(appTheme = currentTheme)

                AnimatedContent(
                    targetState = chats.isEmpty(),
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "list_empty_toggle"
                ) { isEmpty ->
                    if (isEmpty) {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
                            isOneUi = isOneUi,
                            isExthru = isExthru,
                            isForge = isForge,
                            isDark = isDark
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = padding.calculateTopPadding() + if (isExthru) 12.dp else 0.dp,
                                bottom = padding.calculateBottomPadding() + 88.dp
                            )
                        ) {
                            // 📌 ЗАКРЕПЛЕННОЕ: ИЗБРАННОЕ
                            item(key = "saved_messages") {
                                val savedChat = viewModel.savedMessagesEntry
                                ChatListItem(
                                    chat = savedChat,
                                    chatType = ChatType.DIRECT,
                                    currentUid = viewModel.currentUid,
                                    otherProfile = null,
                                    draftText = drafts[savedChat.id],
                                    index = -1,
                                    total = chats.size,
                                    isOneUi = isOneUi,
                                    isExthru = isExthru,
                                    isForge = isForge,
                                    isDark = isDark,
                                    isSavedMessages = true,
                                    isCompactList = compactList,
                                    onClick = { onOpenChat(savedChat.id, viewModel.currentUid) }
                                )
                                if (isOneUi) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                        color = if (isDark) OneUi.DividerDark else OneUi.Divider
                                    )
                                } else if (chats.isNotEmpty() && !isForge) {
                                    Spacer(Modifier.height(16.dp))
                                } else if (isForge && chats.isNotEmpty() && !compactList) {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            // 📌 СПИСОК ЧАТОВ
                            if (compactList && chats.isNotEmpty()) {
                                item(key = "compact_chats_card") {
                                    val shape = if (isForge) RectangleShape else RoundedCornerShape(24.dp)
                                    val shadowMod = if (isForge) Modifier.forgeNeuBrutalism(false, isDark, 4.dp) else if (isExthru) Modifier.exthruRaisedShadow(isDark) else Modifier.shadow(4.dp, shape)
                                    val bgAlpha = if (isDark) 0.4f else 0.75f
                                    val bgColor = if (isForge) style.cardBg else (if (isDark) MaterialTheme.colorScheme.surface else Color.White).copy(alpha = bgAlpha)
                                    val borderColor = if (isForge) Color.Transparent else if (isExthru) (if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else Color.Transparent

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = if (isOneUi) 0.dp else 14.dp)
                                            .padding(bottom = 12.dp)
                                            .then(shadowMod)
                                            .background(if (isExthru && !isForge) bgColor else MaterialTheme.colorScheme.surfaceContainerLow, shape)
                                            .then(if(isForge) Modifier else Modifier.border(1.dp, borderColor, shape))
                                            .clip(shape)
                                    ) {
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
                                                isOneUi = isOneUi,
                                                isExthru = isExthru,
                                                isForge = isForge,
                                                isDark = isDark,
                                                onClick = { onOpenChat(chat.id, otherUid) }
                                            )

                                            // Внутренний разделитель
                                            if (index < chats.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                                    thickness = if (isForge) 2.dp else 0.5.dp,
                                                    color = if (isForge) (if (isDark) Color(0xFF333333) else Color.Black) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.2f else 0.4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // ── РАЗДЕЛЬНЫЕ КАРТОЧКИ ──
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
                                        index = index,
                                        total = chats.size,
                                        isOneUi = isOneUi,
                                        isExthru = isExthru,
                                        isForge = isForge,
                                        isDark = isDark,
                                        isCompactList = compactList,
                                        onClick = { onOpenChat(chat.id, otherUid) }
                                    )

                                    if (isOneUi && index < chats.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                            color = if (isDark) OneUi.DividerDark else OneUi.Divider
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Оверлей для меню FAB
                if (showFabMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isExthru) 0.5f else 0.3f))
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
}

// ─── Вспомогательные компоненты TopBar для Exthru ──────────────────────────────

@Composable
private fun ExthruTopBarButton(
    icon: ImageVector,
    isDark: Boolean,
    isForge: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "btn_scale")

    val shape = if (isForge) RectangleShape else CircleShape
    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
    } else if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(if(isForge) 1f else scale)
            .then(shadowMod)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
            .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath_alpha"
    )

    val primaryColor = when {
        isOneUi -> if (isDark) OneUi.BlueDark else OneUi.Blue
        isExthru -> MaterialTheme.colorScheme.primary // CyanGlow
        else -> MaterialTheme.colorScheme.primary
    }

    val primaryContainer = if (isOneUi) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isForge) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(breathScale)
                            .forgeNeuBrutalism(false, isDark, 4.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                } else if (isExthru) {
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
                            .background(primaryContainer.copy(alpha = breathAlpha), CircleShape)
                    )
                }

                Icon(
                    Icons.Default.ChatBubbleOutline, null,
                    modifier = Modifier.size(40.dp),
                    tint = primaryColor
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.chatlist_empty_title),
                    style = if (isExthru) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.chatlist_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = subColor
                )
            }
        }
    }
}

// ─── Chat List Item (Standalone) ──────────────────────────────────────────────

@Composable
private fun ChatListItem(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    draftText: String?,
    index: Int,
    total: Int,
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    isSavedMessages: Boolean = false,
    isCompactList: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed && isExthru && !isForge -> 0.96f
        isPressed && !isOneUi && !isForge -> 0.97f
        else -> 1f
    }

    val itemScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "item_press"
    )

    val shape = when {
        isForge -> RectangleShape
        isOneUi -> RoundedCornerShape(0.dp)
        isSavedMessages -> RoundedCornerShape(24.dp)
        else -> RoundedCornerShape(24.dp)
    }

    val verticalPadding = if (isSavedMessages) {
        if (isCompactList) 0.dp else 7.dp
    } else {
        if (isCompactList) 0.dp else 7.dp
    }

    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    val innerContent = @Composable {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isOneUi) 20.dp else 16.dp,
                vertical = 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(if (isOneUi) 50.dp else 54.dp)) {
                if (isSavedMessages) {
                    SavedMessagesIcon(isOneUi, isExthru, isForge, isDark, size = if (isOneUi) 50.dp else 54.dp)
                } else {
                    when (chatType) {
                        ChatType.DIRECT -> AvatarWithPresence(
                            avatarUrl = otherProfile?.avatarUrl,
                            displayName = chat.otherDisplayName(currentUid),
                            isOnline = otherProfile?.online ?: false,
                            size = if (isOneUi) 50.dp else 54.dp,
                            isForge = isForge
                        )
                        ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                            avatarUrl = chat.avatarUrl,
                            name = chat.name,
                            isChannel = chatType == ChatType.CHANNEL,
                            isExthru = isExthru,
                            isForge = isForge,
                            size = if (isOneUi) 50.dp else 54.dp
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (chatType != ChatType.DIRECT && !isSavedMessages) {
                        Text(if (chatType == ChatType.CHANNEL) "📢" else "👥", fontSize = 11.sp)
                    }
                    Text(
                        text = when {
                            isSavedMessages -> stringResource(R.string.saved_messages_title)
                            chatType == ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" }
                            else -> chat.name
                        },
                        fontSize = if (isOneUi) 16.sp else 16.sp,
                        fontWeight = if (isOneUi) FontWeight.Medium else FontWeight.SemiBold,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = if (isForge) MaterialTheme.colorScheme.primary else titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    chat.lastMessageAt?.let {
                        Text(
                            formatTime(it.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = if (isForge) FontFamily.Monospace else null,
                            color = subColor,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                if (!draftText.isNullOrEmpty()) {
                    Row {
                        Text(
                            "Черновик: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (isForge) FontFamily.Monospace else null,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = draftText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (isForge) FontFamily.Monospace else null,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    val messageText = chat.lastMessageText()
                    Text(
                        text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (isExthru) {
        val shadowMod = if (isForge) {
            Modifier.forgeNeuBrutalism(isPressed, isDark, 4.dp)
        } else if (isPressed) {
            Modifier.nmInsetShadow(isDark, cornerRadius = 24.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
        } else if (!isCompactList || isSavedMessages) {
            Modifier.exthruRaisedShadow(isDark)
        } else {
            Modifier // No external shadow for individual compact items
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = verticalPadding)
                .scale(if (isForge) 1f else itemScale)
                .then(shadowMod)
                .background(if (isForge) MaterialTheme.colorScheme.surface else (if (isDark) MaterialTheme.colorScheme.surface else Color.White).copy(alpha = if (isDark) 0.4f else 0.75f), shape)
                .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed || (isCompactList && !isSavedMessages)) Color.Transparent else (if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)), shape))
                .clip(shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        ) {
            Column {
                innerContent()
            }
        }
    } else {
        val bgColor = if (isOneUi) {
            if (isPressed) (if (isDark) Color(0xFF383838) else Color(0xFFE8E8E8)) else Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isOneUi) 0.dp else 12.dp)
                .padding(vertical = if (!isOneUi && !isCompactList) 4.dp else 0.dp)
                .scale(itemScale),
            shape = shape,
            color = bgColor,
            tonalElevation = 0.dp,
            onClick = onClick,
            interactionSource = interactionSource
        ) {
            innerContent()
        }
    }
}

// ─── Chat List Item (Compact) ─────────────────────────────────────────────────

@Composable
private fun ChatListItemCompact(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    draftText: String?,
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgHighlight = if (isPressed) {
        if (isForge) MaterialTheme.colorScheme.primary.copy(0.15f)
        else if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.05f)
    } else Color.Transparent

    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgHighlight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = if (isOneUi) 20.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(if (isOneUi) 50.dp else 54.dp)) {
            when (chatType) {
                ChatType.DIRECT -> AvatarWithPresence(
                    avatarUrl = otherProfile?.avatarUrl,
                    displayName = chat.otherDisplayName(currentUid),
                    isOnline = otherProfile?.online ?: false,
                    size = if (isOneUi) 50.dp else 54.dp,
                    isForge = isForge
                )
                ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                    avatarUrl = chat.avatarUrl,
                    name = chat.name,
                    isChannel = chatType == ChatType.CHANNEL,
                    isExthru = isExthru,
                    isForge = isForge,
                    size = if (isOneUi) 50.dp else 54.dp
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (chatType != ChatType.DIRECT) {
                    Text(if (chatType == ChatType.CHANNEL) "📢" else "👥", fontSize = 11.sp)
                }
                Text(
                    text = if (chatType == ChatType.DIRECT) chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" } else chat.name,
                    fontSize = if (isOneUi) 16.sp else 16.sp,
                    fontWeight = if (isOneUi) FontWeight.Medium else FontWeight.SemiBold,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = if (isForge) MaterialTheme.colorScheme.primary else titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                chat.lastMessageAt?.let {
                    Text(
                        formatTime(it.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = subColor,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (!draftText.isNullOrEmpty()) {
                Row {
                    Text(
                        "Черновик: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = draftText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                val messageText = chat.lastMessageText()
                Text(
                    text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Avatar Chip ─────────────────────────────────────────────────────────────

@Composable
private fun AvatarChip(
    avatarUrl: String?,
    displayName: String,
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "avatar_press"
    )

    val placeholderBg = when {
        isOneUi -> if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)
        isExthru -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val placeholderColor = when {
        isOneUi -> if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary
        isExthru -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val shape = if (isForge) RectangleShape else CircleShape
    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 2.dp)
    } else if (isExthru) {
        if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
        else Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(if(isForge) 1f else scale)
            .then(shadowMod)
            .background(if (isForge) MaterialTheme.colorScheme.surface else if (isExthru) MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f) else Color.Transparent, shape)
            .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed || !isExthru) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            CachedImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(color = placeholderBg, shape = shape, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isForge) FontFamily.Monospace else null,
                        color = placeholderColor
                    )
                }
            }
        }
    }
}

// ─── GroupChannel Avatar ──────────────────────────────────────────────────────

@Composable
fun GroupChannelAvatar(
    avatarUrl: String?,
    name: String,
    isChannel: Boolean,
    isExthru: Boolean = false,
    isForge: Boolean = false,
    size: Dp = 48.dp
) {
    val bgBrush = when {
        isExthru -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
        )
        isChannel -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
    }

    val textColor = when {
        isExthru -> MaterialTheme.colorScheme.primary // CyanGlow
        isChannel -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = if (isForge) RectangleShape else CircleShape

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(bgBrush)
            .then(if(isForge) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            CachedImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = if (isChannel) "📢" else (name.firstOrNull()?.uppercase() ?: "G"),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isForge) FontFamily.Monospace else null,
                color = textColor
            )
        }
    }
}

// ─── M3E & OneUI & Exthru FAB ─────────────────────────────────────────────────

@Composable
private fun M3eFab(
    showMenu: Boolean,
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onFindChannel: () -> Unit
) {
    val haptic = rememberHaptic()
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val fabItems = listOf(
            Triple(Icons.Default.Tag, stringResource(R.string.chatlist_fab_find_channel), onFindChannel),
            Triple(Icons.Default.Group, stringResource(R.string.chatlist_fab_new_group), onNewGroup),
            Triple(Icons.Default.PersonAdd, stringResource(R.string.chatlist_fab_new_chat), onNewChat)
        )

        val menuChipBg = when {
            isOneUi -> if (isDark) OneUi.CardBgDark else OneUi.CardBg
            isExthru -> MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.85f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val menuChipText = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface

        val menuIconBg = when {
            isOneUi -> if (isDark) OneUi.CardBgDark else OneUi.CardBg
            isExthru -> MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.85f)
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
        val menuIconTint = when {
            isOneUi -> if (isDark) OneUi.BlueDark else OneUi.Blue
            isExthru -> MaterialTheme.colorScheme.primary // CyanGlow
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }

        fabItems.forEachIndexed { index, (icon, label, action) ->
            val delayMs = index * 50L
            AnimatedVisibility(
                visible = showMenu,
                enter = slideInVertically(
                    initialOffsetY = { it * (3 - index) / 2 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                ) + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(tween(150, delayMillis = delayMs.toInt())),
                exit = scaleOut(targetScale = 0.7f, animationSpec = tween(100, delayMillis = ((2 - index) * 30L).toInt())) + fadeOut(tween(80))
            ) {
                FabMenuItem(
                    icon = icon, label = label, onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); action() },
                    chipBg = menuChipBg, chipText = menuChipText,
                    iconBg = menuIconBg, iconTint = menuIconTint,
                    isExthru = isExthru, isForge = isForge, isDark = isDark
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val fabScale by animateFloatAsState(
            targetValue = if (isPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
            label = "fab_scale"
        )

        val fabBgOpen = when {
            isOneUi -> OneUi.IconRed
            isExthru -> MaterialTheme.colorScheme.error // PinkFlash
            else -> MaterialTheme.colorScheme.errorContainer
        }
        val fabBgClosed = when {
            isOneUi -> if (isDark) OneUi.BlueDark else OneUi.Blue
            isExthru -> MaterialTheme.colorScheme.primary // CyanGlow
            else -> MaterialTheme.colorScheme.primary
        }

        val fabContentOpen = if (isOneUi || isExthru) Color.White else MaterialTheme.colorScheme.onErrorContainer
        val fabContentClosed = if (isOneUi || isExthru) Color.White else MaterialTheme.colorScheme.onPrimary

        if (isExthru) {
            val fabShape = if (isForge) RectangleShape else if (showMenu) RoundedCornerShape(16.dp) else CircleShape
            val shadowMod = if (isForge) Modifier.forgeNeuBrutalism(isPressed, isDark, 4.dp) else if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = if (showMenu) 16.dp else 28.dp) else Modifier.exthruSmallRaisedShadow(isDark)

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(if(isForge) 1f else fabScale)
                    .then(shadowMod)
                    .background(if (showMenu) fabBgOpen else fabBgClosed, fabShape)
                    .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), fabShape))
                    .clip(fabShape)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showMenu,
                    transitionSpec = {
                        scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150)) togetherWith
                                scaleOut(targetScale = 0.4f, animationSpec = tween(100)) + fadeOut(tween(80))
                    },
                    label = "fab_icon_morph"
                ) { isOpen ->
                    val rotation by animateFloatAsState(targetValue = if (isOpen) 45f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "fab_rot")
                    Icon(
                        imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                        tint = if (isOpen) fabContentOpen else fabContentClosed
                    )
                }
            }
        } else {
            FloatingActionButton(
                onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); onToggle() },
                modifier = Modifier.scale(fabScale),
                containerColor = if (showMenu) fabBgOpen else fabBgClosed,
                contentColor = if (showMenu) fabContentOpen else fabContentClosed,
                shape = if (showMenu) RoundedCornerShape(16.dp) else CircleShape
            ) {
                AnimatedContent(
                    targetState = showMenu,
                    transitionSpec = {
                        scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150)) togetherWith
                                scaleOut(targetScale = 0.4f, animationSpec = tween(100)) + fadeOut(tween(80))
                    },
                    label = "fab_icon_morph"
                ) { isOpen ->
                    val rotation by animateFloatAsState(targetValue = if (isOpen) 45f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "fab_rot")
                    Icon(
                        imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        }
    }
}

// ─── FAB Menu Item ────────────────────────────────────────────────────────────

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    chipBg: Color,
    chipText: Color,
    iconBg: Color,
    iconTint: Color,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "fab_item_press"
    )

    Row(
        modifier = Modifier.scale(if(isForge) 1f else scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isExthru) {
            val shape = if (isForge) RectangleShape else RoundedCornerShape(12.dp)
            val shadowMod = if (isForge) Modifier.forgeNeuBrutalism(isPressed, isDark, 2.dp) else if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 12.dp) else Modifier.exthruSmallRaisedShadow(isDark)

            Box(
                modifier = Modifier
                    .then(shadowMod)
                    .background(chipBg, shape)
                    .then(if(isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontFamily = if(isForge) FontFamily.Monospace else null, fontWeight = FontWeight.Medium, color = chipText)
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .then(shadowMod)
                    .background(iconBg, shape)
                    .then(if(isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = iconTint)
            }
        } else {
            Surface(
                color = chipBg,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 2.dp
            ) {
                Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = chipText)
            }

            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = iconBg,
                contentColor = iconTint,
                shape = RoundedCornerShape(14.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(icon, null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
fun SavedMessagesIcon(
    isOneUi: Boolean,
    isExthru: Boolean,
    isForge: Boolean,
    isDark: Boolean,
    size: Dp
) {
    val accent = MaterialTheme.colorScheme.primary
    val bgGradient = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.35f else 0.25f),
            accent.copy(alpha = if (isDark) 0.15f else 0.05f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.3f else 0.6f),
            Color.Transparent,
            accent.copy(alpha = 0.4f)
        )
    )

    val shape = if (isForge) RectangleShape else CircleShape
    val shadowMod = if (isForge) Modifier.forgeNeuBrutalism(false, isDark, 2.dp) else if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier.shadow(8.dp, shape)

    Box(
        modifier = Modifier
            .size(size)
            .then(shadowMod)
            .background(bgGradient, shape)
            .then(if (isForge) Modifier else Modifier.border(1.5.dp, borderBrush, shape)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size((size.value * 0.45f).dp),
            tint = accent
        )
    }
}