package by.iposdev.visorlink.ui.components.mediapicker

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.iposdev.visorlink.data.model.MediaType
import by.iposdev.visorlink.data.model.SelectedMediaItem
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlRaised
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Вкладки медиа-пикера.
 */
enum class MediaPickerTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    ALL("Все", Icons.Outlined.Collections, Icons.Filled.Collections),
    CAMERA("Камера", Icons.Outlined.PhotoCamera, Icons.Filled.PhotoCamera),
    PHOTOS("Фото", Icons.Outlined.Image, Icons.Filled.Image),
    VIDEOS("Видео", Icons.Outlined.Videocam, Icons.Filled.Videocam)
}

/**
 * Компактный медиа-пикер в стиле Telegram и Biolume.
 * По умолчанию открывается на высоту клавиатуры (~половина экрана) и поддерживает свайп наверх для раскрытия.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlMediaPickerSheet(
    maxSelection: Int = 10,
    onDismiss: () -> Unit,
    onOpenAudioPicker: () -> Unit,
    onOpenEditor: (Uri) -> Unit,
    onMediaSelected: (List<SelectedMediaItem>) -> Unit,
    onPhotoTaken: (Uri) -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    viewModel: MediaPickerViewModel = koinViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MediaPickerTab.ALL) }

    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    // Загрузка медиа при смене вкладок
    LaunchedEffect(currentTab) {
        when (currentTab) {
            MediaPickerTab.ALL -> viewModel.loadMedia(MediaFilter.ALL)
            MediaPickerTab.PHOTOS -> viewModel.loadMedia(MediaFilter.PHOTOS_ONLY)
            MediaPickerTab.VIDEOS -> viewModel.loadMedia(MediaFilter.VIDEOS_ONLY)
            MediaPickerTab.CAMERA -> {
                // При переходе на камеру плавно раскрываем на полный экран
                coroutineScope.launch { sheetState.expand() }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (tokens.isBiolume) cs.surfaceContainerLow else cs.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(cs.outlineVariant.copy(alpha = 0.5f))
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                MediaPickerTopBar(
                    currentTab = currentTab,
                    selectedCount = selectedItems.size,
                    maxSelection = maxSelection,
                    onClose = onDismiss,
                    onOpenAudio = onOpenAudioPicker,
                    onClearSelection = { viewModel.clearSelection() }
                )
            },
            bottomBar = {
                MediaPickerBottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { newTab -> currentTab = newTab }
                )
            },
            floatingActionButton = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedItems.isNotEmpty() && currentTab != MediaPickerTab.CAMERA,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Опциональный переход в редактор, если выбрано 1 фото
                        if (selectedItems.size == 1 && selectedItems.first().type == MediaType.IMAGE) {
                            FloatingActionButton(
                                onClick = {
                                    val item = selectedItems.first()
                                    onOpenEditor(item.uri)
                                },
                                containerColor = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Редактировать",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Кнопка прямой отправки (без принудительного редактора!)
                        ExtendedFloatingActionButton(
                            onClick = {
                                val result = selectedItems.map {
                                    SelectedMediaItem(
                                        uri = it.uri,
                                        type = it.type,
                                        durationMs = it.durationMs
                                    )
                                }
                                onMediaSelected(result)
                            },
                            icon = {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                            },
                            text = {
                                Text(
                                    text = if (selectedItems.size == 1) "Отправить" else "Отправить (${selectedItems.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                            shape = tokens.shapes.fab
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    MediaPickerTab.CAMERA -> {
                        CameraTab(
                            onPhotoTaken = onPhotoTaken,
                            onVideoRecorded = onVideoRecorded,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        MediaGalleryGrid(
                            items = mediaItems,
                            selectedItems = selectedItems,
                            isLoading = isLoading,
                            onItemClick = { item ->
                                viewModel.toggleSelection(item, maxSelection = maxSelection)
                            },
                            onItemLongClick = { item ->
                                if (item.type == MediaType.IMAGE) {
                                    onOpenEditor(item.uri)
                                }
                            },
                            onReload = {
                                val filter = when (currentTab) {
                                    MediaPickerTab.PHOTOS -> MediaFilter.PHOTOS_ONLY
                                    MediaPickerTab.VIDEOS -> MediaFilter.VIDEOS_ONLY
                                    else -> MediaFilter.ALL
                                }
                                viewModel.loadMedia(filter)
                            },
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Верхняя компактная панель заголовка и быстрых действий.
 */
@Composable
private fun MediaPickerTopBar(
    currentTab: MediaPickerTab,
    selectedCount: Int,
    maxSelection: Int,
    onClose: () -> Unit,
    onOpenAudio: () -> Unit,
    onClearSelection: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка закрытия (стрелка вниз в стиле Telegram)
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Закрыть",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        // Центральный заголовок или счетчик выбора
        if (selectedCount > 0 && currentTab != MediaPickerTab.CAMERA) {
            Surface(
                color = cs.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Выбрано: $selectedCount/$maxSelection",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        } else {
            Text(
                text = currentTab.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        }

        // Правые действия: кнопка Сбросить или быстрый выбор Аудио
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedCount > 0 && currentTab != MediaPickerTab.CAMERA) {
                TextButton(onClick = onClearSelection) {
                    Text(
                        text = "Сбросить",
                        color = cs.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                IconButton(
                    onClick = onOpenAudio,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Выбрать аудио",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Плавающая нижняя панель переключения вкладок в неоморфном стиле Biolume.
 */
@Composable
private fun MediaPickerBottomNavBar(
    currentTab: MediaPickerTab,
    onTabSelected: (MediaPickerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val barShape: Shape = if (tokens.isForge) tokens.shapes.bar else RoundedCornerShape(32.dp)
    val tabs = MediaPickerTab.values()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, barShape)
                    else Modifier
                )
                .clip(barShape)
                .background(
                    if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                    barShape
                )
                .then(
                    if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.5f), barShape)
                    else Modifier
                )
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == currentTab
                BiolumeTabItem(
                    tab = tab,
                    selected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

/**
 * Пункт навигации с мягкой овальной пилюлей и пружинной анимацией.
 */
@Composable
private fun BiolumeTabItem(
    tab: MediaPickerTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }

    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) cs.primary.copy(alpha = 0.20f)
            else cs.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_pill_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.75f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_content_color"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_scale"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(pillColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.title,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale)
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = tab.title,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
