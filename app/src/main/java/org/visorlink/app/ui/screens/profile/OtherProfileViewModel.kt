package org.visorlink.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OtherProfileViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    val blockRepository: org.visorlink.app.data.repository.BlockRepository,
    private val auth: FirebaseAuth,
    val targetUid: String
) : ViewModel() {

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    /** Кого заблокировал я — для пункта «Заблокировать / Разблокировать». */
    val blocks: StateFlow<Set<String>> = blockRepository.blocks

    /**
     * «Профиль недоступен»: пользователь заблокировал меня (`blockedBy`) или правила закрыли
     * `users/{uid}`. Данные не показываем, запрос не повторяем.
     */
    val unavailable: StateFlow<Boolean> = combine(
        blockRepository.blockedBy,
        userRepository.deniedUids,
    ) { blockedBy, denied -> targetUid in blockedBy || targetUid in denied }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { 
            combine(
                userRepository.userProfileFlow(targetUid),
                PresenceManager.observePresence(targetUid),
                blockRepository.blockedBy,
            ) { profile, presence, blockedBy ->
                if (profile != null && presence != null) {
                    // Заблокировавший меня не показывает «в сети»
                    profile.copy(online = presence.online && targetUid !in blockedBy)
                } else {
                    profile
                }
            }.collect { _user.value = it }
        }
    }

    suspend fun setBlocked(blocked: Boolean) = blockRepository.setBlocked(targetUid, blocked)

    suspend fun openOrCreateChat(): String {
        val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val me = userRepository.getUserProfile(myUid)
            ?: throw Exception("Not logged in")
        return chatRepository.findOrCreateChat(me, targetUid)
    }

    suspend fun openOrCreateEmergencyChat(): String {
        val target = _user.value ?: userRepository.getUserProfile(targetUid)
            ?: throw Exception("User profile not found")
        val chat = chatRepository.getOrCreateEmergencyChat(target)
        return chat.id
    }
}