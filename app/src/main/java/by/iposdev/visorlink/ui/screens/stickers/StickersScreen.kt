package by.iposdev.visorlink.ui.screens.stickers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.Sticker
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

// ── Picker (BottomSheet) ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    onSelectSticker: (Sticker) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StickersViewModel = koinViewModel()
) {
    val stickers by viewModel.stickers.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                Text(
                    "Stickers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        },
        tonalElevation = 4.dp
    ) {
        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEmotions, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No stickers yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add them in your profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = 4.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    Surface(
                        onClick = { onSelectSticker(sticker) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        AsyncImage(
                            model = sticker.url,
                            contentDescription = sticker.name,
                            modifier = Modifier
                                .size(90.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

// ── Management screen (полноэкранный, открывается из профиля) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickersScreen(
    onNavigateBack: () -> Unit,
    onSelectSticker: ((Sticker) -> Unit)? = null,
    viewModel: StickersViewModel = koinViewModel()
) {
    val stickers by viewModel.stickers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPickerMode = onSelectSticker != null
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var stickerName by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Sticker?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingUri = it; showNameDialog = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPickerMode) "Choose Sticker" else "My Stickers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isPickerMode) {
                        IconButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.Add, "Add sticker")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            stickers.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEmotions, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isPickerMode) "No stickers yet"
                        else "No stickers yet.\nTap + to add some!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    Card(
                        onClick = { onSelectSticker?.invoke(sticker) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(Modifier.size(100.dp)) {
                            AsyncImage(
                                model = sticker.url,
                                contentDescription = sticker.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Fit
                            )
                            if (!isPickerMode) {
                                IconButton(
                                    onClick = { deleteTarget = sticker },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false; pendingUri = null },
            title = { Text("Name this sticker") },
            text = {
                OutlinedTextField(
                    value = stickerName,
                    onValueChange = { stickerName = it },
                    label = { Text("Sticker name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUri?.let { viewModel.uploadSticker(it, stickerName.ifEmpty { "Sticker" }) }
                    showNameDialog = false; stickerName = ""; pendingUri = null
                }) { Text("Upload") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false; pendingUri = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete sticker?") },
            text = { Text("\"${sticker.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSticker(sticker); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}