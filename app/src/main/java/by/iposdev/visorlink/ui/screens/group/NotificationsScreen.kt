package by.iposdev.visorlink.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppNotification
import by.iposdev.visorlink.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import by.iposdev.visorlink.R
// Для работы со строками
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    chatRepository: by.iposdev.visorlink.data.repository.ChatRepository = koinInject(),
    auth: FirebaseAuth = koinInject()
) {
    val scope = rememberCoroutineScope()
    val uid = auth.currentUser?.uid ?: return
    val notifications by chatRepository.notificationsFlow(uid)
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.notifications_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(notifications, key = { it.id }) { notif ->
                    NotificationItem(
                        notification = notif,
                        onAccept = {
                            scope.launch {
                                try {
                                    val chatId = chatRepository.respondToInvite(notif.inviteId, true)
                                    if (chatId != null) onOpenChat(chatId)
                                } catch (_: Exception) {}
                            }
                        },
                        onDecline = {
                            scope.launch {
                                try { chatRepository.respondToInvite(notif.inviteId, false) }
                                catch (_: Exception) {}
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(0.2f))
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Group, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notification_group_invite_title),
                style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.notification_from, notification.invitedBy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecline,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(stringResource(R.string.action_decline)) }
            Button(onClick = onAccept,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(stringResource(R.string.action_accept)) }
        }
    }
}