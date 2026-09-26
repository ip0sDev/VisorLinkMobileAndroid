package org.visorlink.app.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPillCardSlideOut
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onNavigateBack: () -> Unit,
    onViewMedia: (String, String) -> Unit = { _, _ -> },
    viewModel: StorageViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val flagsRepository: FlagsRepository = koinInject()
    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleAuthorizationResult(result.resultCode, result.data)
    }

    var showDisconnectConfirm by remember { mutableStateOf(false) }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text(stringResource(R.string.storage_gdrive_disconnect_btn)) },
            text = { Text("Отключить Google Drive? Отправка новых медиафайлов в чаты будет недоступна до повторного подключения.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.storage_gdrive_disconnect_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember(uiState.yandexRelayCustomToken) { mutableStateOf(uiState.yandexRelayCustomToken ?: "") }
    var clientIdInput by remember(uiState.yandexClientId) { mutableStateOf(uiState.yandexClientId) }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text(stringResource(R.string.storage_yandex_relay_token_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.storage_yandex_relay_token_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("OAuth / App Password") },
                        placeholder = { Text("y0_AgAAAA...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text(stringResource(R.string.storage_yandex_relay_client_id_label)) },
                        placeholder = { Text("Client ID (для OAuth)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setYandexCustomToken(tokenInput.ifBlank { null })
                        viewModel.setYandexClientId(clientIdInput.ifBlank { null })
                        showTokenDialog = false
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        VlAmbientGlow()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                VlTopAppBar(
                    modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                    title = { Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            viewModel.refresh()
                        }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp)) }

                // ── Google Drive Storage Card ─────────────────────────────────
                item {
                    GoogleDriveStorageCard(
                        isConnected = uiState.isConnected,
                        accountEmail = uiState.accountEmail,
                        isConnecting = uiState.isConnecting,
                        modifier = Modifier.liquidPillCardSlideOut(index = 0, enabled = isLiquidEnabled),
                        isLiquidEnabled = isLiquidEnabled,
                        onConnectClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            viewModel.connectGoogleDrive { pendingIntent ->
                                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                googleAuthLauncher.launch(request)
                            }
                        },
                        onDisconnectClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            showDisconnectConfirm = true
                        }
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                }

                // ── Firebase Storage Cloud Card ──────────────────────────────
                item {
                    FirebaseStorageCloudCard(
                        modifier = Modifier.liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled),
                        isLiquidEnabled = isLiquidEnabled
                    )
                }

                // ── Yandex Disk Relay Card (Alternative Outbox) ───────────────
                if (uiState.isYandexRelayAvailable) {
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                    item {
                        val context = LocalContext.current
                        YandexDiskRelayCard(
                            isEnabled = uiState.isYandexRelayEnabled,
                            hasToken = uiState.yandexRelayHasActiveToken,
                            hasCustomToken = !uiState.yandexRelayCustomToken.isNullOrBlank(),
                            modifier = Modifier.liquidPillCardSlideOut(index = 2, enabled = isLiquidEnabled),
                            isLiquidEnabled = isLiquidEnabled,
                            onToggle = { enabled ->
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                viewModel.toggleYandexRelay(enabled)
                            },
                            onLoginYandex = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.yandexOAuthUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    tokenInput = uiState.yandexRelayCustomToken ?: ""
                                    clientIdInput = uiState.yandexClientId
                                    showTokenDialog = true
                                }
                            },
                            onDisconnectYandex = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                viewModel.disconnectYandex()
                            },
                            onConfigureToken = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                tokenInput = uiState.yandexRelayCustomToken ?: ""
                                clientIdInput = uiState.yandexClientId
                                showTokenDialog = true
                            }
                        )
                    }
                }

                if (!uiState.error.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleDriveStorageCard(
    isConnected: Boolean,
    accountEmail: String?,
    isConnecting: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false
) {
    VlSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        customRadius = if (isLiquidEnabled) 32.dp else null,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(VlTheme.tokens.shapes.chip)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_gdrive_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                        Text(
                            text = if (isConnected) stringResource(R.string.storage_gdrive_connected) else stringResource(R.string.storage_gdrive_not_connected),
                            fontSize = 14.sp,
                            color = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isConnected && !accountEmail.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VlTheme.tokens.shapes.indicator)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = accountEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.storage_gdrive_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            if (isConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (isConnected) {
                VlButton(
                    isDestructive = true,
                    onClick = onDisconnectClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_gdrive_disconnect_btn))
                    }
                }
            } else {
                VlButton(
                    onClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_gdrive_connect_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun FirebaseStorageCloudCard(
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false
) {
    VlSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        customRadius = if (isLiquidEnabled) 32.dp else null,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(VlTheme.tokens.shapes.chip)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_firebase_cloud_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.storage_gdrive_connected),
                        fontSize = 14.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.storage_firebase_cloud_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun YandexDiskRelayCard(
    isEnabled: Boolean,
    hasToken: Boolean,
    hasCustomToken: Boolean,
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onLoginYandex: () -> Unit,
    onDisconnectYandex: () -> Unit,
    onConfigureToken: () -> Unit
) {
    VlSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        customRadius = if (isLiquidEnabled) 32.dp else null,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(VlTheme.tokens.shapes.chip)
                        .background(if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_yandex_relay_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            !isEnabled -> stringResource(R.string.storage_yandex_relay_disabled)
                            hasCustomToken -> stringResource(R.string.storage_yandex_relay_custom_connected)
                            hasToken -> stringResource(R.string.storage_yandex_relay_ready)
                            else -> "Требуется авторизация"
                        },
                        fontSize = 14.sp,
                        color = if (isEnabled && hasToken) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }
                VlSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.storage_yandex_relay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            if (hasCustomToken) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisconnectYandex,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.storage_yandex_relay_disconnect_btn))
                    }
                    OutlinedButton(
                        onClick = onConfigureToken,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.storage_yandex_relay_manual_btn))
                    }
                }
            } else {
                VlButton(
                    onClick = onLoginYandex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_yandex_relay_login_btn))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onConfigureToken) {
                        Text(
                            text = stringResource(R.string.storage_yandex_relay_manual_btn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}