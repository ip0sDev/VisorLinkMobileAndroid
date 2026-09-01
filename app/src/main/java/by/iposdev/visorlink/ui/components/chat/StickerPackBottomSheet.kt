package by.iposdev.visorlink.ui.components.chat

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.data.repository.StickerPackRepository
import by.iposdev.visorlink.ui.components.CachedImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
