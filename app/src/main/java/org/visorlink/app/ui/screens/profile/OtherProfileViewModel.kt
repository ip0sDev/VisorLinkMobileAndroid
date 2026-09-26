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
    private val auth: FirebaseAuth,
    val targetUid: String
) : ViewModel() {

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    init {
        viewModelScope.launch { 
            combine(
                userRepository.userProfileFlow(targetUid),
                PresenceManager.observePresence(targetUid)
            ) { profile, presence ->
                if (profile != null && presence != null) {
                    profile.copy(online = presence.online)
                } else {
                    profile
                }
            }.collect { _user.value = it }
        }
    }

    suspend fun openOrCreateChat(): String {
        val me = userRepository.getUserProfile(auth.currentUser!!.uid)
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