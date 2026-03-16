package by.iposdev.visorlink.ui.screens.stickers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.Sticker
import by.iposdev.visorlink.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StickersViewModel(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val currentUid get() = auth.currentUser!!.uid

    val stickers: StateFlow<List<Sticker>> = userRepository.stickersFlow(currentUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun uploadSticker(uri: Uri, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try { userRepository.uploadSticker(uri, name) }
            catch (e: Exception) { _error.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun deleteSticker(sticker: Sticker) {
        viewModelScope.launch {
            try { userRepository.deleteSticker(sticker) }
            catch (e: Exception) { _error.value = e.message }
        }
    }

    fun clearError() { _error.value = null }
}