package by.iposdev.visorlink.ui.components.chat

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AlbumImage
import by.iposdev.visorlink.data.model.AlbumImageLocal
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
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

            OutlinedTextField(
                value = caption,
                onValueChange = { onCaptionChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Добавить подпись…") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Text(
                        "${caption.length}/500",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled); onSend() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = images.isNotEmpty()
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
    Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))) {
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(CircleShape)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumLightbox(images: List<AlbumImage>, startIndex: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val img = images[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = img.url, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offsetX; translationY = offsetY
                        }
                    )
                }
            }

            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text("${pagerState.currentPage + 1} / ${images.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 8.dp).size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }

            if (images.size > 1) {
                val stripListState = rememberLazyListState()
                LaunchedEffect(pagerState.currentPage) { stripListState.animateScrollToItem(pagerState.currentPage) }
                LazyRow(
                    state = stripListState,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(images) { idx, img ->
                        val isActive = idx == pagerState.currentPage
                        val scope = rememberCoroutineScope()
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
                                .border(width = if (isActive) 2.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(4.dp))
                                .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                        ) {
                            AsyncImage(model = img.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
