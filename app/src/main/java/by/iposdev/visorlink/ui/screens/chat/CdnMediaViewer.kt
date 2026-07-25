package by.iposdev.visorlink.ui.screens.chat

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import by.iposdev.visorlink.utils.CdnService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.io.File

@Composable
fun CdnMediaViewer(
    mediaId: String?,
    type: String,
    localFile: File? = null,
    isFullscreen: Boolean = false
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var headers by remember { mutableStateOf<Map<String, String>?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var wantsToLoad by remember { mutableStateOf(isFullscreen) }

    LaunchedEffect(mediaId) {
        if (mediaId != null && (type == "video" || type == "gif")) {
            try {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                if (token != null) {
                    url = "${CdnService.BASE_URL}/f/$mediaId?token=$token"
                    headers = mapOf("Authorization" to "Bearer $token")
                }
            } catch (e: Exception) {
                hasError = true
            }
        }
    }

    if (hasError) {
        Box(
            modifier = Modifier.size(240.dp, 160.dp).background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Ошибка загрузки медиа", color = Color.Red)
        }
        return
    }

    if (type == "video" || type == "gif") {
        if (!wantsToLoad && localFile == null) {
            // Превью-заглушка
            Box(
                modifier = Modifier
                    .size(240.dp, 160.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { wantsToLoad = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            return
        }

        if (url == null && localFile == null) {
            Box(Modifier.size(240.dp, 160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        // Рендерим VideoView
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        if (localFile != null) {
                            setVideoPath(localFile.absolutePath)
                        } else if (url != null) {
                            setVideoURI(Uri.parse(url), headers)
                        }

                        // Добавляем контроллеры (пауза/плей/перемотка)
                        val mediaController = MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)

                        setOnPreparedListener { mp ->
                            mp.isLooping = (type == "gif")
                            if (isFullscreen || type == "gif") start()
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            )
        }
    }
}