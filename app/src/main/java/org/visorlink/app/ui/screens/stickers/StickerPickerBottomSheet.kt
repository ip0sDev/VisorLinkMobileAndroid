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
import org.visorlink.app.ui.components.VlAnimatedMedia
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val targetHeight = remember(configuration.screenHeightDp) {
        minOf(540.dp, configuration.screenHeightDp.dp * 0.70f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = if (tokens.isBiolume) cs.surfaceContainerLow else cs.surface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight)
                .navigationBarsPadding()
        ) {
            StickerPickerContent(
                userPacks = uiState.userPacks,
                storePacks = uiState.storePacks,
                isLoading = uiState.isLoading,
                currentUid = viewModel.currentUid,
                onStickerSelected = { packId, sticker ->
                    viewModel.recordPackUsage(packId)
                    onStickerSelected(packId, sticker)
                },
                onDeletePack = { packId, isOwner -> viewModel.deletePack(packId, isOwner) },
                onInstallPack = { packId -> viewModel.addForeignPack(packId) {} },
                onClose = onDismiss
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Внутренняя компоновка
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun StickerPickerContent(
    userPacks: List<StickerPack>,
    storePacks: List<StickerPack>,
    isLoading: Boolean,
    currentUid: String,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onDeletePack: (packId: String, isOwner: Boolean) -> Unit,
    onInstallPack: (packId: String) -> Unit,
    onClose: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = My Stickers, 1 = Store
    val packs = if (selectedTab == 0) userPacks else storePacks

    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var selectedPackIndex by remember { mutableIntStateOf(if (packs.isNotEmpty()) 0 else -1) }
    var showDeleteConfirm by remember { mutableStateOf<StickerPack?>(null) }
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()

    LaunchedEffect(packs, selectedTab, selectedPackId) {
        if (packs.isNotEmpty()) {
            if (selectedPackId != null) {
                val idx = packs.indexOfFirst { it.id == selectedPackId }
                if (idx != -1) {
                    selectedPackIndex = idx
                } else if (selectedPackIndex !in packs.indices) {
                    selectedPackIndex = 0
                    selectedPackId = packs[0].id
                }
            } else {
                if (selectedPackIndex !in packs.indices && selectedTab == 0) {
                    selectedPackIndex = 0
                }
                if (selectedPackIndex in packs.indices) {
                    selectedPackId = packs[selectedPackIndex].id
                }
            }
        } else {
            selectedPackIndex = -1
        }
    }

    val selectedPack = if (selectedPackIndex in packs.indices) packs[selectedPackIndex] else null

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ── Единая компактная верхняя панель (Переключатель + Название пака + Действия) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Компактный переключатель [Мои] [Магазин]
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (tokens.isBiolume) cs.surfaceContainer else cs.surfaceContainerHigh,
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mySelected = selectedTab == 0
                    val storeSelected = selectedTab == 1

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (mySelected) {
                                    if (tokens.isBiolume) cs.primary.copy(alpha = 0.22f) else cs.primaryContainer
                                } else Color.Transparent
                            )
                            .clickable {
                                haptic.perform(HapticType.SELECTION, true)
                                selectedTab = 0
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Мои",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (mySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (mySelected) cs.onPrimaryContainer else cs.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (storeSelected) {
                                    if (tokens.isBiolume) cs.primary.copy(alpha = 0.22f) else cs.primaryContainer
                                } else Color.Transparent
                            )
                            .clickable {
                                haptic.perform(HapticType.SELECTION, true)
                                selectedTab = 1
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Магазин",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (storeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (storeSelected) cs.onPrimaryContainer else cs.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Название выбранного пака
            if (selectedPack != null && selectedPackIndex != -1) {
                Text(
                    text = "${selectedPack.emoji} ${selectedPack.name}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Кнопка удаления пака (для установленных паков или созданных пользователем)
            val canDelete = selectedPack != null && selectedPackIndex != -1 &&
                ((selectedTab == 0 && userPacks.any { it.id == selectedPack.id }) || (selectedPack.authorId == currentUid))
            if (canDelete) {
                IconButton(
                    onClick = { showDeleteConfirm = selectedPack },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Удалить пак",
                        tint = cs.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Кнопка закрытия панели (если передана)
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Закрыть",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (tokens.isBiolume) cs.outlineVariant.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.4f)
        )

        // ── Нижняя компактная полоса с иконками паков ─────────────────────────
        PackTabBar(
            packs = packs,
            selectedIndex = selectedPackIndex,
            onSelect = { idx ->
                selectedPackIndex = idx
                selectedPackId = if (idx in packs.indices) packs[idx].id else null
            }
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (tokens.isBiolume) cs.outlineVariant.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.4f)
        )

        // ── Основная рабочая область со стикерами ──────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                packs.isEmpty() -> EmptyPacksPlaceholder()
                selectedPackIndex == -1 -> PackListView(
                    packs = packs,
                    currentUid = currentUid,
                    onSelectPack = { idx ->
                        selectedPackIndex = idx
                        selectedPackId = if (idx in packs.indices) packs[idx].id else null
                    },
                    onDeletePack = { showDeleteConfirm = it }
                )
                selectedPack != null -> PackContentGrid(
                    pack = selectedPack,
                    currentUid = currentUid,
                    isInstalled = userPacks.any { it.id == selectedPack.id },
                    onInstallPack = {
                        val packId = selectedPack.id
                        selectedPackId = packId
                        onInstallPack(packId)
                        selectedTab = 0 // переключаемся на «Мои стикеры»
                    },
                    onStickerTap = { sticker -> onStickerSelected(selectedPack.id, sticker) }
                )
            }
        }
    }

    // ── Диалог подтверждения удаления ──────────────────────────────────────────

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
                    if (selectedPack?.id == pack.id) {
                        selectedPackId = null
                        selectedPackIndex = -1
                    }
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
            .height(42.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        packs.forEachIndexed { index, pack ->
            val isSelected = index == selectedIndex
            val scale by animateFloatAsState(if (isSelected) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "tab_scale")

            val mod = Modifier
                .padding(horizontal = 3.dp)
                .size(34.dp)
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
                    scope.launch { scrollState.animateScrollTo(index * 40) }
                }

            Box(modifier = mod, contentAlignment = Alignment.Center) {
                if (pack.stickers.isNotEmpty()) {
                    AsyncImage(
                        model = pack.stickers.first().url, contentDescription = pack.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(pack.emoji, fontSize = 16.sp)
                }
            }
        }

        // Кнопка перехода в общий список паков
        val isAddSelected = selectedIndex == -1
        val addScale by animateFloatAsState(if (isAddSelected) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "add_scale")

        val addMod = Modifier
            .padding(horizontal = 3.dp)
            .size(34.dp)
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
                modifier = Modifier.size(18.dp)
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
    isInstalled: Boolean,
    onInstallPack: () -> Unit,
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

    val cs = MaterialTheme.colorScheme
    var previewSticker by remember { mutableStateOf<StickerItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!isInstalled) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${pack.emoji} ${pack.name}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        org.visorlink.app.ui.components.VlButton(
                            onClick = onInstallPack,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Добавить", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        items(pack.stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker,
                onTap = {
                    haptic.perform(HapticType.CLICK, true)
                    onStickerTap(sticker)
                },
                onLongClick = {
                    previewSticker = sticker
                }
            )
        }
    }

    previewSticker?.let { sticker ->
        org.visorlink.app.ui.components.chat.StickerPreviewDialog(
            sticker = sticker,
            onDismiss = { previewSticker = null },
            onSend = {
                onStickerTap(sticker)
                previewSticker = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerItem,
    onTap: () -> Unit,
    onLongClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val itemShape = RoundedCornerShape(14.dp)
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
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        VlAnimatedMedia(
            url = sticker.url,
            contentDescription = sticker.emoji,
            isLottie = sticker.isLottie,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(4.dp)
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
