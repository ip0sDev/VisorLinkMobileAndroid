package org.visorlink.app.ui.components.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.MessageType
import org.visorlink.app.data.model.Reaction
import org.visorlink.app.data.model.SendStatus
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.UsageRankManager
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.sqrt

// Вспомогательная функция для расчета евклидова расстояния
private fun Offset.getDistanceVector(): Float = sqrt(this.x * this.x + this.y * this.y)

/**
 * Модель данных контекстного меню сообщения.
 */
data class ContextMenuData(
    val message: Message,
    val isMine: Boolean,
    val startOffset: Offset
)

/**
 * Современный жидкостный оверлей контекстного меню сообщений:
 * - Парящая капсула реакций (LiquidReactionCapsule) с эффектом магнитной линзы (Dock Magnification).
 * - Компактная стеклянная карточка действий (ActionGlassCard) с неоморфической стилизацией.
 * - Двойная модель взаимодействия: "Hold & Drag to React" (протянуть палец и отпустить)
 *   и "Tap & Choose" (отпустить палец и нажать нужный пункт).
 */
@Composable
fun MessageActionOverlay(
    contextMenuData: ContextMenuData,
    currentDragOffset: Offset = Offset.Zero,
    isDragging: Boolean = false,
    isGestureMode: Boolean = false,
    canReact: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelSending: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)? = null,
    onReact: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onBlockUser: (() -> Unit)? = null,
) {
    // Закрытие меню по системной кнопке "Назад"
    BackHandler(enabled = true) {
        onDismiss()
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    val usageRankManager: UsageRankManager = koinInject()
    val rankedReactions by usageRankManager.rankedReactionsFlow.collectAsState()
    val isLiquidEnabled = rememberLiquidEnabled()
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val density = LocalDensity.current

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }

    val imeBottom = WindowInsets.ime.getBottom(density)
    val effectiveScreenHeightPx = screenHeightPx - imeBottom

    val tapX = contextMenuData.startOffset.x
    val tapY = contextMenuData.startOffset.y
    val isMine = contextMenuData.isMine

    // Выравнивание по стороне пузыря сообщения (входящие слева, исходящие справа)
    val alignRight = when {
        tapX > screenWidthPx * 0.55f -> true
        tapX < screenWidthPx * 0.45f -> false
        else -> isMine
    }

    // Текущая позиция пальца при жесте с удержанием
    val fingerPos = contextMenuData.startOffset + currentDragOffset

    // Безопасные отступы экрана
    val safeTop = with(density) { 56.dp.toPx() }
    val safeBottom = effectiveScreenHeightPx - with(density) { 56.dp.toPx() }

    // Положение контейнера относительно сообщения (сверху или снизу)
    val showAbove = tapY > (effectiveScreenHeightPx * 0.52f)

    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val estimatedHeightPx = with(density) { ((if (canReact) 56.dp + 10.dp else 0.dp) + 290.dp).toPx() }
    val actualOrEstimatedH = if (containerHeightPx > 0f) containerHeightPx else estimatedHeightPx
    val maxCardHeightDp = with(density) { maxOf(180.dp.toPx(), safeBottom - safeTop - 68.dp.toPx()).toDp() }

    val containerY = if (showAbove) {
        (tapY - actualOrEstimatedH - with(density) { 12.dp.toPx() })
            .coerceIn(safeTop, (safeBottom - actualOrEstimatedH).coerceAtLeast(safeTop))
    } else {
        (tapY + with(density) { 12.dp.toPx() })
            .coerceIn(safeTop, (safeBottom - actualOrEstimatedH).coerceAtLeast(safeTop))
    }

    // Отслеживание наведения при drag-жесте
    var hoveredReaction by remember { mutableStateOf<String?>(null) }
    var hoveredAction by remember { mutableStateOf<String?>(null) }

    // Тактильный щелчок при смене наведенной реакции
    var lastVibratedReaction by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoveredReaction) {
        if (hoveredReaction != null && hoveredReaction != lastVibratedReaction) {
            haptic.perform(HapticType.SELECTION, true)
            lastVibratedReaction = hoveredReaction
        } else if (hoveredReaction == null) {
            lastVibratedReaction = null
        }
    }

    // Функция быстрого копирования текста
    val copyTextAction: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = contextMenuData.message.text ?: contextMenuData.message.caption ?: ""
        clipboard.setPrimaryClip(ClipData.newPlainText("message", textToCopy))
        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    // Обработка отпускания пальца после перетаскивания (Hold & Drag)
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (wasDragging && !isDragging) {
            val selectedEmoji = hoveredReaction
            val selectedAction = hoveredAction
            if (selectedEmoji != null) {
                haptic.perform(HapticType.REACTION, true)
                onReact(selectedEmoji)
                onDismiss()
            } else if (selectedAction != null) {
                haptic.perform(HapticType.CLICK, true)
                when (selectedAction) {
                    "reply" -> onReply()
                    "edit" -> onEdit()
                    "copy" -> copyTextAction()
                    "forward" -> onForward?.invoke()
                    "save_image" -> onSaveImage()
                    "open_image" -> onOpenImage()
                    "save_voice" -> onSaveVoice()
                    "retry" -> onRetry?.invoke()
                    "cancel_sending" -> onCancelSending()
                    "delete" -> onDelete()
                    "report" -> onReport?.invoke()
                    "block" -> onBlockUser?.invoke()
                    "cancel" -> { /* onDismiss() вызывается ниже */ }
                }
                onDismiss()
            }
        }
        wasDragging = isDragging
    }

    // Анимация плавного затемнения фона
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val bgAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isLiquidEnabled) spring(dampingRatio = 0.85f, stiffness = 420f) else tween(200),
        label = "overlay_bg"
    )

    val popProgress = rememberLiquidPopProgress(isLiquidEnabled)
    val popOrigin = TransformOrigin(
        pivotFractionX = if (alignRight) 0.85f else 0.15f,
        pivotFractionY = if (showAbove) 1f else 0f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * bgAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
    ) {
        // Единый вертикальный контейнер меню, исключающий наложение карточки на капсулу
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, containerY.toInt()) }
                .liquidPopIn(popProgress, isLiquidEnabled, popOrigin)
                .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() },
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showAbove) {
                // Если меню над сообщением: карточка действий сверху, капсула реакций снизу (ближе к пальцу/пузырю)
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .heightIn(max = maxCardHeightDp)
                ) {
                    ActionGlassCard(
                        message = contextMenuData.message,
                        isMine = contextMenuData.isMine,
                        fingerPos = fingerPos,
                        isDragging = isDragging,
                        hoveredAction = hoveredAction,
                        onHoverActionChanged = { hoveredAction = it },
                        onReply = onReply,
                        onEdit = onEdit,
                        onCopy = copyTextAction,
                        onDelete = onDelete,
                        onCancelSending = onCancelSending,
                        onSaveImage = onSaveImage,
                        onSaveVoice = onSaveVoice,
                        onOpenImage = onOpenImage,
                        onForward = onForward,
                        onRetry = onRetry,
                        onReport = onReport,
                        onBlockUser = onBlockUser,
                        onDismiss = onDismiss
                    )
                }

                if (canReact && !contextMenuData.message.deleted) {
                    LiquidReactionCapsule(
                        rankedReactions = rankedReactions.ifEmpty { QUICK_REACTIONS },
                        currentUid = currentUid,
                        existingReactions = contextMenuData.message.parsedReactions,
                        isDragging = isDragging,
                        fingerPos = fingerPos,
                        isLiquidEnabled = isLiquidEnabled,
                        onHoverReactionChanged = { hoveredReaction = it },
                        onSelectReaction = { emoji ->
                            haptic.perform(HapticType.REACTION, true)
                            onReact(emoji)
                            onDismiss()
                        }
                    )
                }
            } else {
                // Если меню под сообщением: капсула реакций сверху (ближе к сообщению), карточка действий снизу
                if (canReact && !contextMenuData.message.deleted) {
                    LiquidReactionCapsule(
                        rankedReactions = rankedReactions.ifEmpty { QUICK_REACTIONS },
                        currentUid = currentUid,
                        existingReactions = contextMenuData.message.parsedReactions,
                        isDragging = isDragging,
                        fingerPos = fingerPos,
                        isLiquidEnabled = isLiquidEnabled,
                        onHoverReactionChanged = { hoveredReaction = it },
                        onSelectReaction = { emoji ->
                            haptic.perform(HapticType.REACTION, true)
                            onReact(emoji)
                            onDismiss()
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .heightIn(max = maxCardHeightDp)
                ) {
                    ActionGlassCard(
                        message = contextMenuData.message,
                        isMine = contextMenuData.isMine,
                        fingerPos = fingerPos,
                        isDragging = isDragging,
                        hoveredAction = hoveredAction,
                        onHoverActionChanged = { hoveredAction = it },
                        onReply = onReply,
                        onEdit = onEdit,
                        onCopy = copyTextAction,
                        onDelete = onDelete,
                        onCancelSending = onCancelSending,
                        onSaveImage = onSaveImage,
                        onSaveVoice = onSaveVoice,
                        onOpenImage = onOpenImage,
                        onForward = onForward,
                        onRetry = onRetry,
                        onReport = onReport,
                        onBlockUser = onBlockUser,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * Парящая капсула быстрых реакций с эффектом линзы дока (Dock Magnification).
 * При перетаскивании пальца ближайший смайлик увеличивается с эффектом Squash & Stretch.
 */
@Composable
private fun LiquidReactionCapsule(
    rankedReactions: List<String>,
    currentUid: String,
    existingReactions: List<Reaction>,
    isDragging: Boolean,
    fingerPos: Offset,
    isLiquidEnabled: Boolean,
    onHoverReactionChanged: (String?) -> Unit,
    onSelectReaction: (String) -> Unit
) {
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val emojiBounds = remember { mutableStateMapOf<String, Rect>() }
    var showAllReactions by remember { mutableStateOf(false) }

    // Расчет наведения при перетаскивании
    val currentHovered = remember(fingerPos, isDragging, emojiBounds.toMap()) {
        if (!isDragging) null
        else {
            val hitRadius = with(density) { 56.dp.toPx() }
            emojiBounds.entries
                .map { it.key to (fingerPos - it.value.center).getDistanceVector() }
                .filter { it.second < hitRadius }
                .minByOrNull { it.second }
                ?.first
        }
    }

    LaunchedEffect(currentHovered) {
        onHoverReactionChanged(currentHovered)
    }

    Column {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cs.surface.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Отображаем первые 7 реакций
                val visibleReactions = rankedReactions.take(7)
                visibleReactions.forEach { emoji ->
                    val isUserReacted = existingReactions.any { it.emoji == emoji && it.uids.contains(currentUid) }
                    val center = emojiBounds[emoji]?.center
                    val dist = if (isDragging && center != null) (fingerPos - center).getDistanceVector() else Float.MAX_VALUE
                    val maxRadius = with(density) { 72.dp.toPx() }
                    val proximity = if (dist < maxRadius) (1f - dist / maxRadius).coerceIn(0f, 1f) else 0f

                    val targetScale = 1.0f + (if (isLiquidEnabled) 0.45f else 0.25f) * (proximity * proximity)
                    val targetSquashX = if (isLiquidEnabled) 1.0f + 0.08f * proximity else 1.0f
                    val targetSquashY = if (isLiquidEnabled) 1.0f - 0.08f * proximity else 1.0f

                    val animScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
                        label = "dock_scale_$emoji"
                    )
                    val animSquashX by animateFloatAsState(
                        targetValue = targetSquashX,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
                        label = "dock_squash_x_$emoji"
                    )
                    val animSquashY by animateFloatAsState(
                        targetValue = targetSquashY,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
                        label = "dock_squash_y_$emoji"
                    )
                    val liftY = if (isLiquidEnabled) -(animScale - 1f) * with(density) { 16.dp.toPx() } else 0f

                    EmojiDockItem(
                        emoji = emoji,
                        scale = animScale,
                        squashX = animSquashX,
                        squashY = animSquashY,
                        translationY = liftY,
                        isUserReacted = isUserReacted,
                        onPositioned = { bounds -> emojiBounds[emoji] = bounds },
                        onClick = { onSelectReaction(emoji) }
                    )
                }

                // Кнопка "+" для показа всех реакций
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (showAllReactions) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { showAllReactions = !showAllReactions },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showAllReactions) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Show more reactions",
                        tint = if (showAllReactions) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Выпадающая сетка дополнительных реакций при нажатии "+"
        AnimatedVisibility(
            visible = showAllReactions,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = VlTheme.tokens.shapes.card,
                color = cs.surface.copy(alpha = 0.96f),
                tonalElevation = 10.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(280.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    val remainingReactions = rankedReactions.drop(7).ifEmpty { QUICK_REACTIONS.drop(7) }
                    remainingReactions.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row.forEach { emoji ->
                                val isUserReacted = existingReactions.any { it.emoji == emoji && it.uids.contains(currentUid) }
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isUserReacted) cs.primaryContainer else Color.Transparent)
                                        .clickable { onSelectReaction(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Отдельный элемент эмодзи в парящей капсуле с поддержкой анимации желейного отскока.
 */
@Composable
private fun EmojiDockItem(
    emoji: String,
    scale: Float,
    squashX: Float,
    squashY: Float,
    translationY: Float,
    isUserReacted: Boolean,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clickBounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(40.dp)
            .onGloballyPositioned { onPositioned(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = scale * squashX * clickBounce.value
                scaleY = scale * squashY * clickBounce.value
                this.translationY = translationY
            }
            .clip(CircleShape)
            .background(if (isUserReacted) cs.primaryContainer.copy(alpha = 0.85f) else Color.Transparent)
            .clickable {
                scope.launch {
                    clickBounce.animateTo(0.75f, tween(70))
                    clickBounce.animateTo(1.25f, spring(dampingRatio = 0.45f, stiffness = 600f))
                    clickBounce.animateTo(1.0f, tween(90))
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            modifier = Modifier.scale(if (isUserReacted) 1.05f else 1.0f)
        )
    }
}

private data class MenuActionItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Компактная неоморфическая карточка контекстных действий над сообщением.
 * Форма крайних кнопок строго повторяет скругления окна (`cardRadius`),
 * а внизу меню всегда присутствует кнопка «Отмена».
 */
@Composable
private fun ActionGlassCard(
    message: Message,
    isMine: Boolean,
    fingerPos: Offset,
    isDragging: Boolean,
    hoveredAction: String?,
    onHoverActionChanged: (String?) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onCancelSending: () -> Unit,
    onSaveImage: () -> Unit,
    onSaveVoice: () -> Unit,
    onOpenImage: () -> Unit,
    onForward: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onBlockUser: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val actionBounds = remember { mutableStateMapOf<String, Rect>() }

    // Отслеживание наведения пальца при перетаскивании вниз на пункты меню
    val currentHovered = remember(fingerPos, isDragging, actionBounds.toMap()) {
        if (!isDragging) null
        else {
            actionBounds.entries.firstOrNull { it.value.contains(fingerPos) }?.key
        }
    }

    LaunchedEffect(currentHovered) {
        onHoverActionChanged(currentHovered)
    }

    val isSending = message.status == SendStatus.SENDING || 
                    message.status == SendStatus.QUEUED || 
                    message.status == SendStatus.ERROR

    val canEdit = isMine && !message.deleted && !isSending &&
            (message.createdAt?.toDate()?.time ?: 0L) > System.currentTimeMillis() - 30 * 60 * 1000 &&
            (message.type == MessageType.TEXT || message.caption != null)

    // Формируем полный структурированный список всех доступных действий
    val actionList = buildList {
        if (isSending) {
            if (message.status == SendStatus.ERROR && onRetry != null) {
                add(MenuActionItem("retry", Icons.Default.Refresh, stringResource(R.string.action_retry_send)) {
                    onDismiss(); onRetry()
                })
            }
            add(MenuActionItem("cancel_sending", Icons.Default.Close, stringResource(R.string.action_cancel_send), destructive = true) {
                onDismiss(); onCancelSending()
            })
            if (message.type == MessageType.TEXT || !message.text.isNullOrEmpty()) {
                add(MenuActionItem("copy", Icons.Default.ContentCopy, stringResource(R.string.action_copy_text)) {
                    onCopy()
                })
            }
        } else {
            if (!message.deleted) {
                add(MenuActionItem("reply", Icons.AutoMirrored.Filled.Reply, stringResource(R.string.action_reply)) {
                    onDismiss(); onReply()
                })

                if (canEdit) {
                    add(MenuActionItem("edit", Icons.Default.Edit, stringResource(R.string.action_edit)) {
                        onDismiss(); onEdit()
                    })
                }

                if (message.type == MessageType.TEXT || !message.text.isNullOrEmpty() || !message.caption.isNullOrEmpty()) {
                    add(MenuActionItem("copy", Icons.Default.ContentCopy, stringResource(R.string.action_copy_text)) {
                        onCopy()
                    })
                }

                if (message.type == MessageType.IMAGE) {
                    add(MenuActionItem("open_image", Icons.Default.ZoomIn, stringResource(R.string.action_view_image)) {
                        onDismiss(); onOpenImage()
                    })
                    add(MenuActionItem("save_image", Icons.Default.Download, stringResource(R.string.action_save_gallery)) {
                        onDismiss(); onSaveImage()
                    })
                }

                if (message.type == MessageType.VOICE) {
                    add(MenuActionItem("save_voice", Icons.Default.Download, stringResource(R.string.action_save_voice)) {
                        onDismiss(); onSaveVoice()
                    })
                }

                if (onForward != null) {
                    add(MenuActionItem("forward", Icons.AutoMirrored.Filled.Forward, stringResource(R.string.action_forward)) {
                        onDismiss(); onForward()
                    })
                }
            }

            if (isMine && !message.deleted) {
                add(MenuActionItem("delete", Icons.Default.Delete, stringResource(R.string.action_delete_message), destructive = true) {
                    onDismiss(); onDelete()
                })
            }

            if (!isMine && !message.deleted) {
                if (onReport != null) {
                    add(MenuActionItem("report", Icons.Default.ReportProblem, stringResource(R.string.action_report)) {
                        onDismiss(); onReport()
                    })
                }
                if (onBlockUser != null) {
                    add(MenuActionItem("block", Icons.Default.Block, stringResource(R.string.action_block_user), destructive = true) {
                        onDismiss(); onBlockUser()
                    })
                }
            }
        }

        // Кнопка "Отмена" всегда замыкает меню действий
        add(MenuActionItem("cancel", Icons.Default.Close, stringResource(R.string.action_cancel)) {
            onDismiss()
        })
    }

    val cardRadius = VlTheme.tokens.shapes.cardRadius
    val windowShape = RoundedCornerShape(cardRadius)

    Surface(
        shape = windowShape,
        color = cs.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(windowShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(windowShape)
        ) {
            actionList.forEachIndexed { index, action ->
                val isFirst = index == 0
                val isLast = index == actionList.lastIndex
                val itemShape = when {
                    actionList.size == 1 -> RoundedCornerShape(cardRadius)
                    isFirst -> RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius, bottomStart = 0.dp, bottomEnd = 0.dp)
                    isLast -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = cardRadius, bottomEnd = cardRadius)
                    else -> RectangleShape
                }

                ActionRowItem(
                    icon = action.icon,
                    label = action.label,
                    destructive = action.destructive,
                    isHovered = hoveredAction == action.key,
                    shape = itemShape,
                    onPositioned = { actionBounds[action.key] = it },
                    onClick = action.onClick
                )

                if (!isLast) {
                    ActionRowDivider()
                }
            }
        }
    }
}

/**
 * Пункт списка действий с визуальной подсветкой и идеальным повторением формы окна (`shape`).
 */
@Composable
private fun ActionRowItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    isHovered: Boolean = false,
    shape: Shape = RectangleShape,
    onPositioned: (Rect) -> Unit,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val color = if (destructive) cs.error else cs.onSurface
    val bgColor = if (isHovered) {
        if (destructive) cs.errorContainer.copy(alpha = 0.35f) else cs.primaryContainer.copy(alpha = 0.35f)
    } else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPositioned(it.boundsInWindow()) }
            .clip(shape)
            .background(bgColor, shape)
            .clickable {
                haptic.perform(if (destructive) HapticType.LONG_PRESS else HapticType.CLICK, true)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1
        )
    }
}

/**
 * Тонкий аккуратный разделитель между пунктами контекстного меню.
 */
@Composable
private fun ActionRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

