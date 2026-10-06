package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.ui.theme.VlTheme

private val HEX_INPUT = Regex("^[0-9A-Fa-f]{0,6}$")

/** `#RRGGBB` из цвета без альфы. */
private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun hsvOf(color: Color): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }

/**
 * Выбор произвольного цвета: HSV-ползунки и HEX-поле синхронизированы между собой.
 * Контраст кнопок и текста на выбранном акценте дорабатывает тема (withSignalAccent /
 * withMaterialAccent), поэтому здесь цвет не ограничивается.
 */
@Composable
fun VlColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (hex: String) -> Unit,
) {
    val initialHsv = remember { hsvOf(initial) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

    var hexText by remember { mutableStateOf(color.toHex().removePrefix("#")) }
    // HEX обновляется только от ползунков: при наборе вручную поле не перетираем
    fun setHsv(h: Float = hue, s: Float = sat, v: Float = value) {
        hue = h; sat = s; value = v
        hexText = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))).toHex().removePrefix("#")
    }

    val tokens = VlTheme.tokens
    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(color, tokens.shapes.adapt(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            tokens.shapes.adapt(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                        ),
                )
                PickerSlider(stringResource(R.string.custom_color_hue), hue, 0f..360f) { setHsv(h = it) }
                PickerSlider(stringResource(R.string.custom_color_saturation), sat, 0f..1f) { setHsv(s = it) }
                PickerSlider(stringResource(R.string.custom_color_brightness), value, 0f..1f) { setHsv(v = it) }
                VlTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val clean = input.removePrefix("#").take(6)
                        if (!HEX_INPUT.matches(clean)) return@VlTextField
                        hexText = clean.uppercase()
                        if (clean.length == 6) {
                            val parsed = hsvOf(Color(android.graphics.Color.parseColor("#$clean")))
                            hue = parsed[0]; sat = parsed[1]; value = parsed[2]
                        }
                    },
                    prefix = "#",
                    isError = hexText.length != 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text(stringResource(R.string.custom_color_cancel)) }
            VlDialogButton(
                onClick = { onConfirm(color.toHex()) },
                isPrimary = true,
                enabled = hexText.length == 6,
            ) { Text(stringResource(R.string.custom_color_apply)) }
        },
    )
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

/** Кружок «свой цвет» рядом с пресетами: радуга, пока цвет не выбран, затем сам цвет. */
@Composable
fun CustomColorCircle(
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = VlTheme.tokens.shapes.adapt(CircleShape)
    val fill = if (color != null) {
        Modifier.background(color, shape)
    } else {
        Modifier.background(
            androidx.compose.ui.graphics.Brush.sweepGradient(
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            ),
            shape,
        )
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(fill)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) cs.onSurface else cs.outlineVariant.copy(alpha = 0.3f),
                shape = shape,
            )
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isSelected) Icons.Default.Check else Icons.Default.Add,
            contentDescription = stringResource(R.string.custom_color_custom),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
