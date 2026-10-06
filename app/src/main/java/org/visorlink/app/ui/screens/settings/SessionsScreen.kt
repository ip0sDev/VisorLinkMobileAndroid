package org.visorlink.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.repository.SessionInfo
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlIconTray
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.utils.SessionDescriber
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    var toTerminate by remember { mutableStateOf<SessionInfo?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                Text(it, color = cs.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.id }) { s ->
                    SessionCard(s, enabled = !state.isBusy) { toTerminate = s }
                }
            }
            if (state.sessions.any { !it.isCurrent }) {
                VlDialogButton(
                    onClick = { confirmAll = true },
                    isDestructive = true,
                    isLoading = state.isBusy,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                ) { Text(stringResource(R.string.sessions_terminate_all)) }
            }
        }
    }

    toTerminate?.let { s ->
        VlAlertDialog(
            onDismissRequest = { toTerminate = null },
            title = { Text(stringResource(R.string.sessions_confirm_one_title)) },
            text = { Text(deviceName(s)) },
            confirmButton = {
                VlDialogButton(onClick = { viewModel.terminate(s.id); toTerminate = null }, isDestructive = true) {
                    Text(stringResource(R.string.sessions_terminate))
                }
            },
            dismissButton = { VlDialogButton(onClick = { toTerminate = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmAll) {
        VlAlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.sessions_confirm_all_title)) },
            text = { Text(stringResource(R.string.sessions_confirm_all_text)) },
            confirmButton = {
                VlDialogButton(onClick = { viewModel.terminateOthers(); confirmAll = false }, isDestructive = true) {
                    Text(stringResource(R.string.sessions_terminate))
                }
            },
            dismissButton = { VlDialogButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SessionCard(s: SessionInfo, enabled: Boolean, onTerminate: () -> Unit) {
    val mobile = remember(s) { SessionDescriber.describe(s).isMobile }
    VlCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VlIconTray(icon = if (mobile) Icons.Default.PhoneAndroid else Icons.Default.Computer)
            Column(Modifier.weight(1f)) {
                Text(deviceName(s), style = MaterialTheme.typography.titleMedium)
                val tags = buildList {
                    if (s.isCurrent) add(stringResource(R.string.sessions_this_device))
                    if (!s.tfaVerified) add(stringResource(R.string.sessions_tfa_pending))
                }
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    stringResource(
                        R.string.sessions_last_active,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastSeenAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!s.isCurrent) {
                VlDialogButton(onClick = onTerminate, isDestructive = true, enabled = enabled) { Text(stringResource(R.string.sessions_terminate)) }
            }
        }
    }
}

/**
 * Android-приложение: «VisorLink 4.3.00 · Google Pixel 8 · Android 14» (раньше вход с Android
 * писался с User-Agent okhttp и был «Неизвестным устройством»), остальное — «браузер · ОС».
 */
@Composable
private fun deviceName(s: SessionInfo): String =
    SessionDescriber.describe(s).label ?: stringResource(R.string.sessions_unknown_device)
