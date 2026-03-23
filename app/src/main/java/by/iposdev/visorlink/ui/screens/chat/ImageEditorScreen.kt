package by.iposdev.visorlink.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Matrix as AndroidMatrix
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Инструменты редактора ────────────────────────────────────────────────────

enum class EditorTool { NONE, CROP, DRAW, BLUR }

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

// ── Экран редактора ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    uri: Uri,
    onNavigateBack: () -> Unit,
    onSend: (uri: Uri, isSpoiler: Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    // ── Трансформации ─────────────────────────────────────────────────────────
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var isSpoiler by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(EditorTool.NONE) }

    // ── Рисование ────────────────────────────────────────────────────────────
    val drawPaths = remember { mutableStateListOf<DrawPath>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var drawColor by remember { mutableStateOf(Color.Red) }
    var drawStroke by remember { mutableFloatStateOf(8f) }

    // ── Кроп ─────────────────────────────────────────────────────────────────
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var cropStart by remember { mutableStateOf(Offset.Zero) }
    var cropEnd by remember { mutableStateOf(Offset.Zero) }
    var isCropping by remember { mutableStateOf(false) }

    // ── Блюр зоны ────────────────────────────────────────────────────────────
    val blurRects = remember { mutableStateListOf<Rect>() }
    var blurStart by remember { mutableStateOf(Offset.Zero) }
    var blurEnd by remember { mutableStateOf(Offset.Zero) }
    var isDrawingBlur by remember { mutableStateOf(false) }

    // ── Обработка ────────────────────────────────────────────────────────────
    var isProcessing by remember { mutableStateOf(false) }

    // Размер канваса — получаем через onGloballyPositioned
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            "Back", tint = Color.White)
                    }
                },
                title = { Text("Edit Image",
                    color = Color.White, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    // Отменить рисование
                    if (drawPaths.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, true)
                            drawPaths.removeLastOrNull()
                        }) {
                            Icon(Icons.Default.Undo, "Undo",
                                tint = Color.White)
                        }
                    }
                    // Сбросить кроп
                    if (cropRect != null) {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, true)
                            cropRect = null
                        }) {
                            Icon(Icons.Default.CropFree, "Reset crop",
                                tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1A1A1A))
                    .navigationBarsPadding()
            ) {
                // ── Панель инструментов ───────────────────────────────────────
                ToolBar(
                    activeTool = activeTool,
                    onToolSelect = { tool ->
                        haptic.perform(HapticType.SELECTION, true)
                        activeTool = if (activeTool == tool) EditorTool.NONE else tool
                    },
                    onRotate = {
                        haptic.perform(HapticType.CLICK, true)
                        rotationDeg = (rotationDeg + 90f) % 360f
                    },
                    onFlipH = {
                        haptic.perform(HapticType.CLICK, true)
                        flipH = !flipH
                    },
                    onFlipV = {
                        haptic.perform(HapticType.CLICK, true)
                        flipV = !flipV
                    }
                )

                // ── Настройки кисти (только в DRAW режиме) ───────────────────
                AnimatedVisibility(
                    visible = activeTool == EditorTool.DRAW,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    DrawToolOptions(
                        color = drawColor,
                        stroke = drawStroke,
                        onColorChange = { drawColor = it },
                        onStrokeChange = { drawStroke = it }
                    )
                }

                // ── Кнопки кроп-действий ─────────────────────────────────────
                AnimatedVisibility(
                    visible = activeTool == EditorTool.CROP && cropRect != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cropRect = null; activeTool = EditorTool.NONE },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                // Apply crop — обновляем URI через processBitmap
                                haptic.perform(HapticType.SUCCESS, true)
                                activeTool = EditorTool.NONE
                                cropRect = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Apply") }
                    }
                }

                // ── Отправка ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Спойлер-тоггл
                    SpoilerToggle(
                        enabled = isSpoiler,
                        onToggle = {
                            haptic.perform(HapticType.SELECTION, true)
                            isSpoiler = !isSpoiler
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Кнопка отправки
                    IconButton(
                        onClick = {
                            if (isProcessing) return@IconButton
                            haptic.perform(HapticType.MESSAGE_SENT, true)
                            scope.launch {
                                isProcessing = true
                                val result = processBitmap(
                                    context = context,
                                    sourceUri = uri,
                                    rotationDeg = rotationDeg,
                                    flipH = flipH,
                                    flipV = flipV,
                                    cropRect = cropRect,
                                    canvasSize = canvasSize,
                                    drawPaths = drawPaths.toList(),
                                    blurRects = blurRects.toList()
                                )
                                isProcessing = false
                                onSend(result, isSpoiler)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send",
                                tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // ── Изображение с трансформациями ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer(
                        rotationZ = rotationDeg,
                        scaleX = if (flipH) -1f else 1f,
                        scaleY = if (flipV) -1f else 1f
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Блюр-зоны поверх изображения
                blurRects.forEach { rect ->
                    Box(
                        modifier = Modifier
                            .offset(rect.left.dp, rect.top.dp)
                            .size(rect.width.dp, rect.height.dp)
                            .blur(20.dp)
                            .background(Color.Black.copy(alpha = 0.01f)) // нужно для blur
                    )
                }

                // Слой рисования + интерактивность
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            canvasSize = coords.size
                        }
                        .pointerInput(activeTool) {
                            when (activeTool) {
                                EditorTool.DRAW -> detectDragGestures(
                                    onDragStart = { currentPoints.clear(); currentPoints.add(it) },
                                    onDrag = { _, delta ->
                                        val last = currentPoints.lastOrNull() ?: return@detectDragGestures
                                        currentPoints.add(last + delta)
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size > 1) {
                                            drawPaths.add(DrawPath(currentPoints.toList(), drawColor, drawStroke))
                                        }
                                        currentPoints.clear()
                                    }
                                )
                                EditorTool.CROP -> detectDragGestures(
                                    onDragStart = {
                                        cropStart = it
                                        cropEnd = it
                                        isCropping = true
                                    },
                                    onDrag = { _, delta -> cropEnd = cropEnd + delta },
                                    onDragEnd = {
                                        cropRect = Rect(
                                            left = minOf(cropStart.x, cropEnd.x),
                                            top = minOf(cropStart.y, cropEnd.y),
                                            right = maxOf(cropStart.x, cropEnd.x),
                                            bottom = maxOf(cropStart.y, cropEnd.y)
                                        )
                                        isCropping = false
                                    }
                                )
                                EditorTool.BLUR -> detectDragGestures(
                                    onDragStart = {
                                        blurStart = it
                                        blurEnd = it
                                        isDrawingBlur = true
                                    },
                                    onDrag = { _, delta -> blurEnd = blurEnd + delta },
                                    onDragEnd = {
                                        val r = Rect(
                                            left = minOf(blurStart.x, blurEnd.x),
                                            top = minOf(blurStart.y, blurEnd.y),
                                            right = maxOf(blurStart.x, blurEnd.x),
                                            bottom = maxOf(blurStart.y, blurEnd.y)
                                        )
                                        if (r.width > 10 && r.height > 10) blurRects.add(r)
                                        isDrawingBlur = false
                                    }
                                )
                                else -> { /* no-op */ }
                            }
                        }
                ) {
                    // Рисуем сохранённые пути
                    drawPaths.forEach { path ->
                        if (path.points.size > 1) {
                            val composePath = Path().apply {
                                moveTo(path.points.first().x, path.points.first().y)
                                path.points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = composePath,
                                color = path.color,
                                style = Stroke(
                                    width = path.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Текущий рисуемый путь
                    if (currentPoints.size > 1) {
                        val composePath = Path().apply {
                            moveTo(currentPoints.first().x, currentPoints.first().y)
                            currentPoints.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = composePath,
                            color = drawColor,
                            style = Stroke(width = drawStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Рамка кропа
                    val activeCropRect = if (isCropping) {
                        Rect(
                            left = minOf(cropStart.x, cropEnd.x),
                            top = minOf(cropStart.y, cropEnd.y),
                            right = maxOf(cropStart.x, cropEnd.x),
                            bottom = maxOf(cropStart.y, cropEnd.y)
                        )
                    } else cropRect

                    activeCropRect?.let { rect ->
                        // Затемнение за пределами кропа
                        drawRect(Color.Black.copy(alpha = 0.5f))
                        drawRect(
                            color = Color.Transparent,
                            topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                            blendMode = BlendMode.Clear
                        )
                        // Рамка
                        drawRect(
                            color = Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Угловые маркеры
                        listOf(
                            androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                            androidx.compose.ui.geometry.Offset(rect.right, rect.top),
                            androidx.compose.ui.geometry.Offset(rect.left, rect.bottom),
                            androidx.compose.ui.geometry.Offset(rect.right, rect.bottom)
                        ).forEach { corner ->
                            drawCircle(Color.White, radius = 6.dp.toPx(), center = corner)
                        }
                    }

                    // Рамка блюра
                    if (isDrawingBlur && abs(blurEnd.x - blurStart.x) > 10) {
                        val r = Rect(
                            left = minOf(blurStart.x, blurEnd.x),
                            top = minOf(blurStart.y, blurEnd.y),
                            right = maxOf(blurStart.x, blurEnd.x),
                            bottom = maxOf(blurStart.y, blurEnd.y)
                        )
                        drawRect(
                            color = Color(0xFF2196F3).copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top),
                            size = androidx.compose.ui.geometry.Size(r.width, r.height)
                        )
                        drawRect(
                            color = Color(0xFF2196F3),
                            topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top),
                            size = androidx.compose.ui.geometry.Size(r.width, r.height),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

// ── Панель инструментов ───────────────────────────────────────────────────────

@Composable
private fun ToolBar(
    activeTool: EditorTool,
    onToolSelect: (EditorTool) -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Обрезать
        ToolButton(
            icon = Icons.Default.Crop,
            label = "Crop",
            active = activeTool == EditorTool.CROP,
            onClick = { onToolSelect(EditorTool.CROP) }
        )
        // Повернуть
        ToolButton(
            icon = Icons.Default.RotateRight,
            label = "Rotate",
            active = false,
            onClick = onRotate
        )
        // Отразить горизонтально
        ToolButton(
            icon = Icons.Default.Flip,
            label = "Flip H",
            active = false,
            onClick = onFlipH
        )
        // Отразить вертикально
        ToolButton(
            icon = Icons.Default.Flip,
            label = "Flip V",
            active = false,
            onClick = onFlipV,
            rotated = true
        )
        // Карандаш
        ToolButton(
            icon = Icons.Default.Edit,
            label = "Draw",
            active = activeTool == EditorTool.DRAW,
            onClick = { onToolSelect(EditorTool.DRAW) }
        )
        // Блюр
        ToolButton(
            icon = Icons.Default.BlurOn,
            label = "Blur",
            active = activeTool == EditorTool.BLUR,
            onClick = { onToolSelect(EditorTool.BLUR) }
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    rotated: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tool_scale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            icon, null,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(rotationZ = if (rotated) 90f else 0f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label, fontSize = 10.sp,
            color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(0.7f)
        )
    }
}

// ── Опции кисти ───────────────────────────────────────────────────────────────

private val DRAW_COLORS = listOf(
    Color.Red, Color.Yellow, Color(0xFF00E5FF),
    Color.Green, Color.White, Color.Black,
    Color(0xFFFF6F00), Color(0xFFAA00FF)
)

@Composable
private fun DrawToolOptions(
    color: Color,
    stroke: Float,
    onColorChange: (Color) -> Unit,
    onStrokeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Цвета
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DRAW_COLORS.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(if (color == c) 32.dp else 26.dp)
                        .background(c, CircleShape)
                        .then(
                            if (color == c) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                        .clickable { onColorChange(c) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Толщина кисти
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size",
                color = Color.White.copy(0.7f), fontSize = 11.sp,
                modifier = Modifier.width(60.dp))
            Slider(
                value = stroke,
                onValueChange = onStrokeChange,
                valueRange = 4f..32f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = Color.White.copy(0.2f)
                )
            )
        }
    }
}

// ── Спойлер-тоггл ────────────────────────────────────────────────────────────

@Composable
fun SpoilerToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(200),
        label = "spoiler_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(0.7f),
        animationSpec = tween(200),
        label = "spoiler_color"
    )

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (enabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                null, tint = contentColor, modifier = Modifier.size(20.dp)
            )
            Text(
                if (enabled) "Spoiler on"
                else "Hide as spoiler",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ── Bitmap-обработка ─────────────────────────────────────────────────────────

private suspend fun processBitmap(
    context: Context,
    sourceUri: Uri,
    rotationDeg: Float,
    flipH: Boolean,
    flipV: Boolean,
    cropRect: Rect?,
    canvasSize: IntSize,
    drawPaths: List<DrawPath>,
    blurRects: List<Rect>
): Uri = withContext(Dispatchers.IO) {
    val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, sourceUri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, sourceUri)
    }.copy(Bitmap.Config.ARGB_8888, true)

    // Трансформации
    val matrix = AndroidMatrix()
    matrix.postRotate(rotationDeg)
    matrix.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
    var bmp = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

    // Рисование поверх (масштабируем координаты канваса к размеру bitmap)
    if (drawPaths.isNotEmpty() || blurRects.isNotEmpty()) {
        val canvas = android.graphics.Canvas(bmp)
        val scaleX = bmp.width.toFloat() / canvasSize.width.coerceAtLeast(1)
        val scaleY = bmp.height.toFloat() / canvasSize.height.coerceAtLeast(1)

        // Блюр-зоны
        blurRects.forEach { rect ->
            val left = (rect.left * scaleX).toInt().coerceIn(0, bmp.width)
            val top = (rect.top * scaleY).toInt().coerceIn(0, bmp.height)
            val right = (rect.right * scaleX).toInt().coerceIn(0, bmp.width)
            val bottom = (rect.bottom * scaleY).toInt().coerceIn(0, bmp.height)
            if (right > left && bottom > top) {
                val region = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
                val blurPaint = android.graphics.Paint().apply {
                    maskFilter = android.graphics.BlurMaskFilter(
                        30f, android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawBitmap(region, left.toFloat(), top.toFloat(), blurPaint)
            }
        }

        // Рисованные пути
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            style = android.graphics.Paint.Style.STROKE
        }
        drawPaths.forEach { path ->
            paint.color = path.color.toArgb()
            paint.strokeWidth = path.strokeWidth * scaleX
            val gPath = android.graphics.Path()
            if (path.points.isNotEmpty()) {
                gPath.moveTo(path.points.first().x * scaleX, path.points.first().y * scaleY)
                path.points.drop(1).forEach { pt ->
                    gPath.lineTo(pt.x * scaleX, pt.y * scaleY)
                }
                canvas.drawPath(gPath, paint)
            }
        }
    }

    // Кроп
    if (cropRect != null && canvasSize.width > 0) {
        val scaleX = bmp.width.toFloat() / canvasSize.width
        val scaleY = bmp.height.toFloat() / canvasSize.height
        val x = (cropRect.left * scaleX).toInt().coerceIn(0, bmp.width)
        val y = (cropRect.top * scaleY).toInt().coerceIn(0, bmp.height)
        val w = ((cropRect.width) * scaleX).toInt().coerceIn(1, bmp.width - x)
        val h = ((cropRect.height) * scaleY).toInt().coerceIn(1, bmp.height - y)
        bmp = Bitmap.createBitmap(bmp, x, y, w, h)
    }

    // Сохраняем
    val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    Uri.fromFile(file)
}