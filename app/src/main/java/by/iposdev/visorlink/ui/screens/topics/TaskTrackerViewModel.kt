package by.iposdev.visorlink.ui.screens.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import by.iposdev.visorlink.data.model.Member
import by.iposdev.visorlink.data.model.TaskItem
import by.iposdev.visorlink.data.model.Topic
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.TopicsRepository
import by.iposdev.visorlink.data.repository.UserRepository

data class TaskTrackerUiState(
    val topic: Topic? = null,
    val tasks: List<TaskItem> = emptyList(),
    val members: List<Member> = emptyList(),
    val memberProfiles: Map<String, UserProfile> = emptyMap(),
    val currentTab: Int = 0, // 0 = Канбан, 1 = Список, 2 = Обсуждение
    val isLoading: Boolean = true,
    val error: String? = null,
    val showTaskDialog: Boolean = false,
    val editingTask: TaskItem? = null,
    // Task dialog inputs
    val dialogTitle: String = "",
    val dialogDescription: String = "",
    val dialogStatus: String = "todo",
    val dialogPriority: String = "medium",
    val dialogAssigneeId: String? = null,
    val dialogDueDate: String = "",
    val isSavingTask: Boolean = false,
    val myMember: Member? = null,
    val currentProfile: UserProfile? = null
) {
    val completedCount: Int get() = tasks.count { it.status == "done" }
    val totalCount: Int get() = tasks.size
    val progressPercent: Int get() = if (totalCount == 0) 0 else (completedCount * 100) / totalCount
    val canModifyTasks: Boolean get() = myMember?.let { !it.banned && !it.muted } ?: true

    fun tasksByStatus(status: String): List<TaskItem> = tasks.filter { it.status == status }
}

class TaskTrackerViewModel(
    private val topicsRepository: TopicsRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    val chatId: String,
    val topicId: String
) : ViewModel() {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(TaskTrackerUiState())
    val uiState: StateFlow<TaskTrackerUiState> = _uiState.asStateFlow()

    init {
        listenToTopic()
        listenToTasks()
        listenToMembers()
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val profile = try { userRepository.getUserProfile(currentUid) } catch (_: Exception) { null }
            _uiState.update { it.copy(currentProfile = profile) }
        }
    }

    private fun listenToTopic() {
        viewModelScope.launch {
            topicsRepository.getTopicFlow(chatId, topicId).collect { topic ->
                _uiState.update { it.copy(topic = topic) }
            }
        }
    }

    private fun listenToTasks() {
        viewModelScope.launch {
            topicsRepository.tasksFlow(chatId, topicId).collect { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
            }
        }
    }

    private fun listenToMembers() {
        viewModelScope.launch {
            chatRepository.membersFlow(chatId).collect { members ->
                val mine = members.find { it.uid == currentUid }
                _uiState.update { it.copy(members = members, myMember = mine) }

                // Резолвим профили участников (имя, аватар) для отображения исполнителей
                members.forEach { member ->
                    if (member.uid !in _uiState.value.memberProfiles) {
                        viewModelScope.launch {
                            val profile = try { userRepository.getUserProfile(member.uid) } catch (_: Exception) { null }
                            if (profile != null) {
                                _uiState.update { s ->
                                    s.copy(memberProfiles = s.memberProfiles + (member.uid to profile))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openCreateTaskDialog() {
        _uiState.update {
            it.copy(
                showTaskDialog = true,
                editingTask = null,
                dialogTitle = "",
                dialogDescription = "",
                dialogStatus = "todo",
                dialogPriority = "medium",
                dialogAssigneeId = null,
                dialogDueDate = "",
                error = null
            )
        }
    }

    fun openEditTaskDialog(task: TaskItem) {
        _uiState.update {
            it.copy(
                showTaskDialog = true,
                editingTask = task,
                dialogTitle = task.title,
                dialogDescription = task.description,
                dialogStatus = task.status,
                dialogPriority = task.priority,
                dialogAssigneeId = task.assigneeId,
                dialogDueDate = task.dueDate ?: "",
                error = null
            )
        }
    }

    fun closeTaskDialog() {
        _uiState.update { it.copy(showTaskDialog = false, editingTask = null, error = null) }
    }

    fun onDialogTitleChanged(title: String) = _uiState.update { it.copy(dialogTitle = title) }
    fun onDialogDescChanged(desc: String) = _uiState.update { it.copy(dialogDescription = desc) }
    fun onDialogStatusChanged(status: String) = _uiState.update { it.copy(dialogStatus = status) }
    fun onDialogPriorityChanged(priority: String) = _uiState.update { it.copy(dialogPriority = priority) }
    fun onDialogAssigneeChanged(assigneeId: String?) = _uiState.update { it.copy(dialogAssigneeId = assigneeId) }
    fun onDialogDueDateChanged(dueDate: String) = _uiState.update { it.copy(dialogDueDate = dueDate) }

    fun saveTask() {
        val state = _uiState.value
        val title = state.dialogTitle.trim()
        if (title.isEmpty()) return

        if (!state.canModifyTasks) {
            _uiState.update { it.copy(error = "PERMISSION_DENIED") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTask = true, error = null) }

            // Резолвим имя и аватар исполнителя из кэша профилей
            val assigneeProfile = state.dialogAssigneeId?.let { state.memberProfiles[it] }
            val assigneeName = assigneeProfile?.displayName?.ifBlank { assigneeProfile.username }
            val assigneeAvatar = assigneeProfile?.avatarUrl

            val currentUserName = state.currentProfile?.displayName?.ifBlank { state.currentProfile?.username } ?: ""

            val editing = state.editingTask
            if (editing == null) {
                // Создание новой задачи
                val newTask = TaskItem(
                    title = title,
                    description = state.dialogDescription.trim(),
                    status = state.dialogStatus,
                    priority = state.dialogPriority,
                    assigneeId = state.dialogAssigneeId,
                    assigneeName = assigneeName,
                    assigneeAvatar = assigneeAvatar,
                    dueDate = state.dialogDueDate.trim().ifEmpty { null },
                    createdBy = currentUid,
                    createdByName = currentUserName
                )
                val result = topicsRepository.createTask(chatId, topicId, newTask)
                result.onSuccess {
                    _uiState.update { it.copy(isSavingTask = false, showTaskDialog = false) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isSavingTask = false, error = e.message ?: "PERMISSION_DENIED") }
                }
            } else {
                // Обновление существующей задачи
                val updates = mutableMapOf<String, Any?>(
                    "title" to title,
                    "description" to state.dialogDescription.trim(),
                    "status" to state.dialogStatus,
                    "priority" to state.dialogPriority,
                    "assigneeId" to state.dialogAssigneeId,
                    "assigneeName" to assigneeName,
                    "assigneeAvatar" to assigneeAvatar,
                    "dueDate" to state.dialogDueDate.trim().ifEmpty { null }
                )
                val result = topicsRepository.updateTask(chatId, topicId, editing.id, updates)
                result.onSuccess {
                    _uiState.update { it.copy(isSavingTask = false, showTaskDialog = false, editingTask = null) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isSavingTask = false, error = e.message ?: "PERMISSION_DENIED") }
                }
            }
        }
    }

    fun moveTaskStatus(task: TaskItem, newStatus: String) {
        if (!_uiState.value.canModifyTasks) {
            _uiState.update { it.copy(error = "PERMISSION_DENIED") }
            return
        }
        viewModelScope.launch {
            topicsRepository.updateTaskStatus(chatId, topicId, task.id, newStatus)
        }
    }

    fun deleteTask(task: TaskItem) {
        if (!_uiState.value.canModifyTasks) {
            _uiState.update { it.copy(error = "PERMISSION_DENIED") }
            return
        }
        viewModelScope.launch {
            topicsRepository.deleteTask(chatId, topicId, task.id)
            if (_uiState.value.editingTask?.id == task.id) {
                closeTaskDialog()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
