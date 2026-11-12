package org.visorlink.app.ui.components.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import org.visorlink.app.R
import org.visorlink.app.ui.components.LaunchedEffectAfterFirst
import org.visorlink.app.ui.components.VlFab
import org.visorlink.app.ui.components.VlMorphShape
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.theme.VlCurves
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlDurations
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlKeycapBackground
import org.visorlink.app.ui.theme.vlStructure
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

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
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val fabItems = listOf(
            Triple(Icons.Rounded.Tag, stringResource(R.string.chatlist_fab_find_channel), onFindChannel),
            Triple(Icons.Rounded.Group, stringResource(R.string.chatlist_fab_new_group), onNewGroup),
            Triple(Icons.Rounded.PersonAdd, stringResource(R.string.chatlist_fab_new_chat), onNewChat)
        )

        val menuChipBg = cs.surfaceContainerHighest
        val menuChipText = cs.onSurface
        val menuIconBg = cs.primary
        val menuIconTint = cs.onPrimary

        fabItems.forEachIndexed { index, (icon, label, action) ->
            // Нижний пункт ближе к FAB вылетает первым; на закрытие складывается сверху вниз
            val reverseIndex = fabItems.lastIndex - index
            val enterDelay = reverseIndex * 35
            val exitDelay = index * 25

            val enterTransition = if (tokens.reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(
                    initialOffsetY = { (it * (reverseIndex + 1) * 0.55f).toInt() },
                    animationSpec = if (tokens.glitchMotion) {
                        tween(VlDurations.D2, delayMillis = enterDelay, easing = VlCurves.Key)
                    } else {
                        spring(
                            dampingRatio = 0.74f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    }
                ) + scaleIn(
                    initialScale = 0.72f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                    animationSpec = if (tokens.glitchMotion) {
                        tween(VlDurations.D2, delayMillis = enterDelay, easing = VlCurves.Key)
                    } else {
                        spring(
                            dampingRatio = 0.76f,
                            stiffness = Spring.StiffnessMedium
                        )
                    }
                ) + fadeIn(tween(VlDurations.D1, delayMillis = enterDelay))
            }

            val exitTransition = if (tokens.reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    targetOffsetY = { (it * (reverseIndex + 1) * 0.35f).toInt() },
                    animationSpec = tween(VlDurations.D1, delayMillis = exitDelay, easing = VlCurves.Exit)
                ) + scaleOut(
                    targetScale = 0.74f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                    animationSpec = tween(VlDurations.D1, delayMillis = exitDelay, easing = VlCurves.Exit)
                ) + fadeOut(tween(100, delayMillis = exitDelay))
            }

            AnimatedVisibility(
                visible = showMenu,
                enter = enterTransition,
                exit = exitTransition
            ) {
                FabMenuItem(
                    icon = icon,
                    label = label,
                    onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); action() },
                    chipBg = menuChipBg,
                    chipText = menuChipText,
                    iconBg = menuIconBg,
                    iconTint = menuIconTint
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val openProgress by animateFloatAsState(
            targetValue = if (showMenu) 1f else 0f,
            animationSpec = when {
                tokens.reduceMotion -> snap()
                tokens.glitchMotion -> tween(VlDurations.D2, easing = VlCurves.Key)
                else -> spring(dampingRatio = 0.72f, stiffness = 360f)
            },
            label = "fab_open_progress"
        )

        val pressProgress by animateFloatAsState(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = if (tokens.reduceMotion) snap() else tween(durationMillis = 100),
            label = "fab_press_progress"
        )

        val fabJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.72f, stiffness = 320f)
        val allowJelly = !tokens.reduceMotion && !tokens.glitchMotion

        LaunchedEffectAfterFirst(isPressed) {
            if (allowJelly) {
                if (isPressed) {
                    fabJelly.hold(0.08f)
                } else {
                    fabJelly.release(0.05f)
                }
            }
        }
        LaunchedEffectAfterFirst(showMenu) {
            if (allowJelly) {
                fabJelly.pulse(0.07f)
            }
        }

        // Закрытый FAB — главный акцент темы; открытый — нейтральная кнопка отмены,
        // чтобы пункты speed-dial меню выше визуально доминировали.
        val fabContainerColor by animateColorAsState(
            targetValue = if (showMenu) cs.surfaceContainerHighest else cs.primary,
            animationSpec = if (tokens.reduceMotion) snap() else tween(VlDurations.D1),
            label = "fab_bg"
        )
        val fabContentColor by animateColorAsState(
            targetValue = if (showMenu) cs.onSurface else cs.onPrimary,
            animationSpec = if (tokens.reduceMotion) snap() else tween(VlDurations.D1),
            label = "fab_fg"
        )

        // В Biolume/M3E: сбалансированный сквиркл (18dp -> 14dp при нажатии),
        // плавно морфирующий в круг-кнопку отмены при раскрытии. В Forge — приборная клавиша.
        val closedRestShape = remember(tokens.isForge) {
            RoundedCornerShape(18.dp)
        }
        val closedPressedShape = remember {
            RoundedCornerShape(14.dp)
        }
        val openShape = remember(tokens.isForge, tokens.shapes) {
            if (tokens.isForge) tokens.shapes.button else CircleShape
        }
        val baseClosedShape = VlMorphShape(closedRestShape, closedPressedShape, pressProgress)
        val fabShape = VlMorphShape(baseClosedShape, openShape, openProgress.coerceIn(0f, 1f))

        val fabLabel = stringResource(if (showMenu) R.string.action_close else R.string.chatlist_fab_new_chat)

        VlFab(
            onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); onToggle() },
            modifier = Modifier
                .semantics { contentDescription = fabLabel }
                .then(if (allowJelly) Modifier.liquidJelly(fabJelly) else Modifier),
            containerColor = fabContainerColor,
            glowActive = !showMenu,
            shape = fabShape,
            interactionSource = interactionSource,
            hapticEnabled = false
        ) {
            val t = openProgress.coerceIn(0f, 1f)
            // Единая связка вращения + кросс-масштаба без мёртвых состояний внутри AnimatedContent
            val editAlpha = (1f - t / 0.55f).coerceIn(0f, 1f)
            val editScale = 0.45f + 0.55f * editAlpha
            val closeAlpha = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val closeScale = 0.45f + 0.55f * closeAlpha
            val twistAngle = lerp(-90f, 0f, openProgress)

            Box(contentAlignment = Alignment.Center) {
                if (editAlpha > 0.01f) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = fabContentColor,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer {
                                alpha = editAlpha
                                scaleX = editScale
                                scaleY = editScale
                                rotationZ = (1f - editAlpha) * 42f
                            }
                    )
                }
                if (closeAlpha > 0.01f) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = fabContentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                alpha = closeAlpha
                                scaleX = closeScale
                                scaleY = closeScale
                                rotationZ = twistAngle
                            }
                    )
                }
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
    val tokens = VlTheme.tokens
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = if (isPressed && !tokens.reduceMotion) 0.94f else 1f,
        animationSpec = if (tokens.reduceMotion) snap() else tween(durationMillis = 90),
        label = "fab_item_scale"
    )

    val chipShape = if (tokens.isForge) tokens.shapes.chip else tokens.shapes.pill
    val chipDepth = if (isPressed && tokens.structure.keycap) VlDepth.Inset
    else if (tokens.structure.enabled) VlDepth.Raised
    else VlDepth.Flat
    val miniFabShape = if (tokens.isForge) tokens.shapes.chip else CircleShape

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                .then(
                    if (tokens.structure.enabled) Modifier.vlStructure(tokens.structure, chipDepth, chipShape)
                    else Modifier
                )
                .clip(chipShape)
                .vlKeycapBackground(tokens.structure, chipBg, chipShape, chipDepth),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = chipText
            )
        }

        // Отключаем свой VlEntrance и свечение у мини-FAB, чтобы пункты не отставали от пилюль и не нарушали §10
        VlFab(
            onClick = onClick,
            size = 42.dp,
            containerColor = iconBg,
            glowActive = false,
            shape = miniFabShape,
            interactionSource = interactionSource,
            hapticEnabled = false,
            animateEntrance = false
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
