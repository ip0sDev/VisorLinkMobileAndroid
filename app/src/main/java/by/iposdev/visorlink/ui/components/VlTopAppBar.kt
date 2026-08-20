package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline

/**
 * Тематический TopAppBar.
 *
 *  - BIOLUME: плавная заливка surface, без тени M3.
 *  - FORGE: прямоугольный, solid-фон surfaceContainerHigh, плюс нижняя грань.
 *  - MATERIAL3: стандартный M3E.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    val defaultColors = TopAppBarDefaults.topAppBarColors(
        containerColor = when {
            tokens.isForge -> cs.surfaceContainerHigh
            else -> cs.surface
        },
        scrolledContainerColor = when {
            tokens.isForge -> cs.surfaceContainerHigh
            else -> cs.surface.copy(alpha = 0.85f)
        }
    )

    Column(modifier = modifier) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors ?: defaultColors,
            scrollBehavior = scrollBehavior
        )
        if (tokens.isForge) {
            HorizontalDivider(
                thickness = 1.dp,
                color = cs.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}
