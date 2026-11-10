package org.visorlink.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.repository.BotRepository
import org.visorlink.app.data.repository.DmBot
import org.visorlink.app.ui.components.LiquidPullRefreshLayout
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.led.VlProgressIndicator
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/** Ник бота: 3–24 символа, латиница, цифры и `_`. Возвращает ключ ресурса ошибки или null. */
private fun botUsernameErrorRes(raw: String): Int? {
    val u = raw.trim().removePrefix("@")
    if (u.length < 3 || u.length > 24) return R.string.bots_username_error_length
    if (!Regex("^[a-zA-Z0-9_]+$").matches(u)) return R.string.bots_username_error_chars
    return null
}

/** Человекочитаемая ошибка callables (F bots_screen.dart:39). */
private fun errText(e: Exception, fallback: String): String =
    (e as? FirebaseFunctionsException)?.message ?: fallback

// ── Экран «Мои боты» (порт Flutter `bots_screen.dart`) ──────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(
    onBack: () -> Unit,
    repo: BotRepository = koinInject(),
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val genericError = stringResource(R.string.bots_error_generic)

    var bots by remember { mutableStateOf<List<DmBot>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // Форма: бот для правки или «новый»
    var formBot by remember { mutableStateOf<DmBot?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    // Токен показывается один раз: недисмиссабельный диалог (title, token)
    var tokenDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun load() {
        try {
            bots = repo.list()
            error = null
        } catch (e: Exception) {
            error = errText(e, genericError)
        }
    }

    LaunchedEffect(Unit) { load() }

    // Диалоги подтверждения: regen / delete / reset-verification
    var confirmRegen by remember { mutableStateOf<DmBot?>(null) }
    var confirmDelete by remember { mutableStateOf<DmBot?>(null) }
    var confirmReset by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun refreshAfter(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                load()
            } catch (e: Exception) {
                error = errText(e, genericError)
            }
        }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            VlTopAppBar(
                title = { Text(stringResource(R.string.bots_title)) },
                navigationIcon = {
                    org.visorlink.app.ui.components.VlTopAppBarButton(onClick = { haptic.perform(HapticType.CLICK); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    org.visorlink.app.ui.components.VlTopAppBarButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK)
                            formBot = null
                            formOpen = true
                        },
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.bots_create), modifier = Modifier.size(20.dp))
                    }
                },
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                bots == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VlProgressIndicator()
                }
                bots?.isEmpty() == true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Default.SmartToy, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(stringResource(R.string.bots_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VlButton(onClick = { haptic.perform(HapticType.CLICK); formBot = null; formOpen = true }) {
                            Text(stringResource(R.string.bots_create))
                        }
                    }
                }
                else -> LiquidPullRefreshLayout(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch { load(); refreshing = false }
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items((bots ?: emptyList()).size, key = { (bots ?: emptyList())[it].uid }) { i ->
                            BotCard(
                                bot = bots!![i],
                                onEdit = { haptic.perform(HapticType.CLICK); formBot = bots!![i]; formOpen = true },
                                onRegenerate = { haptic.perform(HapticType.CLICK); confirmRegen = bots!![i] },
                                onDelete = { haptic.perform(HapticType.CLICK); confirmDelete = bots!![i] },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Токен: показывается один раз ──
    tokenDialog?.let { (title, token) ->
        VlAlertDialog(
            onDismissRequest = { /* недисмиссабельный: закрыть можно только кнопкой */ },
            dismissible = false,
            title = { Text("🔑 $title") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            .padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.bots_token_warning),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                    ) {
                        SelectionContainer { Text(token, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                    }
                }
            },
            actions = {
                var copied by remember { mutableStateOf(false) }
                VlDialogButton(
                    label = stringResource(if (copied) R.string.bots_copied else R.string.bots_copy),
                    icon = if (copied) Icons.Default.Key else null,
                    onClick = {
                        clipboard.setText(AnnotatedString(token))
                        haptic.perform(HapticType.CLICK)
                        copied = true
                    },
                )
                VlDialogButton(
                    label = stringResource(R.string.bots_done),
                    isPrimary = true,
                    onClick = { tokenDialog = null },
                )
            },
        )
    }

    // ── Подтверждения ──
    confirmRegen?.let { bot ->
        val regenTitle = stringResource(R.string.bots_token_title_regenerated)
        VlAlertDialog(
            onDismissRequest = { confirmRegen = null },
            title = { Text(stringResource(R.string.bots_confirm_regenerate_title)) },
            text = { Text(stringResource(R.string.bots_confirm_regenerate_body)) },
            actions = {
                VlDialogButton(label = stringResource(R.string.bots_cancel), onClick = { confirmRegen = null })
                VlDialogButton(
                    label = stringResource(R.string.bots_regenerate),
                    isPrimary = true,
                    onClick = {
                        confirmRegen = null
                        refreshAfter { tokenDialog = regenTitle to repo.regenerateToken(bot.uid) }
                    },
                )
            },
        )
    }
    confirmDelete?.let { bot ->
        VlAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.bots_confirm_delete_title)) },
            text = { Text(stringResource(R.string.bots_confirm_delete_body, bot.username)) },
            actions = {
                VlDialogButton(label = stringResource(R.string.bots_cancel), onClick = { confirmDelete = null })
                VlDialogButton(
                    label = stringResource(R.string.bots_delete),
                    isDestructive = true,
                    onClick = {
                        confirmDelete = null
                        refreshAfter { repo.delete(bot.uid) }
                    },
                )
            },
        )
    }
    confirmReset?.let { action ->
        VlAlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(stringResource(R.string.bots_confirm_reset_title)) },
            text = { Text(stringResource(R.string.bots_confirm_reset_body)) },
            actions = {
                VlDialogButton(label = stringResource(R.string.bots_cancel), onClick = { confirmReset = null })
                VlDialogButton(
                    label = stringResource(R.string.bots_continue),
                    isPrimary = true,
                    onClick = {
                        confirmReset = null
                        action()
                    },
                )
            },
        )
    }

    // ── Форма бота ──
    if (formOpen) {
        val createdTitle = stringResource(R.string.bots_token_title_new)
        BotFormDialog(
            bot = formBot,
            onDismiss = { formOpen = false },
            // После закрытия формы перезагружаем список; при создании — показываем токен
            onCreated = { token ->
                formOpen = false
                refreshAfter { }
                tokenDialog = createdTitle to token
            },
            onSaved = {
                formOpen = false
                refreshAfter { }
            },
            onResetConfirm = { action -> confirmReset = action },
            onError = { error = errText(it, genericError) },
        )
    }
}

// ── Карточка бота (F bots_screen.dart:143-211) ──────────────────────────────────

@Composable
private fun BotCard(
    bot: DmBot,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    VlCard(contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(cs.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!bot.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = bot.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(bot.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        org.visorlink.app.ui.components.VlTopAppBarButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.bots_edit),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                        BotTag(
                            text = bot.badge.uppercase(),
                            color = if (bot.badge == "unverified") cs.onSurfaceVariant else cs.primary,
                        )
                        if (bot.banned) BotTag(text = stringResource(R.string.bots_tag_banned), color = cs.error)
                    }
                    Text("@${bot.username}", color = cs.primary, fontSize = 13.sp)
                }
            }
            BotTag(
                text = stringResource(if (bot.receiveMessages) R.string.bots_tag_receiving else R.string.bots_tag_send_only),
                color = if (bot.receiveMessages) cs.primary else cs.onSurfaceVariant,
            )
            if (bot.webhookUrl.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "🔗 ${bot.webhookUrl}" + if (bot.hasWebhookSecret) "  🔒 HMAC" else "",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val clipboard = LocalClipboardManager.current
            val haptic = rememberHaptic()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.bots_uid_label, bot.uid),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "⧉",
                    color = cs.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            haptic.perform(HapticType.CLICK)
                            clipboard.setText(AnnotatedString(bot.uid))
                        }
                        .padding(4.dp),
                )
            }
            bot.lastDeliveryError?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    stringResource(R.string.bots_webhook_error, it),
                    color = cs.error,
                    fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VlDialogButton(label = stringResource(R.string.bots_regenerate), icon = Icons.Default.Key, onClick = onRegenerate)
                VlDialogButton(label = stringResource(R.string.bots_delete), isDestructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun BotTag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

// ── Диалог формы: создание и правка (F bots_screen.dart:214-356) ────────────────

@Composable
private fun BotFormDialog(
    bot: DmBot?,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    onSaved: () -> Unit,
    onResetConfirm: (action: () -> Unit) -> Unit,
    onError: (Exception) -> Unit,
) {
    val repo: BotRepository = koinInject()
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val editing = bot != null

    var name by remember { mutableStateOf(bot?.name ?: "") }
    var username by remember { mutableStateOf(bot?.username ?: "") }
    var receiveMessages by remember { mutableStateOf(bot?.receiveMessages ?: false) }
    var webhookUrl by remember { mutableStateOf(bot?.webhookUrl ?: "") }
    var secret by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val usernameErrorRes = if (!editing) botUsernameErrorRes(username) else null
    val valid = name.isNotBlank() && (editing || usernameErrorRes == null)

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            avatarUri = uri
            avatarName = uri.lastPathSegment?.substringAfterLast('/') ?: "avatar"
        }
    }

    fun submit() {
        if (busy || !valid) return
        haptic.perform(HapticType.CLICK)
        // При правке верифицированного бота — сначала подтверждение сброса (F _confirmReset)
        fun doSubmit() {
            scope.launch {
                busy = true
                formError = null
                try {
                    var avatarUrl: String? = null
                    if (avatarUri != null) {
                        avatarUrl = uploadBotAvatar(context, avatarUri!!)
                    }
                    if (editing) {
                        repo.edit(
                            botUid = bot!!.uid,
                            name = name,
                            receiveMessages = receiveMessages,
                            webhookUrl = webhookUrl,
                            webhookSecret = secret,
                            avatarUrl = avatarUrl,
                        )
                        onSaved()
                    } else {
                        val token = repo.create(
                            name = name,
                            username = username,
                            receiveMessages = receiveMessages,
                            webhookUrl = webhookUrl,
                            webhookSecret = secret,
                        )
                        onCreated(token)
                    }
                } catch (e: Exception) {
                    formError = context.getString(R.string.bots_form_error)
                    onError(e)
                } finally {
                    busy = false
                }
            }
        }
        if (editing && bot!!.badge != "unverified") {
            onResetConfirm { doSubmit() }
        } else {
            doSubmit()
        }
    }

    VlAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                if (editing) stringResource(R.string.bots_form_title_edit, bot!!.username)
                else stringResource(R.string.bots_form_title_new)
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VlTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.bots_form_name),
                    enabled = !busy,
                    maxLines = 1,
                )
                if (!editing) {
                    VlTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = stringResource(R.string.bots_form_username),
                        enabled = !busy,
                        isError = usernameErrorRes != null && username.isNotEmpty(),
                        supportingText = usernameErrorRes?.let { if (username.isNotEmpty()) stringResource(it) else null },
                        maxLines = 1,
                    )
                }
                if (editing) {
                    Text(
                        avatarName?.let { stringResource(R.string.bots_form_avatar_file, it) }
                            ?: stringResource(R.string.bots_form_change_avatar),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) { avatarPicker.launch("image/*") }
                            .padding(vertical = 4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.bots_form_receive), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bots_form_receive_sub), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VlSwitch(checked = receiveMessages, onCheckedChange = { receiveMessages = it })
                }
                VlTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    placeholder = "https://…",
                    label = stringResource(R.string.bots_form_webhook),
                    enabled = !busy,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    maxLines = 1,
                )
                VlTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = stringResource(R.string.bots_form_secret),
                    placeholder = stringResource(
                        if (editing && bot!!.hasWebhookSecret) R.string.bots_form_secret_keep else R.string.bots_form_secret_optional
                    ),
                    enabled = !busy,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    maxLines = 1,
                )
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        actions = {
            VlDialogButton(label = stringResource(R.string.bots_cancel), enabled = !busy, onClick = onDismiss)
            VlDialogButton(
                label = stringResource(if (editing) R.string.bots_form_save else R.string.bots_create),
                isPrimary = true,
                isLoading = busy,
                enabled = valid,
                onClick = { submit() },
            )
        },
    )
}

/** Аватар → Firebase Storage `users/{uid}/bot_{ts}` (так пишет веб: другие пути закрыты правилами). */
private suspend fun uploadBotAvatar(context: Context, uri: Uri): String {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("No user")
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Cannot read avatar")
    val type = context.contentResolver.getType(uri) ?: "image/jpeg"
    val ref = com.google.firebase.storage.FirebaseStorage.getInstance().reference
        .child("users/$uid/bot_${System.currentTimeMillis()}")
    ref.putBytes(bytes, com.google.firebase.storage.StorageMetadata.Builder().setContentType(type).build()).await()
    return ref.downloadUrl.await().toString()
}
