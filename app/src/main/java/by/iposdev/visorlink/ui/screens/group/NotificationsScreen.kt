package by.iposdev.visorlink.ui.screens.group

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppNotification
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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
    val notifications by chatRepository.notificationsFlow(uid)
        .collectAsState(initial = emptyList())

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        if (isExthru) {
                            Text(
                                text = stringResource(R.string.notifications_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Text(stringResource(R.string.notifications_title))
                        }
                    },
                    navigationIcon = {
                        val btnModifier = if (isExthru) Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(42.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            },
                            modifier = btnModifier
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                                modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isExthru) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .exthruSmallRaisedShadow(isDark)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsNone, null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.notifications_empty),
                        style = if (isExthru) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
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
                        isExthru = isExthru,
                        isDark = isDark,
                        onAccept = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            scope.launch {
                                try {
                                    val chatId = chatRepository.respondToInvite(notif.inviteId, true)
                                    if (chatId != null) onOpenChat(chatId)
                                } catch (_: Exception) {}
                            }
                        },
                        onDecline = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            scope.launch {
                                try { chatRepository.respondToInvite(notif.inviteId, false) }
                                catch (_: Exception) {}
                            }
                        }
                    )

                    if (!isExthru) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    isExthru: Boolean,
    isDark: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    if (isExthru) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .exthruRaisedShadow(isDark)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExthruIconTray(icon = Icons.Default.Group, isDark = isDark)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notification_group_invite_title),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.notification_from, notification.invitedBy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NmButton(
                    text = stringResource(R.string.action_decline),
                    isDark = isDark,
                    isDestructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onDecline
                )
                NmButton(
                    text = stringResource(R.string.action_accept),
                    isDark = isDark,
                    isDestructive = false,
                    modifier = Modifier.weight(1f),
                    onClick = onAccept
                )
            }
        }
    } else {
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
}

// ─── Примитивы для Exthru ────────────────────────────────────────────────────

@Composable
private fun ExthruIconTray(icon: ImageVector, isDark: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .exthruSmallRaisedShadow(isDark)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NmButton(
    text: String,
    isDark: Boolean,
    isDestructive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btn_scale"
    )

    val bgColor = if (isDestructive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val textColor = if (isDestructive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .scale(scale)
            .exthruSmallRaisedShadow(isDark)
            .background(bgColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
    }
}