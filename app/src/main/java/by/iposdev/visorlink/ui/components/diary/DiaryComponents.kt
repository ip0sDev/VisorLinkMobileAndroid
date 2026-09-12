package by.iposdev.visorlink.ui.components.diary

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.data.model.SavedMessage
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.screens.diary.DiaryStats
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun DiaryStatsCard(stats: DiaryStats) {
    VlSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = stringResource(R.string.diary_stats_total), value = stats.totalEntries.toString(), icon = Icons.Default.Book)
            StatItem(label = stringResource(R.string.diary_stats_month), value = stats.monthlyEntries.toString(), icon = Icons.Default.CalendarMonth)
            StatItem(label = stringResource(R.string.diary_stats_streak), value = "${stats.currentStreak}d", icon = Icons.Default.Whatshot, iconColor = Color(0xFFF97316))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector, iconColor: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconColor ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DateSelector(selectedDate: Calendar, onDateSelected: (Calendar) -> Unit) {
    val df = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { 
            val newDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            onDateSelected(newDate)
        }) {
            Icon(Icons.Default.ChevronLeft, null)
        }
        
        Text(
            text = df.format(selectedDate.time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        IconButton(onClick = {
            val newDate = (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            onDateSelected(newDate)
        }) {
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun DiaryEntryCard(entry: SavedMessage, onClick: () -> Unit, onDelete: () -> Unit) {
    VlSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val timeStr = entry.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.text ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun DiaryPinDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    keystoreSecurityLevel: by.iposdev.visorlink.utils.KeystoreSecurityLevel = by.iposdev.visorlink.utils.KeystoreSecurityLevel.UNKNOWN,
    onPinEntered: (String, Boolean) -> Unit,
    onBiometric: () -> Unit,
    onDismiss: () -> Unit,
    clearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var saveBio by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diary_pin_prompt)) },
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

                if (hasBiometric && keystoreSecurityLevel != by.iposdev.visorlink.utils.KeystoreSecurityLevel.UNKNOWN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                    TextButton(onClick = onBiometric, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.diary_bio_unlock))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPinEntered(pin, saveBio) }, enabled = pin.length >= 4) {
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
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(Path()) }
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var color by remember { mutableStateOf(Color.Red) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.diary_format_draw)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Dummy implementation for saving drawing
                            onSave(Uri.EMPTY)
                        }) { Icon(Icons.Default.Check, null) }
                    }
                )

                val colors = listOf(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow)
                LazyRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(colors) { c ->
                        Box(
                            Modifier.size(40.dp).clip(VlTheme.tokens.shapes.indicator).background(c)
                                .clickable { color = c }
                                .then(if (color == c) Modifier.background(Color.White.copy(0.3f)) else Modifier)
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(Color.White, VlTheme.tokens.shapes.button)
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