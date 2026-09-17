package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline

import androidx.compose.ui.draw.clip
import org.visorlink.app.ui.theme.vlRaised

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance

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
        val isDark = cs.surface.luminance() < 0.5f
        val barShape = tokens.shapes.inputPanel
        val barBrush = remember(isDark, cs) {
            val topColor = if (isDark) cs.surfaceContainer.copy(alpha = 0.95f) else cs.surfaceContainerLow.copy(alpha = 0.98f)
            val bottomColor = if (isDark) cs.surfaceContainerLow.copy(alpha = 0.90f) else cs.surfaceContainer.copy(alpha = 0.92f)
            Brush.verticalGradient(listOf(topColor, bottomColor))
        }
        val barBorder = remember(isDark, cs) {
            val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.50f)
            val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
            BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
        }

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
                    .background(barBrush, barShape)
                    .border(barBorder, barShape)
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
