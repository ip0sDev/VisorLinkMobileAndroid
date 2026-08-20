package by.iposdev.visorlink.ui.components.saved

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessageActionSheet(onDismiss: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.saved_action_delete), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
fun SavedEmptyPlaceholder(modifier: Modifier, isEncrypted: Boolean) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "empty_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(breathScale)
                        .background(accentColor.copy(alpha = 0.2f), CircleShape)
                )
                Text("⭐", fontSize = 48.sp)
            }
            Text(stringResource(R.string.saved_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
            if (isEncrypted) {
                Surface(shape = RoundedCornerShape(16.dp), color = accentColor.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.saved_empty_footer), fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun PinInputDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    onPinEntered: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBiometric: () -> Unit,
    clearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_pin_prompt)) },
        text = {
            Column {
                VlTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            clearError()
                        }
                    },
                    label = stringResource(R.string.saved_pin_hint),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text(stringResource(R.string.saved_pin_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                if (!hasBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { useBiometrics = !useBiometrics }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
                        Text(stringResource(R.string.saved_biometric_enable), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPinEntered(pin, useBiometrics) },
                enabled = pin.length in 4..8
            ) { Text(stringResource(R.string.saved_action_unlock)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasBiometric) {
                    IconButton(onClick = onBiometric) {
                        Icon(Icons.Default.Fingerprint, tint = MaterialTheme.colorScheme.primary, contentDescription = stringResource(R.string.saved_biometric_subtitle))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
