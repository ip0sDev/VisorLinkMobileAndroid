package org.visorlink.app.ui.components.stickers

import org.visorlink.app.ui.components.VlSticker
import org.visorlink.app.ui.screens.stickers.StickerPackViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.StickerFormat
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.FrequentSticker
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.StickerFrequentStore
import org.visorlink.app.utils.rememberHaptic
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════════
//  StickerPickerBottomSheet — точка входа (порт Flutter `showStickerPicker`)
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerBottomSheet(
    onDismiss: () -> Unit,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    viewModel: StickerPackViewModel = koinViewModel(),
    themeVm: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Не дергаем сервер при каждом открытии, если паки уже загружены из локального кэша
    LaunchedEffect(Unit) {
        viewModel.refreshSilently()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    // F vl_sticker_widgets.dart: высота = 72% экрана, зажата 400…560
    val targetHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.72f).dp.coerceIn(400.dp, 560.dp)
    }

    // Материал панели рисуем сами: чистый cs.surface (без серого Monet)
    val panelColor = cs.surface
    val sheetShape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = sheetShape,
        containerColor = panelColor,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, sheetShape) else Modifier
                )
                .clip(sheetShape)
                // Keycap-свет: в Forge верх панели светлее к белому (sheen), в Biolume — плоско
                .vlKeycapBackground(tokens.structure, panelColor, sheetShape, VlDepth.Raised)
                .vlHairline(cs.outlineVariant.copy(alpha = if (tokens.isDark) 0.25f else 0.40f), sheetShape)
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
//  Содержимое: вкладки [Мои стикеры | Магазин] + синхронизированный таб-бар
//  + вертикальная лента секций (порт Flutter `_StickerPickerSheet`)
// ════════════════════════════════════════════════════════════════════════════════

private const val CROSS_AXIS_COUNT = 4

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
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Мои (лента), 1 = Магазин
    var showDeleteConfirm by remember { mutableStateOf<StickerPack?>(null) }
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val frequentStore: StickerFrequentStore = koinInject()
    val frequentStickers = frequentStore.items.take(28)
    val hasFrequent = frequentStickers.isNotEmpty()

    // Лента секций с виртуализацией по строкам
    val listState = rememberLazyListState()
    val tabScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Предвычисленные стартовые индексы секций в LazyColumn для мгновенного скролла и отслеживания активного таба
    val sectionStartIndices = remember(hasFrequent, userPacks, frequentStickers.size) {
        val list = mutableListOf<Int>()
        var current = 0
        if (hasFrequent) {
            list.add(current)
            val fRows = (frequentStickers.size + CROSS_AXIS_COUNT - 1) / CROSS_AXIS_COUNT
            current += 1 + fRows // 1 header item + fRows
        }
        for (pack in userPacks) {
            list.add(current)
            val pRows = if (pack.stickers.isEmpty()) 1 else (pack.stickers.size + CROSS_AXIS_COUNT - 1) / CROSS_AXIS_COUNT
            current += 1 + pRows // 1 header item + pRows
        }
        list
    }

    val activeSection by remember(sectionStartIndices) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val idx = sectionStartIndices.indexOfLast { it <= first }
            idx.coerceAtLeast(0)
        }
    }

    fun scrollToSection(sectionIdx: Int) {
        haptic.perform(HapticType.SELECTION, true)
        val targetItem = sectionStartIndices.getOrNull(sectionIdx) ?: 0
        scope.launch {
            listState.animateScrollToItem(targetItem)
        }
        // Таб-бар подматывается так, чтобы активный таб был в зоне видимости (F: (tabIdx*42) − 100)
        scope.launch {
            val target = with(density) { (42.dp * sectionIdx - 100.dp).toPx().coerceAtLeast(0f) }
            tabScrollState.animateScrollTo(target.roundToInt())
        }
    }

    fun sendSticker(pack: StickerPack, sticker: StickerItem) {
        haptic.perform(HapticType.CLICK, true)
        frequentStore.record(sticker, pack)
        onStickerSelected(pack.id, sticker)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Верхняя панель: Переключатель [Мои] [Магазин] + Закрыть ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(20.dp)),
                color = if (tokens.isBiolume) cs.surfaceContainer else cs.surfaceContainerHigh,
                modifier = Modifier.height(34.dp)
            ) {
                Row(modifier = Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TabPill("Мои стикеры", selectedTab == 0) {
                        if (selectedTab != 0) {
                            selectedTab = 0
                        }
                    }
                    TabPill("Магазин", selectedTab == 1) {
                        if (selectedTab != 1) selectedTab = 1
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Закрыть",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // ── Горизонтальный синхронизированный таб-бар (только «Мои») ──
        if (selectedTab == 0 && (userPacks.isNotEmpty() || hasFrequent)) {
            PackTabBar(
                tabScrollState = tabScrollState,
                hasFrequent = hasFrequent,
                userPacks = userPacks,
                activeIndex = activeSection,
                onSelectIndex = ::scrollToSection,
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = cs.outlineVariant.copy(alpha = 0.3f)
            )
        }

        // ── Основная рабочая область ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> MyStickersFeed(
                    userPacks = userPacks,
                    frequentStickers = frequentStickers,
                    isLoading = isLoading,
                    listState = listState,
                    currentUid = currentUid,
                    onSendPackSticker = { pack, sticker -> sendSticker(pack, sticker) },
                    onSendFrequentSticker = { item ->
                        sendSticker(
                            StickerPack(
                                id = item.packId,
                                name = item.packName,
                                emoji = item.packEmoji,
                            ),
                            item.sticker,
                        )
                    },
                    onDeletePack = { showDeleteConfirm = it },
                    onGoToStore = { selectedTab = 1 },
                )
                else -> StoreView(
                    storePacks = storePacks,
                    userPacks = userPacks,
                    isLoading = isLoading,
                    onInstallPack = onInstallPack,
                    onSendSticker = { pack, sticker -> sendSticker(pack, sticker) },
                )
            }
        }
    }

    // ── Диалог подтверждения удаления ──
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
                }) { Text("Удалить") }
            }
        )
    }
}

// ── Лента «Мои стикеры»: виртуализация по строкам для максимальной плавности 60/120 FPS ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyStickersFeed(
    userPacks: List<StickerPack>,
    frequentStickers: List<FrequentSticker>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentUid: String,
    onSendPackSticker: (StickerPack, StickerItem) -> Unit,
    onSendFrequentSticker: (FrequentSticker) -> Unit,
    onDeletePack: (StickerPack) -> Unit,
    onGoToStore: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var previewPair by remember { mutableStateOf<Pair<StickerPack?, StickerItem>?>(null) }

    when {
        isLoading && userPacks.isEmpty() && frequentStickers.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        userPacks.isEmpty() && frequentStickers.isEmpty() ->
            EmptyPacksView(isStore = false, onGoToStore = onGoToStore)
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (frequentStickers.isNotEmpty()) {
                    item(key = "frequent_hdr") {
                        FrequentSectionHeader(count = frequentStickers.size)
                    }
                    val frequentRows = (frequentStickers.size + CROSS_AXIS_COUNT - 1) / CROSS_AXIS_COUNT
                    items(
                        count = frequentRows,
                        key = { "frequent_row_$it" },
                        contentType = { "sticker_row" }
                    ) { r ->
                        val rowStart = r * CROSS_AXIS_COUNT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0 until CROSS_AXIS_COUNT) {
                                val idx = rowStart + c
                                Box(Modifier.weight(1f)) {
                                    if (idx < frequentStickers.size) {
                                        val item = frequentStickers[idx]
                                        StickerGridTile(
                                            url = item.sticker.url,
                                            format = item.sticker.effectiveFormat,
                                            previewUrl = item.sticker.previewUrl,
                                            emojiFallback = item.sticker.emoji,
                                            onTap = { onSendFrequentSticker(item) },
                                            onLongPress = {
                                                previewPair = StickerPack(
                                                    id = item.packId,
                                                    name = item.packName,
                                                    emoji = item.packEmoji
                                                ) to item.sticker
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                userPacks.forEach { pack ->
                    item(key = "pack_hdr_${pack.id}") {
                        PackSectionHeader(
                            pack = pack,
                            isOwner = pack.authorId == currentUid,
                            onDeletePack = { onDeletePack(pack) }
                        )
                    }

                    if (pack.stickers.isEmpty()) {
                        item(key = "pack_empty_${pack.id}") {
                            Text(
                                "Пак пока пустой",
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        val rowCount = (pack.stickers.size + CROSS_AXIS_COUNT - 1) / CROSS_AXIS_COUNT
                        items(
                            count = rowCount,
                            key = { r -> "row_${pack.id}_$r" },
                            contentType = { "sticker_row" }
                        ) { r ->
                            val rowStart = r * CROSS_AXIS_COUNT
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (c in 0 until CROSS_AXIS_COUNT) {
                                    val idx = rowStart + c
                                    Box(Modifier.weight(1f)) {
                                        if (idx < pack.stickers.size) {
                                            val sticker = pack.stickers[idx]
                                            StickerGridTile(
                                                url = sticker.url,
                                                format = sticker.effectiveFormat,
                                                previewUrl = sticker.previewUrl,
                                                emojiFallback = sticker.emoji,
                                                onTap = { onSendPackSticker(pack, sticker) },
                                                onLongPress = { previewPair = pack to sticker },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            previewPair?.let { (pack, sticker) ->
                org.visorlink.app.ui.components.chat.StickerPreviewDialog(
                    sticker = sticker,
                    onDismiss = { previewPair = null },
                    onSend = {
                        if (pack != null) {
                            onSendPackSticker(pack, sticker)
                        }
                        previewPair = null
                    }
                )
            }
        }
    }
}

// ── Секция «Часто используемые» ──────────────────────────────────────────────────

@Composable
private fun FrequentSectionHeader(count: Int) {
    val cs = MaterialTheme.colorScheme
    SectionHeader(height = 38.dp, contentPadding = 4.dp) {
        Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Часто используемые",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = cs.primary,
        )
        Spacer(Modifier.weight(1f))
        CountBadge(count)
    }
}

// ── Секция ленты: заголовок стикерпака ───────────────────────────────────────────

@Composable
private fun PackSectionHeader(
    pack: StickerPack,
    isOwner: Boolean,
    onDeletePack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    SectionHeader(height = 38.dp, contentPadding = 4.dp) {
        Text(pack.emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            pack.name,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${pack.stickers.size}",
            color = cs.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { showMenu = true }
                    .padding(5.dp),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить из моих", color = cs.error, fontSize = 13.sp) },
                    onClick = { showMenu = false; onDeletePack() },
                )
            }
        }
    }
}

// ── Вкладка «Магазин стикерпаков» ────────────────────────────────────────────────

@Composable
private fun StoreView(
    storePacks: List<StickerPack>,
    userPacks: List<StickerPack>,
    isLoading: Boolean,
    onInstallPack: (String) -> Unit,
    onSendSticker: (StickerPack, StickerItem) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when {
        isLoading && storePacks.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        storePacks.isEmpty() -> EmptyPacksView(isStore = true)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(storePacks.size, key = { "store_${storePacks[it].id}" }) { index ->
                val pack = storePacks[index]
                val isInstalled = userPacks.any { it.id == pack.id }
                StorePackCard(
                    pack = pack,
                    isInstalled = isInstalled,
                    onInstall = { onInstallPack(pack.id) },
                    onSendSticker = { onSendSticker(pack, it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorePackCard(
    pack: StickerPack,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onSendSticker: (StickerItem) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val cardShape = tokens.shapes.adapt(RoundedCornerShape(16.dp))
    var previewSticker by remember { mutableStateOf<StickerItem?>(null) }

    Surface(
        shape = cardShape,
        color = if (tokens.isForge) cs.surfaceContainerLow else cs.surfaceContainer.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, cardShape) else Modifier
            )
            .then(
                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.35f), cardShape) else Modifier
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pack.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pack.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pack.stickerCount} стикеров • ${pack.authorName}",
                        color = cs.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isInstalled) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(10.dp)),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавлен", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onInstall,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(10.dp)),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Превью первых 6 стикеров из пака
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pack.stickers.take(6).forEach { sticker ->
                    val isLed = sticker.effectiveFormat == StickerFormat.LED ||
                        sticker.url.startsWith("led:") || sticker.url.startsWith("led_")
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(VlTheme.tokens.shapes.adapt(RoundedCornerShape(10.dp)))
                            .background(cs.surfaceContainerHighest.copy(alpha = 0.4f))
                            .combinedClickable(
                                onClick = { previewSticker = sticker },
                                onLongClick = { previewSticker = sticker },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLed) {
                            org.visorlink.app.ui.components.chat.InteractiveLedSticker(
                                expression = org.visorlink.app.ui.components.led.VlLedExpression.fromUrl(sticker.url),
                                size = 52.dp,
                                interactive = false,
                            )
                        } else {
                            VlSticker(
                                url = sticker.url,
                                format = sticker.effectiveFormat,
                                previewUrl = sticker.previewUrl,
                                contentDescription = sticker.emoji,
                                modifier = Modifier.size(46.dp),
                                animate = false,
                            )
                        }
                    }
                }
            }
        }
    }

    previewSticker?.let { sticker ->
        org.visorlink.app.ui.components.chat.StickerPreviewDialog(
            sticker = sticker,
            onDismiss = { previewSticker = null },
            onSend = {
                onSendSticker(sticker)
                previewSticker = null
            }
        )
    }
}

// ── Горизонтальный синхронизированный таб-бар Telegram ───────────────────────────

@Composable
private fun PackTabBar(
    tabScrollState: androidx.compose.foundation.ScrollState,
    hasFrequent: Boolean,
    userPacks: List<StickerPack>,
    activeIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val totalTabs = (if (hasFrequent) 1 else 0) + userPacks.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(tabScrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalTabs) {
            val isSelected = i == activeIndex
            if (hasFrequent && i == 0) {
                PackTabThumb(
                    isSelected = isSelected,
                    onClick = { onSelectIndex(0) },
                ) {
                    Icon(
                        Icons.Rounded.AccessTime,
                        contentDescription = "Часто используемые",
                        tint = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                continue
            }
            val packIndex = if (hasFrequent) i - 1 else i
            val pack = userPacks.getOrNull(packIndex) ?: continue
            PackTabThumb(
                isSelected = isSelected,
                onClick = { onSelectIndex(i) },
            ) {
                val first = pack.stickers.firstOrNull()
                if (first == null) {
                    Text(pack.emoji, fontSize = 16.sp)
                } else if (first.effectiveFormat == StickerFormat.LED || first.url.startsWith("led:") || first.url.startsWith("led_")) {
                    // LED-стикер сам сайзится от ширины — фиксированный бокс VlSticker обрезался бы
                    org.visorlink.app.ui.components.chat.InteractiveLedSticker(
                        expression = org.visorlink.app.ui.components.led.VlLedExpression.fromUrl(first.url),
                        size = 26.dp,
                        interactive = false,
                    )
                } else {
                    VlSticker(
                        url = first.previewUrl ?: first.url,
                        format = first.effectiveFormat,
                        previewUrl = first.previewUrl,
                        contentDescription = pack.name,
                        modifier = Modifier.size(22.dp),
                        animate = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackTabThumb(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.adapt(RoundedCornerShape(12.dp))
    val structure = tokens.structure.enabled
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .size(36.dp, 34.dp)
            .clip(shape)
            .background(
                when {
                    isSelected && tokens.isBiolume -> cs.primary.copy(alpha = 0.18f)
                    isSelected -> cs.primaryContainer
                    else -> cs.surfaceContainerLow.copy(alpha = 0.6f)
                },
                shape,
            )
            .then(
                when {
                    isSelected && structure -> Modifier.vlHairline(cs.primary.copy(alpha = 0.50f), shape)
                    structure -> Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.25f), shape)
                    isSelected -> Modifier.border(1.5.dp, cs.primary.copy(alpha = 0.5f), shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Вспомогательные элементы ─────────────────────────────────────────────────────

/** Пилюля вкладки [Мои стикеры | Магазин]. */
@Composable
private fun TabPill(text: String, selected: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val shape = tokens.shapes.adapt(RoundedCornerShape(18.dp))
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    if (tokens.isBiolume) cs.primary.copy(alpha = 0.22f) else cs.primaryContainer
                } else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .clickable {
                haptic.perform(HapticType.SELECTION, true)
                onTap()
            }
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
        )
    }
}

/** Заголовок секции ленты фиксированной высоты. */
@Composable
private fun SectionHeader(
    height: androidx.compose.ui.unit.Dp,
    contentPadding: androidx.compose.ui.unit.Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun CountBadge(count: Int) {
    val cs = MaterialTheme.colorScheme
    Text(
        "$count",
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = cs.primary,
        modifier = Modifier
            .clip(VlTheme.tokens.shapes.adapt(RoundedCornerShape(10.dp)))
            .background(cs.primary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Сетка стикеров через лёгкие Rows (без оверхеда вложенного GridView):
 * [cellIndex] 0..3 внутри строки, null — пустая ячейка.
 */
@Composable
private fun StickerGridRows(
    count: Int,
    cell: @Composable (rowStart: Int, cellIndex: Int) -> Unit,
) {
    val rows = (count + CROSS_AXIS_COUNT - 1) / CROSS_AXIS_COUNT
    Column {
        for (r in 0 until rows) {
            val rowStart = r * CROSS_AXIS_COUNT
            Row(Modifier.padding(bottom = if (r == rows - 1) 0.dp else 8.dp)) {
                for (c in 0 until CROSS_AXIS_COUNT) {
                    val idx = rowStart + c
                    Box(Modifier.weight(1f)) {
                        if (idx < count) cell(rowStart, c)
                    }
                }
            }
        }
    }
}

/** Оптимизированный элемент стикера в сетке: чистый бокс без BoxWithConstraints и без тяжелых плееров. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGridTile(
    url: String,
    format: String,
    previewUrl: String?,
    emojiFallback: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val tileShape = tokens.shapes.adapt(RoundedCornerShape(12.dp))
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isLed = format == StickerFormat.LED || url.startsWith("led:") || url.startsWith("led_")

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(tileShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { haptic.perform(HapticType.CLICK, true); onTap() },
                onLongClick = { onLongPress?.invoke() },
            )
            .then(
                if (isPressed) Modifier.background(cs.primary.copy(alpha = 0.12f), tileShape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLed) {
            org.visorlink.app.ui.components.chat.InteractiveLedSticker(
                expression = org.visorlink.app.ui.components.led.VlLedExpression.fromUrl(url),
                size = 64.dp,
                interactive = false,
                modifier = Modifier.fillMaxSize(0.92f)
            )
        } else {
            // В ленте и сетках — мгновенные легкие постеры без лагов (76% плитки)
            VlSticker(
                url = url,
                format = format,
                previewUrl = previewUrl,
                contentDescription = emojiFallback,
                modifier = Modifier.fillMaxSize(0.76f),
                animate = false,
            )
        }
    }
}

@Composable
private fun EmptyPacksView(isStore: Boolean, onGoToStore: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Rounded.EmojiEmotions,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isStore) "В магазине пока нет стикерпаков" else "У вас пока нет стикеров",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            if (!isStore) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Перейдите в магазин, чтобы добавить стикерпаки в свою библиотеку",
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                if (onGoToStore != null) {
                    Spacer(Modifier.height(12.dp))
                    VlDialogButton(label = "В магазин", onClick = onGoToStore)
                }
            }
        }
    }
}
