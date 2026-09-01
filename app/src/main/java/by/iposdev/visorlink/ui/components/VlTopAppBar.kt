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

import androidx.compose.ui.draw.clip
import by.iposdev.visorlink.ui.theme.vlRaised

/**
 * Тематический TopAppBar.
 *
 *  - BIOLUME: парящая приподнятая панель (vlRaised) со скруглением inputPanel,
 *    нейтральной биолюминесцентной гранью (vlHairline) и мягким фоном surfaceContainerLow.
 *  - FORGE: прямоугольный solid-фон surfaceContainerHigh с нижней гранью.
 *  - MATERIAL3: стандартный плоский M3E.
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

    if (tokens.isBiolume) {
        val barShape = tokens.shapes.inputPanel
        Box(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, barShape)
                        else Modifier
                    )
                    .clip(barShape)
                    .background(cs.surfaceContainerLow)
                    .then(
                        if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, barShape)
                        else Modifier
                    )
            ) {
                TopAppBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    windowInsets = WindowInsets(0.dp),
                    colors = colors ?: TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = cs.onSurface,
                        navigationIconContentColor = cs.onSurface,
                        actionIconContentColor = cs.onSurfaceVariant
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        }
    } else {
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
}
