package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised

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
    val isLiquidEnabled = rememberLiquidEnabled()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        // Диалог «всплывает» каплей: пружина с перелётом вместо мгновенной подстановки
        val popProgress = rememberLiquidPopProgress(isLiquidEnabled)

        val body: @Composable () -> Unit = {
            // Ширина как у M3 AlertDialog: без минимума короткий диалог («Выйти?»)
            // сжимался до ширины кнопок и выглядел обрывком
            Column(
                Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .padding(start = 24.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)
            ) {
                title?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface
                        )
                    ) { it() }
                }
                if (title != null && text != null) Spacer(Modifier.height(16.dp))
                text?.let {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontSize = 15.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp
                        )
                    ) { it() }
                }
                if (actions != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        content = actions,
                    )
                }
            }
        }

        if (tokens.structure.enabled) {
            // §3.1: диалоги и sheet сидят на surfaceContainerHigh; рельеф raised
            // + нейтральная грань, никакого свечения (§10).
            // Форма берётся из токенов напрямую: прибавка к cardRadius давала бы
            // в Forge скругление 8dp вместо прямого угла.
            val shape = tokens.shapes.card
            Box(
                modifier = modifier
                    .liquidPopIn(popProgress, isLiquidEnabled)
                    .vlRaised(tokens.structure, shape)
                    .clip(shape)
                    .background(cs.surfaceContainerHigh, shape)
                    .vlHairline(cs.outlineVariant, shape)
            ) { body() }
        } else {
            VlSurface(
                modifier = modifier.liquidPopIn(popProgress, isLiquidEnabled),
                isInput = false,
                overrideColor = cs.surface,
            ) { body() }
        }
    }
}

/**
 * Тот же диалог в форме API M3 `AlertDialog` (confirmButton / dismissButton /
 * icon). Нужен, чтобы экраны переходили с M3-диалогов заменой имени: раньше
 * десяток диалогов в ChatScreen, ChatSettingsScreen, SettingsScreen и
 * StorageManagerScreen рисовался M3 по умолчанию и выпадал из темы — в Forge
 * со скруглёнными углами и без жёсткой тени.
 */
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
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) { it() }
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

@Composable
fun VlDialogButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val active = onClick != null && enabled && !isLoading

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val color = if (isDestructive) cs.error else if (isPrimary) cs.primary else cs.onSurfaceVariant
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
                    fontSize = 15.sp
                )
            ) { content() }
        }
    }
}
