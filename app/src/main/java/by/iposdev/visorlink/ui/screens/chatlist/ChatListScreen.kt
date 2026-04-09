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
import by.iposdev.visorlink.ui.theme.*
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
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val haptic = rememberHaptic()

    var showFabMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // TopBar collapse — скрываем subtitle при прокрутке
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }


    Scaffold(
        containerColor = when {
            isOneUi -> if (isDark) OneUi.PageBgDark else OneUi.PageBg
            isExthru -> MaterialTheme.colorScheme.background // MidWater / DarkMidWater
            else -> MaterialTheme.colorScheme.surface
        },
        topBar = {
            Surface(
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        when {
                            isOneUi -> {
                                Text(
                                    stringResource(R.string.chatlist_title),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary
                                )
                            }
                            isExthru -> {
                                // ИСПРАВЛЕНИЕ 1: Увеличили размер для узкого шрифта Moniqa и задали Bold
                                Text(
                                    text = stringResource(R.string.chatlist_title),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            else -> {
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
                        }
                    },
                    actions = {
                        val iconTint = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else LocalContentColor.current
                        val badgeColor = when {
                            isOneUi -> OneUi.IconRed
                            isExthru -> MaterialTheme.colorScheme.error // PinkFlash
                            else -> BadgeDefaults.containerColor
                        }

                        // ИСПРАВЛЕНИЕ 2: Чистый модификатор для кнопок без padding'ов, которые ломают форму
                        val exthruBtnModifier = Modifier
                            .size(42.dp) // Единый строгий размер для всех кнопок
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)


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
                                        ) {
                                            Text("$unreadNotifications")
                                        }
                                    }
                                }
                            ) {
                                IconButton(
                                    modifier = if (isExthru) exthruBtnModifier else Modifier,
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, true)
                                        onOpenNotifications()
                                    }
                                ) {
                                    Icon(
                                        if (unreadNotifications > 0) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                        stringResource(R.string.notifications_title),
                                        modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                                    )
                                }
                            }

                            // Добавляем отступы снаружи, а не внутрь модификатора кнопок
                            if (isExthru) Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                modifier = if (isExthru) exthruBtnModifier else Modifier,
                                onClick = {
                                    haptic.perform(HapticType.CLICK, true)
                                    onOpenSearch()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    stringResource(R.string.action_search),
                                    modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                                )
                            }

                            if (isExthru) Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                modifier = if (isExthru) exthruBtnModifier else Modifier,
                                onClick = {
                                    haptic.perform(HapticType.CLICK, true)
                                    onOpenSettings()
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    stringResource(R.string.settings_title),
                                    modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                                )
                            }

                            if (isExthru) Spacer(modifier = Modifier.width(10.dp))

                            AvatarChip(
                                avatarUrl = currentUser?.avatarUrl,
                                displayName = currentUser?.displayName ?: "",
                                isOneUi = isOneUi,
                                isExthru = isExthru,
                                isDark = isDark,
                                onClick = {
                                    haptic.perform(HapticType.CLICK, true)
                                    onOpenProfile()
                                }
                            )

                            if (isExthru) Spacer(modifier = Modifier.width(6.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = when {
                            isExthru -> Color.Transparent // Surface background handles it
                            isOneUi -> if (isDark) OneUi.PageBgDark else OneUi.PageBg
                            else -> MaterialTheme.colorScheme.surface
                        },
                        scrolledContainerColor = when {
                            isExthru -> Color.Transparent
                            isOneUi -> if (isDark) OneUi.PageBgDark else OneUi.PageBg
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                )
            }
        },
        floatingActionButton = {
            M3eFab(
                showMenu = showFabMenu,
                isOneUi = isOneUi,
                isExthru = isExthru,
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
                    .background(Color.Black.copy(alpha = if (isExthru) 0.5f else 0.3f))
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
                    isExthru = isExthru,
                    isDark = isDark
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        top = if (isExthru) 12.dp else 0.dp,
                        bottom = 88.dp
                    )
                ) {
                    // 📌 ЗАКРЕПЛЕННОЕ: ИЗБРАННОЕ
                    item(key = "saved_messages") {
                        val savedChat = viewModel.savedMessagesEntry

                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true } // Появляется сразу

                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(300)) + expandVertically()
                        ) {
                            Column {
                                ChatListItem(
                                    chat = savedChat,
                                    chatType = ChatType.DIRECT,
                                    currentUid = viewModel.currentUid,
                                    otherProfile = null, // Для избранного профиль не нужен
                                    index = -1, // Специальный индекс
                                    total = chats.size,
                                    isOneUi = isOneUi,
                                    isExthru = isExthru,
                                    isDark = isDark,
                                    isSavedMessages = true, // Новый флаг
                                    onClick = { onOpenChat(savedChat.id, viewModel.currentUid) }
                                )
                                if (isOneUi) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                        color = if (isDark) OneUi.DividerDark else OneUi.Divider
                                    )
                                }
                            }
                        }
                    }
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
                                    isExthru = isExthru,
                                    isDark = isDark,
                                    onClick = { onOpenChat(chat.id, otherUid) }
                                )
                                // В One UI используют тонкий разделитель, в Exthru разделителей нет - только тени
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
private fun EmptyState(
    modifier: Modifier = Modifier,
    isOneUi: Boolean,
    isExthru: Boolean,
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
                if (isExthru) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(breathScale)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
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
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.chatlist_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor
                )
            }
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
    isExthru: Boolean,
    isDark: Boolean,
    isSavedMessages: Boolean = false, // Добавили параметр
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed && isExthru -> 0.94f
        isPressed && !isOneUi -> 0.97f
        else -> 1f
    }

    val itemScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "item_press"
    )

    val shape = when {
        isOneUi -> RoundedCornerShape(0.dp)
        isExthru -> ShapesExthru.medium // 18dp
        else -> itemShape(index, total)
    }

    val topPad    = if (index == 0 && !isOneUi && !isExthru) 2.dp else if (!isOneUi && !isExthru) 1.dp else 0.dp
    val bottomPad = if (index == total - 1 && !isOneUi && !isExthru) 2.dp else if (!isOneUi && !isExthru) 1.dp else 0.dp

    val bgColor = when {
        isOneUi -> if (isPressed) (if (isDark) Color(0xFF383838) else Color(0xFFE8E8E8)) else Color.Transparent
        isExthru -> MaterialTheme.colorScheme.surface // DeepWater
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val titleColor = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOneUi) (if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary) else MaterialTheme.colorScheme.onSurfaceVariant

    val itemModifier = Modifier
        .fillMaxWidth()
        .then(
            when {
                isOneUi -> Modifier
                isExthru -> Modifier
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .exthruRaisedShadow(isDark)
                else -> Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = topPad, bottom = bottomPad)
            }
        )
        .scale(itemScale)

    Surface(
        modifier = itemModifier,
        shape = shape,
        color = bgColor,
        tonalElevation = 0.dp,
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isOneUi) 20.dp else 14.dp,
                vertical = if (isOneUi) 14.dp else 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(if (isOneUi) 50.dp else 54.dp)) {
                if (isSavedMessages) {
                    // Иконка для Избранного
                    SavedMessagesIcon(isOneUi, isExthru, isDark, size = if (isOneUi) 50.dp else 54.dp)
                } else {
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
                            isExthru = isExthru,
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
                    if (chatType != ChatType.DIRECT) {
                        Text(if (chatType == ChatType.CHANNEL) "📢" else "👥", fontSize = 11.sp)
                    }
                    Text(
                        text = when {
                            // 1. Сначала проверяем на "Избранное"
                            isSavedMessages -> stringResource(R.string.saved_messages_title) // "Избранное"

                            // 2. Если не избранное, проверяем тип DIRECT
                            chatType == ChatType.DIRECT -> {
                                chat.otherDisplayName(currentUid)
                                    .ifEmpty { "@${chat.otherUsername(currentUid)}" }
                            }

                            // 3. Во всех остальных случаях (группы, каналы) берем имя чата
                            else -> chat.name
                        },
                        fontSize = if (isOneUi) 16.sp else 15.sp,
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
                // ИСПОЛЬЗУЕМ НОВУЮ ФУНКЦИЮ ДЛЯ ОТОБРАЖЕНИЯ
                val messageText = chat.lastMessageText()
                Text(
                    text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
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
    isExthru: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed && isExthru -> 0.86f // Сильный spring для неоморфизма
        isPressed -> 0.90f
        else -> 1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
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

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
            .scale(scale)
            .then(if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
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
    isExthru: Boolean = false,
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

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgBrush),
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
    isDark: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onFindChannel: () -> Unit
) {
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
            isExthru -> MaterialTheme.colorScheme.surface // DeepWater
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val menuChipText = if (isOneUi) (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary) else MaterialTheme.colorScheme.onSurface

        val menuIconBg = when {
            isOneUi -> if (isDark) OneUi.CardBgDark else OneUi.CardBg
            isExthru -> MaterialTheme.colorScheme.surface // DeepWater
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
                    icon = icon, label = label, onClick = action,
                    chipBg = menuChipBg, chipText = menuChipText,
                    iconBg = menuIconBg, iconTint = menuIconTint,
                    isExthru = isExthru, isDark = isDark
                )
            }
        }

        val fabScale = remember { Animatable(1f) }
        val fabScope = rememberCoroutineScope()

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

        FloatingActionButton(
            onClick = {
                fabScope.launch {
                    fabScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy))
                    fabScale.animateTo(1.1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                    fabScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                onToggle()
            },
            modifier = Modifier
                .scale(fabScale.value)
                .then(if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier),
            containerColor = if (showMenu) fabBgOpen else fabBgClosed,
            contentColor = if (showMenu) fabContentOpen else fabContentClosed,
            shape = if (showMenu) RoundedCornerShape(16.dp) else CircleShape,
            elevation = if (isExthru) FloatingActionButtonDefaults.elevation(0.dp) else FloatingActionButtonDefaults.elevation()
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
    iconTint: Color,
    isExthru: Boolean,
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
        modifier = Modifier.scale(scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val chipModifier = Modifier
            .then(if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)

        Surface(
            modifier = chipModifier,
            color = chipBg,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (isExthru) 0.dp else 3.dp,
            shadowElevation = if (isExthru) 0.dp else 2.dp
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = chipText
            )
        }

        val iconBtnModifier = Modifier
            .then(if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)

        SmallFloatingActionButton(
            modifier = iconBtnModifier,
            onClick = onClick,
            interactionSource = interactionSource,
            containerColor = iconBg,
            contentColor = iconTint,
            shape = RoundedCornerShape(14.dp),
            elevation = if (isExthru) FloatingActionButtonDefaults.elevation(0.dp) else FloatingActionButtonDefaults.elevation(2.dp)
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
@Composable
fun SavedMessagesIcon(
    isOneUi: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    size: Dp
) {
    // Красивые Teal-градиенты
    val bgGradient = when {
        isExthru -> Brush.linearGradient(
            listOf(Color(0xFF1DE9B6), Color(0xFF00BFA5)) // Яркий Teal (светлее к темному)
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF4DB6AC), Color(0xFF00695C)) // Глубокий, насыщенный Teal
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            // Добавляем тень ДО фона и явно указываем, что она должна быть круглой!
            .then(if (isExthru) Modifier.shadow(8.dp, CircleShape) else Modifier)
            // Заливаем фон и сразу обрезаем в круг (заменяет clip)
            .background(bgGradient, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size((size.value * 0.5f).dp),
            tint = Color.White
        )
    }
}