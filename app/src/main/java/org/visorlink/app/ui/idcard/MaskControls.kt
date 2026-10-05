package org.visorlink.app.ui.idcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSettingsItem
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.MaskDuration
import org.visorlink.app.utils.MaskModeManager
import org.visorlink.app.utils.MaskState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * Mask Mode (спека §8): раздел во вкладке «ID-карта» и быстрая кнопка в шапке списка чатов.
 * Видно только при особом режиме — Standard-пользователь об этой функции не узнаёт (§10).
 */

/** «Маска до 18:30» / «Маска до 5 окт., 09:00» / «Маска до выключения». */
@Composable
fun maskUntilLabel(mask: MaskState): String {
    val until = mask.until ?: return stringResource(R.string.idcard_mask_until_off)
    val locale = LocalConfiguration.current.locales[0]
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(until))
    val sameDay = Calendar.getInstance().let { now ->
        val c = Calendar.getInstance().apply { timeInMillis = until }
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    }
    if (sameDay) return stringResource(R.string.idcard_mask_until, time)
    val date = SimpleDateFormat(if (locale.language == "ru") "d MMM" else "MMM d", locale).format(Date(until))
    return stringResource(R.string.idcard_mask_until_date, date, time)
}

@Composable
private fun durationLabel(d: MaskDuration) = stringResource(
    when (d) {
        MaskDuration.FOREVER -> R.string.idcard_mask_d_forever
        MaskDuration.M15 -> R.string.idcard_mask_d_m15
        MaskDuration.H1 -> R.string.idcard_mask_d_h1
        MaskDuration.H4 -> R.string.idcard_mask_d_h4
        MaskDuration.TOMORROW -> R.string.idcard_mask_d_tomorrow
    },
)

/** Раздел «Маска» во вкладке «ID-карта». Ничего не рисует без особого режима (и без активной маски). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MaskSection(modifier: Modifier = Modifier) {
    val state = LocalIdModeState.current
    val mask = state.mask
    if (!state.special && !mask.active) return
    val manager: MaskModeManager = koinInject()
    val cs = MaterialTheme.colorScheme
    var pickTime by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.idcard_mask_desc),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (mask.active) {
            VlSettingsItem(
                icon = Icons.Outlined.TheaterComedy,
                title = maskUntilLabel(mask),
                trailing = {
                    VlDialogButton(onClick = { manager.disable() }) { Text(stringResource(R.string.idcard_mask_off)) }
                },
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaskDuration.entries.forEach { d ->
                    MaskChip(durationLabel(d)) { manager.enable(d.until(System.currentTimeMillis())) }
                }
                MaskChip(stringResource(R.string.idcard_mask_custom_label), Icons.Default.Schedule) { pickTime = true }
            }
        }
    }

    if (pickTime) {
        val picker = rememberTimePickerState(is24Hour = true)
        VlAlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.idcard_mask_custom_label)) },
            text = { TimePicker(state = picker) },
            confirmButton = {
                VlDialogButton(onClick = {
                    manager.enable(MaskDuration.untilClock(picker.hour, picker.minute, System.currentTimeMillis()))
                    pickTime = false
                }, isPrimary = true) { Text(stringResource(R.string.idcard_mask_on)) }
            },
            dismissButton = { VlDialogButton(onClick = { pickTime = false }) { Text(stringResource(R.string.idcard_tab_cancel)) } },
        )
    }
}

@Composable
private fun MaskChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = VlTheme.tokens.shapes.chip
    Row(
        Modifier
            .clip(shape)
            .background(cs.surfaceContainerHigh, shape)
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
    }
}

/**
 * Быстрая кнопка-маска в шапке: маска до выключения одним нажатием; пока маска включена —
 * индикатор «Маска до 18:30» с кнопкой снять. Ничего не рисует без особого режима.
 */
@Composable
fun MaskToolbarButton() {
    val state = LocalIdModeState.current
    val mask = state.mask
    if (!state.special && !mask.active) return
    val manager: MaskModeManager = koinInject()
    val cs = MaterialTheme.colorScheme
    if (mask.active) {
        val shape = VlTheme.tokens.shapes.chip
        Row(
            Modifier
                .clip(shape)
                .background(VlTheme.tokens.selectionFill, shape)
                .height(32.dp)
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // В шапке места мало: длинная подпись выдавливала заголовок, и тот переносился
            // по буквам на всю высоту экрана. Здесь — только время окончания, полная подпись
            // остаётся в описании для TalkBack и во вкладке «ID-карта»
            val full = maskUntilLabel(mask)
            Icon(Icons.Outlined.TheaterComedy, full, Modifier.size(16.dp), tint = cs.onSurface)
            mask.until?.let { until ->
                Text(
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, LocalConfiguration.current.locales[0]).format(Date(until)),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            IconButton(onClick = { manager.disable() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.idcard_mask_off), Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            }
        }
    } else {
        IconButton(onClick = { manager.enable(null) }) {
            Icon(Icons.Outlined.TheaterComedy, stringResource(R.string.idcard_mask_quick))
        }
    }
}
