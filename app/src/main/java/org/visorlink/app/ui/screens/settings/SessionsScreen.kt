package org.visorlink.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.data.repository.SessionInfo
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlTopAppBar
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
                title = { Text("Устройства", style = MaterialTheme.typography.titleLarge) },
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
                ) { Text("Завершить все другие") }
            }
        }
    }

    toTerminate?.let { s ->
        VlAlertDialog(
            onDismissRequest = { toTerminate = null },
            title = { Text("Завершить сеанс?") },
            text = { Text(deviceName(s)) },
            confirmButton = { VlDialogButton(onClick = { viewModel.terminate(s.id); toTerminate = null }, isDestructive = true) { Text("Завершить") } },
            dismissButton = { VlDialogButton(onClick = { toTerminate = null }) { Text("Отмена") } },
        )
    }
    if (confirmAll) {
        VlAlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Завершить все другие сеансы?") },
            text = { Text("Все устройства, кроме этого, выйдут из аккаунта.") },
            confirmButton = { VlDialogButton(onClick = { viewModel.terminateOthers(); confirmAll = false }, isDestructive = true) { Text("Завершить") } },
            dismissButton = { VlDialogButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SessionCard(s: SessionInfo, enabled: Boolean, onTerminate: () -> Unit) {
    VlCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(deviceName(s), style = MaterialTheme.typography.titleMedium)
                val tags = buildList {
                    if (s.isCurrent) add("это устройство")
                    if (!s.tfaVerified) add("ожидает 2FA")
                }
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Активность: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastSeenAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!s.isCurrent) {
                VlDialogButton(onClick = onTerminate, isDestructive = true, enabled = enabled) { Text("Завершить") }
            }
        }
    }
}

/** UA от Firebase Functions SDK может не содержать названия устройства — тогда карточка безымянная. */
private fun deviceName(s: SessionInfo): String = s.userAgent.ifBlank { "Неизвестное устройство" }
