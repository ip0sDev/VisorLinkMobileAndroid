package by.iposdev.visorlink.ui.screens.topics

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.Topic
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.ui.components.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic

private val SUGGESTED_EMOJIS = listOf("💬", "📋", "🎨", "🚀", "💡", "🐛", "📢", "⚙️", "🎯", "🔥", "📱", "💻", "🔒", "📊")
private val ACCENT_COLORS = listOf(
    "#35C7E8", "#8C6BFF", "#0EA5E9", "#10B981",
    "#F59E0B", "#EF4444", "#EC4899", "#6366F1"
)

fun parseHexColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF35C7E8)
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) Color(colorInt or 0xFF000000) else Color(colorInt)
    } catch (_: Exception) {
        Color(0xFF35C7E8)
    }
}

fun formatCompactTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val date = timestamp.toDate()
    val now = System.currentTimeMillis()
    val diff = now - date.time
    val cal = Calendar.getInstance().apply { time = date }
    val nowCal = Calendar.getInstance()
    return when {
        diff < 60_000 -> "сейчас"
        diff < 3600_000 -> "${(diff / 60_000).coerceAtLeast(1)}м"
        nowCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        nowCal.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 && nowCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) ->
            "вчера"
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onOpenTopicChat: (chatId: String, topicId: String) -> Unit,
    onOpenTaskTracker: (chatId: String, topicId: String) -> Unit,
    onOpenSettings: (chatId: String) -> Unit,
    viewModel: TopicListViewModel = koinViewModel { parametersOf(chatId) }
) {
    val state by viewModel.uiState.collectAsState()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val hapticEnabled = true

    Scaffold(
        topBar = {
            VlTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            onOpenSettings(chatId)
                        }
                    ) {
                        GroupChannelAvatar(
                            avatarUrl = state.chat?.avatarUrl,
                            name = state.chat?.name ?: "",
                            isChannel = state.chat?.chatType() == ChatType.CHANNEL,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.chat?.name ?: stringResource(R.string.topics_title),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val memberCount = state.chat?.memberCount ?: 0
                            Text(
                                text = if (memberCount > 0) {
                                    "${stringResource(R.string.members_topbar, memberCount).trimEnd(' ')} • ${stringResource(R.string.topics_title)}"
                                } else {
                                    stringResource(R.string.topics_title)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (state.canCreateTopic) {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            viewModel.openCreateDialog()
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.topics_create_title))
                        }
                    }
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        onOpenSettings(chatId)
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search & Filter Bar ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.topics_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = cs.onSurfaceVariant) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    shape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerLow,
                        unfocusedContainerColor = cs.surfaceContainerLow,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                VlSegmentedControl(
                    labels = listOf(
                        stringResource(R.string.topics_filter_all),
                        stringResource(R.string.topics_filter_chats),
                        stringResource(R.string.topics_filter_tasks)
                    ),
                    selectedIndex = state.selectedFilter,
                    onSelected = viewModel::onFilterSelected
                )
            }

            // ── Список тем ───────────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredTopics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "💬", fontSize = 48.sp)
                        Text(
                            text = stringResource(R.string.topics_empty),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.topics_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.canCreateTopic) {
                            Spacer(Modifier.height(8.dp))
                            VlButton(
                                onClick = { viewModel.openCreateDialog() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text(stringResource(R.string.topics_create_title))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filteredTopics, key = { it.id }) { topic ->
                        TopicCardItem(
                            topic = topic,
                            onClick = {
                                if (topic.isTasks) {
                                    onOpenTaskTracker(chatId, topic.id)
                                } else {
                                    onOpenTopicChat(chatId, topic.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Диалог создания темы ─────────────────────────────────────────────────
    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeCreateDialog() },
            title = { Text(stringResource(R.string.topics_create_title)) },
            confirmButton = {
                VlButton(
                    onClick = {
                        viewModel.createTopic { newTopicId ->
                            if (state.createType == "tasks") {
                                onOpenTaskTracker(chatId, newTopicId)
                            } else {
                                onOpenTopicChat(chatId, newTopicId)
                            }
                        }
                    },
                    enabled = state.createTitle.isNotBlank() && !state.isCreating
                ) {
                    Text(stringResource(R.string.topics_create_action))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.closeCreateDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Название
                    VlTextField(
                        value = state.createTitle,
                        onValueChange = viewModel::onCreateTitleChanged,
                        label = stringResource(R.string.topics_name_label),
                        placeholder = stringResource(R.string.topics_name_hint),
                        singleLine = true
                    )

                    // Тип темы
                    Text(
                        stringResource(R.string.topics_type_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    VlSegmentedControl(
                        labels = listOf(
                            stringResource(R.string.topics_type_chat),
                            stringResource(R.string.topics_type_tasks)
                        ),
                        selectedIndex = if (state.createType == "tasks") 1 else 0,
                        onSelected = { viewModel.onCreateTypeChanged(if (it == 1) "tasks" else "chat") }
                    )

                    // Выбор иконки
                    Text(
                        stringResource(R.string.topics_icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SUGGESTED_EMOJIS.forEach { emoji ->
                            val isSelected = state.createIcon == emoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) cs.primaryContainer else cs.surfaceContainerLow)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) cs.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.onCreateIconChanged(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    // Выбор акцентного цвета
                    Text(
                        stringResource(R.string.topics_color_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ACCENT_COLORS.forEach { hexColor ->
                            val color = parseHexColor(hexColor)
                            val isSelected = state.createColor.equals(hexColor, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) cs.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.onCreateColorChanged(hexColor) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        Text(
                            text = if (state.error == "PERMISSION_DENIED")
                                stringResource(R.string.topics_permission_denied)
                            else state.error!!,
                            color = cs.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun TopicCardItem(
    topic: Topic,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val topicColor = parseHexColor(topic.displayColor)
    val cardShape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cs.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp))
                .background(topicColor.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = topicColor.copy(alpha = 0.4f),
                    shape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = topic.displayIcon, fontSize = 22.sp)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (topic.isGeneral) stringResource(R.string.topics_general) else topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (topic.isGeneral) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "#Общий",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (topic.isClosed) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.topics_closed_badge),
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            val lastMsg = topic.lastMessageText()
            if (lastMsg.isNotEmpty()) {
                Text(
                    text = lastMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (topic.isTasks) stringResource(R.string.topics_filter_tasks) else stringResource(R.string.topics_filter_chats),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val timeText = formatCompactTime(topic.lastMessageAt ?: topic.createdAt)
            if (timeText.isNotEmpty()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }

            if (topic.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cs.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (topic.unreadCount > 99) "99+" else "${topic.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
