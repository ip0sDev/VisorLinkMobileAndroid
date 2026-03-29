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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
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
    viewModel: StickerPackViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,                          // мы рисуем свой хедер
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        StickerPickerContent(
            packs = uiState.packs,
            isLoading = uiState.isLoading,
            currentUid = viewModel.currentUid,
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
//  Внутренняя компоновка (вынесена для тестируемости)
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun StickerPickerContent(
    packs: List<StickerPack>,
    isLoading: Boolean,
    currentUid: String,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onCreatePack: (name: String, emoji: String) -> Unit,
    onDeletePack: (packId: String) -> Unit,
    onRenamePack: (packId: String, name: String, emoji: String) -> Unit,
    onUploadSticker: (packId: String, uri: Uri, emoji: String) -> Unit,
    onDeleteSticker: (packId: String, sticker: StickerItem) -> Unit,
    onDismiss: () -> Unit
) {
    // selectedPackIndex: -1 = "список паков", >=0 = индекс в packs
    var selectedPackIndex by remember { mutableIntStateOf(if (packs.isNotEmpty()) 0 else -1) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<StickerPack?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<StickerPack?>(null) }
    var showAddStickerSheet by remember { mutableStateOf<StickerPack?>(null) }

    // Синхронизируем индекс если пачки изменились
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

        // ── Заголовок строки с кнопками ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedPackIndex >= 0 && packs.isNotEmpty()) {
                // Кнопка "назад" (как в Telegram — список паков)
                IconButton(
                    onClick = { selectedPackIndex = -1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack, null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    selectedPack?.let { "${it.emoji} ${it.name}" } ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Кнопки управления паком (только для автора)
                if (selectedPack?.authorId == currentUid) {
                    IconButton(
                        onClick = { showAddStickerSheet = selectedPack },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { showRenameDialog = selectedPack },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = selectedPack },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Text(
                    "Стикеры",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // ── Нижняя полоса с иконками паков (Telegram-style tab bar) ──────────
        PackTabBar(
            packs = packs,
            selectedIndex = selectedPackIndex,
            onSelect = { selectedPackIndex = it }
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // ── Контент ────────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                packs.isEmpty() -> EmptyPacksPlaceholder(onCreatePack = { showCreateDialog = true })
                selectedPackIndex == -1 -> PackListView(
                    packs = packs,
                    currentUid = currentUid,
                    onSelectPack = { idx -> selectedPackIndex = idx },
                    onDeletePack = { showDeleteConfirm = it },
                    onRenamePack = { showRenameDialog = it }
                )
                selectedPack != null -> PackContentGrid(
                    pack = selectedPack,
                    currentUid = currentUid,
                    onStickerTap = { sticker -> onStickerSelected(selectedPack.id, sticker) },
                    onDeleteSticker = { sticker -> onDeleteSticker(selectedPack.id, sticker) }
                )
            }
        }
    }

    // ─── Dialogs & Sheets ──────────────────────────────────────────────────────

    if (showCreateDialog) {
        CreatePackDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, emoji ->
                onCreatePack(name, emoji)
                showCreateDialog = false
            }
        )
    }

    showRenameDialog?.let { pack ->
        CreatePackDialog(
            initialName = pack.name,
            initialEmoji = pack.emoji,
            title = "Переименовать пак",
            confirmLabel = "Сохранить",
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
                TextButton(onClick = {
                    onDeletePack(pack.id)
                    showDeleteConfirm = null
                    if (selectedPack?.id == pack.id) selectedPackIndex = -1
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Отмена") }
            }
        )
    }

    showAddStickerSheet?.let { pack ->
        AddStickerSheet(
            pack = pack,
            onDismiss = { showAddStickerSheet = null },
            onUpload = { uri, emoji ->
                onUploadSticker(pack.id, uri, emoji)
                showAddStickerSheet = null
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackTabBar — горизонтальная полоска иконок паков (как в Telegram)
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun PackTabBar(
    packs: List<StickerPack>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        packs.forEachIndexed { index, pack ->
            val isSelected = index == selectedIndex
            val bgColor by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                animationSpec = tween(200),
                label = "tab_bg_$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable {
                        onSelect(index)
                        scope.launch {
                            // Скролл чтобы таб был виден
                            scrollState.animateScrollTo(index * 48)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (pack.stickers.isNotEmpty()) {
                    AsyncImage(
                        model = pack.stickers.first().url,
                        contentDescription = pack.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(pack.emoji, fontSize = 22.sp)
                }
            }
        }
        // Кнопка "+ новый пак" в конце таб-бара
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { onSelect(-1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GridView, null,
                tint = if (selectedIndex == -1)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackListView — список всех паков (как в Telegram «все стикеры»)
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun PackListView(
    packs: List<StickerPack>,
    currentUid: String,
    onSelectPack: (Int) -> Unit,
    onDeletePack: (StickerPack) -> Unit,
    onRenamePack: (StickerPack) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(packs.size, key = { packs[it].id }) { index ->
            val pack = packs[index]
            PackListRow(
                pack = pack,
                isOwner = pack.authorId == currentUid,
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
        // Превью первого стикера или эмодзи
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (pack.stickers.isNotEmpty()) {
                AsyncImage(
                    model = pack.stickers.first().url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Text(pack.emoji, fontSize = 28.sp)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "${pack.emoji} ${pack.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            // Превью трёх стикеров
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pack.stickers.take(4).forEach { sticker ->
                    AsyncImage(
                        model = sticker.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                if (pack.stickers.isEmpty()) {
                    Text(
                        "Стикеров нет",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            "${pack.stickerCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isOwner) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  PackContentGrid — грид стикеров одного пака
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackContentGrid(
    pack: StickerPack,
    currentUid: String,
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
                Text("Пак пока пустой", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(pack.stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker,
                isOwner = isOwner,
                onTap = { onStickerTap(sticker) },
                onLongPress = { longPressTarget = sticker }
            )
        }
    }

    // Confirm delete on long-press
    longPressTarget?.let { sticker ->
        AlertDialog(
            onDismissRequest = { longPressTarget = null },
            title = { Text("Удалить стикер?") },
            text = {
                AsyncImage(
                    model = sticker.url,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSticker(sticker)
                    longPressTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { longPressTarget = null }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerItem,
    isOwner: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(
                onClick = {
                    scope.launch {
                        scale.animateTo(0.85f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    onTap()
                },
                onLongClick = { if (isOwner) onLongPress() }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = sticker.url,
            contentDescription = sticker.emoji,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        )
        // Эмодзи-метка в правом нижнем углу
        Text(
            sticker.emoji,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    CircleShape
                )
                .padding(2.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  CreatePackDialog
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun CreatePackDialog(
    initialName: String = "",
    initialEmoji: String = "📦",
    title: String = "Создать пак",
    confirmLabel: String = "Создать",
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 2) emoji = it },
                    label = { Text("Эмодзи") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, textAlign = TextAlign.Center)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) name = it },
                    label = { Text("Название пака") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${name.length}/32", textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), emoji.ifBlank { "📦" }) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  AddStickerSheet — выбрать файл + ввести эмодзи → добавить
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStickerSheet(
    pack: StickerPack,
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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Добавить стикер в «${pack.name}»",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Превью выбранного файла
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (pendingUri != null) {
                    AsyncImage(
                        model = pendingUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(6.dp))
                        Text("Нажмите, чтобы выбрать изображение",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedTextField(
                value = emojiInput,
                onValueChange = { if (it.length <= 2) emojiInput = it },
                label = { Text("Эмодзи для стикера") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Отмена") }

                Button(
                    onClick = {
                        pendingUri?.let { uri ->
                            onUpload(uri, emojiInput.ifBlank { "🎭" })
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = pendingUri != null
                ) { Text("Добавить") }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  EmptyPacksPlaceholder
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyPacksPlaceholder(onCreatePack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🎭", fontSize = 52.sp)
            Text(
                "У вас нет стикеров",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Создайте свой первый пак или получите\nстикер-пак в чате",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onCreatePack,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Создать пак")
            }
        }
    }
}