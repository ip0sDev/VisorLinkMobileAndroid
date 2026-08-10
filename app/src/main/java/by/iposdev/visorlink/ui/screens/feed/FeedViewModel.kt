package by.iposdev.visorlink.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.FeedItem
import by.iposdev.visorlink.data.repository.FeedRepository
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().flatMapLatest { profile ->
                val interests = profile?.interestWeights ?: emptyMap()
                feedRepository.getFeedFlow(interests)
            }.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            delay(1000)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onItemClick(item: FeedItem) {
        viewModelScope.launch {
            feedRepository.incrementView(item.id)
        }
    }

    fun toggleLike(item: FeedItem) {
        val uid = currentUid
        if (uid.isEmpty()) return
        val isLiked = item.displayLikedUids.contains(uid)
        val cid = item.displayChatId ?: return
        val mid = item.messageId ?: item.id
        viewModelScope.launch {
            feedRepository.toggleLike(cid, mid, isLiked)
        }
    }
}