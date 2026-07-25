package by.iposdev.visorlink.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.MessageType
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.floor

// Данные о вызове меню
data class ContextMenuData(
    val message: Message,
    val isMine: Boolean,
    val startOffset: Offset
)

@Composable
fun MessageActionOverlay(
    contextMenuData: ContextMenuData,
    currentDragOffset: Offset,      // Для жестового режима
    isGestureMode: Boolean,         // Флаг режима из настроек
    canReact: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)? = null,
    onReact: (String) -> Unit,
    themeVm: ThemeViewModel = koinViewModel()
) {
    val appTheme by themeVm.appTheme.collectAsState()
    val simplifiedGraphics = false // В Kotlin-версии пока нет этого флага в ThemeViewModel

    // Анимация появления фона
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200),
        label = "overlay_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f * alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onDismiss()
            }
    ) {
        if (isGestureMode) {
            GestureMessageMenu(
                data = contextMenuData,
                currentDragOffset = currentDragOffset,
                canReact = canReact,
                appTheme = appTheme,
                onAction = { action, emoji ->
                    when (action) {
                        "reply" -> onReply()
                        "copy" -> { /* Логика копирования в DisposableEffect */ }
                        "delete" -> onDelete()
                        "react" -> emoji?.let { onReact(it) }
                    }
                }
            )
        } else {
            NormalMessageMenu(
                data = contextMenuData,
                canReact = canReact,
                currentUid = currentUid,
                appTheme = appTheme,
                simplifiedGraphics = simplifiedGraphics,
                onDismiss = onDismiss,
                onReply = onReply,
                onDelete = onDelete,
                onSaveImage = onSaveImage,
                onSaveVoice = onSaveVoice,
                onOpenImage = onOpenImage,
                onForward = onForward,
                onReact = onReact
            )
        }
    }
}

// ── Обычный режим (Haze Blur Menu) ─────────────────────────────────────────

@Composable
private fun NormalMessageMenu(
    data: ContextMenuData,
    canReact: Boolean,
    currentUid: String,
    appTheme: AppTheme,
    simplifiedGraphics: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)?,
    onReact: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val style = rememberExthruStyle(appTheme)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Расчет позиции (с clamp, чтобы не уходило за экран)
    val tapX = data.startOffset.x
    val tapY = data.startOffset.y

    val expectedWidthPx = with(density) { 260.dp.toPx() }
    var xOffset = tapX - (expectedWidthPx / 2)
    val maxX = with(density) { screenWidth.toPx() } - expectedWidthPx - 32f
    xOffset = xOffset.coerceIn(32f, maxX)

    var yOffset = tapY - with(density) { 20.dp.toPx() }
    var showAbove = true
    if (yOffset < with(density) { 150.dp.toPx() }) {
        yOffset = tapY + with(density) { 20.dp.toPx() }
        showAbove = false
    }
    val maxY = with(density) { screenHeight.toPx() } - (menuSize.height) - 100f
    if (yOffset > maxY && menuSize.height > 0) yOffset = maxY

    // Анимация выезда
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menu_slide"
    )

    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(if (style.isForge) 0.dp else 20.dp)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = xOffset.toInt(),
                    y = (yOffset + (if (showAbove) 50f else -50f) * translateY).toInt()
                )
            }
            .width(260.dp)
            .onGloballyPositioned { menuSize = it.size }
            .then(
                if (simplifiedGraphics) Modifier else Modifier.hazeChild(
                    state = hazeState,
                    style = HazeStyle(blurRadius = 24.dp, tint = null)
                )
            )
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                shape
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
            .clip(shape)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (canReact && !data.message.deleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val alreadyReacted = data.message.parsedReactions.find { it.emoji == emoji }?.uids?.contains(currentUid) == true
                        EmojiReactionButton(
                            emoji = emoji, isSelected = alreadyReacted,
                            onClick = {
                                haptic.perform(HapticType.REACTION, true)
                                onReact(emoji)
                                onDismiss()
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))
            }

            if (!data.message.deleted) {
                ActionItem(Icons.Default.Reply, stringResource(R.string.action_reply)) {
                    haptic.perform(HapticType.CLICK, true); onDismiss(); onReply()
                }

                when (data.message.type) {
                    MessageType.TEXT -> {
                        ActionItem(Icons.Default.ContentCopy, stringResource(R.string.action_copy_text)) {
                            haptic.perform(HapticType.CLICK, true)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", data.message.text ?: ""))
                            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    MessageType.IMAGE -> {
                        ActionItem(Icons.Default.ZoomIn, stringResource(R.string.action_view_image)) {
                            haptic.perform(HapticType.CLICK, true); onDismiss(); onOpenImage()
                        }
                        ActionItem(Icons.Default.Download, stringResource(R.string.action_save_gallery)) {
                            haptic.perform(HapticType.CLICK, true); onDismiss(); onSaveImage()
                        }
                    }
                    MessageType.VOICE -> {
                        ActionItem(Icons.Default.Download, stringResource(R.string.action_save_voice)) {
                            haptic.perform(HapticType.CLICK, true); onDismiss(); onSaveVoice()
                        }
                    }
                }

                if (onForward != null) {
                    ActionItem(Icons.Default.Forward, "Переслать") {
                        haptic.perform(HapticType.CLICK, true); onDismiss(); onForward()
                    }
                }
            }

            if (data.isMine && !data.message.deleted) {
                ActionItem(Icons.Default.Delete, stringResource(R.string.action_delete_message), destructive = true) {
                    haptic.perform(HapticType.LONG_PRESS, true); onDismiss(); onDelete()
                }
            }
        }
    }
}

// ── Жестовый режим (Floating Nodes) ────────────────────────────────────────

@Composable
private fun GestureMessageMenu(
    data: ContextMenuData,
    currentDragOffset: Offset,
    canReact: Boolean,
    appTheme: AppTheme,
    onAction: (action: String, emoji: String?) -> Unit
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val style = rememberExthruStyle(appTheme)
    val cs = MaterialTheme.colorScheme

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current

    val dirX = if (data.isMine) -1 else 1
    val growDown = data.startOffset.y < with(density) { 300.dp.toPx() }

    val safePadding = with(density) { 20.dp.toPx() }
    val expectedEmojiGridWidth = with(density) { 190.dp.toPx() }

    var minDx = if (data.isMine) (expectedEmojiGridWidth + with(density) { 65.dp.toPx() } + safePadding) else with(density) { 80.dp.toPx() }
    var maxDx = if (data.isMine) with(density) { screenWidth.toPx() } - with(density) { 80.dp.toPx() } else with(density) { screenWidth.toPx() } - (expectedEmojiGridWidth + with(density) { 65.dp.toPx() } + safePadding)

    if (minDx > maxDx) {
        minDx = with(density) { screenWidth.toPx() } / 2
        maxDx = with(density) { screenWidth.toPx() } / 2
    }

    val rawDx = data.startOffset.x + (dirX * with(density) { 40.dp.toPx() })
    val menuOrigin = Offset(rawDx.coerceIn(minDx, maxDx), data.startOffset.y)

    val actions = mutableListOf<String>()
    if (canReact) actions.add("react")
    actions.add("reply")
    if (data.message.type == MessageType.TEXT) actions.add("copy")
    if (data.isMine) actions.add("delete")

    val gridTopY = if (actions.contains("react")) {
        val reactIndex = actions.indexOf("react")
        val reactButtonY = (if (growDown) 1 else -1) * with(density) { 60.dp.toPx() } * (reactIndex + 1)
        val globalReactY = menuOrigin.y + reactButtonY
        val panelHeight = with(density) { (((QUICK_REACTIONS.size / 4) * 40) + 28).dp.toPx() }
        var gTop = globalReactY - (panelHeight / 2)
        gTop = gTop.coerceIn(with(density) { 40.dp.toPx() }, with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() } - 40f - panelHeight)
        gTop - menuOrigin.y
    } else 0f

    // Быстрый пересчет выбора при свайпе
    val dirY = if (growDown) 1 else -1
    val signedDy = currentDragOffset.y * dirY
    val signedDx = currentDragOffset.x * dirX

    val verticalIdx = floor((signedDy - with(density) { 20.dp.toPx() }) / with(density) { 60.dp.toPx() }).toInt()
    val isReactRow = verticalIdx == actions.indexOf("react")

    val currentSelection = remember(currentDragOffset, actions) {
        var newSel = "cancel"
        if (signedDx > with(density) { 40.dp.toPx() } && actions.contains("react")) {
            val panelWidth = with(density) { (4 * 40 + 30).dp.toPx() }
            val dxInside = if (dirX == 1) currentDragOffset.x - with(density) { 65.dp.toPx() } - with(density) { 14.dp.toPx() }
            else currentDragOffset.x + with(density) { 65.dp.toPx() } + panelWidth - with(density) { 14.dp.toPx() }

            val dyInside = currentDragOffset.y - gridTopY - with(density) { 14.dp.toPx() }

            val col = floor(dxInside / with(density) { 40.dp.toPx() }).toInt().coerceIn(0, 3)
            val row = floor(dyInside / with(density) { 40.dp.toPx() }).toInt().coerceIn(0, (QUICK_REACTIONS.size / 4) - 1)

            val idx = (row * 4 + col).coerceIn(0, QUICK_REACTIONS.size - 1)
            newSel = "react_${QUICK_REACTIONS[idx]}"
        } else {
            if (signedDy < with(density) { 20.dp.toPx() } && kotlin.math.abs(currentDragOffset.x) < with(density) { 40.dp.toPx() }) {
                newSel = "cancel"
            } else {
                val idx = verticalIdx.coerceIn(0, actions.size - 1)
                newSel = actions[idx]
            }
        }
        newSel
    }

    LaunchedEffect(currentSelection) {
        when {
            currentSelection == "delete" -> haptic.perform(HapticType.LONG_PRESS, true)
            currentSelection == "cancel" -> haptic.perform(HapticType.CLICK, true)
            currentSelection.startsWith("react_") -> haptic.perform(HapticType.CLICK, true)
            else -> haptic.perform(HapticType.SELECTION, true)
        }
    }

    val finalSelection by rememberUpdatedState(currentSelection)

    // При отпускании меню: закрывается оверлей -> вызывается onDispose
    DisposableEffect(Unit) {
        onDispose {
            if (finalSelection != "cancel") {
                if (finalSelection == "copy") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", data.message.text ?: ""))
                    Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                } else {
                    val emoji = if (finalSelection.startsWith("react_")) finalSelection.removePrefix("react_") else null
                    onAction(if (finalSelection.startsWith("react_")) "react" else finalSelection, emoji)
                }
            }
        }
    }

    // Анимация входа
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val enterScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "enter_scale"
    )

    // ── РЕНДЕР ПЛАВАЮЩИХ ПУЗЫРЕЙ ──
    Box(Modifier.fillMaxSize()) {

        val isCancelSelected = currentSelection == "cancel"
        val cancelColor = if (isCancelSelected) Color(0xFFFFC107) else cs.surfaceVariant.copy(0.8f)
        val cancelScale by animateFloatAsState(if (isCancelSelected) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "cancel_scale")

        Box(
            Modifier
                .offset { IntOffset((menuOrigin.x - with(density){24.dp.toPx()}).toInt(), (menuOrigin.y - with(density){24.dp.toPx()}).toInt()) }
                .scale(cancelScale * enterScale)
                .background(cancelColor, RoundedCornerShape(20.dp))
                .border(1.dp, if (isCancelSelected) Color(0xFFFFC107) else cs.outlineVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, null, tint = if (isCancelSelected) Color.Black else cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отмена", color = if (isCancelSelected) Color.Black else cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }

        actions.forEachIndexed { i, action ->
            val yPos = (if (growDown) 1 else -1) * with(density) { 60.dp.toPx() } * (i + 1)
            val isSelected = currentSelection == action || (action == "react" && currentSelection.startsWith("react_"))

            val icon = when(action) {
                "react" -> Icons.Default.AddReaction
                "reply" -> Icons.Default.Reply
                "copy" -> Icons.Default.ContentCopy
                "delete" -> Icons.Default.Delete
                else -> Icons.Default.Warning
            }
            val text = when(action) {
                "react" -> "Реакция"
                "reply" -> stringResource(R.string.action_reply)
                "copy" -> stringResource(R.string.action_copy_text)
                "delete" -> stringResource(R.string.action_delete_message)
                else -> ""
            }
            val color = if (action == "delete") style.destructive else cs.onSurface

            val bgColor = if (isSelected) {
                if (action == "delete") style.destructive else style.accent
            } else cs.surfaceVariant.copy(alpha = 0.9f)

            val contentColor = if (isSelected) Color.White else color
            val actionScale by animateFloatAsState(if (isSelected) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "action_scale")

            Box(
                Modifier
                    .offset { IntOffset((menuOrigin.x - with(density){24.dp.toPx()}).toInt(), (menuOrigin.y + yPos - with(density){24.dp.toPx()}).toInt()) }
                    .scale(actionScale * enterScale)
                    .background(bgColor, RoundedCornerShape(20.dp))
                    .border(1.dp, cs.outlineVariant.copy(0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, color = contentColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (actions.contains("react")) {
            val showGrid = currentSelection == "react" || currentSelection.startsWith("react_")
            val gridScale by animateFloatAsState(if (showGrid) 1f else 0f, spring(dampingRatio = 0.6f), label = "grid_scale")

            val panelWidth = with(density) { (4 * 40 + 30).dp.toPx() }
            val leftPos = if (dirX == 1) menuOrigin.x + with(density) { 65.dp.toPx() }
            else menuOrigin.x - with(density) { 65.dp.toPx() } - panelWidth

            Box(
                Modifier
                    .offset { IntOffset(leftPos.toInt(), (menuOrigin.y + gridTopY).toInt()) }
                    .scale(gridScale * enterScale)
                    .background(cs.surfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                    .border(1.dp, cs.outlineVariant.copy(0.3f), RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                Column {
                    QUICK_REACTIONS.chunked(4).forEach { row ->
                        Row {
                            row.forEach { emoji ->
                                val isSelected = currentSelection == "react_$emoji"
                                val emojiScale by animateFloatAsState(if (isSelected) 1.5f else 1f, label = "emoji_scale")
                                Box(
                                    Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .scale(emojiScale)
                                            .background(if (isSelected) style.accent.copy(0.4f) else Color.Transparent, CircleShape)
                                            .padding(4.dp)
                                    ) {
                                        Text(emoji, fontSize = 20.sp)
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

// ── Общие переиспользуемые элементы ────────────────────────────────────────

@Composable
private fun ActionItem(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun EmojiReactionButton(emoji: String, isSelected: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable {
                scope.launch {
                    scale.animateTo(0.75f)
                    scale.animateTo(1.2f)
                    scale.animateTo(1f)
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.scale(if (isSelected) 1.1f else 1f).scale(scale.value))
    }
}