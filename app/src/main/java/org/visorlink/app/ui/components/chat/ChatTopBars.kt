package org.visorlink.app.ui.components.chat

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
import org.visorlink.app.R
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.TopbarStatus
import org.visorlink.app.ui.components.AvatarWithPresence
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.components.chatlist.GroupChannelAvatar
import org.visorlink.app.ui.components.led.VlLedMarquee
import org.visorlink.app.ui.components.led.VlLedScreen
import org.visorlink.app.ui.components.led.VlLedTyping
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.screens.chat.ChatUiState
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import java.text.SimpleDateFormat
import java.util.*

/**
 * Статус под именем в шапке чата: в Forge (`tokens.led != null`) выводится на LED-табло
 * бегущей строкой (порт Flutter `_ledStatus` в `chat_app_bar.dart`).
 */
@Composable
private fun ChatStatusLine(
    text: String,
    color: Color,
) {
    if (text.isEmpty()) {
        Text("", style = MaterialTheme.typography.labelSmall)
        return
    }
    val tokens = VlTheme.tokens
    if (tokens.led != null) {
        VlLedScreen(
            radius = 8.dp,
            padding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
        ) {
            VlLedMarquee(
                text = text,
                dot = 1.5.dp,
                gap = 0.75.dp,
                fallbackStyle = MaterialTheme.typography.labelSmall.copy(
                    color = color,
                    fontSize = 11.sp,
                ),
            )
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
    onOpenTopicList: (() -> Unit)? = null
) {
    val haptic = rememberHaptic()
    val tokens = VlTheme.tokens

    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    VlTopAppBar(
        modifier = Modifier.liquidJelly(topBarJelly),
        navigationIcon = {
            VlTopAppBarButton(onClick = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                topBarJelly.press(0.06f)
                onNavigateBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.action_back),
                    modifier = Modifier.size(20.dp)
                )
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
                // Заблокировавший меня: аватарка скрыта, остаётся инициал
                val directAvatarUrl = if (uiState.blockedByPeer) null
                else uiState.otherUser?.avatarUrl ?: uiState.chat?.avatarUrl

                when (uiState.chatType) {
                    ChatType.DIRECT, ChatType.EMERGENCY -> {
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
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ChatStatusLine(
                            text = uiState.chat?.name ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (uiState.chatType) {
                                    ChatType.EMERGENCY -> "$directName [SOS]"
                                    ChatType.DIRECT -> directName
                                    else -> uiState.chat?.name ?: ""
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // Значок режима собеседника — только смотрящему с особым режимом (спека §9)
                            if (uiState.chatType == ChatType.DIRECT && org.visorlink.app.ui.idcard.LocalIdModeState.current.viewerSpecial) {
                                org.visorlink.app.ui.components.idcard.IdModeGlyph(
                                    org.visorlink.app.data.idcard.IdMode.of(uiState.otherUser?.idMode),
                                    Modifier.padding(start = 6.dp),
                                    size = 14.dp,
                                )
                            }
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
                                is TopbarStatus.WaitingForNetwork -> ChatStatusLine(
                                    text = stringResource(R.string.status_waiting_for_network),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                is TopbarStatus.Connecting -> ChatStatusLine(
                                    text = stringResource(R.string.status_connecting),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                is TopbarStatus.Updating -> ChatStatusLine(
                                    text = stringResource(R.string.status_updating),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                is TopbarStatus.Typing -> {
                                    if (tokens.led != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            VlLedTyping()
                                            ChatStatusLine(
                                                text = stringResource(R.string.chat_typing),
                                                color = tokens.led.color,
                                            )
                                        }
                                    } else {
                                        TypingDots()
                                    }
                                }
                                is TopbarStatus.Online -> ChatStatusLine(
                                    text = stringResource(R.string.chat_status_online),
                                    color = VlTheme.tokens.status.success,
                                )
                                is TopbarStatus.LastSeenLongAgo -> ChatStatusLine(
                                    text = stringResource(R.string.chat_last_seen_long_ago),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                is TopbarStatus.LastSeen -> ChatStatusLine(
                                    text = status.ts?.let { ts ->
                                        stringResource(
                                            R.string.last_seen_topbar,
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                                        )
                                    } ?: "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> Text("", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        ChatType.GROUP -> {
                            if (uiState.topbarStatus is TopbarStatus.WaitingForNetwork) {
                                ChatStatusLine(
                                    text = stringResource(R.string.status_waiting_for_network),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                val memberCount = uiState.chat?.memberCount ?: uiState.members.size
                                val online = uiState.onlineCount
                                ChatStatusLine(
                                    text = buildString {
                                        append(stringResource(R.string.members_topbar, memberCount))
                                        if (online > 0) append(stringResource(R.string.online_topbar, online))
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ChatType.CHANNEL -> {
                            if (uiState.topbarStatus is TopbarStatus.WaitingForNetwork) {
                                ChatStatusLine(
                                    text = stringResource(R.string.status_waiting_for_network),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                ChatStatusLine(
                                    text = stringResource(R.string.subscribers_topbar, uiState.chat?.memberCount ?: 0),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ChatType.EMERGENCY -> {
                            ChatStatusLine(
                                text = "🛡️ Аварийный канал связи",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (isAegisEnabled) {
                VlTopAppBarButton(onClick = {
                    haptic.perform(HapticType.REACTION, hapticEnabled)
                    onAegisClick()
                }) {
                    Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(20.dp))
                }
            }
            if (canSetWallpaper) {
                VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onWallpaperClick() }) {
                    Icon(
                        Icons.Default.Wallpaper,
                        contentDescription = stringResource(R.string.wallpaper),
                        tint = if (uiState.wallpaperMode != "none") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (uiState.chat?.isForumActive == true && onOpenTopicList != null) {
                VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenTopicList() }) {
                    Icon(Icons.Default.Forum, contentDescription = stringResource(R.string.topics_title), modifier = Modifier.size(20.dp))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && isAdmin) {
                VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenChatSettings(chatId) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), modifier = Modifier.size(20.dp))
                }
            }
            if (uiState.chatType != ChatType.DIRECT && !isOwner && (uiState.chatType != ChatType.CHANNEL || uiState.isChannelMember)) {
                VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onLeaveClick() }) {
                    Icon(
                        Icons.Default.ExitToApp,
                        stringResource(R.string.leave),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}
