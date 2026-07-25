package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.TopbarStatus
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════════
//  Exthru Chat Top Bar (Glassmorphism & Neomorphism)
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExthruChatTopBar(
    uiState: ChatUiState,
    otherUid: String,
    chatId: String,
    isDark: Boolean,
    canSetWallpaper: Boolean,
    isAdmin: Boolean,
    isOwner: Boolean,
    hapticEnabled: Boolean,
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    val hazeState = LocalHazeState.current

    TopAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)),
        navigationIcon = {
            InteractiveTopBarIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                isDark = isDark,
                hapticEnabled = hapticEnabled,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                onClick = onNavigateBack
            )
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        when (uiState.chatType) {
                            ChatType.DIRECT -> onOpenOtherProfile(otherUid)
                            else -> onOpenChatSettings(chatId)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                when (uiState.chatType) {
                    ChatType.DIRECT -> AvatarWithPresence(
                        avatarUrl = uiState.otherUser?.avatarUrl,
                        displayName = uiState.otherUser?.displayName ?: "",
                        isOnline = uiState.topbarStatus is TopbarStatus.Online || uiState.topbarStatus is TopbarStatus.Typing,
                        size = 36.dp,
                    )
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp,
                        isExthru = true
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (uiState.chatType) {
                            ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                            else -> uiState.chat?.name ?: ""
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExthruChat.textPrimary(isDark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status",
                        ) { status ->
                            when (status) {
                                is TopbarStatus.Typing -> TypingDots(primaryColor = ExthruChat.Accent)
                                is TopbarStatus.Online -> Text(
                                    stringResource(R.string.chat_status_online),
                                    fontSize = 11.sp, fontWeight = FontWeight.Normal,
                                    color = ExthruChat.Online,
                                )
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "",
                                    fontSize = 11.sp, fontWeight = FontWeight.Normal,
                                    color = ExthruChat.textHint(isDark),
                                )
                                else -> Text("", fontSize = 11.sp, color = ExthruChat.textHint(isDark))
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members, memberCount))
                                if (online > 0) append(stringResource(R.string.online, online))
                            }, fontSize = 11.sp, fontWeight = FontWeight.Normal, color = ExthruChat.textHint(isDark))
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers, uiState.chat?.memberCount ?: 0),
                            fontSize = 11.sp, fontWeight = FontWeight.Normal, color = ExthruChat.textHint(isDark),
                        )
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                InteractiveTopBarIcon(
                    icon = Icons.Default.Wallpaper,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    tint = if (uiState.wallpaperUrl != null) ExthruChat.Accent else ExthruChat.textSecondary(isDark),
                    onClick = onWallpaperClick
                )
                Spacer(Modifier.width(6.dp))
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                InteractiveTopBarIcon(
                    icon = Icons.Default.Settings,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    tint = ExthruChat.textSecondary(isDark),
                    onClick = { onOpenChatSettings(chatId) }
                )
                Spacer(Modifier.width(6.dp))
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                InteractiveTopBarIcon(
                    icon = Icons.Default.ExitToApp,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    tint = ExthruChat.Destructive,
                    onClick = onLeaveClick
                )
                Spacer(Modifier.width(6.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun InteractiveTopBarIcon(
    icon: ImageVector,
    isDark: Boolean,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "icon_scale"
    )

    val shadowMod = if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .scale(scale)
            .then(shadowMod)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  OneUI / M3 Top Bars
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneUiChatTopBar(
    uiState: ChatUiState,
    otherUid: String,
    chatId: String,
    isDark: Boolean,
    canSetWallpaper: Boolean,
    isAdmin: Boolean,
    isOwner: Boolean,
    hapticEnabled: Boolean,
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    val bgColor      = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val textPrimary  = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary
    val textSecondary = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary
    val haptic = rememberHaptic()

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFECECEC)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = textPrimary, modifier = Modifier.size(18.dp))
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    when (uiState.chatType) {
                        ChatType.DIRECT -> onOpenOtherProfile(otherUid)
                        else -> onOpenChatSettings(chatId)
                    }
                },
            ) {
                when (uiState.chatType) {
                    ChatType.DIRECT -> AvatarWithPresence(
                        avatarUrl = uiState.otherUser?.avatarUrl,
                        displayName = uiState.otherUser?.displayName ?: "",
                        isOnline = uiState.topbarStatus is TopbarStatus.Online ||
                                uiState.topbarStatus is TopbarStatus.Typing,
                        size = 36.dp,
                    )
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (uiState.chatType) {
                            ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                            else -> uiState.chat?.name ?: ""
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status",
                        ) { status ->
                            when (status) {
                                is TopbarStatus.Typing -> TypingDots(primaryColor = if (isDark) OneUiChat.BlueDark else OneUiChat.Blue)
                                is TopbarStatus.Online -> Text(stringResource(R.string.chat_status_online),
                                    fontSize = 11.sp, color = Color(0xFF22C55E))
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "", fontSize = 11.sp, color = textSecondary)
                                else -> Text("", fontSize = 11.sp, color = textSecondary)
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members, memberCount))
                                if (online > 0) append(stringResource(R.string.online, online))
                            }, fontSize = 11.sp, color = textSecondary)
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers, uiState.chat?.memberCount ?: 0),
                            fontSize = 11.sp, color = textSecondary)
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onWallpaperClick() }) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null)
                            (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue) else textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onLeaveClick() }) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, scrolledContainerColor = bgColor),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultChatTopBar(
    uiState: ChatUiState,
    otherUid: String,
    chatId: String,
    canSetWallpaper: Boolean,
    isAdmin: Boolean,
    isOwner: Boolean,
    hapticEnabled: Boolean,
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    val haptic = rememberHaptic()

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    when (uiState.chatType) {
                        ChatType.DIRECT -> onOpenOtherProfile(otherUid)
                        else -> onOpenChatSettings(chatId)
                    }
                },
            ) {
                when (uiState.chatType) {
                    ChatType.DIRECT -> AvatarWithPresence(
                        avatarUrl = uiState.otherUser?.avatarUrl,
                        displayName = uiState.otherUser?.displayName ?: "",
                        isOnline = uiState.topbarStatus is TopbarStatus.Online ||
                                uiState.topbarStatus is TopbarStatus.Typing,
                        size = 36.dp,
                    )
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (uiState.chatType) {
                            ChatType.DIRECT -> uiState.otherUser?.displayName ?: ""
                            else -> uiState.chat?.name ?: ""
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status",
                        ) { status ->
                            when (status) {
                                is TopbarStatus.Typing  -> TypingDots()
                                is TopbarStatus.Online  -> Text(stringResource(R.string.chat_status_online),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF22C55E), fontSize = 11.sp)
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen_topbar,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                else -> Text("", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members_topbar, memberCount))
                                if (online > 0) append(stringResource(R.string.online_topbar, online))
                            }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers_topbar, uiState.chat?.memberCount ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onWallpaperClick() }) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onLeaveClick() }) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}