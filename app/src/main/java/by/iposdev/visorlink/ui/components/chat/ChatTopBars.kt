package by.iposdev.visorlink.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.TopbarStatus
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.VlTopAppBar
import by.iposdev.visorlink.ui.screens.chat.ChatUiState
import by.iposdev.visorlink.ui.components.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
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
    onAegisClick: () -> Unit = {},
    isAegisEnabled: Boolean = false,
    onOpenTopicList: (() -> Unit)? = null,
) {
    val haptic = rememberHaptic()

    VlTopAppBar(
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
                val currentUid = uiState.currentUser?.uid ?: ""
                val directName = uiState.otherUser?.displayName?.ifEmpty { null }
                    ?: uiState.chat?.displayName(currentUid)?.ifEmpty { null }
                    ?: uiState.chat?.name?.ifEmpty { null }
                    ?: ""
                val directAvatarUrl = uiState.otherUser?.avatarUrl
                    ?: uiState.chat?.avatarUrl

                when (uiState.chatType) {
                    ChatType.DIRECT -> {
                        val isFaulty = uiState.otherUser?.uid == "bot_faultywire" ||
                                uiState.otherUser?.username == "faultywire" ||
                                uiState.chat?.otherUsername(currentUid) == "faultywire"
                        AvatarWithPresence(
                            avatarUrl = directAvatarUrl,
                            displayName = directName,
                            isOnline = uiState.topbarStatus is TopbarStatus.Online ||
                                    uiState.topbarStatus is TopbarStatus.Typing,
                            size = 36.dp,
                            isFaultyWireBot = isFaulty
                        )
                    }
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = uiState.chat?.avatarUrl, name = uiState.chat?.name ?: "",
                        isChannel = uiState.chatType == ChatType.CHANNEL, size = 36.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.currentTopic != null) {
                        Text(
                            text = "${uiState.currentTopic.displayIcon} ${uiState.currentTopic.title}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.chat?.name ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (uiState.chatType) {
                                    ChatType.DIRECT -> directName
                                    else -> uiState.chat?.name ?: ""
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val isOfficial = uiState.chatType == ChatType.DIRECT && (
                                uiState.otherUser?.botBadge == "official" ||
                                uiState.otherUser?.uid == "bot_faultywire" ||
                                uiState.otherUser?.username == "faultywire" ||
                                uiState.chat?.otherUsername(currentUid) == "faultywire"
                            )
                            if (isOfficial) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Official Bot",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    when (uiState.chatType) {
                        ChatType.DIRECT -> AnimatedContent(
                            targetState = uiState.topbarStatus,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "topbar_status",
                        ) { status ->
                            when (status) {
                                is TopbarStatus.WaitingForNetwork -> Text(stringResource(R.string.status_waiting_for_network),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                is TopbarStatus.Connecting -> Text(stringResource(R.string.status_connecting),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                is TopbarStatus.Updating -> Text(stringResource(R.string.status_updating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                is TopbarStatus.Typing  -> TypingDots()
                                is TopbarStatus.Online  -> Text(stringResource(R.string.chat_status_online),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VlTheme.tokens.status.success, fontSize = 11.sp)
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
                            if (uiState.topbarStatus is TopbarStatus.WaitingForNetwork) {
                                Text(stringResource(R.string.status_waiting_for_network),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                                val online = uiState.onlineCount
                                Text(buildString {
                                    append(stringResource(R.string.members_topbar, memberCount))
                                    if (online > 0) append(stringResource(R.string.online_topbar, online))
                                }, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        ChatType.CHANNEL -> {
                            if (uiState.topbarStatus is TopbarStatus.WaitingForNetwork) {
                                Text(stringResource(R.string.status_waiting_for_network),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Text(
                                    stringResource(R.string.subscribers_topbar, uiState.chat?.memberCount ?: 0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            if (isAegisEnabled) {
                IconButton(onClick = { 
                    haptic.perform(HapticType.REACTION, hapticEnabled)
                    onAegisClick() 
                }) {
                    Icon(Icons.Default.SmartToy, null)
                }
            }
            if (canSetWallpaper) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onWallpaperClick() }) {
                    Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperUrl != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (uiState.chat?.isForumActive == true && onOpenTopicList != null) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenTopicList() }) {
                    Icon(Icons.Default.Forum, contentDescription = stringResource(R.string.topics_title))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner && (uiState.chatType != ChatType.CHANNEL || uiState.isChannelMember)) {
                IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onLeaveClick() }) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}
