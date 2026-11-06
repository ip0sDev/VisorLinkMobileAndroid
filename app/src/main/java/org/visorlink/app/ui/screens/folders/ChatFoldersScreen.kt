package org.visorlink.app.ui.screens.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.visorlink.app.ui.components.VlSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.ChatFolder
import org.visorlink.app.data.model.ChatFolderRules
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.model.chatInFolder
import org.visorlink.app.data.repository.ChatFoldersRepository
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.theme.VlTheme

private data class FolderPreset(
    val emoji: String,
    val name: String,
    val rule: String,
    val icon: ImageVector,
    val desc: String
)

private val PRESETS = listOf(
    FolderPreset("💬", "Личные", "direct", Icons.Default.Person, "Только диалоги 1-на-1"),
    FolderPreset("👥", "Группы", "group", Icons.Default.Group, "Рабочие и общие чаты"),
    FolderPreset("📢", "Каналы", "channel", Icons.Default.Campaign, "Ленты и новости"),
    FolderPreset("🔔", "Непрочитанные", "unread", Icons.Default.MarkChatUnread, "Чаты с новыми сообщениями")
)

private val QUICK_EMOJIS = listOf("📁", "💬", "👥", "📢", "🔔", "💼", "🎮", "❤️", "🔥", "⭐", "🔒", "🤖")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFoldersScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit = {}
) {
    val foldersRepo: ChatFoldersRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val chatRepo: ChatRepository = koinInject()
    val scope = rememberCoroutineScope()

    val currentUser by userRepo.currentUserFlow().collectAsStateWithLifecycle(initialValue = null)
    val uid = currentUser?.uid.orEmpty()
    val isPro = currentUser?.isPro == true

    val folders by remember(uid) { foldersRepo.foldersFlow(uid) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val chats by remember(uid) { chatRepo.allChatsFlow(uid) }.collectAsStateWithLifecycle(initialValue = emptyList())

    var editingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun saveFolders(newList: List<ChatFolder>) {
        if (isSaving || uid.isEmpty()) return
        scope.launch {
            isSaving = true
            errorMsg = null
            try {
                foldersRepo.save(uid, newList)
            } catch (e: Exception) {
                errorMsg = if (!isPro) "Папки доступны с подпиской VisorLink PRO." else "Ошибка сохранения: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = { Text("Папки чатов") },
                navigationIcon = {
                    VlTopAppBarButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isPro && folders.size < ChatFolderRules.MAX_FOLDERS) {
                        VlTopAppBarButton(onClick = { isCreating = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Создать папку")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            VlAmbientGlow()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isPro) {
                    item {
                        VlCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = VlTheme.tokens.status.warning
                                    )
                                    Text(
                                        "Возможность VisorLink PRO",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Создавайте до 10 удобных вкладок-папок, группируйте чаты по правилам или вручную.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                VlButton(
                                    onClick = onOpenPro,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Оформить PRO")
                                }
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    item {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (folders.isNotEmpty()) {
                    item {
                        Text(
                            text = "Ваши папки (${folders.size}/${ChatFolderRules.MAX_FOLDERS})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(folders, key = { _, f -> f.id }) { index, folder ->
                        val matchingChats = chats.count { chatInFolder(it, folder, it.unreadCountFor(uid)) }
                        FolderItemCard(
                            folder = folder,
                            matchingCount = matchingChats,
                            canMoveUp = index > 0,
                            canMoveDown = index < folders.size - 1,
                            onMoveUp = {
                                val list = folders.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index - 1, item)
                                saveFolders(list)
                            },
                            onMoveDown = {
                                val list = folders.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index + 1, item)
                                saveFolders(list)
                            },
                            onEdit = { editingFolder = folder },
                            onDelete = {
                                val list = folders.filter { it.id != folder.id }
                                saveFolders(list)
                            }
                        )
                    }
                }

                // Быстрые шаблоны
                val existingRules = folders.flatMap { it.rules }.toSet()
                val availablePresets = PRESETS.filter { it.rule !in existingRules }

                if (isPro && availablePresets.isNotEmpty() && folders.size < ChatFolderRules.MAX_FOLDERS) {
                    item {
                        Text(
                            text = "Рекомендуемые шаблоны",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    itemsIndexed(availablePresets, key = { _, p -> p.rule }) { _, preset ->
                        PresetCard(
                            preset = preset,
                            onClick = {
                                val created = ChatFolder(
                                    id = ChatFolder.newId(),
                                    name = preset.name,
                                    emoji = preset.emoji,
                                    rules = setOf(preset.rule),
                                    chatIds = emptyList()
                                )
                                saveFolders(folders + created)
                            }
                        )
                    }
                }

                if (isPro && folders.size < ChatFolderRules.MAX_FOLDERS) {
                    item {
                        VlButton(
                            onClick = { isCreating = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Создать новую папку")
                        }
                    }
                }
            }
        }
    }

    if (isCreating || editingFolder != null) {
        val target = editingFolder
        FolderEditorDialog(
            folder = target,
            chats = chats,
            onDismiss = {
                isCreating = false
                editingFolder = null
            },
            onSave = { updated ->
                val nextList = if (target != null) {
                    folders.map { if (it.id == target.id) updated else it }
                } else {
                    folders + updated
                }
                saveFolders(nextList)
                isCreating = false
                editingFolder = null
            }
        )
    }
}

@Composable
private fun FolderItemCard(
    folder: ChatFolder,
    matchingCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    VlCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (folder.emoji.isNotEmpty()) {
                    Text(folder.emoji, fontSize = 20.sp)
                } else {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = buildString {
                    val rulesDesc = folder.rules.mapNotNull { ChatFolderRules.RULE_TITLES[it] }.joinToString(", ")
                    if (rulesDesc.isNotEmpty()) append(rulesDesc)
                    if (folder.chatIds.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append("${folder.chatIds.size} чатов вручную")
                    }
                    if (isEmpty()) append("Пустая папка") else append(" • $matchingCount чатов")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: FolderPreset,
    onClick: () -> Unit
) {
    VlCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(preset.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = preset.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderEditorDialog(
    folder: ChatFolder?,
    chats: List<Chat>,
    onDismiss: () -> Unit,
    onSave: (ChatFolder) -> Unit
) {
    var name by remember { mutableStateOf(folder?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(folder?.emoji.orEmpty()) }
    var selectedRules by remember { mutableStateOf(folder?.rules ?: emptySet()) }
    var selectedChatIds by remember { mutableStateOf(folder?.chatIds ?: emptyList()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        VlSurface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Text(
                text = if (folder == null) "Новая папка" else "Настройка папки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= ChatFolderRules.NAME_MAX) name = it
                },
                label = { Text("Название папки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Быстрый значок", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (em in QUICK_EMOJIS) {
                    val isSel = emoji == em
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { emoji = if (isSel) "" else em },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(em, fontSize = 18.sp)
                    }
                }
            }

            Text("Правила включения чатов", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (rule in ChatFolderRules.RULES) {
                    val isChecked = selectedRules.contains(rule)
                    val title = ChatFolderRules.RULE_TITLES[rule] ?: rule
                    FilterChip(
                        selected = isChecked,
                        onClick = {
                            selectedRules = if (isChecked) selectedRules - rule else selectedRules + rule
                        },
                        label = { Text(title) },
                        leadingIcon = if (isChecked) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

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
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            validationError = "Укажите название папки"
                            return@VlButton
                        }
                        if (selectedRules.isEmpty() && selectedChatIds.isEmpty()) {
                            validationError = "Выберите хотя бы одно правило или чаты"
                            return@VlButton
                        }
                        val result = ChatFolder(
                            id = folder?.id ?: ChatFolder.newId(),
                            name = trimmed,
                            emoji = emoji,
                            rules = selectedRules,
                            chatIds = selectedChatIds
                        )
                        onSave(result)
                    }
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}
}
