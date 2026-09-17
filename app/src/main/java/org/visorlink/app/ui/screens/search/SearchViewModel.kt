package org.visorlink.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.TagSearchResult
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val userResult: UserProfile? = null,
    val chatResult: TagSearchResult? = null,
    val isLoading: Boolean = false,
    val isJoining: Boolean = false,
    val error: String? = null,
    val notFound: Boolean = false
)

class SearchViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _currentUser = MutableStateFlow<UserProfile?>(null)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { _currentUser.value = it }
        }
    }

    fun initSearch(initialQuery: String?) {
        if (!initialQuery.isNullOrBlank() && _uiState.value.query.isBlank()) {
            onQueryChange(initialQuery)
            search()
        }
    }

    fun onQueryChange(q: String) {
        val sanitized = q.trim().removePrefix("@")
        _uiState.update {
            it.copy(query = sanitized, userResult = null, chatResult = null, error = null, notFound = false)
        }
    }

    fun search() {
        val q = _uiState.value.query
        if (q.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userResult = null, chatResult = null, notFound = false, error = null) }
            try {
                // 1. Ищем пользователя
                val user = try { userRepository.findUserByUsername(q) } catch (e: Exception) { null }

                // 2. Ищем канал/группу по тегу
                val chat = try {
                    val r = chatRepository.findByTag(q)
                    if (r.found) r else null
                } catch (e: Exception) { null }

                // Проверяем, не нашли ли мы сами себя
                val isMe = user?.uid == auth.currentUser?.uid
                val finalUser = if (isMe) null else user

                if (finalUser == null && chat == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notFound = true,
                            error = if (isMe) "That's you!" else null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, userResult = finalUser, chatResult = chat)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    suspend fun openOrCreateChat(targetUser: UserProfile): String {
        val me = _currentUser.value ?: throw Exception("Not logged in")
        return chatRepository.findOrCreateChat(me, targetUser.uid)
    }

    suspend fun joinChatByTag(tag: String): String {
        _uiState.update { it.copy(isJoining = true, error = null) }
        try {
            val chatId = chatRepository.joinByTag(tag)
            _uiState.update { it.copy(isJoining = false) }
            return chatId
        } catch (e: Exception) {
            _uiState.update { it.copy(isJoining = false, error = e.message) }
            throw e
        }
    }

    suspend fun joinByInviteToken(token: String): String {
        _uiState.update { it.copy(isJoining = true, error = null) }
        try {
            val (chatId, _) = chatRepository.joinByInvite(token)
            _uiState.update { it.copy(isJoining = false) }
            return chatId
        } catch (e: Exception) {
            _uiState.update { it.copy(isJoining = false, error = e.message) }
            throw e
        }
    }
}