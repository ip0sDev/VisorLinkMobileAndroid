package by.iposdev.visorlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.components.ThemePreviewGrid
import by.iposdev.visorlink.ui.components.VlAlertDialog
import by.iposdev.visorlink.ui.components.VlDialogButton

@Composable
fun SettingsThemeDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit
) {
    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите оформление") },
        text = {
            Column {
                Text("Выберите стиль интерфейса, который вам больше нравится.")
                Spacer(Modifier.height(8.dp))
                ThemePreviewGrid(
                    selectedTheme = currentTheme,
                    onThemeSelected = { theme ->
                        onThemeSelected(theme)
                        onDismiss()
                    },
                    modifier = Modifier.height(400.dp)
                )
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
