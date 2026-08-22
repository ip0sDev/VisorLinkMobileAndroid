package by.iposdev.visorlink.ui.components.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import by.iposdev.visorlink.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.data.repository.BotRepository
import by.iposdev.visorlink.data.repository.DmBot
import by.iposdev.visorlink.ui.update.UpdateChannel
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var botUid by remember { mutableStateOf("") }
    var userUid by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Админ-панель", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("🤖 Управление ботами", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            VlTextField(value = botUid, onValueChange = { botUid = it }, label = "UID бота", modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminBanBot").call(mapOf("botUid" to botUid, "banned" to true)).await(); Toast.makeText(context, "Забанен", Toast.LENGTH_SHORT).show() } catch(e:Exception){Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()} finally{isSaving=false} } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ban") }
                Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminBanBot").call(mapOf("botUid" to botUid, "banned" to false)).await(); Toast.makeText(context, "Разбанен", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.weight(1f)) { Text("Unban") }
            }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("📢 Каналы", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            VlTextField(value = channelId, onValueChange = { channelId = it }, label = "Chat ID канала", modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminVerifyChannel").call(mapOf("chatId" to channelId, "badge" to "official")).await(); Toast.makeText(context, "Верифицирован", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.fillMaxWidth()) { Text("Верифицировать") }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("👤 Пользователи", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            VlTextField(value = userUid, onValueChange = { userUid = it }, label = "UID пользователя (пусто = себе)", modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminGrantEternalPro").call(mapOf("targetUid" to userUid)).await(); Toast.makeText(context, "Вечный PRO выдан", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) { Text("Выдать Вечный PRO") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsManagerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val botRepository: BotRepository = koinInject()

    var bots by remember { mutableStateOf<List<DmBot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var botName by remember { mutableStateOf("") }
    var botUsername by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            isLoading = true
            try {
                bots = botRepository.listBots()
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_bots_manager_title), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text(stringResource(R.string.settings_bots_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (bots.isEmpty()) {
                    Text(stringResource(R.string.settings_bots_empty), modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    bots.forEach { bot ->
                        BotItem(
                            bot = bot,
                            onRegenerate = {
                                scope.launch {
                                    try {
                                        val newToken = botRepository.regenerateToken(bot.uid)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("bot token", newToken)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.settings_bots_token_copied), Toast.LENGTH_LONG).show()
                                        refresh()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${context.getString(R.string.toast_save_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    try {
                                        botRepository.deleteBot(bot.uid)
                                        refresh()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${context.getString(R.string.action_delete)} ${context.getString(R.string.toast_save_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text(stringResource(R.string.settings_bots_create_title), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                VlTextField(value = botName, onValueChange = { botName = it }, label = stringResource(R.string.settings_bots_field_name), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                VlTextField(value = botUsername, onValueChange = { botUsername = it }, label = stringResource(R.string.settings_bots_field_username), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (botName.isBlank() || botUsername.isBlank()) return@Button
                        scope.launch {
                            isSaving = true
                            try {
                                val token = botRepository.createBot(botName, botUsername)
                                Toast.makeText(context, "${context.getString(R.string.settings_info_saved)} Token: $token", Toast.LENGTH_LONG).show()
                                botName = ""; botUsername = ""
                                refresh()
                            } catch(e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.action_accept))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun BotItem(bot: DmBot, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bot.name, fontWeight = FontWeight.Bold)
                    Text("@${bot.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            if (bot.token != null) {
                Spacer(Modifier.height(8.dp))
                Text("Token: ${bot.token}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onRegenerate) {
                Text(stringResource(R.string.settings_bots_token_regenerate))
            }
        }
    }
}

@Composable
fun ChannelSelectionDialog(
    currentChannel: UpdateChannel,
    canaryAvailable: Boolean,
    onDismiss: () -> Unit,
    onSelect: (UpdateChannel) -> Unit
) {
    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Канал обновлений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateChannel.entries.forEach { channel ->
                    if (channel == UpdateChannel.CANARY && !canaryAvailable && currentChannel != UpdateChannel.CANARY) {
                        return@forEach
                    }
                    
                    VlOptionRow(
                        icon = when(channel) {
                            UpdateChannel.RELEASE -> Icons.Default.CheckCircle
                            UpdateChannel.BETA -> Icons.Default.BugReport
                            UpdateChannel.NIGHTLY -> Icons.Default.NightsStay
                            UpdateChannel.CANARY -> Icons.Default.Science
                        },
                        label = channel.title,
                        desc = when(channel) {
                            UpdateChannel.RELEASE -> "Стабильные версии"
                            UpdateChannel.BETA -> "Публичное тестирование"
                            UpdateChannel.NIGHTLY -> "Ежедневные сборки"
                            UpdateChannel.CANARY -> "Экспериментальные фичи"
                        },
                        selected = currentChannel == channel,
                        onClick = { onSelect(channel) }
                    )
                }
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
