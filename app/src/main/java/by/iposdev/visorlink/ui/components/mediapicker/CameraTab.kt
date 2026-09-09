package by.iposdev.visorlink.ui.components.mediapicker

import android.Manifest
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Режим съемки камеры (фото или видео).
 */
enum class CameraCaptureMode {
    PHOTO,
    VIDEO
}

/**
 * Вкладка встроенной камеры на базе CameraX.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraTab(
    onPhotoTaken: (Uri) -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    if (!cameraPermission.status.isGranted) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Для съемки фото и видео необходим доступ к камере",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Разрешить камеру")
                }
            }
        }
        return
    }

    var captureMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    var isCapturingPhoto by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Привязка CameraX use cases к жизненному циклу
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val capture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
            .build()
        val video = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture,
                video
            )
            imageCapture = capture
            videoCapture = video
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Остановка записи при уходе с экрана
    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            timerJob?.cancel()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val newZoom = (currentZoom * zoom).coerceIn(
                        cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                        cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 5f
                    )
                    cam.cameraControl.setZoomRatio(newZoom)
                }
            }
    ) {
        // Превью камеры
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Верхняя панель управления (вспышка, переключение камеры, таймер записи)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Вспышка
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    imageCapture?.flashMode = flashMode
                    val isTorch = flashMode == ImageCapture.FLASH_MODE_ON
                    camera?.cameraControl?.enableTorch(isTorch)
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                )
            }

            // Таймер записи видео
            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Red.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            text = String.format(Locale.US, "00:%02d / 00:60", recordingSeconds),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Переключение передней/задней камеры
            IconButton(
                onClick = {
                    if (!isRecording) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }
                },
                enabled = !isRecording,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = Color.White
                )
            }
        }

        // Нижняя панель управления спуском и режимами
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Переключатель режима съемки: Фото / Видео
            if (!isRecording) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { captureMode = CameraCaptureMode.PHOTO },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (captureMode == CameraCaptureMode.PHOTO) Color.White else Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = if (captureMode == CameraCaptureMode.PHOTO) {
                            Modifier.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        } else Modifier
                    ) {
                        Text("ФОТО", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (!audioPermission.status.isGranted) {
                                audioPermission.launchPermissionRequest()
                            }
                            captureMode = CameraCaptureMode.VIDEO
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (captureMode == CameraCaptureMode.VIDEO) Color.White else Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = if (captureMode == CameraCaptureMode.VIDEO) {
                            Modifier.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        } else Modifier
                    ) {
                        Text("ВИДЕО (60с)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Кнопка спуска / записи
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                if (captureMode == CameraCaptureMode.PHOTO) {
                    // Кнопка съемки фото
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (isCapturingPhoto) Color.Gray else Color.White)
                            .clickable(enabled = !isCapturingPhoto) {
                                val capture = imageCapture ?: return@clickable
                                isCapturingPhoto = true
                                val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturingPhoto = false
                                            onPhotoTaken(Uri.fromFile(photoFile))
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturingPhoto = false
                                            exception.printStackTrace()
                                        }
                                    }
                                )
                            }
                    )
                } else {
                    // Кнопка записи видео (с прогресс-индикатором до 60 секунд)
                    val progress = recordingSeconds / 60f
                    if (isRecording) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(80.dp),
                            color = Color.Red,
                            strokeWidth = 4.dp,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 40.dp else 68.dp)
                            .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                            .background(Color.Red)
                            .clickable {
                                if (isRecording) {
                                    // Остановка записи видео
                                    activeRecording?.stop()
                                    activeRecording = null
                                    timerJob?.cancel()
                                    isRecording = false
                                } else {
                                    // Начало записи видео
                                    val video = videoCapture ?: return@clickable
                                    if (!audioPermission.status.isGranted) {
                                        audioPermission.launchPermissionRequest()
                                    }

                                    val videoFile = File(context.cacheDir, "camera_video_${System.currentTimeMillis()}.mp4")
                                    val outputOptions = FileOutputOptions.Builder(videoFile).build()

                                    val pendingRecording = video.output.prepareRecording(context, outputOptions)
                                    if (audioPermission.status.isGranted) {
                                        pendingRecording.withAudioEnabled()
                                    }

                                    recordingSeconds = 0
                                    isRecording = true

                                    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                timerJob?.cancel()
                                                if (!event.hasError()) {
                                                    onVideoRecorded(Uri.fromFile(videoFile))
                                                }
                                            }
                                        }
                                    }

                                    // Таймер на 60 секунд
                                    timerJob = scope.launch {
                                        while (isRecording && recordingSeconds < 60) {
                                            delay(1000)
                                            recordingSeconds++
                                        }
                                        if (recordingSeconds >= 60 && isRecording) {
                                            activeRecording?.stop()
                                            activeRecording = null
                                            isRecording = false
                                        }
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}
