package by.iposdev.visorlink.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.MusicPlaylist
import by.iposdev.visorlink.data.model.MusicTrack
import by.iposdev.visorlink.data.repository.MusicRepository
import by.iposdev.visorlink.utils.MusicPlayerManager
import by.iposdev.visorlink.utils.MusicPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicUiState(
    val selectedTab: Int = 0, // 0 = Playlists, 1 = All Tracks, 2 = Device
    val searchQuery: String = "",
    val playlists: List<MusicPlaylist> = emptyList(),
    val allTracks: List<MusicTrack> = emptyList(),
    val deviceTracks: List<MusicTrack> = emptyList(),
    val selectedPlaylist: MusicPlaylist? = null,
    val selectedPlaylistTracks: List<MusicTrack> = emptyList(),
    val hasAudioPermission: Boolean = false,
    val playerState: MusicPlayerState = MusicPlayerState(),
    val downloadProgress: Map<String, Float> = emptyMap()
)

class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        // Подписка на состояние плеера
        viewModelScope.launch {
            playerManager.state.collect { playerState ->
                _uiState.update { it.copy(playerState = playerState) }
            }
        }

        // Подписка на плейлисты
        viewModelScope.launch {
            musicRepository.getPlaylistsFlow().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }

        // Подписка на все треки
        viewModelScope.launch {
            musicRepository.getAllTracksFlow().collect { tracks ->
                _uiState.update { it.copy(allTracks = tracks) }
            }
        }

        // Подписка на прогресс скачивания треков
        viewModelScope.launch {
            musicRepository.downloadProgress.collect { progressMap ->
                _uiState.update { it.copy(downloadProgress = progressMap) }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index, selectedPlaylist = null) }
        if (index == 2 && _uiState.value.hasAudioPermission) {
            loadDeviceTracks()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectPlaylist(playlist: MusicPlaylist?) {
        _uiState.update { it.copy(selectedPlaylist = playlist) }
        if (playlist != null) {
            viewModelScope.launch {
                musicRepository.getPlaylistTracksFlow(playlist.id).collect { tracks ->
                    _uiState.update { it.copy(selectedPlaylistTracks = tracks) }
                }
            }
        }
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack>) {
        playerManager.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun openFullscreenPlayer() {
        playerManager.openFullscreenPlayer()
    }

    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch {
            val isFav = musicRepository.toggleFavorite(track)
            playerManager.updateFavoriteStatus(track.id, isFav)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            if (_uiState.value.selectedPlaylist?.id == playlistId) {
                _uiState.update { it.copy(selectedPlaylist = null) }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun updateAudioPermission(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
        if (granted) {
            loadDeviceTracks()
        }
    }

    fun loadDeviceTracks() {
        viewModelScope.launch {
            val tracks = musicRepository.getDeviceTracks()
            _uiState.update { it.copy(deviceTracks = tracks) }
        }
    }
}
