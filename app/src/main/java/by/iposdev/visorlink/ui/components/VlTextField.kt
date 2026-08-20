package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlInset
import by.iposdev.visorlink.ui.theme.vlSignalBorder
import by.iposdev.visorlink.ui.theme.vlSignalGlow

/**
 * Текстовое поле — единственное место, где сигнальный слой включается сам по себе.
 *
 * Гайдлайн §7: neumorphic-inset в покое → при фокусе добавляется 1px контур
 * `primary` и `glowPrimary`, при этом inset-тень остаётся. Именно здесь
 * «расходуется» правило §1.2 «один сигнал за раз»: сфокусированное поле —
 * обычно единственный активный glow на экране.
 *
 * В MATERIAL3 деградирует до стандартного [OutlinedTextField], чтобы вид темы
 * оставался чистым M3E.
 */
@Composable
fun VlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle? = null,
    supportingText: String? = null,
    prefix: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    if (!tokens.structure.enabled) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle ?: LocalTextStyle.current,
            leadingIcon = leading,
            trailingIcon = trailing,
            prefix = prefix?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } }
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape: Shape = tokens.shapes.field
    val signalColor = if (isError) cs.error else cs.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                // BasicTextField падает, если singleLine и maxLines противоречат друг другу.
                maxLines = if (singleLine) 1 else maxLines,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                textStyle = (textStyle ?: LocalTextStyle.current).copy(color = cs.onSurface),
                cursorBrush = SolidColor(signalColor),
                modifier = Modifier
                    .fillMaxWidth()
                    // Сигнал рисуется снаружи формы, поэтому идёт до clip.
                    .vlSignalGlow(
                        tokens = tokens.signal,
                        color = signalColor,
                        shape = shape,
                        active = isFocused || isError,
                    )
                    .clip(shape)
                    .background(cs.surfaceContainer, shape)
                    // Inset-тень остаётся и при фокусе (§4.2).
                    .vlInset(tokens.structure, shape)
                    .vlSignalBorder(
                        tokens = tokens.signal,
                        color = signalColor,
                        shape = shape,
                        active = isFocused || isError,
                    )
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Box(Modifier.padding(end = 12.dp))
                        }
                        if (prefix != null && value.isEmpty()) {
                            Text(
                                text = prefix,
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant,
                            )
                        } else if (prefix != null) {
                            Text(
                                text = prefix,
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurface,
                            )
                        }
                        Box(Modifier.fillMaxWidth(if (trailing != null) 0.88f else 1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                        if (trailing != null) {
                            Box(Modifier.padding(start = 12.dp))
                            trailing()
                        }
                    }
                },
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = if (isError) cs.error else cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 16.dp)
            )
        }
    }
}
