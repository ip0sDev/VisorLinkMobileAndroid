package org.visorlink.app.ui.screens.group

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppNotification
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    chatRepository: ChatRepository = koinInject(),
    auth: FirebaseAuth = koinInject(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val uid = auth.currentUser?.uid ?: return
    val rawNotifications by chatRepository.notificationsFlow(uid)
        .collectAsState(initial = emptyList())
    var handledIds by remember { mutableStateOf(setOf<String>()) }
    val notifications = remember(rawNotifications, handledIds) {
        rawNotifications.filter { it.id !in handledIds }
    }

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.notifications_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    NotificationItem(
                        notification = notif,
                        onAccept = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            handledIds = handledIds + notif.id
                            scope.launch {
                                try {
                                    val chatId = chatRepository.respondToInvite(
                                        inviteId = notif.inviteId,
                                        accept = true,
                                        notificationId = notif.id,
                                        uid = uid
                                    )
                                    val targetChatId = chatId ?: notif.chatId.takeIf { it.isNotBlank() }
                                    if (targetChatId != null) onOpenChat(targetChatId)
                                } catch (_: Exception) {}
                            }
                        },
                        onDecline = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            handledIds = handledIds + notif.id
                            scope.launch {
                                try {
                                    chatRepository.respondToInvite(
                                        inviteId = notif.inviteId,
                                        accept = false,
                                        notificationId = notif.id,
                                        uid = uid
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    )

                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notification_group_invite_title),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.notification_from, notification.invitedBy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onDecline) {
                Icon(Icons.Default.Close, "Decline", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, "Accept", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
