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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.AppLanguage
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val currentTheme   by themeViewModel.appTheme.collectAsState()
    val currentMode    by themeViewModel.themeMode.collectAsState()
    val hapticEnabled  by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled   by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang    by themeViewModel.language.collectAsState()

    val buildDate = remember {
        SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIMESTAMP))
    }
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back))
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

            // ── Appearance ────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_appearance))

            SettingsCard {
                CardTitle(Icons.Default.Palette, stringResource(R.string.settings_theme_title))
                Spacer(Modifier.height(12.dp))
                ThemeOption(
                    label       = stringResource(R.string.settings_theme_m3_name),
                    description = stringResource(R.string.settings_theme_m3_desc),
                    icon        = Icons.Default.AutoAwesome,
                    selected    = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE,
                    onClick     = { themeViewModel.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) }
                )
                Spacer(Modifier.height(8.dp))
                ThemeOption(
                    label       = stringResource(R.string.settings_theme_oneui_name),
                    description = stringResource(R.string.settings_theme_oneui_desc),
                    icon        = Icons.Default.PhoneAndroid,
                    selected    = currentTheme == AppTheme.ONE_UI,
                    onClick     = { themeViewModel.setTheme(AppTheme.ONE_UI) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Dark mode ─────────────────────────────────────────────────────
            SettingsCard {
                CardTitle(Icons.Default.DarkMode, stringResource(R.string.settings_dark_title))
                Spacer(Modifier.height(12.dp))
                DarkModeOption(
                    label       = stringResource(R.string.settings_dark_system),
                    description = stringResource(R.string.settings_dark_system_desc),
                    icon        = Icons.Default.SettingsBrightness,
                    selected    = currentMode == ThemeMode.SYSTEM,
                    onClick     = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) }
                )
                Spacer(Modifier.height(8.dp))
                DarkModeOption(
                    label       = stringResource(R.string.settings_dark_light),
                    description = stringResource(R.string.settings_dark_light_desc),
                    icon        = Icons.Default.LightMode,
                    selected    = currentMode == ThemeMode.LIGHT,
                    onClick     = { themeViewModel.setThemeMode(ThemeMode.LIGHT) }
                )
                Spacer(Modifier.height(8.dp))
                DarkModeOption(
                    label       = stringResource(R.string.settings_dark_dark),
                    description = stringResource(R.string.settings_dark_dark_desc),
                    icon        = Icons.Default.DarkMode,
                    selected    = currentMode == ThemeMode.DARK,
                    onClick     = { themeViewModel.setThemeMode(ThemeMode.DARK) }
                )
            }

            // ── Language ──────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_language))

            SettingsCard {
                CardTitle(Icons.Default.Language, stringResource(R.string.settings_language_title))
                Spacer(Modifier.height(12.dp))
                LanguageOption(
                    label    = stringResource(R.string.settings_language_system),
                    selected = currentLang == AppLanguage.SYSTEM,
                    onClick  = { themeViewModel.setLanguage(AppLanguage.SYSTEM) }
                )
                Spacer(Modifier.height(8.dp))
                LanguageOption(
                    label    = stringResource(R.string.settings_language_en),
                    selected = currentLang == AppLanguage.EN,
                    onClick  = { themeViewModel.setLanguage(AppLanguage.EN) }
                )
                Spacer(Modifier.height(8.dp))
                LanguageOption(
                    label    = stringResource(R.string.settings_language_ru),
                    selected = currentLang == AppLanguage.RU,
                    onClick  = { themeViewModel.setLanguage(AppLanguage.RU) }
                )
            }

            // ── Notifications & Feedback ──────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_notifications))

            SettingsCard(padding = false) {
                SwitchRow(
                    icon    = Icons.Default.Notifications,
                    title   = stringResource(R.string.settings_push_title),
                    sub     = stringResource(R.string.settings_push_sub),
                    checked = notifEnabled,
                    onCheckedChange = { themeViewModel.setNotifications(it) }
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 56.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
                SwitchRow(
                    icon    = Icons.Default.Vibration,
                    title   = stringResource(R.string.settings_haptic_title),
                    sub     = stringResource(R.string.settings_haptic_sub),
                    checked = hapticEnabled,
                    onCheckedChange = { themeViewModel.setHaptic(it) }
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_about))

            SettingsCard(padding = false) {
                InfoRow(
                    icon     = Icons.Default.Info,
                    title    = stringResource(R.string.settings_version),
                    subtitle = versionString
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Reusable layout helpers ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    padding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = if (padding) Modifier.padding(16.dp) else Modifier,
            content  = content
        )
    }
}

@Composable
private fun CardTitle(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub,   style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            Text(title,    style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        shape   = MaterialTheme.shapes.medium,
        color   = if (selected) MaterialTheme.colorScheme.primaryContainer
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
                Text(label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface)
                Text(description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
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
        onClick  = onClick,
        shape    = MaterialTheme.shapes.medium,
        color    = if (selected) MaterialTheme.colorScheme.secondaryContainer
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
                Text(label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface)
                Text(description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(
                selected = selected,
                onClick  = onClick,
                colors   = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        shape    = MaterialTheme.shapes.medium,
        color    = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}