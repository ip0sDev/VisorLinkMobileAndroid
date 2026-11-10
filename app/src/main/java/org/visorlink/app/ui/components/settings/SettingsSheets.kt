package org.visorlink.app.ui.components.settings

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
import org.visorlink.app.ui.components.*
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
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlTextField


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

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("🎟️ Инвайт-коды", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            var inviteNote by remember { mutableStateOf("") }
            val authRepository: org.visorlink.app.data.repository.AuthRepository = koinInject()
            var adminSecretTokenData by remember { mutableStateOf<Pair<String, String>?>(null) }
            VlTextField(value = inviteNote, onValueChange = { inviteNote = it }, label = "Примечание к коду", modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val code = authRepository.adminCreateRegistrationCode(inviteNote)
                            adminSecretTokenData = "Инвайт-код" to code
                            inviteNote = ""
                        } catch(e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сгенерировать инвайт-код")
            }

            adminSecretTokenData?.let { (title, token) ->
                SecretTokenDialog(title = title, token = token, onDismiss = { adminSecretTokenData = null })
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
