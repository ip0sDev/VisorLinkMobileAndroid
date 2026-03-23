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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
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
    viewModel: ChatListViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val profileCache by viewModel.profileCache.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val isOneUi = currentTheme == AppTheme.ONE_UI
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val haptic = rememberHaptic()

    var showFabMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // TopBar collapse — скрываем subtitle при прокрутке
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        containerColor = if (isOneUi) (if (isDark) OneUi.PageBgDark else OneUi.PageBg) else MaterialTheme.colorScheme.surface,
        topBar = {
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
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
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
                        BadgedBox(badge = {
                            if (unreadNotifications > 0) {
                                Badge(
                                    modifier = Modifier.scale(badgeScale.value),
                                    containerColor = if (isOneUi) OneUi.IconRed else BadgeDefaults.containerColor
                                ) {
                                    Text("$unreadNotifications")
                                }
                            }
                        }) {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, true)
                                onOpenNotifications()
                            }) {
                                Icon(
                                    if (unreadNotifications > 0) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                    stringResource(R.string.notifications_title)
                                )
                            }
                        }

                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, true)
                            onOpenSearch()
                        }) {
                            Icon(Icons.Default.Search, stringResource(R.string.action_search))
                        }

                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, true)
                            onOpenSettings()
                        }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                        }

                        AvatarChip(
                            avatarUrl = currentUser?.avatarUrl,
                            displayName = currentUser?.displayName ?: "",
                            isOneUi = isOneUi,
                            isDark = isDark,
                            onClick = {
                                haptic.perform(HapticType.CLICK, true)
                                onOpenProfile()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isOneUi) (if (isDark) OneUi.PageBgDark else OneUi.PageBg) else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (isOneUi) (if (isDark) OneUi.PageBgDark else OneUi.PageBg) else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        floatingActionButton = {
            M3eFab(
                showMenu = showFabMenu,
                isOneUi = isOneUi,
                isDark = isDark,
                onToggle = {
                    haptic.perform(HapticType.SELECTION, true)
                    showFabMenu = !showFabMenu
                },
                onNewChat = {
                    haptic.perform(HapticType.CLICK, true)
                    showFabMenu = false; onOpenSearch()
                },
                onNewGroup = {
                    haptic.perform(HapticType.CLICK, true)
                    showFabMenu = false; onCreateChat()
                },
                onFindChannel = {
                    haptic.perform(HapticType.CLICK, true)
                    showFabMenu = false; onFindChannel()
                }
            )
        }
    ) { padding ->

        if (showFabMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.perform(HapticType.CLICK, true)
                        showFabMenu = false
                    }
            )
        }

        AnimatedContent(
            targetState = chats.isEmpty(),
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "list_empty_toggle"
        ) { isEmpty ->
            if (isEmpty) {
                EmptyState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    isOneUi = isOneUi,
                    isDark = isDark
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    itemsIndexed(
                        items = chats,
                        key = { _, chat -> chat.id }
                    ) { index, chat ->
                        val chatType = chat.chatType()
                        val otherUid = when (chatType) {
                            ChatType.DIRECT -> chat.otherParticipantId(viewModel.currentUid)
                            else -> chat.id
                        }

                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay((index * 30L).coerceAtMost(180L))
                            visible = true
                        }

                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically(
                                initialOffsetY = { it / 3 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(tween(220))
                        ) {
                            Column {
                                ChatListItem(
                                    chat = chat,
                                    chatType = chatType,
                                    currentUid = viewModel.currentUid,
                                    otherProfile = if (chatType == ChatType.DIRECT) profileCache[otherUid] else null,
                                    index = index,
                                    total = chats.size,
                                    isOneUi = isOneUi,
                                    isDark = isDark,
                                    onClick = { onOpenChat(chat.id, otherUid) }
                                )
                                // В One UI часто используют тонкий разделитель между элементами списка
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
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier, isOneUi: Boolean, isDark: Boolean) {
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

    val primaryColor = if (isOneUi) (if (isDark) OneUi.BlueDark else OneUi.Blue) else MaterialTheme.colorScheme.primary
    val primaryContainer = if (isOneUi) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(breathScale)
                        .background(primaryContainer.copy(alpha = breathAlpha), CircleShape)
                )
                Icon(
                    Icons.Default.ChatBubbleOutline, null,
                    modifier = Modifier.size(40.dp),
                    tint = primaryColor
                )
            }
            Text(
                stringResource(R.string.chatlist_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                stringResource(R.string.chatlist_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = subColor
            )
        }
    }
}

// ─── Chat List Item ───────────────────────────────────────────────────────────

private fun itemShape(index: Int, total: Int): RoundedCornerShape {
    val big = 20
    val small = 4
    return when {
        total == 1 -> RoundedCornerShape(big.dp)
        index == 0 -> RoundedCornerShape(topStart = big.dp, topEnd = big.dp, bottomStart = small.dp, bottomEnd = small.dp)
        index == total - 1 -> RoundedCornerShape(topStart = small.dp, topEnd = small.dp, bottomStart = big.dp, bottomEnd = big.dp)
        else -> RoundedCornerShape(small.dp)
    }
}

@Composable
private fun ChatListItem(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    index: Int,
    total: Int,
    isOneUi: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = if (isPressed && !isOneUi) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "item_press"
    )

    val shape = if (isOneUi) RoundedCornerShape(0.dp) else itemShape(index, total)
    val topPad    = if (index == 0 && !isOneUi) 2.dp else if (!isOneUi) 1.dp else 0.dp
    val bottomPad = if (index == total - 1 && !isOneUi) 2.dp else if (!isOneUi) 1.dp else 0.dp

    val bgColor = if (isOneUi) {
        if (isPressed) (if (isDark) Color(0xFF383838) else Color(0xFFE8E8E8)) else Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isOneUi) Modifier else Modifier.padding(horizontal = 12.dp).padding(top = topPad, bottom = bottomPad))
            .scale(itemScale),
        shape = shape,
        color = bgColor,
        tonalElevation = 0.dp,
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isOneUi) 20.dp else 14.dp, vertical = if (isOneUi) 14.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(if (isOneUi) 50.dp else 54.dp)) {
                when (chatType) {
                    ChatType.DIRECT -> AvatarWithPresence(
                        avatarUrl = otherProfile?.avatarUrl,
                        displayName = chat.otherDisplayName(currentUid),
                        isOnline = otherProfile?.online ?: false,
                        size = if (isOneUi) 50.dp else 54.dp
                    )
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = chat.avatarUrl,
                        name = chat.name,
                        isChannel = chatType == ChatType.CHANNEL,
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
                        text = when (chatType) {
                            ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" }
                            else -> chat.name
                        },
                        fontSize = if (isOneUi) 16.sp else 14.sp,
                        fontWeight = if (isOneUi) FontWeight.Medium else FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    chat.lastMessageAt?.let {
                        Text(
                            formatTime(it.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    chat.lastMessage ?: stringResource(R.string.chatlist_no_messages),
                    style = MaterialTheme.typography.bodySmall,
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
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "avatar_press"
    )

    val placeholderBg = if (isOneUi) (if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)) else MaterialTheme.colorScheme.primaryContainer
    val placeholderColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(color = placeholderBg, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
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
    size: Dp = 48.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isChannel) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
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
                color = if (isChannel) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ─── M3E & OneUI FAB ─────────────────────────────────────────────────────────

@Composable
private fun M3eFab(
    showMenu: Boolean,
    isOneUi: Boolean,
    isDark: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onFindChannel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val fabItems = listOf(
            Triple(Icons.Default.Tag, stringResource(R.string.chatlist_fab_find_channel), onFindChannel),
            Triple(Icons.Default.Group, stringResource(R.string.chatlist_fab_new_group), onNewGroup),
            Triple(Icons.Default.PersonAdd, stringResource(R.string.chatlist_fab_new_chat), onNewChat)
        )

        val menuChipBg = if (isOneUi) (if (isDark) OneUi.CardBgDark else OneUi.CardBg) else MaterialTheme.colorScheme.surfaceContainerHigh
        val menuChipText = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
        val menuIconBg = if (isOneUi) (if (isDark) OneUi.CardBgDark else OneUi.CardBg) else MaterialTheme.colorScheme.secondaryContainer
        val menuIconTint = if (isOneUi) (if (isDark) OneUi.BlueDark else OneUi.Blue) else MaterialTheme.colorScheme.onSecondaryContainer

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
                    icon = icon, label = label, onClick = action,
                    chipBg = menuChipBg, chipText = menuChipText, iconBg = menuIconBg, iconTint = menuIconTint
                )
            }
        }

        val fabScale = remember { Animatable(1f) }
        val fabScope = rememberCoroutineScope()

        val fabBgOpen = if (isOneUi) OneUi.IconRed else MaterialTheme.colorScheme.errorContainer
        val fabBgClosed = if (isOneUi) (if (isDark) OneUi.BlueDark else OneUi.Blue) else MaterialTheme.colorScheme.primary
        val fabContentOpen = if (isOneUi) Color.White else MaterialTheme.colorScheme.onErrorContainer
        val fabContentClosed = if (isOneUi) Color.White else MaterialTheme.colorScheme.onPrimary

        FloatingActionButton(
            onClick = {
                fabScope.launch {
                    fabScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy))
                    fabScale.animateTo(1.1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                    fabScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onToggle()
            },
            modifier = Modifier.scale(fabScale.value),
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
                val rotation by animateFloatAsState(
                    targetValue = if (isOpen) 45f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "fab_rotation"
                )
                Icon(
                    imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }
                )
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
    iconTint: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "fab_item_press"
    )

    Row(
        modifier = Modifier.scale(scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = chipBg,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = chipText
            )
        }

        SmallFloatingActionButton(
            onClick = onClick,
            interactionSource = interactionSource,
            containerColor = iconBg,
            contentColor = iconTint,
            shape = RoundedCornerShape(14.dp),
            elevation = FloatingActionButtonDefaults.elevation(2.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
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