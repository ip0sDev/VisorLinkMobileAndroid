package by.iposdev.visorlink.ui.components.chatlist

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlSignalGlow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic

@Composable
fun ChatListFab(
    showMenu: Boolean,
    hapticEnabled: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onFindChannel: () -> Unit
) {
    val haptic = rememberHaptic()
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val fabItems = listOf(
            Triple(Icons.Default.Tag, stringResource(R.string.chatlist_fab_find_channel), onFindChannel),
            Triple(Icons.Default.Group, stringResource(R.string.chatlist_fab_new_group), onNewGroup),
            Triple(Icons.Default.PersonAdd, stringResource(R.string.chatlist_fab_new_chat), onNewChat)
        )

        val cs = MaterialTheme.colorScheme
        val menuChipBg = cs.surfaceContainerHigh
        val menuChipText = cs.onSurface
        val menuIconBg = cs.secondaryContainer
        val menuIconTint = cs.onSecondaryContainer

        fabItems.forEachIndexed { index, (icon, label, action) ->
            val delayMs = index * 50L
            AnimatedVisibility(
                visible = showMenu,
                enter = slideInVertically(
                    initialOffsetY = { it * (3 - index) / 2 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                ) + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(tween(150, delayMillis = delayMs.toInt())),
                exit = scaleOut(targetScale = 0.7f, animationSpec = tween(100, delayMillis = ((2 - index) * 30L).toInt())) + fadeOut(tween(80))
            ) {
                FabMenuItem(
                    icon = icon, label = label, onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); action() },
                    chipBg = menuChipBg, chipText = menuChipText,
                    iconBg = menuIconBg, iconTint = menuIconTint
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val fabScale by animateFloatAsState(
            targetValue = if (isPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
            label = "fab_scale"
        )

        val fabBgOpen = cs.errorContainer
        val fabBgClosed = cs.primary

        val fabContentOpen = cs.onErrorContainer
        val fabContentClosed = cs.onPrimary

        // §7: в Biolume FAB — асимметричная M3E-форма с постоянным, но статичным
        // свечением (единственное исключение из «в покое не светится», §10).
        // Открытое состояние (крестик) — «отмена», поэтому свечения там нет.
        val tokens = VlTheme.tokens
        val fabShape = when {
            showMenu -> RoundedCornerShape(16.dp)
            tokens.isBiolume -> tokens.shapes.fab
            else -> CircleShape
        }

        FloatingActionButton(
            onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); onToggle() },
            modifier = Modifier
                .scale(fabScale)
                .vlSignalGlow(
                    tokens = tokens.signal,
                    color = cs.primary,
                    shape = fabShape,
                    active = !showMenu,
                    alphaOverride = if (isPressed) tokens.signal.glowAlpha else tokens.signal.fabRestAlpha,
                ),
            containerColor = if (showMenu) fabBgOpen else fabBgClosed,
            contentColor = if (showMenu) fabContentOpen else fabContentClosed,
            shape = fabShape,
            interactionSource = interactionSource,
        ) {
            AnimatedContent(
                targetState = showMenu,
                transitionSpec = {
                    scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150)) togetherWith
                            scaleOut(targetScale = 0.4f, animationSpec = tween(100)) + fadeOut(tween(80))
                },
                label = "fab_icon_morph"
            ) { isOpen ->
                val rotation by animateFloatAsState(targetValue = if (isOpen) 45f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "fab_rot")
                Icon(
                    imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    chipBg: Color,
    chipText: Color,
    iconBg: Color,
    iconTint: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "fab_item_press"
    )

    Row(
        modifier = Modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = chipBg,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 2.dp
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = chipText)
        }

        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = iconBg,
            contentColor = iconTint,
            shape = RoundedCornerShape(14.dp),
            elevation = FloatingActionButtonDefaults.elevation(2.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
        }
    }
}
