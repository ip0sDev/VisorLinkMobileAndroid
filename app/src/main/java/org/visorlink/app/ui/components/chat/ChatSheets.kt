package org.visorlink.app.ui.components.chat

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.R
import org.visorlink.app.data.model.AlbumImage
import org.visorlink.app.data.model.AlbumImageLocal
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.liquidDragStretch
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import org.visorlink.app.ui.components.rubberBand
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.ImageCache
import org.visorlink.app.utils.rememberHaptic
import org.visorlink.app.ui.theme.VlTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperBottomSheet(
    hasWallpaper: Boolean,
    isGroupOrChannel: Boolean,
    onDismiss: () -> Unit,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val isLiquidEnabled = rememberLiquidEnabled()
        val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.68f, stiffness = 480f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPopIn(popProgress, isLiquidEnabled, TransformOrigin(0.5f, 1f))
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            Text(
                text = "Обои чата",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (isGroupOrChannel) {
                Text(
                    text = "Применяются для всех участников чата.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                )
            }

            ListItem(
                headlineContent = { Text("Выбрать из галереи") },
                leadingContent = { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onPickWallpaper() }
            )

            if (hasWallpaper) {
                ListItem(
                    headlineContent = { Text("Удалить обои", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onRemoveWallpaper() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPreviewSheet(
    images: List<AlbumImageLocal>,
    caption: String,
    hapticEnabled: Boolean,
    onSpoilerToggle: (Int) -> Unit,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    val haptic = rememberHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val isLiquidEnabled = rememberLiquidEnabled()
        val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.68f, stiffness = 480f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPopIn(popProgress, isLiquidEnabled, TransformOrigin(0.5f, 1f))
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                Text(
                    "${images.size} фото",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled); onSend() }, enabled = images.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send),
                        tint = if (images.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(images) { index, item ->
                    AlbumThumbnailCell(
                        uri = item.uri,
                        spoiler = item.spoiler,
                        onToggleSpoiler = { haptic.perform(HapticType.SELECTION, hapticEnabled); onSpoilerToggle(index) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            VlTextField(
                value = caption,
                onValueChange = { onCaptionChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = "Добавить подпись…",
                maxLines = 3,
                singleLine = false,
                supportingText = "${caption.length}/500"
            )

            Spacer(Modifier.height(12.dp))

            VlButton(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = images.isNotEmpty(),
                hapticEnabled = hapticEnabled
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отправить ${images.size} фото")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AlbumThumbnailCell(uri: Uri, spoiler: Boolean, onToggleSpoiler: () -> Unit) {
    val blurRadius by animateDpAsState(targetValue = if (spoiler) 12.dp else 0.dp, animationSpec = tween(200), label = "thumb_blur")
    Box(modifier = Modifier.size(140.dp).clip(VlTheme.tokens.shapes.card)) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        )
        if (spoiler) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("SPOILER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(VlTheme.tokens.shapes.indicator)
                .background(if (spoiler) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onToggleSpoiler),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (spoiler) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (spoiler) "Убрать spoiler" else "Пометить spoiler",
                tint = Color.White, modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumLightbox(images: List<AlbumImage>, startIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })

    var showControls by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Анимация свайпа вниз для закрытия
    val swipeOffset = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val isDismissing = remember { mutableStateOf(false) }
    val isLiquidEnabled = rememberLiquidEnabled()

    Dialog(
        onDismissRequest = { if (!isDismissing.value) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - abs(swipeOffset.value) / 1000f).coerceIn(0.1f, 1f)))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, swipeOffset.value.roundToInt()) },
                beyondViewportPageCount = 1,
                // Листаем влево-вправо только если НЕ тянем вниз активно
                userScrollEnabled = abs(swipeOffset.value) < 10f
            ) { page ->
                val img = images[page]
                val resolvedUrl = resolveCdnUrl(img.cdnMediaId, img.url)

                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                // Анимированный scale для плавного зума
                val animatedScale by animateFloatAsState(
                    targetValue = scale,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .pointerInput(Unit) {
                            // Единый обработчик жестов с правильным приоритетом
                            awaitEachGesture {
                                awaitFirstDown()
                                var isPinching = false
                                var initialScale = 1f
                                var initialOffsetX = 0f
                                var initialOffsetY = 0f
                                var initialCentroid = Offset.Zero
                                // Сырое смещение до резинки: именно его копит палец
                                var rawSwipeY = swipeOffset.value

                                do {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    val pointerCount = changes.count { it.pressed }
                                    
                                    if (pointerCount >= 2) {
                                        // PINCH ZOOM - 2+ пальца
                                        isPinching = true
                                        if (changes.any { it.pressed && it.isConsumed == false }) {
                                            // Начало pincha
                                            val pointers = changes.filter { it.pressed }.toList()
                                            val c1 = pointers[0].position
                                            val c2 = pointers[1].position
                                            initialCentroid = Offset((c1.x + c2.x) / 2f, (c1.y + c2.y) / 2f)
                                            initialScale = scale
                                            initialOffsetX = offsetX
                                            initialOffsetY = offsetY
                                        }
                                        
                                        val pointers = changes.filter { it.pressed }.toList()
                                        val c1 = pointers[0].position
                                        val c2 = pointers[1].position
                                        val centroid = Offset((c1.x + c2.x) / 2f, (c1.y + c2.y) / 2f)
                                        val diff = c1 - c2
                                        val currentDist = sqrt(diff.x * diff.x + diff.y * diff.y)
                                        
                                        // Находим предыдущие позиции для расчета zoom
                                        val prevC1 = pointers[0].previousPosition
                                        val prevC2 = pointers[1].previousPosition
                                        val prevDiff = prevC1 - prevC2
                                        val prevDist = sqrt(prevDiff.x * prevDiff.x + prevDiff.y * prevDiff.y)
                                        
                                        if (prevDist > 0) {
                                            val zoom = currentDist / prevDist
                                            val newScale = (initialScale * zoom).coerceIn(1f, 5f)
                                            scale = newScale
                                            
                                            // Pan от центра pincha
                                            if (scale > 1f) {
                                                val panDelta = centroid - initialCentroid
                                                offsetX = initialOffsetX + panDelta.x
                                                offsetY = initialOffsetY + panDelta.y
                                            }
                                        }
                                        changes.forEach { it.consume() }
                                    } else if (pointerCount == 1) {
                                        // SINGLE FINGER
                                        val change = changes.first { it.pressed }
                                        val pan = change.position - change.previousPosition
                                        
                                        if (isPinching) {
                                            // Завершили pinch, сбрасываем флаг
                                            isPinching = false
                                        } else if (scale > 1f) {
                                            // PAN при зуме - двигаем картинку
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            change.consume()
                                        } else {
                                            // Scale == 1f: проверяем вертикальный свайп для закрытия
                                            if (abs(pan.y) > abs(pan.x) * 2f && abs(pan.y) > 5.dp.toPx()) {
                                                rawSwipeY += pan.y
                                                val target = if (isLiquidEnabled) {
                                                    // У порога закрытия картинка вязнет, как капля перед отрывом
                                                    rubberBand(rawSwipeY, dismissThreshold, dismissThreshold * 0.8f)
                                                } else {
                                                    swipeOffset.value + pan.y
                                                }
                                                scope.launch { swipeOffset.snapTo(target) }
                                                change.consume()
                                            }
                                            // Горизонтальный свайп НЕ потребляем - уходит в HorizontalPager
                                        }
                                    }
                                } while (changes.any { it.pressed })

                                // Палец отпущен: за порогом закрываем, иначе упруго возвращаем на место
                                if (!isDismissing.value && abs(swipeOffset.value) > 0.5f) {
                                    if (abs(swipeOffset.value) > dismissThreshold) {
                                        isDismissing.value = true
                                        onDismiss()
                                    } else {
                                        scope.launch {
                                            swipeOffset.animateTo(
                                                0f,
                                                if (isLiquidEnabled) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
                                                else spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            // Двойной тап для зума
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 3f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidDragStretch(
                                dragPx = swipeOffset.value,
                                referencePx = dismissThreshold,
                                enabled = isLiquidEnabled && scale <= 1f,
                                maxStretch = 0.08f,
                                vertical = true
                            )
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }
            }

            // Controls
            AnimatedVisibility(
                visible = showControls && !isDismissing.value,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Bar Background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                    )

                    TopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${pagerState.currentPage + 1} / ${images.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { if (!isDismissing.value) onDismiss() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    val currentImg = images[pagerState.currentPage]
                                    scope.launch {
                                        isSaving = true
                                        val url = currentImg.url ?: ""

                                        val success = ImageCache.saveImageToGallery(context, url)
                                        isSaving = false
                                        Toast.makeText(context, if (success) "Сохранено" else "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Download", tint = Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        modifier = Modifier.statusBarsPadding()
                    )

                    // Bottom Strip
                    if (images.size > 1) {
                        val stripListState = rememberLazyListState()
                        LaunchedEffect(pagerState.currentPage) { stripListState.animateScrollToItem(pagerState.currentPage) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                                .navigationBarsPadding()
                        ) {
                            LazyRow(
                                state = stripListState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(images) { idx, img ->
                                    val isActive = idx == pagerState.currentPage
                                    val scope = rememberCoroutineScope()
                                    val resolvedThumb = resolveCdnUrl(img.cdnMediaId, img.url)

                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(VlTheme.tokens.shapes.card)
                                            .border(
                                                width = if (isActive) 2.dp else 0.dp,
                                                color = Color.White,
                                                shape = VlTheme.tokens.shapes.card
                                            )
                                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                                    ) {
                                        AsyncImage(
                                            model = resolvedThumb,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}