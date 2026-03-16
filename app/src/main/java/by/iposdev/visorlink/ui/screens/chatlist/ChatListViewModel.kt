package by.iposdev.visorlink.ui.screens.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentUid: String get() = auth.currentUser!!.uid

    val currentUser: StateFlow<UserProfile?> = userRepository.currentUserFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chats: StateFlow<List<Chat>> = chatRepository.chatsFlow(currentUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _profileCache = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val profileCache: StateFlow<Map<String, UserProfile>> = _profileCache.asStateFlow()

    init {
        viewModelScope.launch {
            chats.collect { list ->
                val knownUids = _profileCache.value.keys
                list.map { it.otherParticipantId(currentUid) }
                    .filter { it.isNotEmpty() && it !in knownUids }
                    .distinct()
                    .forEach { uid ->
                        launch {
                            userRepository.getUserProfile(uid)?.let { profile ->
                                _profileCache.value = _profileCache.value + (uid to profile)
                            }
                        }
                    }
            }
        }
    }
}