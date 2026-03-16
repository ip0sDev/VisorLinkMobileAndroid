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
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()

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
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text("APPEARANCE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 4.dp))

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
                        label = "OneUI Style",
                        description = "Clean blue palette, minimal aesthetics",
                        icon = Icons.Default.PhoneAndroid,
                        selected = currentTheme == AppTheme.ONE_UI,
                        onClick = { themeViewModel.setTheme(AppTheme.ONE_UI) }
                    )
                }
            }

            Text("ABOUT", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 4.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Version", style = MaterialTheme.typography.bodyMedium)
                        Text("1.0.0", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}