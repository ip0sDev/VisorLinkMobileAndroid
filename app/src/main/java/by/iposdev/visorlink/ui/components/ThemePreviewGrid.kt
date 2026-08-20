package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.ui.components.settings.VlThemeSelector

/**
 * Совместимая обёртка над [VlThemeSelector].
 *
 * Реализация переехала в `ui/components/settings/ThemeSelector.kt`; здесь остался
 * только прежний вход, чтобы не ломать существующие вызовы. Для нового кода
 * используйте [VlThemeSelector] напрямую.
 */
@Composable
fun ThemePreviewGrid(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.DEFAULT,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    VlThemeSelector(
        selected = selectedTheme,
        onSelect = onThemeSelected,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        themeMode = themeMode,
        colorPreset = colorPreset,
    )
}
