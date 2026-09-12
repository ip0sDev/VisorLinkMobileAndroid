package by.iposdev.visorlink.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlRaised

/**
 * Нижняя навигация — плавающая панель в стиле Biolume с раскрывающимися пилюлями.
 */
@Composable
fun VlNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    diaryEnabled: Boolean,
    discoverEnabled: Boolean,
    musicEnabled: Boolean = false,
    onOpenDiary: () -> Unit,
    onOpenMusic: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f
    val barShape: Shape = if (tokens.isForge) tokens.shapes.bar else RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = if (tokens.isForge) 0.dp else 24.dp,
                end = if (tokens.isForge) 0.dp else 24.dp,
                top = 8.dp,
                bottom = if (tokens.isForge) 0.dp else 16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val barBrush = remember(tokens.isBiolume, tokens.structure.enabled, cs) {
            if (tokens.isBiolume) {
                val topColor = cs.surfaceContainerHigh.copy(alpha = 0.96f)
                val bottomColor = cs.surfaceContainer.copy(alpha = 0.94f)
                Brush.verticalGradient(listOf(topColor, bottomColor))
            } else {
                val color = if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow
                Brush.verticalGradient(listOf(color, color))
            }
        }

        val barBorder = remember(tokens.isBiolume, cs, isDark) {
            if (tokens.isBiolume) {
                val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.50f)
                val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
                BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
            } else null
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, barShape)
                    else Modifier
                )
                .clip(barShape)
                .background(barBrush, barShape)
                .then(
                    if (barBorder != null) Modifier.border(barBorder, barShape)
                    else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, barShape)
                    else Modifier
                )
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlTabItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = Icons.Outlined.ChatBubbleOutline,
                selectedIcon = Icons.Filled.ChatBubble,
                label = stringResource(R.string.nav_tab_chats),
            )
            if (discoverEnabled) {
                VlTabItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = Icons.Outlined.Explore,
                    selectedIcon = Icons.Filled.Explore,
                    label = stringResource(R.string.feed_title),
                )
            }
            if (musicEnabled) {
                VlTabItem(
                    selected = selectedTab == 3,
                    onClick = onOpenMusic,
                    icon = Icons.Default.Audiotrack,
                    selectedIcon = Icons.Default.Audiotrack,
                    label = stringResource(R.string.nav_tab_music),
                )
            }
            if (diaryEnabled) {
                VlTabItem(
                    selected = selectedTab == 2,
                    onClick = onOpenDiary,
                    icon = Icons.Default.Edit,
                    selectedIcon = Icons.Default.Edit,
                    label = stringResource(R.string.diary_title),
                )
            }
        }
    }
}

/**
 * Пункт навигации: горизонтальная расширяющаяся pill-капсула со скейлом иконки и анимацией текста.
 */
@Composable
fun VlTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f

    val interactionSource = remember { MutableInteractionSource() }

    // Selection pill color
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) cs.primary.copy(alpha = 0.20f)
            else cs.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_pill_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_content_color"
    )

    val pillShape: Shape = if (tokens.isForge) tokens.shapes.pill else CircleShape

    val pillModifier = if (tokens.isBiolume && selected) {
        val pillGradient = Brush.horizontalGradient(
            listOf(
                cs.primary.copy(alpha = if (isDark) 0.16f else 0.12f),
                cs.primary.copy(alpha = if (isDark) 0.08f else 0.05f)
            )
        )
        val pillBorder = BorderStroke(
            1.dp,
            if (isDark) cs.primary.copy(alpha = 0.18f) else cs.primary.copy(alpha = 0.15f)
        )
        Modifier
            .clip(pillShape)
            .background(pillGradient)
            .border(pillBorder, pillShape)
    } else {
        Modifier
            .clip(pillShape)
            .background(pillColor)
    }

    Box(
        modifier = modifier
            .then(pillModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "icon_scale"
            )

            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(150)) + expandHorizontally(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(120))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = contentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
