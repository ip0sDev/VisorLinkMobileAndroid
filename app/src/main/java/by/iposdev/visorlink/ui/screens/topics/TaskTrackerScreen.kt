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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.TaskItem
import by.iposdev.visorlink.data.model.TaskPriority
import by.iposdev.visorlink.data.model.TaskStatus
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.screens.chat.ChatScreen
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackerScreen(
    chatId: String,
    topicId: String,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit = {},
    onOpenImageViewer: (String, String) -> Unit = { _, _ -> },
    onOpenChatSettings: (String) -> Unit = {},
    viewModel: TaskTrackerViewModel = koinViewModel { parametersOf(chatId, topicId) }
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val topicColor = parseHexColor(state.topic?.displayColor)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(topicColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = state.topic?.displayIcon ?: "📋", fontSize = 16.sp)
                            }
                            Text(
                                text = state.topic?.title ?: stringResource(R.string.tasks_title),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Мини-прогрессбар выполнения задач (X/Y выполнено (Z%))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val progressFloat = if (state.totalCount == 0) 0f else state.completedCount.toFloat() / state.totalCount
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = cs.primary,
                                trackColor = cs.surfaceContainerHigh
                            )
                            Text(
                                text = stringResource(
                                    R.string.tasks_progress_format,
                                    state.completedCount,
                                    state.totalCount,
                                    state.progressPercent
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    if (state.canModifyTasks) {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            viewModel.openCreateTaskDialog()
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tasks_add_button))
                        }
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
            // Вкладки переключения: «📋 Канбан-доска», «📄 Список задач», «💬 Обсуждение»
            VlSegmentedControl(
                labels = listOf(
                    stringResource(R.string.tasks_tab_kanban),
                    stringResource(R.string.tasks_tab_list),
                    stringResource(R.string.tasks_tab_chat)
                ),
                selectedIndex = state.currentTab,
                onSelected = viewModel::selectTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.currentTab) {
                    0 -> KanbanBoardTab(
                        state = state,
                        onTaskClick = viewModel::openEditTaskDialog,
                        onMoveStatus = viewModel::moveTaskStatus
                    )
                    1 -> TaskListTab(
                        state = state,
                        onTaskClick = viewModel::openEditTaskDialog
                    )
                    2 -> TaskChatTab(
                        chatId = chatId,
                        topicId = topicId,
                        onNavigateBack = onNavigateBack,
                        onOpenOtherProfile = onOpenOtherProfile,
                        onOpenImageViewer = onOpenImageViewer
                    )
                }
            }
        }
    }

    // Диалог создания / редактирования задачи
    if (state.showTaskDialog) {
        TaskEditDialog(
            state = state,
            onDismiss = viewModel::closeTaskDialog,
            onSave = viewModel::saveTask,
            onDelete = { state.editingTask?.let { viewModel.deleteTask(it) } },
            onTitleChanged = viewModel::onDialogTitleChanged,
            onDescChanged = viewModel::onDialogDescChanged,
            onStatusChanged = viewModel::onDialogStatusChanged,
            onPriorityChanged = viewModel::onDialogPriorityChanged,
            onAssigneeChanged = viewModel::onDialogAssigneeChanged,
            onDueDateChanged = viewModel::onDialogDueDateChanged
        )
    }
}

// ─── Канбан-доска ─────────────────────────────────────────────────────────────

@Composable
private fun KanbanBoardTab(
    state: TaskTrackerUiState,
    onTaskClick: (TaskItem) -> Unit,
    onMoveStatus: (TaskItem, String) -> Unit
) {
    val statuses = remember {
        listOf(
            TaskStatus.TODO,
            TaskStatus.IN_PROGRESS,
            TaskStatus.REVIEW,
            TaskStatus.DONE
        )
    }
    val pagerState = rememberPagerState(pageCount = { statuses.size })
    val coroutineScope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Ряд чипов колонок со счетчиками и быстрой прокруткой
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statuses.forEachIndexed { index, status ->
                val isSelected = pagerState.currentPage == index
                val count = state.tasksByStatus(status.id).size
                val statusColor = when (status) {
                    TaskStatus.TODO -> cs.outline
                    TaskStatus.IN_PROGRESS -> cs.primary
                    TaskStatus.REVIEW -> cs.tertiary
                    TaskStatus.DONE -> Color(0xFF4CAF50)
                }

                Surface(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) statusColor.copy(alpha = 0.18f) else cs.surfaceContainerLow,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, statusColor) else null,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = status.icon, fontSize = 14.sp)
                        Text(
                            text = status.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) cs.onSurface else cs.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) statusColor else cs.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else cs.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Полноэкранный пэйджер колонок с постраничным снаппингом
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            pageSpacing = 12.dp
        ) { page ->
            val status = statuses[page]
            val tasksInColumn = state.tasksByStatus(status.id)
            KanbanColumn(
                status = status,
                tasks = tasksInColumn,
                onTaskClick = onTaskClick,
                onMoveStatus = onMoveStatus
            )
        }
    }
}

@Composable
private fun KanbanColumn(
    status: TaskStatus,
    tasks: List<TaskItem>,
    onTaskClick: (TaskItem) -> Unit,
    onMoveStatus: (TaskItem, String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(cs.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = status.icon, fontSize = 18.sp)
                Text(
                    text = status.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${tasks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurfaceVariant
                )
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "✨", fontSize = 28.sp)
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    KanbanTaskCard(
                        task = task,
                        onClick = { onTaskClick(task) },
                        onMovePrev = {
                            val prevStatus = when (task.status) {
                                "done" -> "review"
                                "review" -> "in_progress"
                                "in_progress" -> "todo"
                                else -> null
                            }
                            if (prevStatus != null) onMoveStatus(task, prevStatus)
                        },
                        onMoveNext = {
                            val nextStatus = when (task.status) {
                                "todo" -> "in_progress"
                                "in_progress" -> "review"
                                "review" -> "done"
                                else -> null
                            }
                            if (nextStatus != null) onMoveStatus(task, nextStatus)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    onMovePrev: () -> Unit,
    onMoveNext: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val priority = TaskPriority.fromId(task.priority)
    val cardShape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cs.surface)
            .border(0.5.dp, cs.outlineVariant.copy(alpha = 0.5f), cardShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = priority.icon, fontSize = 12.sp)
                Text(
                    text = priority.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = parseHexColor(priority.hexColor),
                    fontWeight = FontWeight.Bold
                )
            }

            if (!task.dueDate.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = task.dueDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!task.assigneeName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CachedImage(
                        model = task.assigneeAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                        error = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(cs.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = task.assigneeName.take(1),
                                    fontSize = 10.sp,
                                    color = cs.onPrimaryContainer
                                )
                            }
                        }
                    )
                    Text(
                        text = task.assigneeName,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (task.status != "todo") {
                    IconButton(onClick = onMovePrev, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(14.dp))
                    }
                }
                if (task.status != "done") {
                    IconButton(onClick = onMoveNext, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Вперед", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ─── Список задач ─────────────────────────────────────────────────────────────

@Composable
private fun TaskListTab(
    state: TaskTrackerUiState,
    onTaskClick: (TaskItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    if (state.tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.tasks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.tasks, key = { it.id }) { task ->
                val status = TaskStatus.fromId(task.status)
                val priority = TaskPriority.fromId(task.priority)
                val cardShape = RoundedCornerShape(12.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(cs.surfaceContainerLow)
                        .clickable { onTaskClick(task) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = status.icon, fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${priority.icon} ${priority.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = parseHexColor(priority.hexColor)
                            )
                            if (!task.assigneeName.isNullOrBlank()) {
                                Text(
                                    text = "• ${task.assigneeName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            if (!task.dueDate.isNullOrBlank()) {
                                Text(
                                    text = "• ${task.dueDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Вкладка обсуждения темы ──────────────────────────────────────────────────

@Composable
private fun TaskChatTab(
    chatId: String,
    topicId: String,
    onNavigateBack: () -> Unit,
    onOpenOtherProfile: (String) -> Unit,
    onOpenImageViewer: (String, String) -> Unit
) {
    ChatScreen(
        chatId = chatId,
        otherUid = chatId,
        topicId = topicId,
        onNavigateBack = onNavigateBack,
        onOpenOtherProfile = onOpenOtherProfile,
        onOpenStickers = {},
        onOpenImageViewer = onOpenImageViewer
    )
}

// ─── Диалог редактирования / создания задачи ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditDialog(
    state: TaskTrackerUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onDescChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit,
    onPriorityChanged: (String) -> Unit,
    onAssigneeChanged: (String?) -> Unit,
    onDueDateChanged: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isEdit = state.editingTask != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) stringResource(R.string.tasks_edit_title) else stringResource(R.string.tasks_create_title))
        },
        confirmButton = {
            VlButton(
                onClick = onSave,
                enabled = state.dialogTitle.isNotBlank() && !state.isSavingTask
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEdit && state.canModifyTasks) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = cs.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VlTextField(
                    value = state.dialogTitle,
                    onValueChange = onTitleChanged,
                    label = stringResource(R.string.tasks_name_label),
                    singleLine = true
                )

                VlTextField(
                    value = state.dialogDescription,
                    onValueChange = onDescChanged,
                    label = stringResource(R.string.tasks_desc_label),
                    singleLine = false
                )

                Text(
                    stringResource(R.string.tasks_status_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskStatus.entries.forEach { status ->
                        val isSelected = state.dialogStatus == status.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStatusChanged(status.id) },
                            label = { Text("${status.icon} ${status.title}") }
                        )
                    }
                }

                Text(
                    stringResource(R.string.tasks_priority_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { priority ->
                        val isSelected = state.dialogPriority == priority.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPriorityChanged(priority.id) },
                            label = { Text("${priority.icon} ${priority.title}") }
                        )
                    }
                }

                Text(
                    stringResource(R.string.tasks_assignee_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.dialogAssigneeId == null,
                        onClick = { onAssigneeChanged(null) },
                        label = { Text(stringResource(R.string.tasks_assignee_none)) }
                    )

                    state.members.forEach { member ->
                        val profile = state.memberProfiles[member.uid]
                        val displayName = profile?.displayName?.ifBlank { profile.username } ?: member.uid.take(6)
                        val isSelected = state.dialogAssigneeId == member.uid
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAssigneeChanged(member.uid) },
                            label = { Text(displayName) },
                            leadingIcon = {
                                CachedImage(
                                    model = profile?.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape),
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(cs.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(displayName.take(1), fontSize = 10.sp)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                VlTextField(
                    value = state.dialogDueDate,
                    onValueChange = onDueDateChanged,
                    label = stringResource(R.string.tasks_due_date_label),
                    placeholder = stringResource(R.string.tasks_due_date_hint),
                    singleLine = true
                )

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
