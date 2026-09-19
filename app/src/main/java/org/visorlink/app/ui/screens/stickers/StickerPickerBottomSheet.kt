package org.visorlink.app.ui.screens.stickers

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
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
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

    // При каждом открытии шторки втихую обновляем список стикеров с сервера
    LaunchedEffect(Unit) {
        viewModel.refreshSilently()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (tokens.isBiolume) cs.surfaceContainerLow else cs.surface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        StickerPickerContent(
            packs = uiState.packs,
            isLoading = uiState.isLoading,
            currentUid = viewModel.currentUid,
            onStickerSelected = { packId, sticker ->
                viewModel.recordPackUsage(packId)
                onStickerSelected(packId, sticker)
            },
            onDeletePack = { packId, isOwner -> viewModel.deletePack(packId, isOwner) }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Внутренняя компоновка
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun StickerPickerContent(
    packs: List<StickerPack>,
    isLoading: Boolean,
    currentUid: String,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onDeletePack: (packId: String, isOwner: Boolean) -> Unit
) {
    var selectedPackIndex by remember { mutableIntStateOf(if (packs.isNotEmpty()) 0 else -1) }
    var showDeleteConfirm by remember { mutableStateOf<StickerPack?>(null) }
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(packs) {
        if (packs.isNotEmpty()) {
            if (selectedPackIndex >= packs.size) {
                selectedPackIndex = 0
            } else if (selectedPackIndex == -1) {
                selectedPackIndex = 0
            }
        } else {
            selectedPackIndex = -1
        }
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
                        cs.outlineVariant.copy(alpha = 0.4f),
                        CircleShape
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

                // Кнопка удаления доступна всегда (для своих - удалить, для чужих - убрать из библиотеки)
                IconButton(onClick = { showDeleteConfirm = selectedPack }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    "Стикеры",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (tokens.isBiolume) cs.outlineVariant.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.5f)
        )

        // ── Нижняя полоса с иконками паков ────────────────────────────────────
        PackTabBar(
            packs = packs,
            selectedIndex = selectedPackIndex,
            onSelect = { selectedPackIndex = it }
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (tokens.isBiolume) cs.outlineVariant.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.5f)
        )

        // ── Контент ────────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                packs.isEmpty() -> EmptyPacksPlaceholder()
                selectedPackIndex == -1 -> PackListView(
                    packs = packs,
                    currentUid = currentUid,
                    onSelectPack = { idx -> selectedPackIndex = idx },
                    onDeletePack = { showDeleteConfirm = it }
                )
                selectedPack != null -> PackContentGrid(
                    pack = selectedPack,
                    currentUid = currentUid,
                    onStickerTap = { sticker -> onStickerSelected(selectedPack.id, sticker) }
                )
            }
        }
    }

    // ── Dialogs & Sheets ───────────────────────────────────────────────────────

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
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val pillShape = tokens.shapes.chip

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
                .then(
                    if (isSelected && tokens.structure.enabled) Modifier.vlRaised(tokens.structure, pillShape)
                    else Modifier
                )
                .clip(pillShape)
                .background(
                    if (isSelected) {
                        if (tokens.isBiolume) cs.primary.copy(alpha = 0.18f) else cs.primaryContainer
                    } else {
                        if (tokens.isBiolume) cs.surfaceContainer.copy(alpha = 0.5f) else cs.surfaceContainerLow
                    },
                    pillShape
                )
                .then(
                    if (isSelected && tokens.structure.enabled) Modifier.vlHairline(cs.primary.copy(alpha = 0.45f), pillShape)
                    else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.25f), pillShape)
                    else Modifier
                )
                .clickable {
                    haptic.perform(HapticType.SELECTION, true)
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

        // Кнопка перехода в общий список паков
        val isAddSelected = selectedIndex == -1
        val addScale by animateFloatAsState(if (isAddSelected) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "add_scale")

        val addMod = Modifier
            .padding(horizontal = 4.dp)
            .size(46.dp)
            .scale(addScale)
            .then(
                if (isAddSelected && tokens.structure.enabled) Modifier.vlRaised(tokens.structure, pillShape)
                else Modifier
            )
            .clip(pillShape)
            .background(
                if (isAddSelected) {
                    if (tokens.isBiolume) cs.primary.copy(alpha = 0.18f) else cs.primaryContainer
                } else {
                    if (tokens.isBiolume) cs.surfaceContainer.copy(alpha = 0.5f) else cs.surfaceContainerLow
                },
                pillShape
            )
            .then(
                if (isAddSelected && tokens.structure.enabled) Modifier.vlHairline(cs.primary.copy(alpha = 0.45f), pillShape)
                else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.25f), pillShape)
                else Modifier
            )
            .clickable {
                haptic.perform(HapticType.SELECTION, true)
                onSelect(-1)
            }

        Box(modifier = addMod, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GridView, null,
                tint = if (isAddSelected) cs.primary else cs.onSurfaceVariant,
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
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

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
        val avatarShape = tokens.shapes.chip
        val avatarMod = Modifier
            .size(58.dp)
            .then(
                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, avatarShape)
                else Modifier
            )
            .clip(avatarShape)
            .background(
                if (tokens.isBiolume) cs.surfaceContainer else cs.surfaceVariant,
                avatarShape
            )
            .then(
                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.35f), avatarShape)
                else Modifier
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
            Text("${pack.emoji} ${pack.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pack.stickers.take(4).forEach { sticker ->
                    AsyncImage(model = sticker.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp).clip(tokens.shapes.indicator))
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
    onStickerTap: (StickerItem) -> Unit
) {
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
                sticker = sticker,
                onTap = {
                    haptic.perform(HapticType.CLICK, true)
                    onStickerTap(sticker)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerItem,
    onTap: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val itemShape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f))

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .then(
                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, itemShape)
                else Modifier
            )
            .clip(itemShape)
            .background(
                if (tokens.isBiolume) cs.surfaceContainer.copy(alpha = 0.65f)
                else cs.surfaceVariant.copy(alpha = 0.4f),
                itemShape
            )
            .then(
                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.35f), itemShape)
                else Modifier
            )
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = sticker.url, contentDescription = sticker.emoji,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp)
        )

        val emojiMod = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .clip(CircleShape)
            .background(cs.surface.copy(alpha = if (tokens.isBiolume) 0.85f else 0.75f))
            .then(
                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.3f), CircleShape)
                else Modifier
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)

        Text(sticker.emoji, fontSize = 11.sp, modifier = emojiMod)
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Вспомогательные Exthru UI-компоненты
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyPacksPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎭", fontSize = 64.sp)
            Text("У вас нет стикеров", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Официальные стикер-паки скоро появятся\nили добавьте стикеры из чата", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f), textAlign = TextAlign.Center)
        }
    }
}
