package by.iposdev.visorlink.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val result: UserProfile? = null,
    val isLoading: Boolean = false,
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

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q, result = null, error = null, notFound = false) }
    }

    fun search() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, result = null, notFound = false, error = null) }
            try {
                val user = userRepository.findUserByUsername(q)
                when {
                    user == null -> _uiState.update { it.copy(isLoading = false, notFound = true) }
                    user.uid == auth.currentUser?.uid -> _uiState.update { it.copy(isLoading = false, error = "That's you!") }
                    else -> _uiState.update { it.copy(isLoading = false, result = user) }
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
}