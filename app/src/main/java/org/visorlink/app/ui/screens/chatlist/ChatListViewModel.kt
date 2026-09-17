package org.visorlink.app.ui.screens.chatlist

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.*
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.DraftManager
import org.visorlink.app.data.repository.BackendFallbackManager
import org.visorlink.app.utils.NotificationHelper
import org.visorlink.app.utils.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import org.visorlink.app.utils.NetworkMonitor

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val draftManager: DraftManager,
    private val fallbackManager: BackendFallbackManager? = null,
    private val context: Context? = null,
    private val sidebarTypingManager: org.visorlink.app.utils.SidebarTypingManager? = null,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor?.isOnline
        ?: MutableStateFlow(true).asStateFlow()

    val syncState: StateFlow<SyncState> = isOnline.map { online ->
        if (!online) SyncState.WAITING_FOR_NETWORK else SyncState.SYNCED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, if (networkMonitor?.isOnline?.value == false) SyncState.WAITING_FOR_NETWORK else SyncState.SYNCED)

    val typingMap: StateFlow<Map<String, Boolean>> = sidebarTypingManager?.typingMap
        ?: MutableStateFlow<Map<String, Boolean>>(emptyMap()).asStateFlow()

    val isManualFallbackActive: StateFlow<Boolean> = fallbackManager?.manualFallbackActive
        ?: MutableStateFlow(false).asStateFlow()

    fun retryNewBackend() {
        fallbackManager?.disableManualFallback()
    }

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

    private val observedPresenceUids = mutableSetOf<String>()

    init {
        sidebarTypingManager?.startListening(currentUid)
        viewModelScope.launch {
            chats.collect { list ->
                launch(Dispatchers.IO) {
                    list.forEach { chat ->
                        if (chat.unreadCountFor(currentUid) == 0) {
                            context?.let { NotificationHelper.clearNotification(it, chat.id) }
                        }
                    }
                }

                val directUids = list.filter { it.chatType() == ChatType.DIRECT }
                    .map { it.otherParticipantId(currentUid) }
                    .filter { it.isNotEmpty() }
                    .distinct()

                directUids.forEach { uid ->
                    if (uid !in _profileCache.value.keys) {
                        launch {
                            try {
                                userRepository.getUserProfile(uid)?.let { profile ->
                                    _profileCache.update { it + (uid to profile) }
                                }
                            } catch (e: Exception) {
                                Log.e("ChatListVM", "Failed to fetch profile for $uid: ${e.message}")
                            }
                        }
                    }

                    if (observedPresenceUids.add(uid)) {
                        launch {
                            PresenceManager.observePresence(uid).collect { presence ->
                                if (presence != null) {
                                    _profileCache.update { currentMap ->
                                        val existing = currentMap[uid]
                                        if (existing != null && existing.online != presence.online) {
                                            currentMap + (uid to existing.copy(online = presence.online))
                                        } else {
                                            currentMap
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sidebarTypingManager?.stopListening()
    }
}