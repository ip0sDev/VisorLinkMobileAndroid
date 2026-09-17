package org.visorlink.app.ui.screens.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.visorlink.app.R
import org.visorlink.app.data.model.MusicPlaylist
import org.visorlink.app.data.model.MusicTrack
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.chat.AudioPlaybackDockBar
import org.visorlink.app.ui.theme.VlTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    viewModel: MusicViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateAudioPermission(isGranted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        viewModel.updateAudioPermission(granted)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VlAmbientGlow()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    VlTopAppBar(
                        title = {
                            Text(
                                text = uiState.selectedPlaylist?.title ?: stringResource(R.string.music_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (uiState.selectedPlaylist != null) {
                                    viewModel.selectPlaylist(null)
                                } else {
                                    onNavigateBack?.invoke()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (uiState.selectedTab == 0 && uiState.selectedPlaylist == null) {
                                IconButton(onClick = { showCreatePlaylistDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                                }
                            }
                        }
                    )

                    // Вкладки отображаются, только если не выбран отдельный плейлист
                    if (uiState.selectedPlaylist == null) {
                        TabRow(
                            selectedTabIndex = uiState.selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = uiState.selectedTab == 0,
                                onClick = { viewModel.setTab(0) },
                                text = { Text(stringResource(R.string.music_tab_playlists)) }
                            )
                            Tab(
                                selected = uiState.selectedTab == 1,
                                onClick = { viewModel.setTab(1) },
                                text = { Text(stringResource(R.string.music_tab_all_tracks)) }
                            )
                            Tab(
                                selected = uiState.selectedTab == 2,
                                onClick = { viewModel.setTab(2) },
                                text = { Text(stringResource(R.string.music_tab_device)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.selectedPlaylist != null) {
                    // Просмотр треков конкретного плейлиста
                    PlaylistTracksView(
                        playlist = uiState.selectedPlaylist!!,
                        tracks = uiState.selectedPlaylistTracks,
                        currentTrackId = uiState.playerState.currentTrack?.id,
                        isPlaying = uiState.playerState.isPlaying,
                        downloadProgress = uiState.downloadProgress,
                        onTrackClick = { track ->
                            viewModel.playTrack(track, uiState.selectedPlaylistTracks)
                        },
                        onRemoveTrack = { trackId ->
                            viewModel.removeTrackFromPlaylist(uiState.selectedPlaylist!!.id, trackId)
                        }
                    )
                } else {
                    when (uiState.selectedTab) {
                        0 -> PlaylistsTabView(
                            playlists = uiState.playlists,
                            onPlaylistClick = { playlist -> viewModel.selectPlaylist(playlist) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) }
                        )
                        1 -> AllTracksTabView(
                            tracks = uiState.allTracks,
                            searchQuery = uiState.searchQuery,
                            currentTrackId = uiState.playerState.currentTrack?.id,
                            isPlaying = uiState.playerState.isPlaying,
                            downloadProgress = uiState.downloadProgress,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onTrackClick = { track -> viewModel.playTrack(track, uiState.allTracks) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                        2 -> DeviceTracksTabView(
                            tracks = uiState.deviceTracks,
                            hasPermission = uiState.hasAudioPermission,
                            searchQuery = uiState.searchQuery,
                            currentTrackId = uiState.playerState.currentTrack?.id,
                            isPlaying = uiState.playerState.isPlaying,
                            downloadProgress = uiState.downloadProgress,
                            onRequestPermission = { permissionLauncher.launch(audioPermission) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onTrackClick = { track -> viewModel.playTrack(track, uiState.deviceTracks) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }
    }

    // Диалог создания плейлиста
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.music_playlist_create)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.music_playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylist(name)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ── Вкладка 0: Плейлисты ─────────────────────────────────────────────────────

@Composable
private fun PlaylistsTabView(
    playlists: List<MusicPlaylist>,
    onPlaylistClick: (MusicPlaylist) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.music_empty_playlists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = playlist.icon, fontSize = 24.sp)
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlist.trackCount} треков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    if (!playlist.isDefault) {
                        IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Вкладка 1: Все сохраненные треки ─────────────────────────────────────────

@Composable
private fun AllTracksTabView(
    tracks: List<MusicTrack>,
    searchQuery: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    downloadProgress: Map<String, Float> = emptyMap(),
    onSearchChange: (String) -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit
) {
    val filtered = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
            it.displayPerformer.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Поиск треков…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.music_empty_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        downloadProgress = downloadProgress[track.id],
                        onClick = { onTrackClick(track) },
                        onToggleFavorite = { onToggleFavorite(track) }
                    )
                }
            }
        }
    }
}

// ── Вкладка 2: Музыка с устройства ──────────────────────────────────────────

@Composable
private fun DeviceTracksTabView(
    tracks: List<MusicTrack>,
    hasPermission: Boolean,
    searchQuery: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    downloadProgress: Map<String, Float> = emptyMap(),
    onRequestPermission: () -> Unit,
    onSearchChange: (String) -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit
) {
    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📁",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.music_device_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.music_grant_permission))
                }
            }
        }
        return
    }

    val filtered = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
            it.displayPerformer.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Поиск на устройстве…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.music_empty_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        downloadProgress = downloadProgress[track.id],
                        onClick = { onTrackClick(track) },
                        onToggleFavorite = { onToggleFavorite(track) }
                    )
                }
            }
        }
    }
}

// ── Просмотр треков в выбранном плейлисте ────────────────────────────────────

@Composable
private fun PlaylistTracksView(
    playlist: MusicPlaylist,
    tracks: List<MusicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    downloadProgress: Map<String, Float> = emptyMap(),
    onTrackClick: (MusicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.music_empty_tracks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying && track.id == currentTrackId,
                    downloadProgress = downloadProgress[track.id],
                    onClick = { onTrackClick(track) },
                    onDelete = { onRemoveTrack(track.id) }
                )
            }
        }
    }
}

// ── Элемент строки трека ─────────────────────────────────────────────────────

@Composable
private fun TrackRowItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Мини-обложка или винил
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E20)),
                contentAlignment = Alignment.Center
            ) {
                val cover = track.coverUrl
                if (!cover.isNullOrBlank()) {
                    CachedImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🎵", fontSize = 16.sp)
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.displayPerformer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.localPath != null && File(track.localPath).exists() && File(track.localPath).length() > 0L) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Индикатор прогресса загрузки
            if (downloadProgress != null && downloadProgress in 0.0f..1.0f) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (track.duration > 0) {
                Text(
                    text = String.format("%d:%02d", track.duration / 60, track.duration % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
