package by.iposdev.visorlink.ui.screens.stickers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// ════════════════════════════════════════════════════════════════════════════════
//  StickerPickerBottomSheet — точка входа
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerBottomSheet(
    onDismiss: () -> Unit,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    viewModel: StickerPackViewModel = koinViewModel(),
    themeVm: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val appTheme by themeVm.appTheme.collectAsState()

    val isExthru = appTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        StickerPickerContent(
            packs = uiState.packs,
            isLoading = uiState.isLoading,
            currentUid = viewModel.currentUid,
            isExthru = isExthru,
            isDark = isDark,
            onStickerSelected = { packId, sticker ->
                onStickerSelected(packId, sticker)
            },
            onCreatePack = { name, emoji ->
                viewModel.createPack(name, emoji)
            },
            onDeletePack = { viewModel.deletePack(it) },
            onRenamePack = { id, n, e -> viewModel.renamePack(id, n, e) },
            onUploadSticker = { packId, uri, emoji ->
                viewModel.uploadSticker(packId, uri, emoji)
            },
            onDeleteSticker = { packId, sticker ->
                viewModel.deleteSticker(packId, sticker)
            },
            onDismiss = onDismiss
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Внутренняя компоновка
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun StickerPickerContent(
    packs: List<StickerPack>,
    isLoading: Boolean,
    currentUid: String,
    isExthru: Boolean,
    isDark: Boolean,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onCreatePack: (name: String, emoji: String) -> Unit,
    onDeletePack: (packId: String) -> Unit,
    onRenamePack: (packId: String, name: String, emoji: String) -> Unit,
    onUploadSticker: (packId: String, uri: Uri, emoji: String) -> Unit,
    onDeleteSticker: (packId: String, sticker: StickerItem) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPackIndex by remember { mutableIntStateOf(if (packs.isNotEmpty()) 0 else -1) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<StickerPack?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<StickerPack?>(null) }
    var showAddStickerSheet by remember { mutableStateOf<StickerPack?>(null) }

    LaunchedEffect(packs.size) {
        if (selectedPackIndex >= packs.size) selectedPackIndex = if (packs.isNotEmpty()) 0 else -1
        if (selectedPackIndex == -1 && packs.isNotEmpty()) selectedPackIndex = 0
    }

    val selectedPack = if (selectedPackIndex in packs.indices) packs[selectedPackIndex] else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .navigationBarsPadding()
    ) {
        // ── Drag handle ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isExthru) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(6.dp)
                        .nmInsetShadow(isDark, cornerRadius = 3.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // ── Заголовок строки с кнопками ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedPackIndex >= 0 && packs.isNotEmpty()) {
                ThemedIconButton(Icons.Default.ArrowBack, MaterialTheme.colorScheme.onSurface, isExthru, isDark) { selectedPackIndex = -1 }
                Spacer(Modifier.width(12.dp))
                Text(
                    selectedPack?.let { "${it.emoji} ${it.name}" } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (selectedPack?.authorId == currentUid) {
                    ThemedIconButton(Icons.Default.Add, MaterialTheme.colorScheme.primary, isExthru, isDark) { showAddStickerSheet = selectedPack }
                    Spacer(Modifier.width(10.dp))
                    ThemedIconButton(Icons.Default.Edit, MaterialTheme.colorScheme.onSurfaceVariant, isExthru, isDark) { showRenameDialog = selectedPack }
                    Spacer(Modifier.width(10.dp))
                    ThemedIconButton(Icons.Default.Delete, MaterialTheme.colorScheme.error, isExthru, isDark) { showDeleteConfirm = selectedPack }
                }
            } else {
                Text(
                    "Стикеры",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                ThemedIconButton(Icons.Default.Add, MaterialTheme.colorScheme.primary, isExthru, isDark) { showCreateDialog = true }
            }
        }

        ThemedDivider(isExthru, isDark)

        // ── Нижняя полоса с иконками паков ────────────────────────────────────
        PackTabBar(
            packs = packs,
            selectedIndex = selectedPackIndex,
            isExthru = isExthru,
            isDark = isDark,
            onSelect = { selectedPackIndex = it }
        )

        ThemedDivider(isExthru, isDark)

        // ── Контент ────────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                packs.isEmpty() -> EmptyPacksPlaceholder(onCreatePack = { showCreateDialog = true }, isExthru = isExthru, isDark = isDark)
                selectedPackIndex == -1 -> PackListView(
                    packs = packs,
                    currentUid = currentUid,
                    isExthru = isExthru,
                    isDark = isDark,
                    onSelectPack = { idx -> selectedPackIndex = idx },
                    onDeletePack = { showDeleteConfirm = it },
                    onRenamePack = { showRenameDialog = it }
                )
                selectedPack != null -> PackContentGrid(
                    pack = selectedPack,
                    currentUid = currentUid,
                    isExthru = isExthru,
                    isDark = isDark,
                    onStickerTap = { sticker -> onStickerSelected(selectedPack.id, sticker) },
                    onDeleteSticker = { sticker -> onDeleteSticker(selectedPack.id, sticker) }
                )
            }
        }
    }

    // ── Dialogs & Sheets ───────────────────────────────────────────────────────

    if (showCreateDialog) {
        CreatePackDialog(
            isExthru = isExthru, isDark = isDark,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, emoji ->
                onCreatePack(name, emoji)
                showCreateDialog = false
            }
        )
    }

    showRenameDialog?.let { pack ->
        CreatePackDialog(
            initialName = pack.name, initialEmoji = pack.emoji,
            title = "Переименовать пак", confirmLabel = "Сохранить",
            isExthru = isExthru, isDark = isDark,
            onDismiss = { showRenameDialog = null },
            onCreate = { name, emoji ->
                onRenamePack(pack.id, name, emoji)
                showRenameDialog = null
            }
        )
    }

    showDeleteConfirm?.let { pack ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Удалить пак?") },
            text = { Text("Пак «${pack.emoji} ${pack.name}» и все его стикеры будут удалены безвозвратно.") },
            confirmButton = {
                ThemedButton("Удалить", isExthru, isDark, isError = true) {
                    onDeletePack(pack.id)
                    showDeleteConfirm = null
                    if (selectedPack?.id == pack.id) selectedPackIndex = -1
                }
            },
            dismissButton = {
                ThemedButton("Отмена", isExthru, isDark) { showDeleteConfirm = null }
            }
        )
    }

    showAddStickerSheet?.let { pack ->
        AddStickerSheet(
            pack = pack, isExthru = isExthru, isDark = isDark,
            onDismiss = { showAddStickerSheet = null },
            onUpload = { uri, emoji ->
                onUploadSticker(pack.id, uri, emoji)
                showAddStickerSheet = null
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackTabBar
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun PackTabBar(
    packs: List<StickerPack>,
    selectedIndex: Int,
    isExthru: Boolean,
    isDark: Boolean,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        packs.forEachIndexed { index, pack ->
            val isSelected = index == selectedIndex

            val mod = Modifier
                .padding(horizontal = 4.dp)
                .size(44.dp)
                .then(
                    if (isExthru) {
                        if (isSelected) Modifier
                            .nmRaisedShadow(isDark, 6.dp, 3.dp, 12.dp, if(isDark) 0.55f else 0.40f, if(isDark) 0.08f else 0.65f)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        else Modifier.background(Color.Transparent, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    }
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    onSelect(index)
                    scope.launch { scrollState.animateScrollTo(index * 52) }
                }

            Box(modifier = mod, contentAlignment = Alignment.Center) {
                if (pack.stickers.isNotEmpty()) {
                    AsyncImage(
                        model = pack.stickers.first().url, contentDescription = pack.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(pack.emoji, fontSize = 22.sp)
                }
            }
        }

        // Кнопка "+ новый пак"
        val addMod = Modifier
            .padding(horizontal = 4.dp)
            .size(44.dp)
            .then(
                if (isExthru) Modifier
                    .nmRaisedShadow(isDark, 6.dp, 3.dp, 12.dp, if(isDark) 0.55f else 0.40f, if(isDark) 0.08f else 0.65f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                else Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(-1) }

        Box(modifier = addMod, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GridView, null,
                tint = if (selectedIndex == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackListView
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun PackListView(
    packs: List<StickerPack>,
    currentUid: String,
    isExthru: Boolean,
    isDark: Boolean,
    onSelectPack: (Int) -> Unit,
    onDeletePack: (StickerPack) -> Unit,
    onRenamePack: (StickerPack) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(packs.size, key = { packs[it].id }) { index ->
            val pack = packs[index]
            PackListRow(
                pack = pack,
                isOwner = pack.authorId == currentUid,
                isExthru = isExthru, isDark = isDark,
                onClick = { onSelectPack(index) },
                onDelete = { onDeletePack(pack) },
                onRename = { onRenamePack(pack) }
            )
        }
    }
}

@Composable
private fun PackListRow(
    pack: StickerPack,
    isOwner: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка пака
        val avatarMod = Modifier
            .size(56.dp)
            .then(
                if (isExthru) Modifier
                    .nmRaisedShadow(isDark, 6.dp, 3.dp, 14.dp, if(isDark) 0.55f else 0.40f, if(isDark) 0.08f else 0.65f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                else Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

        Box(modifier = avatarMod, contentAlignment = Alignment.Center) {
            if (pack.stickers.isNotEmpty()) {
                AsyncImage(model = pack.stickers.first().url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(46.dp))
            } else {
                Text(pack.emoji, fontSize = 28.sp)
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text("${pack.emoji} ${pack.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pack.stickers.take(4).forEach { sticker ->
                    AsyncImage(model = sticker.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)))
                }
                if (pack.stickers.isEmpty()) {
                    Text("Стикеров нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text("${pack.stickerCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (isOwner) {
            Box {
                ThemedIconButton(Icons.Default.MoreVert, MaterialTheme.colorScheme.onSurfaceVariant, isExthru, isDark) { showMenu = true }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackContentGrid
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackContentGrid(
    pack: StickerPack,
    currentUid: String,
    isExthru: Boolean,
    isDark: Boolean,
    onStickerTap: (StickerItem) -> Unit,
    onDeleteSticker: (StickerItem) -> Unit
) {
    val isOwner = pack.authorId == currentUid
    var longPressTarget by remember { mutableStateOf<StickerItem?>(null) }

    if (pack.stickers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pack.emoji, fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("Пак пока пустой", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pack.stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker, isOwner = isOwner, isExthru = isExthru, isDark = isDark,
                onTap = { onStickerTap(sticker) },
                onLongPress = { longPressTarget = sticker }
            )
        }
    }

    longPressTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { longPressTarget = null },
            title = { Text("Удалить стикер?") },
            text = {
                AsyncImage(model = sticker.url, contentDescription = null, modifier = Modifier.size(80.dp), contentScale = ContentScale.Fit)
            },
            confirmButton = {
                ThemedButton("Удалить", isExthru, isDark, isError = true) {
                    onDeleteSticker(sticker)
                    longPressTarget = null
                }
            },
            dismissButton = {
                ThemedButton("Отмена", isExthru, isDark) { longPressTarget = null }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerItem,
    isOwner: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val cellMod = Modifier
        .aspectRatio(1f)
        .then(
            if (isExthru) Modifier
                .nmRaisedShadow(isDark, 6.dp, 3.dp, 16.dp, if(isDark) 0.55f else 0.40f, if(isDark) 0.08f else 0.65f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            else Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        )
        .clip(RoundedCornerShape(16.dp))
        .combinedClickable(
            onClick = {
                scope.launch {
                    scale.animateTo(0.85f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                    scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                onTap()
            },
            onLongClick = { if (isOwner) onLongPress() }
        )

    Box(modifier = cellMod, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = sticker.url, contentDescription = sticker.emoji,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp)
        )

        val emojiMod = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .then(
                if(isExthru) Modifier.exthruSmallRaisedShadow(isDark).background(MaterialTheme.colorScheme.surface, CircleShape)
                else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
            )
            .padding(2.dp)

        Text(sticker.emoji, fontSize = 11.sp, modifier = emojiMod)
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  CreatePackDialog / AddStickerSheet Components
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun CreatePackDialog(
    initialName: String = "",
    initialEmoji: String = "📦",
    title: String = "Создать пак",
    confirmLabel: String = "Создать",
    isExthru: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemedOutlinedTextField(
                    value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                    label = { Text("Эмодзи") }, isExthru = isExthru, isDark = isDark,
                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, textAlign = TextAlign.Center)
                )
                ThemedOutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 32) name = it },
                    label = { Text("Название пака") }, isExthru = isExthru, isDark = isDark,
                    supportingText = { Text("${name.length}/32", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }
                )
            }
        },
        confirmButton = {
            ThemedButton(confirmLabel, isExthru, isDark, enabled = name.isNotBlank()) {
                onCreate(name.trim(), emoji.ifBlank { "📦" })
            }
        },
        dismissButton = {
            ThemedButton("Отмена", isExthru, isDark) { onDismiss() }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStickerSheet(
    pack: StickerPack,
    isExthru: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onUpload: (uri: Uri, emoji: String) -> Unit
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var emojiInput by remember { mutableStateOf("🎭") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingUri = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Добавить стикер в «${pack.name}»", style = MaterialTheme.typography.titleMedium)

            val previewMod = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (isExthru) Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
                    else Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                )
                .clickable { picker.launch("image/*") }

            Box(modifier = previewMod, contentAlignment = Alignment.Center) {
                if (pendingUri != null) {
                    AsyncImage(model = pendingUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(6.dp))
                        Text("Нажмите, чтобы выбрать изображение", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            ThemedOutlinedTextField(
                value = emojiInput, onValueChange = { if (it.length <= 2) emojiInput = it },
                label = { Text("Эмодзи для стикера") }, isExthru = isExthru, isDark = isDark,
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { ThemedButton("Отмена", isExthru, isDark, modifier = Modifier.fillMaxWidth()) { onDismiss() } }
                Box(Modifier.weight(1f)) {
                    ThemedButton("Добавить", isExthru, isDark, modifier = Modifier.fillMaxWidth(), enabled = pendingUri != null) {
                        pendingUri?.let { uri -> onUpload(uri, emojiInput.ifBlank { "🎭" }) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Вспомогательные Exthru UI-компоненты
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemedIconButton(
    icon: ImageVector,
    tint: Color,
    isExthru: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp) // Уменьшенный размер кнопок (вместо IconButton)
            .clip(CircleShape)
            .then(if (isExthru) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
            .background(if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ThemedButton(
    text: String,
    isExthru: Boolean,
    isDark: Boolean,
    isError: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (isExthru) {
        val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Box(
            modifier = modifier
                .clip(CircleShape)
                .then(if (enabled) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
                .background(if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = if(enabled) color else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    } else {
        TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
    }
}

@Composable
private fun ThemedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)?,
    isExthru: Boolean,
    isDark: Boolean,
    supportingText: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current
) {
    if (isExthru) {
        Box(modifier = Modifier.fillMaxWidth().nmInsetShadow(isDark, cornerRadius = 16.dp)) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange, label = label,
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = textStyle,
                supportingText = supportingText, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    } else {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, label = label,
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle, supportingText = supportingText
        )
    }
}

@Composable
private fun ThemedDivider(isExthru: Boolean, isDark: Boolean) {
    if (isExthru) {
        Box(Modifier.fillMaxWidth().height(2.dp).nmDividerBottom(isDark))
    } else {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyPacksPlaceholder(onCreatePack: () -> Unit, isExthru: Boolean, isDark: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎭", fontSize = 52.sp)
            Text("У вас нет стикеров", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Создайте свой первый пак или получите\nстикер-пак в чате", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            ThemedButton("Создать пак", isExthru, isDark, onClick = onCreatePack)
        }
    }
}