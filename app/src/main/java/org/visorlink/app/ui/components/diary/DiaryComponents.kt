package org.visorlink.app.ui.components.diary

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.R
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.data.model.SavedMessage
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import org.visorlink.app.ui.screens.diary.DiaryStats
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val jelly = rememberLiquidJellyState(softness = 0.14f, damping = 0.60f)
    IconButton(
        onClick = {
            if (isLiquidEnabled) jelly.pulse(0.16f)
            onClick()
        },
        modifier = Modifier
            .size(48.dp)
            .liquidJelly(jelly, enabled = isLiquidEnabled)
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun DiaryStatsCard(stats: DiaryStats) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val isLiquidEnabled = rememberLiquidEnabled()
    val isDark = cs.surface.luminance() < 0.5f

    val shape = if (isLiquidEnabled) RoundedCornerShape(28.dp) else tokens.shapes.card

    val cardBrush = remember(isLiquidEnabled, isDark, cs) {
        if (isLiquidEnabled) {
            val top = if (isDark) cs.surfaceContainer.copy(alpha = 0.95f) else cs.surfaceContainerLow.copy(alpha = 0.98f)
            val bottom = if (isDark) cs.surfaceContainerLow.copy(alpha = 0.88f) else cs.surfaceContainer.copy(alpha = 0.92f)
            Brush.verticalGradient(listOf(top, bottom))
        } else null
    }

    val cardBorder = remember(isLiquidEnabled, isDark, cs) {
        if (isLiquidEnabled) {
            val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.60f)
            val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
            BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape)
                else Modifier
            )
            .clip(shape)
            .then(
                if (cardBrush != null) Modifier.background(cardBrush, shape)
                else Modifier.background(
                    if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                    shape
                )
            )
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, shape)
                else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, shape)
                else Modifier
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = stringResource(R.string.diary_stats_total),
                value = stats.totalEntries.toString(),
                icon = Icons.Default.Book,
                isLiquid = isLiquidEnabled
            )
            StatItem(
                label = stringResource(R.string.diary_stats_month),
                value = stats.monthlyEntries.toString(),
                icon = Icons.Default.CalendarMonth,
                isLiquid = isLiquidEnabled
            )
            StatItem(
                label = stringResource(R.string.diary_stats_streak),
                value = "${stats.currentStreak}d",
                icon = Icons.Default.Whatshot,
                iconColor = Color(0xFFF97316),
                isLiquid = isLiquidEnabled
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: ImageVector,
    iconColor: Color? = null,
    isLiquid: Boolean = false
) {
    val jelly = rememberLiquidJellyState(softness = 0.08f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .liquidJelly(jelly, enabled = isLiquid)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (isLiquid) jelly.pulse(0.10f)
            }
            .then(
                if (isLiquid) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                } else Modifier
            )
    ) {
        Icon(icon, null, tint = iconColor ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DateSelector(selectedDate: Calendar, onDateSelected: (Calendar) -> Unit) {
    val df = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val isLiquidEnabled = rememberLiquidEnabled()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val shape = if (isLiquidEnabled) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    val leftJelly = rememberLiquidJellyState(softness = 0.12f)
    val rightJelly = rememberLiquidJellyState(softness = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLiquidEnabled) {
                    Modifier
                        .then(
                            if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape)
                            else Modifier
                        )
                        .clip(shape)
                        .background(cs.surfaceContainerHigh.copy(alpha = 0.6f), shape)
                        .then(
                            if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, shape)
                            else Modifier.border(1.dp, cs.outlineVariant.copy(alpha = 0.2f), shape)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isLiquidEnabled) leftJelly.pulse(0.12f)
                    val newDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                    onDateSelected(newDate)
                },
                modifier = Modifier.liquidJelly(leftJelly, enabled = isLiquidEnabled)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day", tint = cs.primary)
            }

            AnimatedContent(
                targetState = df.format(selectedDate.time),
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.92f)).togetherWith(
                        fadeOut(tween(120)) + scaleOut(targetScale = 1.08f)
                    )
                },
                label = "date_text"
            ) { dateStr ->
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }

            IconButton(
                onClick = {
                    if (isLiquidEnabled) rightJelly.pulse(0.12f)
                    val newDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                    onDateSelected(newDate)
                },
                modifier = Modifier.liquidJelly(rightJelly, enabled = isLiquidEnabled)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Day", tint = cs.primary)
            }
        }
    }
}

@Composable
fun DiaryEntryCard(
    entry: SavedMessage,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val shape = if (isLiquidEnabled) RoundedCornerShape(22.dp) else tokens.shapes.card
    val cardJelly = rememberLiquidJellyState(softness = 0.05f)

    val cardBorder = remember(isLiquidEnabled, isDark, cs) {
        if (isLiquidEnabled) {
            val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
            val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.03f) else cs.outlineVariant.copy(alpha = 0.10f)
            BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidJelly(cardJelly, enabled = isLiquidEnabled)
            .then(
                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape)
                else Modifier
            )
            .clip(shape)
            .background(
                if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                shape
            )
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, shape)
                else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, shape)
                else Modifier
            )
            .clickable(
                onClick = {
                    if (isLiquidEnabled) cardJelly.pulse(0.06f)
                    onClick()
                }
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val timeStr = entry.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
                if (timeStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = entry.text ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            val deleteJelly = rememberLiquidJellyState(softness = 0.14f)
            IconButton(
                onClick = {
                    if (isLiquidEnabled) deleteJelly.pulse(0.15f)
                    onDelete()
                },
                modifier = Modifier.liquidJelly(deleteJelly, enabled = isLiquidEnabled)
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = cs.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DiaryPinDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    keystoreSecurityLevel: org.visorlink.app.utils.KeystoreSecurityLevel = org.visorlink.app.utils.KeystoreSecurityLevel.UNKNOWN,
    onPinEntered: (String, Boolean) -> Unit,
    onBiometric: () -> Unit,
    onDismiss: () -> Unit,
    clearError: () -> Unit
) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.65f, stiffness = 420f)
    var pin by remember { mutableStateOf("") }
    var saveBio by remember { mutableStateOf(false) }
    val shape = if (isLiquidEnabled) RoundedCornerShape(28.dp) else MaterialTheme.shapes.extraLarge

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.liquidPopIn(popProgress, isLiquidEnabled),
        shape = shape,
        title = {
            Text(
                stringResource(R.string.diary_pin_prompt),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VlTextField(
                    value = pin,
                    onValueChange = { 
                        if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                            pin = it
                            if (pinError) clearError()
                        }
                    },
                    label = "PIN",
                    isError = pinError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text(stringResource(R.string.saved_pin_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                if (hasBiometric && keystoreSecurityLevel != org.visorlink.app.utils.KeystoreSecurityLevel.UNKNOWN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = keystoreSecurityLevel.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saveBio = !saveBio }) {
                    Checkbox(checked = saveBio, onCheckedChange = { saveBio = it })
                    Text(stringResource(R.string.saved_biometric_enable), style = MaterialTheme.typography.bodySmall)
                }
                
                if (hasBiometric) {
                    val bioJelly = rememberLiquidJellyState(softness = 0.10f)
                    TextButton(
                        onClick = {
                            if (isLiquidEnabled) bioJelly.pulse(0.12f)
                            onBiometric()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .liquidJelly(bioJelly, enabled = isLiquidEnabled)
                    ) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.diary_bio_unlock))
                    }
                }
            }
        },
        confirmButton = {
            val confirmJelly = rememberLiquidJellyState(softness = 0.10f)
            Button(
                onClick = {
                    if (isLiquidEnabled) confirmJelly.pulse(0.10f)
                    onPinEntered(pin, saveBio)
                },
                enabled = pin.length >= 4,
                modifier = Modifier.liquidJelly(confirmJelly, enabled = isLiquidEnabled),
                shape = if (isLiquidEnabled) RoundedCornerShape(18.dp) else ButtonDefaults.shape
            ) {
                Text(stringResource(R.string.tfa_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlDrawingDialog(
    onDismiss: () -> Unit,
    onSave: (Uri) -> Unit
) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.60f, stiffness = 380f)
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(Path()) }
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var color by remember { mutableStateOf(Color.Red) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .liquidPopIn(popProgress, isLiquidEnabled),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.diary_format_draw), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    actions = {
                        val saveJelly = rememberLiquidJellyState()
                        IconButton(
                            onClick = {
                                if (isLiquidEnabled) saveJelly.pulse()
                                // Dummy implementation for saving drawing
                                onSave(Uri.EMPTY)
                            },
                            modifier = Modifier.liquidJelly(saveJelly, enabled = isLiquidEnabled)
                        ) { Icon(Icons.Default.Check, null) }
                    }
                )

                val colors = listOf(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow)
                val paletteShape = if (isLiquidEnabled) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(paletteShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        items(colors) { c ->
                            val isSelected = color == c
                            val dotJelly = rememberLiquidJellyState(softness = 0.15f)
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .liquidJelly(dotJelly, enabled = isLiquidEnabled)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        if (isLiquidEnabled) dotJelly.pulse(0.18f)
                                        color = c
                                    }
                            )
                        }
                    }
                }

                val canvasShape = if (isLiquidEnabled) RoundedCornerShape(28.dp) else RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(canvasShape)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), canvasShape)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentPath = Path().apply { moveTo(offset.x, offset.y) } },
                                onDrag = { change, _ -> currentPath.lineTo(change.position.x, change.position.y) },
                                onDragEnd = { paths = paths + currentPath; currentPath = Path() }
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        paths.forEach { path ->
                            drawPath(path, color, style = Stroke(width = 8f, cap = StrokeCap.Round))
                        }
                        drawPath(currentPath, color, style = Stroke(width = 8f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}