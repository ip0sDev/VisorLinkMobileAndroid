package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.TopbarStatus
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.ui.theme.nmDividerBottom
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════════
//  Exthru Chat Top Bar
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
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.nmDividerBottom(),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ExthruChat.barBg(isDark)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = ExthruChat.Accent,
                        modifier = Modifier.size(18.dp),
                    )
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                    color = ExthruChat.Online,
                                )
                                is TopbarStatus.LastSeen -> Text(
                                    status.ts?.let { ts ->
                                        stringResource(R.string.last_seen,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                                    } ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                    color = ExthruChat.textHint(isDark),
                                )
                                else -> Text("", fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                    color = ExthruChat.textHint(isDark))
                            }
                        }
                        ChatType.GROUP -> {
                            val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                            val online = uiState.onlineCount
                            Text(buildString {
                                append(stringResource(R.string.members, memberCount))
                                if (online > 0) append(stringResource(R.string.online, online))
                            }, fontSize = 11.sp, fontWeight = FontWeight.Normal,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                color = ExthruChat.textHint(isDark))
                        }
                        ChatType.CHANNEL -> Text(
                            stringResource(R.string.subscribers, uiState.chat?.memberCount ?: 0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            color = ExthruChat.textHint(isDark),
                        )
                    }
                }
            }
        },
        actions = {
            if (canSetWallpaper) {
                IconButton(onClick = onWallpaperClick) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null) ExthruChat.Accent else ExthruChat.textSecondary(isDark))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings),
                        tint = ExthruChat.textSecondary(isDark))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = onLeaveClick) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = ExthruChat.Destructive)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ExthruChat.barBg(isDark),
            scrolledContainerColor = ExthruChat.barBg(isDark),
        ),
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  OneUI Chat Top Bar
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
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    val bgColor      = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val textPrimary  = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary
    val textSecondary = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
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
                        fontSize = 18.sp, // Увеличено с 16.sp
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
                IconButton(onClick = onWallpaperClick) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null)
                            (if (isDark) OneUiChat.BlueDark else OneUiChat.Blue) else textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = textPrimary)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = onLeaveClick) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, scrolledContainerColor = bgColor),
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  Default (M3) Chat Top Bar
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultChatTopBar(
    uiState: ChatUiState,
    otherUid: String,
    chatId: String,
    canSetWallpaper: Boolean,
    isAdmin: Boolean,
    isOwner: Boolean,
    onWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onLeaveClick: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
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
                        fontSize = 18.sp, // Установлен явный увеличенный размер
                        fontWeight = FontWeight.Bold, // Изменено с SemiBold
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
                IconButton(onClick = onWallpaperClick) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner) {
                IconButton(onClick = onLeaveClick) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}