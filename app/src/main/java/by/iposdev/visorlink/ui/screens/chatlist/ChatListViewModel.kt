package by.iposdev.visorlink.ui.screens.chatlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.DraftManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val draftManager: DraftManager
) : ViewModel() {

    val currentUid: String get() = auth.currentUser!!.uid

    val currentUser: StateFlow<UserProfile?> = userRepository.currentUserFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chats: StateFlow<List<Chat>> = chatRepository.allChatsFlow(currentUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotificationsCount: StateFlow<Int> = chatRepository.notificationsFlow(currentUid)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val drafts: StateFlow<Map<String, String>> = draftManager.draftsFlow

    private val _profileCache = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val profileCache: StateFlow<Map<String, UserProfile>> = _profileCache.asStateFlow()
    val savedMessagesEntry: Chat = Chat(
        id   = "saved_${currentUid}",
        type = "direct",
        name = "Избранное",
        lastMessage = "Нажмите, чтобы открыть",
    )

    init {
        viewModelScope.launch {
            chats.collect { list ->
                val knownUids = _profileCache.value.keys

                list.filter { it.chatType() == ChatType.DIRECT }
                    .map { it.otherParticipantId(currentUid) }
                    .filter { it.isNotEmpty() && it !in knownUids }
                    .distinct()
                    .forEach { uid ->
                        launch {
                            try {
                                userRepository.getUserProfile(uid)?.let { profile ->
                                    _profileCache.value = _profileCache.value + (uid to profile)
                                }
                            } catch (e: Exception) {
                                Log.e("ChatListVM", "Failed to fetch profile for $uid: ${e.message}")
                            }
                        }
                    }
            }
        }
    }
}