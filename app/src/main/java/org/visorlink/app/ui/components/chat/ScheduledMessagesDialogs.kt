package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.data.model.ScheduledMessage
import org.visorlink.app.data.model.schedulePresets
import org.visorlink.app.data.model.scheduleTimeError
import org.visorlink.app.data.model.scheduledReasonText
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("d MMMM y, HH:mm", Locale("ru"))
private val SHORT_DATE_FORMAT = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/**
 * Диалог выбора времени для отложенного сообщения (VisorLink PRO).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMessageDialog(
    textPreview: String,
    onDismiss: () -> Unit,
    onConfirm: (sendAtMs: Long) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val presets = remember(now) { schedulePresets(now) }
    var selectedTimeMs by remember { mutableLongStateOf(presets.firstOrNull()?.atMs ?: (now + 3600 * 1000L)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateMs by remember { mutableLongStateOf(selectedTimeMs) }

    Dialog(onDismissRequest = onDismiss) {
        VlSurface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Отправить позже",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (textPreview.isNotEmpty()) {
                Text(
                    text = textPreview,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Быстрые варианты", style = MaterialTheme.typography.labelMedium)

            for (preset in presets) {
                val isSel = selectedTimeMs == preset.atMs
                VlCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTimeMs = preset.atMs
                            errorText = null
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            preset.label,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            SHORT_DATE_FORMAT.format(Date(preset.atMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Выбранное время:", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.Event, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DATE_FORMAT.format(Date(selectedTimeMs)))
                }
            }

            if (errorText != null) {
                Text(
                    errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Сообщение уйдёт автоматически, даже если вы не в сети. Возможность PRO (до 20 сообщений).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
                Spacer(Modifier.width(8.dp))
                VlButton(
                    onClick = {
                        val err = scheduleTimeError(selectedTimeMs)
                        if (err != null) {
                            errorText = err
                        } else {
                            onConfirm(selectedTimeMs)
                        }
                    }
                ) {
                    Text("Запланировать")
                }
            }
        }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimeMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        pickedDateMs = it
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) {
                    Text("Далее")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimeMs }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            VlSurface(
                modifier = Modifier.padding(16.dp),
                
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Text("Выберите время", style = MaterialTheme.typography.titleMedium)
                TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    VlButton(
                        onClick = {
                            val combinedCal = Calendar.getInstance().apply {
                                timeInMillis = pickedDateMs
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            selectedTimeMs = combinedCal.timeInMillis
                            showTimePicker = false
                            errorText = null
                        }
                    ) {
                        Text("Готово")
                    }
                }
            }
            }
        }
    }
}

/**
 * Нижний лист со списком отложенных сообщений в текущем чате.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesSheet(
    messages: List<ScheduledMessage>,
    onDismiss: () -> Unit,
    onSendNow: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Отложенные сообщения (${messages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            if (messages.isEmpty()) {
                Text(
                    "Нет запланированных сообщений в этом чате.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ScheduledMessageItem(
                            message = msg,
                            onSendNow = { onSendNow(msg.id) },
                            onCancel = { onCancel(msg.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageItem(
    message: ScheduledMessage,
    onSendNow: () -> Unit,
    onCancel: () -> Unit
) {
    VlCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.failed) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Ошибка отправки",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (message.sendAt != null) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            DATE_FORMAT.format(Date(message.sendAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!message.failed) {
                        IconButton(onClick = onSendNow, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Send, "Отправить сейчас", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (message.replyUsername != null) {
                Text(
                    text = "В ответ ${message.replyUsername}: ${message.replyText.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )

            if (message.failed && message.failReason != null) {
                Text(
                    text = scheduledReasonText(message.failReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
