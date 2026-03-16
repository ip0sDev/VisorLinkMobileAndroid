package by.iposdev.visorlink.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notificationsEnabled by themeViewModel.notificationsEnabled.collectAsState()

    // Форматируем timestamp в читаемую строку
    val buildDate = remember {
        SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIMESTAMP))
    }
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Тема оформления ───────────────────────────────────────────────
            SectionHeader("Appearance")

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("App Theme", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    ThemeOption(
                        label = "Material 3 Expressive",
                        description = "Vibrant colors, bold shapes, dynamic theming on Android 12+",
                        icon = Icons.Default.AutoAwesome,
                        selected = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE,
                        onClick = { themeViewModel.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeOption(
                        label = "One UI 8.5",
                        description = "Samsung-inspired design with clean blue palette and large rounded cards",
                        icon = Icons.Default.PhoneAndroid,
                        selected = currentTheme == AppTheme.ONE_UI,
                        onClick = { themeViewModel.setTheme(AppTheme.ONE_UI) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Тёмный режим ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Dark Mode", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    DarkModeOption(
                        label = "System default",
                        description = "Follows your device theme setting",
                        icon = Icons.Default.SettingsBrightness,
                        selected = currentMode == ThemeMode.SYSTEM,
                        onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DarkModeOption(
                        label = "Light",
                        description = "Always use light theme",
                        icon = Icons.Default.LightMode,
                        selected = currentMode == ThemeMode.LIGHT,
                        onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                    Spacer(Modifier.height(8.dp))
                    DarkModeOption(
                        label = "Dark",
                        description = "Always use dark theme",
                        icon = Icons.Default.DarkMode,
                        selected = currentMode == ThemeMode.DARK,
                        onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) }
                    )
                }
            }
// ── Уведомления и отклик ──────────────────────────────────
            SectionHeader("Notifications & Feedback")

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Уведомления
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Notifications, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Push Notifications", style = MaterialTheme.typography.bodyMedium)
                            Text("New message alerts", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { themeViewModel.setNotifications(it) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    // Вибрация
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Vibration, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Haptic Feedback", style = MaterialTheme.typography.bodyMedium)
                            Text("Vibration on interactions", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { themeViewModel.setHaptic(it) }
                        )
                    }
                }
            }
            // ── О приложении ─────────────────────────────────────────────────
            SectionHeader("About")

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    InfoRow(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = versionString
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun ThemeOption(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun DarkModeOption(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}