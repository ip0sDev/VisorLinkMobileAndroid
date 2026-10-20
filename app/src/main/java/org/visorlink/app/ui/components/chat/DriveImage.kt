package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import org.visorlink.app.R

/** Паузы перед повторами, когда все URL цепочки отказали (как utils/driveImage.js). */
private val RETRY_DELAYS_MS = listOf(0L, 800L, 1500L)

private val DRIVE_ID = Regex("""lh3\.googleusercontent\.com/d/([A-Za-z0-9_-]+)""")

/**
 * Цепочка URL для фото из Google Drive (спека подписей §2.1). lh3 иногда отвечает ошибкой,
 * поэтому дальше thumbnail и uc. Для не-Drive ссылок — единственный URL.
 */
internal fun driveImageCandidates(url: String?, driveFileId: String?): List<String> {
    val id = driveFileId?.takeIf { it.isNotBlank() } ?: url?.let { DRIVE_ID.find(it)?.groupValues?.get(1) }
    if (id == null) return listOfNotNull(url?.takeIf { it.isNotBlank() })
    return listOf(
        "https://lh3.googleusercontent.com/d/$id",
        "https://drive.google.com/thumbnail?id=$id&sz=w2000",
        "https://drive.google.com/uc?export=view&id=$id",
    )
}

/**
 * Фото чата с отказоустойчивой загрузкой: перебор [driveImageCandidates], затем до трёх
 * повторов всей цепочки, затем заглушка «Нажмите, чтобы загрузить снова».
 * Публичные ссылки Drive грузятся без Referer и Authorization — Coil их и не шлёт.
 */
@Composable
internal fun DriveImage(url: String?, driveFileId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val candidates = remember(url, driveFileId) { driveImageCandidates(url, driveFileId) }
    var round by remember(candidates) { mutableIntStateOf(0) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    var waiting by remember(candidates) { mutableStateOf(false) }

    val exhausted = candidates.isEmpty() || round >= RETRY_DELAYS_MS.size
    if (exhausted) {
        DriveImageError(onRetry = { round = 0; index = 0 })
        return
    }

    LaunchedEffect(round) {
        if (round > 0) {
            waiting = true
            delay(RETRY_DELAYS_MS[round])
            waiting = false
        }
    }
    if (waiting) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
        return
    }

    val target = candidates[index]
    AsyncImage(
        model = remember(target, round) {
            ImageRequest.Builder(context).data(target).setParameter("round", round, memoryCacheKey = null).build()
        },
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.Crop,
        onError = {
            if (index + 1 < candidates.size) index++
            else { index = 0; round++ }
        }
    )
}

/** Заглушка ошибки: без пунктирной рамки, заливка surfaceContainerHigh, 240 dp; тап — новая серия. */
@Composable
private fun DriveImageError(onRetry: () -> Unit) {
    Box(
        Modifier
            .width(240.dp)
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Refresh, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)
            )
            Text(
                stringResource(R.string.media_tap_to_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
