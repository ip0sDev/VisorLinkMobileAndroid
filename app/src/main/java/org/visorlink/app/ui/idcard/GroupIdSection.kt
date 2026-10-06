package org.visorlink.app.ui.idcard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.repository.IdCardException
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.ui.components.VlSettingsItem
import org.visorlink.app.ui.components.VlSettingsSection
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.VlIdCard

/**
 * «ID группы» (спека §6, веб: GroupIdSection.jsx): владелец группы/канала с особым режимом
 * включает группе свою карту и тему Forge для участников с особым ID. Остальным блок не
 * показывается вовсе (§10).
 */
@Composable
fun GroupIdSection(chat: Chat, isOwner: Boolean) {
    val state = LocalIdModeState.current
    if (!state.enabled || !isOwner || !state.myMode.isSpecial) return
    if (chat.type != "group" && chat.type != "channel") return
    val repo: IdCardRepository = koinInject()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cooldownText = stringResource(R.string.idcard_group_err_cooldown)
    val genericText = stringResource(R.string.idcard_tab_err_generic)
    val groupId = chat.groupIdCard()
    val enabled = groupId?.enabled == true

    VlSettingsSection(title = stringResource(R.string.idcard_group_title)) {
        Column {
            VlSettingsItem(
                icon = Icons.Default.Badge,
                title = stringResource(R.string.idcard_group_toggle),
                subtitle = stringResource(R.string.idcard_group_desc),
                trailing = {
                    VlSwitch(
                        checked = enabled,
                        onCheckedChange = {
                            if (busy) return@VlSwitch
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    repo.setGroupIdCard(chat.id, !enabled)
                                } catch (e: IdCardException) {
                                    error = if (e.reason == "cooldown") cooldownText else e.message ?: genericText
                                } catch (e: Exception) {
                                    error = e.message ?: genericText
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val card = groupId?.asCard(state.myMode)
            if (card != null && enabled) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    VlIdCard(
                        card = card,
                        person = IdCardPerson(chat.name, chat.tag.ifBlank { chat.name }, chat.avatarUrl),
                        width = 280.dp,
                    )
                }
            }
        }
    }
}
