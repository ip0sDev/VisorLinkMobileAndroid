package by.iposdev.visorlink.ui.screens.stickers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.scale
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
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.ui.components.VlAlertDialog
import by.iposdev.visorlink.ui.components.VlButton
import by.iposdev.visorlink.ui.components.VlDialogButton
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
            onStickerSelected = { packId, sticker ->
                onStickerSelected(packId, sticker)
            },
            onCreatePack = { name, emoji ->
                viewModel.createPack(name, emoji)
            },
            onDeletePack = { packId, isOwner -> viewModel.deletePack(packId, isOwner) },
            onUploadSticker = { packId, uri, emoji ->
                viewModel.uploadSticker(packId, uri, emoji)
            },
            onDeleteSticker = { packId, sticker ->
                viewModel.deleteSticker(packId, sticker)
            }
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
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onCreatePack: (name: String, emoji: String) -> Unit,
    onDeletePack: (packId: String, isOwner: Boolean) -> Unit,
    onUploadSticker: (packId: String, uri: Uri, emoji: String) -> Unit,
    onDeleteSticker: (packId: String, sticker: StickerItem) -> Unit
) {
    var selectedPackIndex by remember { mutableIntStateOf(if (packs.isNotEmpty()) 0 else -1) }
    var showCreateDialog by remember { mutableStateOf(false) }
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
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        VlTheme.tokens.shapes.indicator
                    )
            )
        }

        // ── Заголовок строки с кнопками ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedPackIndex >= 0 && packs.isNotEmpty()) {
                IconButton(onClick = { selectedPackIndex = -1 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    selectedPack?.let { "${it.emoji} ${it.name}" } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (selectedPack?.authorId == currentUid) {
                    IconButton(onClick = { showAddStickerSheet = selectedPack }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                // Кнопка удаления доступна всегда (для своих - удалить, для чужих - убрать из библиотеки)
                IconButton(onClick = { showDeleteConfirm = selectedPack }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    "Стикеры",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // ── Нижняя полоса с иконками паков ────────────────────────────────────
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
                    onDeletePack = { showDeleteConfirm = it }
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

    // ── Dialogs & Sheets ───────────────────────────────────────────────────────

    if (showCreateDialog) {
        CreatePackDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, emoji ->
                onCreatePack(name, emoji)
                showCreateDialog = false
            }
        )
    }

    showDeleteConfirm?.let { pack ->
        val isOwner = pack.authorId == currentUid
        VlAlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(if (isOwner) "Удалить пак?" else "Удалить из моих?") },
            text = { Text(if (isOwner) "Пак «${pack.emoji} ${pack.name}» и все его стикеры будут удалены безвозвратно." else "Пак «${pack.emoji} ${pack.name}» будет удален из вашей библиотеки.") },
            actions = {
                VlDialogButton(onClick = { showDeleteConfirm = null }) { Text("Отмена") }
                VlDialogButton(isDestructive = true, onClick = {
                    onDeletePack(pack.id, isOwner)
                    showDeleteConfirm = null
                    if (selectedPack?.id == pack.id) selectedPackIndex = -1
                }) { Text("Удалить") }
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
//  PackTabBar
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun PackTabBar(
    packs: List<StickerPack>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        packs.forEachIndexed { index, pack ->
            val isSelected = index == selectedIndex
            val scale by animateFloatAsState(if (isSelected) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "tab_scale")

            val mod = Modifier
                .padding(horizontal = 4.dp)
                .size(46.dp)
                .scale(scale)
                .clip(VlTheme.tokens.shapes.chip)
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .background(MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.chip)
                .clip(VlTheme.tokens.shapes.chip)
                .clickable {
                    haptic.perform(HapticType.SELECTION, true)
                    onSelect(index)
                    scope.launch { scrollState.animateScrollTo(index * 52) }
                }

            Box(modifier = mod, contentAlignment = Alignment.Center) {
                if (pack.stickers.isNotEmpty()) {
                    AsyncImage(
                        model = pack.stickers.first().url, contentDescription = pack.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(34.dp)
                    )
                } else {
                    Text(pack.emoji, fontSize = 24.sp)
                }
            }
        }

        // Кнопка "+ новый пак"
        val isAddSelected = selectedIndex == -1
        val addScale by animateFloatAsState(if (isAddSelected) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "add_scale")

        val addMod = Modifier
            .padding(horizontal = 4.dp)
            .size(46.dp)
            .scale(addScale)
            .clip(VlTheme.tokens.shapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .background(MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.chip)
            .clip(VlTheme.tokens.shapes.chip)
            .clickable {
                haptic.perform(HapticType.SELECTION, true)
                onSelect(-1)
            }

        Box(modifier = addMod, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GridView, null,
                tint = if (isAddSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
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
    onSelectPack: (Int) -> Unit,
    onDeletePack: (StickerPack) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(packs.size, key = { packs[it].id }) { index ->
            val pack = packs[index]
            PackListRow(
                pack = pack,
                isOwner = pack.authorId == currentUid,
                onClick = { onSelectPack(index) },
                onDelete = { onDeletePack(pack) }
            )
        }
    }
}

@Composable
private fun PackListRow(
    pack: StickerPack,
    isOwner: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(dampingRatio = 0.6f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.perform(HapticType.CLICK, true)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка пака
        val avatarMod = Modifier
            .size(58.dp)
            .clip(VlTheme.tokens.shapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant)

        Box(modifier = avatarMod, contentAlignment = Alignment.Center) {
            if (pack.stickers.isNotEmpty()) {
                AsyncImage(model = pack.stickers.first().url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(46.dp))
            } else {
                Text(pack.emoji, fontSize = 28.sp)
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text("${pack.emoji} ${pack.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pack.stickers.take(4).forEach { sticker ->
                    AsyncImage(model = sticker.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp).clip(VlTheme.tokens.shapes.indicator))
                }
                if (pack.stickers.isEmpty()) {
                    Text("Стикеров нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Text("${pack.stickerCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)

        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isOwner) "Удалить пак" else "Удалить из моих", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
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
    onStickerTap: (StickerItem) -> Unit,
    onDeleteSticker: (StickerItem) -> Unit
) {
    val isOwner = pack.authorId == currentUid
    var longPressTarget by remember { mutableStateOf<StickerItem?>(null) }
    val haptic = rememberHaptic()

    if (pack.stickers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pack.emoji, fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text("Пак пока пустой", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pack.stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker, isOwner = isOwner,
                onTap = {
                    haptic.perform(HapticType.CLICK, true)
                    onStickerTap(sticker)
                },
                onLongPress = {
                    haptic.perform(HapticType.LONG_PRESS, true)
                    longPressTarget = sticker
                }
            )
        }
    }

    longPressTarget?.let { sticker ->
        VlAlertDialog(
            onDismissRequest = { longPressTarget = null },
            title = { Text("Удалить стикер?") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(model = sticker.url, contentDescription = null, modifier = Modifier.size(100.dp), contentScale = ContentScale.Fit)
                }
            },
            actions = {
                VlDialogButton(onClick = { longPressTarget = null }) { Text("Отмена") }
                VlDialogButton(isDestructive = true, onClick = {
                    onDeleteSticker(sticker)
                    longPressTarget = null
                }) { Text("Удалить") }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f))

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(VlTheme.tokens.shapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onTap, onLongClick = { if(isOwner) onLongPress() }),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = sticker.url, contentDescription = sticker.emoji,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp)
        )

        val emojiMod = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), VlTheme.tokens.shapes.indicator)
            .padding(3.dp)

        Text(sticker.emoji, fontSize = 12.sp, modifier = emojiMod)
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
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }

    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VlTextField(
                    value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                    label = "Эмодзи",
                    textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, textAlign = TextAlign.Center)
                )
                VlTextField(
                    value = name, onValueChange = { if (it.length <= 32) name = it },
                    label = "Название пака",
                    supportingText = "${name.length}/32"
                )
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(isPrimary = true, onClick = {
                if (name.isNotBlank()) onCreate(name.trim(), emoji.ifBlank { "📦" })
            }) { Text(confirmLabel) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStickerSheet(
    pack: StickerPack,
    onDismiss: () -> Unit,
    onUpload: (uri: Uri, emoji: String) -> Unit
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var emojiInput by remember { mutableStateOf("🎭") }
    val haptic = rememberHaptic()

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
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Добавить стикер в «${pack.name}»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            val previewMod = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(VlTheme.tokens.shapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    haptic.perform(HapticType.CLICK, true)
                    picker.launch("image/*")
                }

            Box(modifier = previewMod, contentAlignment = Alignment.Center) {
                if (pendingUri != null) {
                    AsyncImage(model = pendingUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(8.dp))
                        Text("Нажмите, чтобы выбрать изображение", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            VlTextField(
                value = emojiInput, onValueChange = { if (it.length <= 2) emojiInput = it },
                label = "Эмодзи для стикера",
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    VlButton(isDestructive = true, onClick = onDismiss) { Text("Отмена") }
                }
                Box(Modifier.weight(1f)) {
                    if (pendingUri != null) {
                        VlButton(onClick = { pendingUri?.let { uri -> onUpload(uri, emojiInput.ifBlank { "🎭" }) } }) { Text("Добавить") }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = VlTheme.tokens.shapes.button
                        ) {
                            Text("Добавить")
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Вспомогательные Exthru UI-компоненты
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyPacksPlaceholder(onCreatePack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎭", fontSize = 64.sp)
            Text("У вас нет стикеров", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Создайте свой первый пак или получите\nстикер-пак в чате", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(200.dp)) {
                VlButton(onClick = onCreatePack) { Text("Создать пак") }
            }
        }
    }
}
