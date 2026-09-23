package org.visorlink.app.ui.screens.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import org.visorlink.app.ui.components.NeumorphicLiquidSegmentedControl
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPillCardSlideOut
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    viewModel: MusicViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val isLiquidEnabled = rememberLiquidEnabled()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)
    val addJelly = rememberLiquidJellyState()

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
                        modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                        title = {
                            Text(
                                text = uiState.selectedPlaylist?.title ?: stringResource(R.string.music_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isLiquidEnabled) topBarJelly.press(0.06f)
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
                                IconButton(
                                    onClick = {
                                        if (isLiquidEnabled) addJelly.pulse(0.12f)
                                        showCreatePlaylistDialog = true
                                    },
                                    modifier = Modifier.liquidJelly(addJelly, enabled = isLiquidEnabled)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                                }
                            }
                        }
                    )

                    // Вкладки отображаются, только если не выбран отдельный плейлист
                    if (uiState.selectedPlaylist == null) {
                        if (isLiquidEnabled) {
                            val tabTitles = listOf(
                                stringResource(R.string.music_tab_playlists),
                                stringResource(R.string.music_tab_all_tracks),
                                stringResource(R.string.music_tab_device)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                NeumorphicLiquidSegmentedControl(
                                    tabs = tabTitles,
                                    selectedIndex = uiState.selectedTab,
                                    onTabSelected = { viewModel.setTab(it) }
                                )
                            }
                        } else {
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
                        isLiquidEnabled = isLiquidEnabled,
                        onTrackClick = { track ->
                            viewModel.playTrack(track, uiState.selectedPlaylistTracks)
                        },
                        onRemoveTrack = { trackId ->
                            viewModel.removeTrackFromPlaylist(uiState.selectedPlaylist!!.id, trackId)
                        },
                        onDownloadTrack = { viewModel.downloadTrack(it) }
                    )
                } else {
                    when (uiState.selectedTab) {
                        0 -> PlaylistsTabView(
                            playlists = uiState.playlists,
                            isLiquidEnabled = isLiquidEnabled,
                            onPlaylistClick = { playlist -> viewModel.selectPlaylist(playlist) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) }
                        )
                        1 -> AllTracksTabView(
                            tracks = uiState.allTracks,
                            searchQuery = uiState.searchQuery,
                            currentTrackId = uiState.playerState.currentTrack?.id,
                            isPlaying = uiState.playerState.isPlaying,
                            downloadProgress = uiState.downloadProgress,
                            isLiquidEnabled = isLiquidEnabled,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onTrackClick = { track -> viewModel.playTrack(track, uiState.allTracks) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadTrack = { viewModel.downloadTrack(it) }
                        )
                        2 -> DeviceTracksTabView(
                            tracks = uiState.deviceTracks,
                            hasPermission = uiState.hasAudioPermission,
                            searchQuery = uiState.searchQuery,
                            currentTrackId = uiState.playerState.currentTrack?.id,
                            isPlaying = uiState.playerState.isPlaying,
                            downloadProgress = uiState.downloadProgress,
                            isLiquidEnabled = isLiquidEnabled,
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
        val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.65f, stiffness = 420f)
        val dialogShape = if (isLiquidEnabled) RoundedCornerShape(28.dp) else MaterialTheme.shapes.extraLarge
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            modifier = Modifier.liquidPopIn(popProgress, isLiquidEnabled),
            shape = dialogShape,
            title = {
                Text(
                    stringResource(R.string.music_playlist_create),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.music_playlist_name)) },
                    singleLine = true,
                    shape = if (isLiquidEnabled) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val confirmJelly = rememberLiquidJellyState()
                Button(
                    onClick = {
                        if (isLiquidEnabled) confirmJelly.pulse(0.12f)
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylist(name)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    modifier = Modifier.liquidJelly(confirmJelly, enabled = isLiquidEnabled),
                    shape = if (isLiquidEnabled) RoundedCornerShape(16.dp) else ButtonDefaults.shape
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

// ── Поисковая панель ─────────────────────────────────────────────────────────

@Composable
private fun MusicSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    placeholder: String,
    isLiquidEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val shape = if (isLiquidEnabled) RoundedCornerShape(26.dp) else RoundedCornerShape(12.dp)
    val clearJelly = rememberLiquidJellyState()

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = cs.primary)
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (isLiquidEnabled) clearJelly.pulse(0.15f)
                        onSearchChange("")
                    },
                    modifier = Modifier.liquidJelly(clearJelly, enabled = isLiquidEnabled)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = cs.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isLiquidEnabled) cs.surfaceContainerHigh.copy(alpha = 0.5f) else Color.Transparent,
            unfocusedContainerColor = if (isLiquidEnabled) cs.surfaceContainerLow.copy(alpha = 0.5f) else Color.Transparent,
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = if (isLiquidEnabled) cs.outlineVariant.copy(alpha = 0.35f) else cs.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isLiquidEnabled && tokens.structure.enabled) Modifier.vlInset(tokens.structure, shape)
                else Modifier
            )
    )
}

// ── Вкладка 0: Плейлисты ─────────────────────────────────────────────────────

@Composable
private fun PlaylistsTabView(
    playlists: List<MusicPlaylist>,
    isLiquidEnabled: Boolean,
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

    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
            val cardShape = if (isLiquidEnabled) RoundedCornerShape(22.dp) else RoundedCornerShape(12.dp)
            val cardJelly = rememberLiquidJellyState(softness = 0.06f)

            val border = remember(isLiquidEnabled, isDark, cs) {
                if (isLiquidEnabled) {
                    val top = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
                    val bot = if (isDark) cs.outlineVariant.copy(alpha = 0.03f) else cs.outlineVariant.copy(alpha = 0.10f)
                    BorderStroke(1.dp, Brush.verticalGradient(listOf(top, bot)))
                } else null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidPillCardSlideOut(index = index, enabled = isLiquidEnabled, triggerKey = "playlists")
            ) {
                Surface(
                    shape = cardShape,
                    color = if (isLiquidEnabled) {
                        if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow
                    } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidJelly(cardJelly, enabled = isLiquidEnabled)
                        .then(
                            if (isLiquidEnabled && tokens.structure.enabled) Modifier.vlRaised(tokens.structure, cardShape)
                            else Modifier
                        )
                        .clip(cardShape)
                        .then(
                            if (border != null) Modifier.border(border, cardShape)
                            else if (isLiquidEnabled && tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, cardShape)
                            else Modifier
                        )
                        .clickable {
                            if (isLiquidEnabled) cardJelly.pulse(0.08f)
                            onPlaylistClick(playlist)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(if (isLiquidEnabled) RoundedCornerShape(16.dp) else RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = playlist.icon, fontSize = 26.sp)
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${playlist.trackCount} треков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        if (!playlist.isDefault) {
                            val deleteJelly = rememberLiquidJellyState(softness = 0.14f)
                            IconButton(
                                onClick = {
                                    if (isLiquidEnabled) deleteJelly.pulse(0.15f)
                                    onDeletePlaylist(playlist.id)
                                },
                                modifier = Modifier.liquidJelly(deleteJelly, enabled = isLiquidEnabled)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
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
    isLiquidEnabled: Boolean = false,
    onSearchChange: (String) -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onDownloadTrack: ((MusicTrack) -> Unit)? = null
) {
    val filtered = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
            it.displayPerformer.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MusicSearchBar(
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            placeholder = "Поиск треков…",
            isLiquidEnabled = isLiquidEnabled
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { _, track -> track.id }) { index, track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPillCardSlideOut(
                                index = index,
                                enabled = isLiquidEnabled,
                                triggerKey = searchQuery
                            )
                    ) {
                        TrackRowItem(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying && track.id == currentTrackId,
                            downloadProgress = downloadProgress[track.id],
                            isLiquidEnabled = isLiquidEnabled,
                            onClick = { onTrackClick(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onDownload = onDownloadTrack?.let { { it(track) } }
                        )
                    }
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
    isLiquidEnabled: Boolean = false,
    onRequestPermission: () -> Unit,
    onSearchChange: (String) -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit
) {
    if (!hasPermission) {
        val permJelly = rememberLiquidJellyState()
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
                Button(
                    onClick = {
                        if (isLiquidEnabled) permJelly.pulse(0.12f)
                        onRequestPermission()
                    },
                    modifier = Modifier.liquidJelly(permJelly, enabled = isLiquidEnabled),
                    shape = if (isLiquidEnabled) RoundedCornerShape(18.dp) else ButtonDefaults.shape
                ) {
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
        MusicSearchBar(
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            placeholder = "Поиск на устройстве…",
            isLiquidEnabled = isLiquidEnabled
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { _, track -> track.id }) { index, track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidPillCardSlideOut(
                                index = index,
                                enabled = isLiquidEnabled,
                                triggerKey = searchQuery
                            )
                    ) {
                        TrackRowItem(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying && track.id == currentTrackId,
                            downloadProgress = downloadProgress[track.id],
                            isLiquidEnabled = isLiquidEnabled,
                            onClick = { onTrackClick(track) },
                            onToggleFavorite = { onToggleFavorite(track) }
                        )
                    }
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
    isLiquidEnabled: Boolean = false,
    onTrackClick: (MusicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDownloadTrack: ((MusicTrack) -> Unit)? = null
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidPillCardSlideOut(
                            index = index,
                            enabled = isLiquidEnabled,
                            triggerKey = playlist.id
                        )
                ) {
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        downloadProgress = downloadProgress[track.id],
                        isLiquidEnabled = isLiquidEnabled,
                        onClick = { onTrackClick(track) },
                        onDelete = { onRemoveTrack(track.id) },
                        onDownload = onDownloadTrack?.let { { it(track) } }
                    )
                }
            }
        }
    }
}

// ── Анимированный эквалайзер трека ──────────────────────────────────────────

@Composable
private fun MiniEqualizerIndicator(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    Row(
        modifier = modifier.height(14.dp).width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val effH1 = if (isPlaying) h1 else 0.35f
        val effH2 = if (isPlaying) h2 else 0.6f
        val effH3 = if (isPlaying) h3 else 0.25f
        Box(Modifier.weight(1f).fillMaxHeight(effH1).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.weight(1f).fillMaxHeight(effH2).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.weight(1f).fillMaxHeight(effH3).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

// ── Элемент строки трека ─────────────────────────────────────────────────────

@Composable
private fun TrackRowItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    downloadProgress: Float? = null,
    isLiquidEnabled: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val trackShape = if (isLiquidEnabled) RoundedCornerShape(18.dp) else RoundedCornerShape(10.dp)
    val itemJelly = rememberLiquidJellyState(softness = 0.05f)

    val border = remember(isLiquidEnabled, isCurrent, isDark, cs) {
        if (isLiquidEnabled) {
            if (isCurrent) {
                BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.60f))
            } else {
                val top = if (isDark) cs.outlineVariant.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.50f)
                val bot = if (isDark) cs.outlineVariant.copy(alpha = 0.03f) else cs.outlineVariant.copy(alpha = 0.08f)
                BorderStroke(1.dp, Brush.verticalGradient(listOf(top, bot)))
            }
        } else null
    }

    Surface(
        shape = trackShape,
        color = if (isCurrent) {
            if (isLiquidEnabled) cs.primary.copy(alpha = 0.16f) else cs.primary.copy(alpha = 0.12f)
        } else {
            if (isLiquidEnabled) {
                if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow
            } else cs.surfaceVariant.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .liquidJelly(itemJelly, enabled = isLiquidEnabled)
            .then(
                if (isLiquidEnabled && tokens.structure.enabled) Modifier.vlRaised(tokens.structure, trackShape)
                else Modifier
            )
            .clip(trackShape)
            .then(
                if (border != null) Modifier.border(border, trackShape)
                else if (isLiquidEnabled && tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, trackShape)
                else Modifier
            )
            .clickable {
                if (isLiquidEnabled) itemJelly.pulse(0.06f)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isLiquidEnabled) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Мини-обложка или винил
            val coverShape = if (isLiquidEnabled) RoundedCornerShape(12.dp) else CircleShape
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(coverShape)
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
                    Text("🎵", fontSize = 18.sp)
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isCurrent) cs.primary else cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent && isPlaying) {
                        Spacer(Modifier.width(6.dp))
                        MiniEqualizerIndicator(isPlaying = true, color = cs.primary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.displayPerformer,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.localPath != null && File(track.localPath).exists() && File(track.localPath).length() > 0L) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = cs.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
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
                        color = cs.primary
                    )
                }
            }

            if (track.duration > 0) {
                Text(
                    text = String.format("%d:%02d", track.duration / 60, track.duration % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (onDownload != null && track.sourceType == MusicTrack.SOURCE_CHAT) {
                val isDownloaded = track.localPath != null && java.io.File(track.localPath).exists()
                if (!isDownloaded && (downloadProgress == null || downloadProgress !in 0.0f..1.0f)) {
                    val downJelly = rememberLiquidJellyState(softness = 0.14f)
                    IconButton(
                        onClick = {
                            if (isLiquidEnabled) downJelly.pulse(0.15f)
                            onDownload()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .liquidJelly(downJelly, enabled = isLiquidEnabled)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = cs.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (onToggleFavorite != null) {
                val favJelly = rememberLiquidJellyState(softness = 0.16f)
                IconButton(
                    onClick = {
                        if (isLiquidEnabled) favJelly.pulse(0.18f)
                        onToggleFavorite()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .liquidJelly(favJelly, enabled = isLiquidEnabled)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color.Red else cs.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onDelete != null) {
                val delJelly = rememberLiquidJellyState(softness = 0.14f)
                IconButton(
                    onClick = {
                        if (isLiquidEnabled) delJelly.pulse(0.15f)
                        onDelete()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .liquidJelly(delJelly, enabled = isLiquidEnabled)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

