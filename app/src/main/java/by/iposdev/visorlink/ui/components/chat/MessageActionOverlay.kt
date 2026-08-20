package by.iposdev.visorlink.ui.components.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.MessageType
import by.iposdev.visorlink.data.model.SendStatus
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.sqrt

// Вспомогательная функция для расчета дистанции
private fun Offset.getDistanceVector(): Float = sqrt(this.x * this.x + this.y * this.y)

// Данные о вызове меню
data class ContextMenuData(
    val message: Message,
    val isMine: Boolean,
    val startOffset: Offset
)

@Composable
fun MessageActionOverlay(
    contextMenuData: ContextMenuData,
    currentDragOffset: Offset,
    isGestureMode: Boolean,
    canReact: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onCancelSending: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)? = null,
    onReact: (String) -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200),
        label = "overlay_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onDismiss()
            }
    ) {
        if (isGestureMode) {
            GestureMessageMenu(
                data = contextMenuData,
                currentDragOffset = currentDragOffset,
                canReact = canReact,
                onAction = { action, emoji ->
                    when (action) {
                        "reply" -> onReply()
                        "delete" -> onDelete()
                        "cancel_sending" -> onCancelSending()
                        "forward" -> onForward?.invoke()
                        "react" -> emoji?.let { onReact(it) }
                    }
                }
            )
        } else {
            NormalMessageMenu(
                data = contextMenuData,
                canReact = canReact,
                currentUid = currentUid,
                onDismiss = onDismiss,
                onReply = onReply,
                onDelete = onDelete,
                onCancelSending = onCancelSending,
                onSaveImage = onSaveImage,
                onSaveVoice = onSaveVoice,
                onOpenImage = onOpenImage,
                onForward = onForward,
                onReact = onReact
            )
        }
    }
}

@Composable
private fun NormalMessageMenu(
    data: ContextMenuData,
    canReact: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onCancelSending: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)?,
    onReact: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val isSending = data.message.status == SendStatus.SENDING || data.message.status == SendStatus.QUEUED || data.message.status == SendStatus.ERROR

    val expectedWidthPx = with(density) { 260.dp.toPx() }
    val xOffset = if (data.isMine) {
        with(density) { screenWidth.toPx() } - expectedWidthPx - with(density) { 16.dp.toPx() }
    } else {
        with(density) { 16.dp.toPx() }
    }

    val tapY = data.startOffset.y
    var yOffset = tapY - with(density) { 20.dp.toPx() }
    var showAbove = true
    if (yOffset < with(density) { 150.dp.toPx() }) {
        yOffset = tapY + with(density) { 20.dp.toPx() }
        showAbove = false
    }
    val maxY = with(density) { screenHeight.toPx() } - (menuSize.height) - 100f
    if (yOffset > maxY && menuSize.height > 0) yOffset = maxY

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menu_slide"
    )

    Surface(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = xOffset.toInt(),
                    y = (yOffset + (if (showAbove) 50f else -50f) * translateY).toInt()
                )
            }
            .width(260.dp)
            .onGloballyPositioned { menuSize = it.size },
        shape = RoundedCornerShape(20.dp),
        color = cs.surface,
        tonalElevation = 8.dp,
        border = borderStroke(cs)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (isSending) {
                ActionItem(Icons.Default.Close, "Отменить отправку", destructive = true) {
                    haptic.perform(HapticType.CLICK, true); onDismiss(); onCancelSending()
                }
                if (data.message.type == MessageType.TEXT) {
                    ActionItem(Icons.Default.ContentCopy, stringResource(R.string.action_copy_text)) {
                        haptic.perform(HapticType.CLICK, true)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("message", data.message.text ?: ""))
                        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            } else {
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
                    HorizontalDivider(color = cs.outlineVariant.copy(0.2f))
                }

                if (!data.message.deleted) {
                    ActionItem(Icons.AutoMirrored.Filled.Reply, stringResource(R.string.action_reply)) {
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
                        ActionItem(Icons.AutoMirrored.Filled.Forward, "Переслать") {
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
}

@Composable
private fun GestureMessageMenu(
    data: ContextMenuData,
    currentDragOffset: Offset,
    canReact: Boolean,
    onAction: (action: String, emoji: String?) -> Unit
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val density = LocalDensity.current

    val isSending = data.message.status == SendStatus.SENDING || data.message.status == SendStatus.QUEUED || data.message.status == SendStatus.ERROR

    val actions = mutableListOf<String>()
    if (isSending) {
        actions.add("cancel_sending")
        if (data.message.type == MessageType.TEXT) actions.add("copy")
    } else {
        if (canReact) actions.add("react")
        actions.add("reply")
        if (data.message.type == MessageType.TEXT) actions.add("copy")
        actions.add("forward")
        if (data.isMine) actions.add("delete")
    }

    val btnHalfW = with(density) { 75.dp.toPx() }
    val gridW = with(density) { (4 * 36 + 20).dp.toPx() }
    val gridH = with(density) { (((QUICK_REACTIONS.size / 4) * 36) + 20).dp.toPx() }
    val actionStep = with(density) { 52.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }

    val dirX = if (data.isMine) -1 else 1
    val growDown = data.startOffset.y < screenHeightPx / 2f
    val hasGrid = actions.contains("react")

    val menuOriginX = if (data.isMine) {
        screenWidthPx - margin - btnHalfW
    } else {
        margin + btnHalfW
    }

    val totalActionH = actions.size * actionStep
    val maxNeededH = maxOf(totalActionH, if (hasGrid) gridH else 0f)

    val rawMaxY = if (growDown) screenHeightPx - margin - maxNeededH - btnHalfW else screenHeightPx - margin - btnHalfW
    val rawMinY = if (growDown) margin + btnHalfW else margin + maxNeededH + btnHalfW

    val safeMinY = minOf(rawMinY, rawMaxY)
    val safeMaxY = maxOf(rawMinY, rawMaxY)
    val menuOriginY = data.startOffset.y.coerceIn(safeMinY, safeMaxY)

    val menuOrigin = Offset(menuOriginX, menuOriginY)

    val actionCenters = actions.mapIndexed { i, action ->
        val yPos = (if (growDown) 1 else -1) * actionStep * (i + 1)
        action to Offset(menuOrigin.x, menuOrigin.y + yPos)
    }.toMap()

    val gridLeftX = if (hasGrid) {
        val idealX = if (dirX == 1) menuOrigin.x + btnHalfW + margin
        else menuOrigin.x - btnHalfW - gridW - margin
        idealX.coerceIn(margin, screenWidthPx - gridW - margin)
    } else 0f

    val gridTopY = if (hasGrid) {
        val reactCenterY = actionCenters["react"]?.y ?: 0f
        val gTop = reactCenterY - (gridH / 2f)
        gTop.coerceIn(margin, screenHeightPx - margin - gridH)
    } else 0f

    val gridBounds = if (hasGrid) Rect(gridLeftX, gridTopY, gridLeftX + gridW, gridTopY + gridH) else null
    val virtualFingerPos = menuOrigin + currentDragOffset

    val currentSelection = remember(virtualFingerPos, actions) {
        var newSel = "cancel"
        var foundInGrid = false

        var closestAction = "cancel"
        var minDistance = (virtualFingerPos - menuOrigin).getDistanceVector()

        actionCenters.forEach { (action, center) ->
            val dist = (virtualFingerPos - center).getDistanceVector()
            if (dist < minDistance) {
                minDistance = dist
                closestAction = action
            }
        }

        if (hasGrid && gridBounds != null) {
            val hitBounds = Rect(
                left = gridBounds.left - 40f, top = gridBounds.top - 40f,
                right = gridBounds.right + 40f, bottom = gridBounds.bottom + 40f
            )
            if (hitBounds.contains(virtualFingerPos)) {
                val dxInside = virtualFingerPos.x - gridBounds.left - with(density) { 10.dp.toPx() }
                val dyInside = virtualFingerPos.y - gridBounds.top - with(density) { 10.dp.toPx() }

                val col = floor(dxInside / with(density) { 36.dp.toPx() }).toInt().coerceIn(0, 3)
                val row = floor(dyInside / with(density) { 36.dp.toPx() }).toInt().coerceIn(0, (QUICK_REACTIONS.size / 4) - 1)

                val idx = (row * 4 + col).toInt().coerceIn(0, QUICK_REACTIONS.size - 1)
                newSel = "react_${QUICK_REACTIONS[idx]}"
                foundInGrid = true
            }
        }

        if (!foundInGrid) newSel = closestAction
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

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val enterScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "enter_scale"
    )

    var cancelSize by remember { mutableStateOf(IntSize.Zero) }
    val actionSizes = remember { mutableStateMapOf<String, IntSize>() }

    Box(Modifier.fillMaxSize()) {
        val isCancelSelected = currentSelection == "cancel"
        val cancelColor = if (isCancelSelected) Color(0xFFFFC107) else cs.surface
        val cancelScale by animateFloatAsState(if (isCancelSelected) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "cancel_scale")

        Box(
            Modifier.offset { IntOffset(menuOrigin.x.toInt() - cancelSize.width / 2, menuOrigin.y.toInt() - cancelSize.height / 2) }
        ) {
            Surface(
                modifier = Modifier
                    .onGloballyPositioned { cancelSize = it.size }
                    .graphicsLayer {
                        scaleX = cancelScale * enterScale
                        scaleY = cancelScale * enterScale
                    },
                shape = RoundedCornerShape(20.dp),
                color = cancelColor,
                tonalElevation = if (isCancelSelected) 12.dp else 4.dp,
                border = borderStroke(cs)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, null, tint = if (isCancelSelected) Color.Black else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отмена", color = if (isCancelSelected) Color.Black else cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        actions.forEach { action ->
            val isSelected = currentSelection == action || (action == "react" && currentSelection.startsWith("react_"))
            val center = actionCenters[action] ?: Offset.Zero

            val icon = when(action) {
                "react" -> Icons.Default.AddReaction
                "reply" -> Icons.AutoMirrored.Filled.Reply
                "copy" -> Icons.Default.ContentCopy
                "forward" -> Icons.AutoMirrored.Filled.Forward
                "delete" -> Icons.Default.Delete
                "cancel_sending" -> Icons.Default.Close
                else -> Icons.Default.Warning
            }
            val text = when(action) {
                "react" -> "Реакция"
                "reply" -> stringResource(R.string.action_reply)
                "copy" -> stringResource(R.string.action_copy_text)
                "forward" -> "Переслать"
                "delete" -> stringResource(R.string.action_delete_message)
                "cancel_sending" -> "Отменить"
                else -> ""
            }
            val color = if (action == "delete" || action == "cancel_sending") cs.error else cs.onSurface
            val bgColor = if (isSelected) {
                if (action == "delete" || action == "cancel_sending") cs.error else cs.primary
            } else cs.surface

            val contentColor = if (isSelected) Color.White else color
            val actionScale by animateFloatAsState(if (isSelected) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "action_scale")
            val currentSize = actionSizes[action] ?: IntSize.Zero

            Box(
                Modifier.offset { IntOffset(center.x.toInt() - currentSize.width / 2, center.y.toInt() - currentSize.height / 2) }
            ) {
                Surface(
                    modifier = Modifier
                        .onGloballyPositioned { actionSizes[action] = it.size }
                        .graphicsLayer {
                            scaleX = actionScale * enterScale
                            scaleY = actionScale * enterScale
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    tonalElevation = if (isSelected) 12.dp else 4.dp,
                    border = borderStroke(cs)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (hasGrid) {
            val showGrid = currentSelection == "react" || currentSelection.startsWith("react_")
            val gridScale by animateFloatAsState(if (showGrid) 1f else 0f, spring(dampingRatio = 0.6f), label = "grid_scale")

            Box(
                Modifier.offset { IntOffset(gridLeftX.toInt(), gridTopY.toInt()) }
            ) {
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(if (dirX == 1) 0f else 1f, 0.5f)
                            scaleX = gridScale * enterScale
                            scaleY = gridScale * enterScale
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surface,
                    tonalElevation = 6.dp,
                    border = borderStroke(cs)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        QUICK_REACTIONS.chunked(4).forEach { row ->
                            Row {
                                row.forEach { emoji ->
                                    val isSelected = currentSelection == "react_$emoji"
                                    val emojiScale by animateFloatAsState(if (isSelected) 1.5f else 1f, label = "emoji_scale")
                                    Box(
                                        Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .scale(emojiScale)
                                                .background(if (isSelected) cs.primary.copy(0.4f) else Color.Transparent, CircleShape)
                                                .padding(4.dp)
                                        ) {
                                            Text(text = emoji, fontSize = 18.sp)
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
}

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

@Composable
private fun borderStroke(cs: ColorScheme) = BorderStroke(
    1.dp, cs.outlineVariant.copy(alpha = 0.3f)
)
