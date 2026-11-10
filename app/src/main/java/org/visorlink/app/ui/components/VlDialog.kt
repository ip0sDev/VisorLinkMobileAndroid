package org.visorlink.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.ui.theme.vlSignalBorder

@Composable
fun VlAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    dismissible: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        val popProgress = rememberLiquidPopProgress()

        val body: @Composable () -> Unit = {
            Column(
                Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .padding(start = 24.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)
            ) {
                title?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                            shadow = MaterialTheme.typography.titleLarge.shadow,
                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface
                        )
                    ) { it() }
                }
                if (title != null && text != null) Spacer(Modifier.height(16.dp))
                text?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                            fontSize = 15.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp
                        )
                    ) { it() }
                }
                if (actions != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }

        if (tokens.structure.enabled) {
            val shape = tokens.shapes.card
            Box(
                modifier = modifier
                    .liquidPopIn(popProgress)
                    .vlRaised(tokens.structure, shape)
                    .clip(shape)
                    .background(cs.surfaceContainerHigh, shape)
                    .vlHairline(cs.outlineVariant, shape)
            ) { body() }
        } else {
            VlSurface(
                modifier = modifier.liquidPopIn(popProgress),
                isInput = false,
                overrideColor = cs.surface,
            ) { body() }
        }
    }
}

@Composable
fun VlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissible: Boolean = true,
) {
    VlAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = if (icon == null && title == null) null else {
            {
                Column {
                    icon?.let {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { it() }
                        if (title != null) Spacer(Modifier.height(16.dp))
                    }
                    title?.invoke()
                }
            }
        },
        text = text,
        actions = {
            dismissButton?.invoke()
            confirmButton()
        },
        dismissible = dismissible,
    )
}

/**
 * Текстовая кнопка диалогов и второстепенных действий (порт Flutter `VlDialogButton`).
 */
@Composable
fun VlDialogButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    label: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val active = onClick != null && enabled && !isLoading

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val tokens = VlTheme.tokens
    val color = if (isDestructive) cs.error else if (isPrimary) cs.primary else cs.onSurfaceVariant
    // Плавное затухание при нажатии/деактивации
    val opacity by animateFloatAsState(
        targetValue = when {
            !active -> 0.4f
            isPressed -> 0.55f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "vldialogbtn_opacity",
    )
    val shape = tokens.shapes.rounded(12.dp)

    Box(
        modifier = modifier
            .alpha(opacity)
            .clip(shape)
            .vlSignalBorder(tokens.signal, cs.primary, shape, active = isFocused)
            .then(
                if (active) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick!!,
                ) else Modifier
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
            } else {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                if (label != null) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                            color = color,
                            fontWeight = if (isPrimary) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                        )
                    )
                } else if (content != null) {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                            color = color,
                            fontWeight = if (isPrimary) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                        )
                    ) { content() }
                }
            }
        }
    }
}

/** Перегрузка с меткой-строкой [label] (Flutter signature). */
@Composable
fun VlDialogButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = VlDialogButton(
    onClick = onClick,
    modifier = modifier,
    isPrimary = isPrimary,
    isDestructive = isDestructive,
    isLoading = isLoading,
    enabled = enabled,
    icon = icon,
    label = label,
    content = null,
)
