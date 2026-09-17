package org.visorlink.app.ui.screens.stickers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.data.repository.StickerPackRepository
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
    private val auth: FirebaseAuth,
    private val usageRankManager: org.visorlink.app.utils.UsageRankManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerPackUiState(isLoading = true))
    val uiState: StateFlow<StickerPackUiState> = _uiState.asStateFlow()

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    init {
        viewModelScope.launch {
            repo.refreshPacks()
        }
        viewModelScope.launch {
            repo.observeUserPacks()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { packs ->
                    val ranked = usageRankManager?.rankPacks(packs) ?: packs
                    _uiState.update { it.copy(packs = ranked, isLoading = false) }
                }
        }
    }

    /**
     * Зафиксировать выбор стикера из пака и мгновенно переранжировать паки.
     */
    fun recordPackUsage(packId: String) {
        usageRankManager?.recordPackUsage(packId)
        _uiState.update { state ->
            state.copy(packs = usageRankManager?.rankPacks(state.packs) ?: state.packs)
        }
    }

    // ─── Delete pack ──────────────────────────────────────────────────────────

    fun deletePack(packId: String, isOwner: Boolean) {
        viewModelScope.launch {
            try { repo.deletePack(packId, isOwner) }
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