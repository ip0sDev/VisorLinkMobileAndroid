package org.visorlink.app.ui.components.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import org.visorlink.app.data.model.StickerPack
import org.visorlink.app.data.repository.StickerPackRepository
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StickerPackBottomSheet(
    packId: String,
    fallbackPackName: String? = null,
    fallbackPackEmoji: String? = null,
    onDismiss: () -> Unit,
    repository: StickerPackRepository = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf<StickerPack?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isInstalling by remember { mutableStateOf(false) }
    var isInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(packId) {
        isLoading = true
        isInstalled = repository.hasPack(packId)
        val fetched = repository.fetchPackDetails(packId)
        pack = fetched
        if (fetched != null) {
            isInstalled = repository.hasPack(packId)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val isLiquidEnabled = rememberLiquidEnabled()
        val popProgress = rememberLiquidPopProgress(isLiquidEnabled, damping = 0.68f, stiffness = 480f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPopIn(popProgress, isLiquidEnabled, TransformOrigin(0.5f, 1f))
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val displayName = pack?.name ?: fallbackPackName ?: "Стикерпак"
            val displayEmoji = pack?.emoji ?: fallbackPackEmoji ?: "✨"
            val author = pack?.authorName

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = displayEmoji, fontSize = 24.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = "Автор: $author",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                val stickers = pack?.stickers ?: emptyList()
                var previewSticker by remember { mutableStateOf<org.visorlink.app.data.model.StickerItem?>(null) }

                if (stickers.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(stickers) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { previewSticker = item },
                                        onLongClick = { previewSticker = item }
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CachedImage(
                                    model = item.url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                previewSticker?.let { sticker ->
                    StickerPreviewDialog(
                        sticker = sticker,
                        onDismiss = { previewSticker = null }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isInstalled) {
                OutlinedButton(
                    onClick = { /* already installed */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Стикерпак установлен")
                }
            } else {
                Button(
                    onClick = {
                        if (isInstalling) return@Button
                        isInstalling = true
                        scope.launch {
                            try {
                                repository.addPackToUser(packId)
                                isInstalled = true
                                Toast.makeText(context, "Стикерпак добавлен!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isInstalling = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "Добавить стикерпак",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
