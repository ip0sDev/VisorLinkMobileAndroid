package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.rememberExthruStyle

/**
 * Кастомный алерт-диалог — порт `VlAlertDialog` + `vlShowDialog` из design_system.dart.
 * Кнопки-действия собираются через [VlDialogButton] и передаются в [actions].
 *
 * Пример:
 * ```
 * VlAlertDialog(
 *     appTheme = appTheme,
 *     onDismissRequest = { showDialog = false },
 *     title = { Text("Удалить чат?") },
 *     text = { Text("Это действие необратимо.") },
 *     actions = {
 *         VlDialogButton(appTheme = appTheme, onClick = { showDialog = false }) { Text("Отмена") }
 *         VlDialogButton(appTheme = appTheme, onClick = { onDelete() }, isPrimary = true, isDestructive = true) { Text("Удалить") }
 *     }
 * )
 * ```
 */
@Composable
fun VlAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    appTheme: AppTheme = AppTheme.BIOLUME,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    dismissible: Boolean = true,
) {
    val style = rememberExthruStyle(appTheme)
    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        VlSurface(
            appTheme = appTheme,
            modifier = modifier,
            isInput = false,
            customRadius = if (style.isForge) 0.dp else 28.dp,
            overrideColor = cs.surface.copy(alpha = 0.96f),
        ) {
            Column(Modifier.padding(start = 24.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)) {
                title?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface,
                            fontFamily = if (style.isForge) FontFamily.Monospace else null,
                        )
                    ) { it() }
                }
                if (title != null && text != null) Spacer(Modifier.height(16.dp))
                text?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontSize = 15.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp,
                            fontFamily = if (style.isForge) FontFamily.Monospace else null,
                        )
                    ) { it() }
                }
                if (actions != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                        content = actions,
                    )
                }
            }
        }
    }
}

/** Кнопка внутри [VlAlertDialog] — порт `VlDialogButton`. */
@Composable
fun VlDialogButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    appTheme: AppTheme = AppTheme.BIOLUME,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    content: @Composable () -> Unit,
) {
    val style = rememberExthruStyle(appTheme)
    val cs = MaterialTheme.colorScheme
    val active = onClick != null && !isLoading

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val color = if (isDestructive) style.destructive else if (isPrimary) style.accent else cs.onSurfaceVariant
    val opacity = if (!active) 0.4f else if (isPressed) 0.55f else 1f

    Row(
        modifier
            .alpha(opacity)
            .then(
                if (active) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick!!,
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
        } else {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(
                    color = color,
                    fontWeight = if (isPrimary) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    fontFamily = if (style.isForge) FontFamily.Monospace else null,
                )
            ) { content() }
        }
    }
}