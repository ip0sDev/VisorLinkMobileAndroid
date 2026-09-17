package org.visorlink.app.ui.screens.decoy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.utils.CrossoutNewsItem
import org.visorlink.app.utils.CrossoutNewsService
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun DecoyNewsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<CrossoutNewsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    val refresh: (Boolean) -> Unit = { showSpinner ->
        scope.launch {
            if (showSpinner && items.isEmpty()) isLoading = true
            val fresh = CrossoutNewsService.fetchFresh(context)
            isLoading = false
            if (fresh != null) {
                items = fresh
                hasError = false
            } else if (items.isEmpty()) {
                hasError = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val cached = CrossoutNewsService.loadCached(context)
        if (cached.isNotEmpty()) {
            items = cached
            isLoading = false
        }
        if (!CrossoutNewsService.isCacheFresh(context)) {
            refresh(cached.isEmpty())
        } else {
            isLoading = false
        }
    }

    if (isLoading && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DecoyPalette.Accent, strokeWidth = 2.dp)
        }
        return
    }

    if (hasError && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.WifiOff, null, tint = DecoyPalette.TextSecondary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Не удалось загрузить новости", color = DecoyPalette.TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { refresh(true) }) {
                    Text("Повторить", color = DecoyPalette.Accent, fontFamily = FontFamily.Monospace)
                }
            }
        }
        return
    }

    val uriHandler = LocalUriHandler.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DecoyPalette.Border)
                    .background(DecoyPalette.Panel)
                    .clickable {
                        try { uriHandler.openUri(item.url) } catch (_: Exception) {}
                    }
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(DecoyPalette.PanelAlt), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ImageNotSupported, null, tint = DecoyPalette.TextSecondary)
                    }
                }

                Column(Modifier.padding(14.dp)) {
                    if (item.tag != null || item.date != null) {
                        Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.tag != null) {
                                Box(modifier = Modifier.background(DecoyPalette.AccentDim).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(item.tag.uppercase(), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (item.date != null) {
                                Text(item.date, color = DecoyPalette.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Text(item.title, color = DecoyPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    if (item.excerpt != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.excerpt, color = DecoyPalette.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}