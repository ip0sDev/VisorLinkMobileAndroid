package by.iposdev.visorlink.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
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
        viewModelScope.launch { _user.value = userRepository.getUserProfile(targetUid) }
    }

    suspend fun openOrCreateChat(): String {
        val me = userRepository.getUserProfile(auth.currentUser!!.uid)
            ?: throw Exception("Not logged in")
        return chatRepository.findOrCreateChat(me, targetUid)
    }
}