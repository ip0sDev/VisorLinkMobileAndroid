package org.visorlink.app.ui.screens.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.toChatOrNull
import org.visorlink.app.data.model.Member
import org.visorlink.app.data.model.Topic
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.TopicsRepository
import org.visorlink.app.data.repository.UserRepository

data class TopicListUiState(
    val chat: Chat? = null,
    val topics: List<Topic> = emptyList(),
    val filteredTopics: List<Topic> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: Int = 0, // 0 = Все, 1 = Чаты, 2 = Задачи
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val createTitle: String = "",
    val createIcon: String = "💬",
    val createColor: String = "#35C7E8",
    val createType: String = "chat", // "chat" | "tasks"
    val isCreating: Boolean = false,
    val myMember: Member? = null,
    val userProfiles: Map<String, UserProfile> = emptyMap()
) {
    val canCreateTopic: Boolean
        get() = myMember?.let { !it.banned && !it.muted } ?: true
}

class TopicListViewModel(
    private val topicsRepository: TopicsRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    val chatId: String
) : ViewModel() {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(TopicListUiState())
    val uiState: StateFlow<TopicListUiState> = _uiState.asStateFlow()

    init {
        loadChatDetails()
        listenToTopics()
        listenToMembers()
    }

    private fun loadChatDetails() {
        viewModelScope.launch {
            db.collection("chats").document(chatId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val chat = snapshot.toChatOrNull()
                        _uiState.update { it.copy(chat = chat) }
                    }
                }
        }
    }

    private fun listenToTopics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                topicsRepository.ensureGeneralTopic(chatId)
            } catch (e: Exception) {
                android.util.Log.e("TopicListVM", "ensureGeneralTopic error: ${e.message}")
            }

            topicsRepository.topicsFlow(chatId).collect { topics ->
                android.util.Log.d("TopicListVM", "Received ${topics.size} topics for chat $chatId")
                _uiState.update { state ->
                    val filtered = applyFilterAndSearch(topics, state.searchQuery, state.selectedFilter)
                    state.copy(
                        topics = topics,
                        filteredTopics = filtered,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun listenToMembers() {
        viewModelScope.launch {
            chatRepository.membersFlow(chatId).collect { members ->
                val mine = members.find { it.uid == currentUid }
                _uiState.update { it.copy(myMember = mine) }

                // Загружаем профили участников для резолва имён
                members.forEach { member ->
                    if (member.uid !in _uiState.value.userProfiles) {
                        viewModelScope.launch {
                            val profile = try { userRepository.getUserProfile(member.uid) } catch (_: Exception) { null }
                            if (profile != null) {
                                _uiState.update { s ->
                                    s.copy(userProfiles = s.userProfiles + (member.uid to profile))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.topics, query, state.selectedFilter)
            state.copy(searchQuery = query, filteredTopics = filtered)
        }
    }

    fun onFilterSelected(filterIndex: Int) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.topics, state.searchQuery, filterIndex)
            state.copy(selectedFilter = filterIndex, filteredTopics = filtered)
        }
    }

    private fun applyFilterAndSearch(topics: List<Topic>, query: String, filterIndex: Int): List<Topic> {
        return topics.filter { topic ->
            val matchesFilter = when (filterIndex) {
                1 -> topic.type == "chat"
                2 -> topic.type == "tasks"
                else -> true
            }
            val matchesQuery = if (query.isBlank()) true else {
                topic.title.contains(query.trim(), ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createTitle = "",
                createIcon = "💬",
                createColor = "#35C7E8",
                createType = "chat",
                error = null
            )
        }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, error = null) }
    }

    fun onCreateTitleChanged(title: String) {
        _uiState.update { it.copy(createTitle = title.take(64)) }
    }

    fun onCreateIconChanged(icon: String) {
        _uiState.update { it.copy(createIcon = icon) }
    }

    fun onCreateColorChanged(color: String) {
        _uiState.update { it.copy(createColor = color) }
    }

    fun onCreateTypeChanged(type: String) {
        val defaultIcon = if (type == "tasks") "📋" else "💬"
        val defaultColor = if (type == "tasks") "#8C6BFF" else "#35C7E8"
        _uiState.update {
            it.copy(
                createType = type,
                createIcon = if (it.createIcon == "💬" || it.createIcon == "📋") defaultIcon else it.createIcon,
                createColor = if (it.createColor == "#35C7E8" || it.createColor == "#8C6BFF") defaultColor else it.createColor
            )
        }
    }

    fun createTopic(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        val title = state.createTitle.trim()
        if (title.isEmpty()) return

        if (!state.canCreateTopic) {
            _uiState.update { it.copy(error = "PERMISSION_DENIED") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            val result = topicsRepository.createTopic(
                chatId = chatId,
                title = title,
                icon = state.createIcon,
                color = state.createColor,
                type = state.createType
            )
            result.onSuccess { newTopicId ->
                _uiState.update { it.copy(isCreating = false, showCreateDialog = false) }
                onSuccess(newTopicId)
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, error = e.message ?: "PERMISSION_DENIED") }
            }
        }
    }

    fun deleteTopic(topic: Topic) {
        if (topic.isGeneral) return // Нельзя удалять главную тему
        viewModelScope.launch {
            topicsRepository.deleteTopic(chatId, topic.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
