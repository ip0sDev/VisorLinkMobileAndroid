package org.visorlink.app.ui.components.mediapicker

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import org.visorlink.app.data.model.MediaType
import org.visorlink.app.data.model.SelectedMediaItem
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard

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
 * По умолчанию открывается на высоту клавиатуры (~половина экрана) со всеми видимыми кнопками и табами,
 * и поддерживает свайп наверх / кнопку развертывания для полноэкранного режима.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MediaPickerTab.ALL) }
    var isExpanded by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val compactHeight = remember(screenHeight) { minOf(480.dp, screenHeight * 0.65f) }

    val targetHeight = if (isExpanded) screenHeight else compactHeight
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mediaPickerSheetHeight"
    )

    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    // Автоматическое разворачивание при переходе на камеру
    LaunchedEffect(currentTab) {
        if (currentTab == MediaPickerTab.CAMERA) {
            isExpanded = true
        }
    }

    // Жест свайпа вверх/вниз для ручки перетаскивания и заголовка
    val dragModifier = Modifier.pointerInput(isExpanded) {
        detectVerticalDragGestures { _, dragAmount ->
            if (dragAmount < -15f && !isExpanded) {
                isExpanded = true
            } else if (dragAmount > 15f && isExpanded) {
                isExpanded = false
            } else if (dragAmount > 30f && !isExpanded) {
                coroutineScope.launch {
                    sheetState.hide()
                    onDismiss()
                }
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
                    .then(dragModifier)
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
        contentWindowInsets = { WindowInsets(0) },
        modifier = Modifier.fillMaxWidth()
    ) {
        VlMediaPickerViewContent(
            viewModel = viewModel,
            maxSelection = maxSelection,
            onClose = onDismiss,
            onOpenAudioPicker = onOpenAudioPicker,
            onOpenEditor = onOpenEditor,
            onMediaSelected = onMediaSelected,
            onPhotoTaken = onPhotoTaken,
            onVideoRecorded = onVideoRecorded,
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .navigationBarsPadding()
        )
    }
}

/**
 * Внутреннее содержимое медиа-пикера (галерея, вкладки, камера, кнопки отправки).
 * Переиспользуется как в модальной шторке, так и в жидкостной панели чата.
 */
@Composable
fun VlMediaPickerViewContent(
    viewModel: MediaPickerViewModel = koinViewModel(),
    maxSelection: Int = 10,
    onClose: () -> Unit,
    onOpenAudioPicker: () -> Unit,
    onOpenEditor: (Uri) -> Unit,
    onMediaSelected: (List<SelectedMediaItem>) -> Unit,
    onPhotoTaken: (Uri) -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(MediaPickerTab.ALL) }
    var isExpanded by remember { mutableStateOf(false) }

    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    val context = LocalContext.current

    val multiplePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxSelection)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val categorized = uris.map { uri ->
            val mime = context.contentResolver.getType(uri) ?: ""
            val isVideo = mime.startsWith("video/") || uri.toString().contains("video", ignoreCase = true)
            uri to if (isVideo) MediaType.VIDEO else MediaType.IMAGE
        }

        val videos = categorized.filter { it.second == MediaType.VIDEO }
        val images = categorized.filter { it.second == MediaType.IMAGE }

        // Ограничение 1: запрет одновременного выбора видео и фото
        if (videos.isNotEmpty() && images.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.error_cannot_mix_photo_video), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        // Ограничение 2: запрет выбора более одного видео
        if (videos.size > 1) {
            Toast.makeText(context, context.getString(R.string.error_max_video_exceeded), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        // Ограничение 3: запрет выбора фото больше чем размер альбома
        if (images.size > maxSelection) {
            Toast.makeText(context, context.getString(R.string.error_max_photos_exceeded, maxSelection), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val result = (videos + images).map { (uri, type) ->
            SelectedMediaItem(uri = uri, type = type)
        }
        viewModel.clearSelection()
        onMediaSelected(result)
    }

    val singleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.clearSelection()
            onMediaSelected(listOf(SelectedMediaItem(uri = uri, type = MediaType.VIDEO)))
        }
    }

    val launchPicker = {
        when (currentTab) {
            MediaPickerTab.PHOTOS -> {
                multiplePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            MediaPickerTab.VIDEOS -> {
                singleVideoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
            else -> {
                multiplePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            }
        }
    }

    // Автоматическое разворачивание при переходе на камеру
    LaunchedEffect(currentTab) {
        if (currentTab == MediaPickerTab.CAMERA) {
            isExpanded = true
        }
    }

    Column(modifier = modifier) {
        // Верхняя панель заголовка и быстрых действий
        MediaPickerTopBar(
            currentTab = currentTab,
            selectedCount = selectedItems.size,
            maxSelection = maxSelection,
            isExpanded = isExpanded,
            onToggleExpand = { isExpanded = !isExpanded },
            onClose = onClose,
            onOpenAudio = onOpenAudioPicker,
            onClearSelection = { viewModel.clearSelection() }
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (tokens.isBiolume) cs.outlineVariant.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.4f)
        )

        // Основная область контента (кнопка системного пикера или камера)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentTab) {
                MediaPickerTab.CAMERA -> {
                    CameraTab(
                        onPhotoTaken = { uri ->
                            viewModel.clearSelection()
                            onPhotoTaken(uri)
                        },
                        onVideoRecorded = { uri ->
                            viewModel.clearSelection()
                            onVideoRecorded(uri)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VlCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = tokens.shapes.card
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val icon = when (currentTab) {
                                    MediaPickerTab.PHOTOS -> Icons.Outlined.Image
                                    MediaPickerTab.VIDEOS -> Icons.Outlined.Videocam
                                    else -> Icons.Outlined.Collections
                                }
                                val titleText = when (currentTab) {
                                    MediaPickerTab.PHOTOS -> stringResource(R.string.photo)
                                    MediaPickerTab.VIDEOS -> "Видео"
                                    else -> stringResource(R.string.media)
                                }
                                val descText = when (currentTab) {
                                    MediaPickerTab.PHOTOS -> stringResource(R.string.media_picker_photos_desc)
                                    MediaPickerTab.VIDEOS -> stringResource(R.string.media_picker_videos_desc)
                                    else -> stringResource(R.string.media_picker_select_desc)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(cs.primaryContainer.copy(alpha = 0.65f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = descText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(20.dp))

                                VlButton(
                                    onClick = launchPicker,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.media_picker_open_system_picker),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Блок плавающих кнопок действий над нижним таб-баром (при выборе элементов)
        AnimatedVisibility(
            visible = selectedItems.isNotEmpty() && currentTab != MediaPickerTab.CAMERA,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка перехода в редактор (аккуратный неоморфный кругляш)
                if (selectedItems.size == 1 && selectedItems.first().type == MediaType.IMAGE) {
                    val editShape = CircleShape
                    Surface(
                        onClick = {
                            val item = selectedItems.first()
                            viewModel.clearSelection()
                            onOpenEditor(item.uri)
                        },
                        shape = editShape,
                        color = if (tokens.structure.enabled) cs.surfaceContainerHigh else cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(44.dp)
                            .then(
                                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, editShape)
                                else Modifier
                            )
                            .then(
                                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.5f), editShape)
                                else Modifier
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Кнопка отправки с подсчетом
                ExtendedFloatingActionButton(
                    onClick = {
                        val result = selectedItems.map {
                            SelectedMediaItem(
                                uri = it.uri,
                                type = it.type,
                                durationMs = it.durationMs
                            )
                        }
                        viewModel.clearSelection()
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
                    shape = tokens.shapes.fab,
                    modifier = Modifier.height(48.dp)
                )
            }
        }

        // Нижняя панель вкладок (всегда видна внизу шторки)
        MediaPickerBottomNavBar(
            currentTab = currentTab,
            onTabSelected = { newTab -> currentTab = newTab }
        )
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
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onOpenAudio: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = modifier
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
            val tokens = VlTheme.tokens
            val pillShape = CircleShape
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .then(
                        if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, pillShape)
                        else Modifier
                    )
                    .clip(pillShape)
                    .background(
                        if (tokens.isBiolume) cs.primary.copy(alpha = 0.18f) else cs.primaryContainer,
                        pillShape
                    )
                    .then(
                        if (tokens.structure.enabled) Modifier.vlHairline(cs.primary.copy(alpha = 0.40f), pillShape)
                        else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Выбрано: $selectedCount/$maxSelection",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary
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

        // Правые действия: кнопка Сбросить или быстрый выбор Аудио, плюс кнопка развертывания
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

            // Кнопка переключения полного экрана / компактного режима
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
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
    val tokens = VlTheme.tokens
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

    val pillShape = CircleShape
    Box(
        modifier = modifier
            .then(
                if (selected && tokens.structure.enabled) Modifier.vlRaised(tokens.structure, pillShape)
                else Modifier
            )
            .clip(pillShape)
            .background(pillColor, pillShape)
            .then(
                if (selected && tokens.structure.enabled) Modifier.vlHairline(cs.primary.copy(alpha = 0.40f), pillShape)
                else Modifier
            )
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
