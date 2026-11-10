package org.visorlink.app.ui.screens.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlSurface

/**
 * Полноэкранный флоу миграции шифрования (TODO §14.4): стадии с прогрессом,
 * в конце — recovery-код крупно с кнопкой «Копировать» (показывается один раз).
 * rollback=true — обратная миграция v2 → v1 после отключения kill-switch;
 * rotate=true — сброс ключа восстановления (ротация CK, старый код перестаёт действовать).
 */
@Composable
fun CryptoMigrationScreen(
    rollback: Boolean,
    rotate: Boolean = false,
    stage: String,
    textsDone: Int,
    textsTotal: Int,
    mediaDone: Int,
    mediaTotal: Int,
    finished: Boolean,
    failed: String?,
    recoveryCode: String?,
    onDone: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    val title = when {
        failed != null -> when {
            rollback -> "Возврат не завершён"
            rotate -> "Сброс не завершён"
            else -> "Обновление не завершено"
        }
        finished -> when {
            rollback -> "Возврат выполнен"
            rotate -> "Ключ восстановления обновлён"
            else -> "Шифрование обновлено"
        }
        rollback -> "Возврат к прежней схеме"
        rotate -> "Сброс ключа восстановления"
        else -> "Обновление шифрования"
    }

    // Общий прогресс: тексты + медиа, нормированные на их totals (0-тотал → indeterminate)
    val fraction: Float? = run {
        val tTotal = textsTotal.coerceAtLeast(0)
        val mTotal = mediaTotal.coerceAtLeast(0)
        val grandTotal = tTotal + mTotal
        if (grandTotal <= 0) null
        else {
            val tDone = if (textsDone < 0) 0 else textsDone.coerceAtMost(tTotal)
            val mDone = if (mediaDone < 0) 0 else mediaDone.coerceAtMost(mTotal)
            (tDone + mDone).toFloat() / grandTotal
        }
    }

    // Стадии для чек-листа
    data class Step(val label: String, val progress: String?, val done: Boolean, val active: Boolean)
    val steps = listOf(
        Step(
            label = if (rollback) "Документы" else "Тексты",
            progress = if (textsTotal > 0 && textsDone >= 0) "${textsDone.coerceAtMost(textsTotal)} / $textsTotal" else null,
            done = finished || (textsDone >= 0 && textsTotal > 0 && textsDone >= textsTotal),
            active = stage.startsWith(if (rollback) "Возврат" else "Пере-шифровка текстов")
        ),
        Step(
            label = "Медиа",
            progress = if (mediaTotal > 0 && mediaDone >= 0) "${mediaDone.coerceAtMost(mediaTotal)} / $mediaTotal" else null,
            done = finished || (mediaTotal > 0 && mediaDone >= mediaTotal),
            active = stage.startsWith("Пере-шифровка медиа")
        ),
        Step(
            label = "Настройки",
            progress = null,
            done = finished || stage.startsWith("Ключ восстановления") || stage == "Готово",
            active = stage.startsWith("Обновление настроек")
        ),
        Step(
            label = "Ключ восстановления",
            progress = null,
            done = finished,
            active = stage.startsWith("Ключ восстановления"),
            // шаг только для прямой миграции; для отката скрывается ниже
        )
    ).let { list -> if (rollback) list.filter { it.label != "Ключ восстановления" && it.label != "Медиа" } else list }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    failed != null -> "Данные не пострадали: недошедшие документы будут обработаны при следующем входе."
                    finished && rollback -> "Контент возвращён к прежней схеме шифрования."
                    finished && rotate -> "Старый код восстановления больше не действует. Новый код показан ниже — запишите его."
                    finished -> "Ваш ключ восстановления показан ниже. Запишите его — он больше не появится."
                    rotate -> "Идёт пере-шифровка контента под новый ключ. Старый код восстановления перестанет действовать."
                    else -> "Идёт пере-шифровка вашего контента. Не закрывайте приложение."
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            if (!finished && failed == null) {
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                }
                Spacer(Modifier.height(20.dp))

                VlSurface(contentPadding = PaddingValues(16.dp)) {
                    Column {
                        steps.forEach { step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                if (step.done) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                } else if (step.active) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(step.label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                step.progress?.let { p ->
                                    Spacer(Modifier.weight(1f))
                                    Text(p, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Recovery-код после прямой миграции ──
            if (finished && failed == null && !rollback && recoveryCode != null) {
                Spacer(Modifier.height(24.dp))
                VlSurface(contentPadding = PaddingValues(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ключ восстановления", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = recoveryCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        VlButton(onClick = { clipboard.setText(AnnotatedString(recoveryCode)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Копировать код")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Запишите его в надёжное место. Код показывается только один раз: без него «Избранное» и дневник не восстановить на новом устройстве.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (failed != null) {
                Text(
                    failed,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }

            if (finished || failed != null) {
                VlButton(onClick = onDone) {
                    Text(if (failed != null) stringResource(android.R.string.cancel) else "Продолжить")
                }
            }
        }
    }
}
