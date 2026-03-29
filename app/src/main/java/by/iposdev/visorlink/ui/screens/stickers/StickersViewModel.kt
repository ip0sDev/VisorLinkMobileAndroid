package by.iposdev.visorlink.ui.screens.stickers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.data.repository.StickerPackRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI State ────────────────────────────────────────────────────────────────

data class StickerPackUiState(
    val packs: List<StickerPack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class AddPackBannerState {
    object Idle : AddPackBannerState()
    object Loading : AddPackBannerState()
    data class Done(val packName: String) : AddPackBannerState()
    object Error : AddPackBannerState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class StickerPackViewModel(
    private val repo: StickerPackRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerPackUiState(isLoading = true))
    val uiState: StateFlow<StickerPackUiState> = _uiState.asStateFlow()

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    init {
        viewModelScope.launch {
            repo.observeUserPacks()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { packs ->
                    _uiState.update { it.copy(packs = packs, isLoading = false) }
                }
        }
    }

    // ─── Create pack ──────────────────────────────────────────────────────────

    fun createPack(name: String, emoji: String, onSuccess: (packId: String) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val packId = repo.createPack(name, emoji)
                onSuccess(packId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Delete pack ──────────────────────────────────────────────────────────

    fun deletePack(packId: String) {
        viewModelScope.launch {
            try { repo.deletePack(packId) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Rename ───────────────────────────────────────────────────────────────

    fun renamePack(packId: String, name: String, emoji: String) {
        viewModelScope.launch {
            try { repo.renamePack(packId, name, emoji) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Upload sticker ───────────────────────────────────────────────────────

    fun uploadSticker(
        packId: String,
        uri: Uri,
        emoji: String,
        onSuccess: (StickerItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sticker = repo.uploadSticker(packId, uri, emoji)
                onSuccess(sticker)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Delete sticker ───────────────────────────────────────────────────────

    fun deleteSticker(packId: String, sticker: StickerItem) {
        viewModelScope.launch {
            try { repo.deleteStickerFromPack(packId, sticker) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Add foreign pack (from banner) ───────────────────────────────────────

    fun addForeignPack(
        packId: String,
        onResult: (AddPackBannerState) -> Unit
    ) {
        viewModelScope.launch {
            onResult(AddPackBannerState.Loading)
            try {
                val name = repo.addPackToUser(packId)
                onResult(AddPackBannerState.Done(name))
            } catch (e: Exception) {
                onResult(AddPackBannerState.Error)
            }
        }
    }

    suspend fun hasPack(packId: String) = repo.hasPack(packId)
    suspend fun getPackById(packId: String) = repo.getPackById(packId)

    fun clearError() { _uiState.update { it.copy(error = null) } }
}