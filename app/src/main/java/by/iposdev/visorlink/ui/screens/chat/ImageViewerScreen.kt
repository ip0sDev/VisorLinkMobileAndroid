package by.iposdev.visorlink.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import by.iposdev.visorlink.R
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    url: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Логируем URL для диагностики
    LaunchedEffect(url) {
        Log.d("ImageViewer", "Loading URL: $url")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Painter для отслеживания состояния загрузки
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        )

        val painterState = painter.state

        // Само изображение
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }

                            when {
                                pointers.size >= 2 -> {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale
                                    offset = if (newScale > 1f) {
                                        Offset(offset.x + panChange.x, offset.y + panChange.y)
                                    } else {
                                        Offset.Zero
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                pointers.size == 1 && scale > 1f -> {
                                    val panChange = event.calculatePan()
                                    offset = Offset(offset.x + panChange.x, offset.y + panChange.y)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Tap / double-tap
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300L) {
                            scale = if (scale > 1.5f) 1f else 2.5f
                            if (scale <= 1f) offset = Offset.Zero
                        } else {
                            showControls = !showControls
                        }
                        lastTapTime = now
                    }
                }
        )

        // Индикатор загрузки
        when (painterState) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
            is AsyncImagePainter.State.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.image_viewer_load_error), color = Color.White)
                    Text(url, color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp))
                    Log.e("ImageViewer", "Error loading: $url", painterState.result.throwable)
                }
            }
            else -> {}
        }

        // Top bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White
                        )
                    }
                },
                title = {},
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                isSaving = true
                                val success = saveImageToGallery(context, url)
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    if (success) "Saved to gallery" else "Failed to save",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(Icons.Default.Download, stringResource(R.string.image_viewer_save), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    }
}

suspend fun saveImageToGallery(context: Context, url: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            Log.d("ImageViewer", "Saving from URL: $url")
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // нужно для доступа к пикселям
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable
                ?.let { (it as? BitmapDrawable)?.bitmap }

            if (bitmap == null) {
                Log.e("ImageViewer", "Bitmap is null, result: $result")
                return@withContext false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "visorlink_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VisorLink")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "visorlink_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), null, null
                )
            }
            Log.d("ImageViewer", "Saved successfully")
            true
        } catch (e: Exception) {
            Log.e("ImageViewer", "Save failed", e)
            false
        }
    }