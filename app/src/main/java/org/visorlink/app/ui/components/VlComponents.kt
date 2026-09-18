package org.visorlink.app.ui.components

import android.os.Build
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.visorlink.app.data.model.UserProfile

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.text.TextStyle

@Composable
fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    onMentionClick: (String) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotatedString = remember(text, linkColor) {
        org.visorlink.app.utils.MarkdownTextParser.parse(text, linkColor)
    }

    Text(
        text = annotatedString,
        color = color,
        style = style,
        textAlign = textAlign,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layout ->
                    if (pos.x >= 0 && pos.x <= layout.size.width && pos.y >= 0 && pos.y <= layout.size.height) {
                        val offset = layout.getOffsetForPosition(pos)
                        annotatedString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                                return@detectTapGestures
                            }
                        annotatedString.getStringAnnotations("MENTION", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                onMentionClick(annotation.item.removePrefix("@"))
                                return@detectTapGestures
                            }
                    }
                }
            }
        },
    )
}

@Composable
fun ColorPresetCircle(
    preset: ColorPreset,
    isSelected: Boolean,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val isDefault = preset == ColorPreset.DEFAULT
    val color = preset.seedColor ?: Color.Transparent
    val cs = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isLiquidEnabled = rememberLiquidEnabled()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.25f else 1f,
        animationSpec = if (isLiquidEnabled) spring(dampingRatio = 0.58f, stiffness = 320f) else VlTheme.tokens.motion.motionSpec<Float>(),
        label = "scale"
    )

    val shape = CircleShape

    val bgModifier = if (isDefault) {
        Modifier.background(Brush.sweepGradient(listOf(Color.Blue, Color.Magenta, Color.Red, Color(0xFFFFA500), Color.Blue)), shape)
    } else {
        Modifier.background(color, shape)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .then(bgModifier)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) cs.onSurface else cs.outlineVariant.copy(alpha = 0.3f),
                shape = shape
            )
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDefault) Icon(Icons.Default.Palette, contentDescription = "Default theme palette", tint = Color.White, modifier = Modifier.size(20.dp))
        else if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected preset", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// VlAmbientGlow переехал в VlAmbientGlow.kt — там же добавлено отключение в
// Biolume (§10) и уважение системного отключения анимаций. Дубль удалён:
// два перегруженных объявления в одном пакете резолвились непредсказуемо.

// ── VlGlassPanel ─────────────────────────────────────────────────────────────

/**
 * Всплывающая панель. В Biolume «стекла» нет как приёма (§0: язык строится на
 * материале и рельефе), поэтому панель непрозрачная и приподнятая; в MATERIAL3
 * сохраняется прежняя полупрозрачная подача.
 */
@Composable
fun VlGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    simplifiedGraphics: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)

    if (tokens.structure.enabled) {
        Box(
            modifier = modifier
                .vlRaised(tokens.structure, shape)
                .clip(shape)
                // §3.1: sheet/диалог живут на surfaceContainerHigh.
                .background(cs.surfaceContainerHigh, shape)
                .vlHairline(cs.outlineVariant, shape)
        ) {
            content()
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = cs.surface.copy(alpha = if (simplifiedGraphics) 1f else 0.7f),
        tonalElevation = 2.dp
    ) {
        content()
    }
}

// ── VlButton — главный CTA (filled) ─────────────────────────────────────────

/**
 * Гайдлайн §7: заливка `primary`, форма stadium, обычная (не цветная) тень в
 * покое; `glowPrimary` — ТОЛЬКО на время нажатия, и радиус при нажатии слегка
 * уменьшается (shape-morph — паттерн M3E, §2).
 *
 * В MATERIAL3 поведение прежнее: скругление 16dp, без свечения и без морфинга.
 */
@Composable
fun VlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    hapticEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val container = if (isDestructive) cs.error else cs.primary
    val onContainer = if (isDestructive) cs.onError else cs.onPrimary

    val shape: Shape = if (isPressed) tokens.shapes.buttonPressed else tokens.shapes.button

    Button(
        onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            // Свечение — ответ на действие, а не константа (§4.2).
            .vlSignalGlow(
                tokens = tokens.signal,
                color = container,
                shape = shape,
                active = isPressed && enabled,
            ),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        // Forge: плоская заливка без M3-elevation — объём даёт жёсткая тень.
        elevation = when {
            tokens.isForge -> ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            tokens.isBiolume -> ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
            else -> ButtonDefaults.buttonElevation()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = onContainer
        )
    ) {
        content()
    }
}

// ── VlSwitch ─────────────────────────────────────────────────────────────────

/**
 * §4.1: трек «принимает» → neumorphic-inset, thumb «нажимает» → raised.
 * Стандартный принцип soft UI, поэтому в Biolume собираем тумблер вручную —
 * M3 `Switch` не даёт задать рельеф трека и бегунка раздельно.
 */
@Composable
fun VlSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val tokens = VlTheme.tokens
    val isLiquidEnabled = rememberLiquidEnabled()

    if (!tokens.structure.enabled && !isLiquidEnabled) {
        Switch(
            checked = checked,
            onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(it) },
            modifier = modifier,
        )
        return
    }

    val cs = MaterialTheme.colorScheme
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val trackShape = tokens.shapes.pill

    val trackJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.65f)
    val thumbStretch = remember { Animatable(0f) }

    LaunchedEffect(checked) {
        if (isLiquidEnabled) {
            trackJelly.pulse(0.08f)
            // Фаза полёта: быстрое растяжение бегунка в каплю вдоль оси X
            thumbStretch.animateTo(0.26f, tween(65, easing = FastOutSlowInEasing))
            // Пружинный отскок с перелётом в сжатие (squash) и мягкой стабилизацией
            thumbStretch.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = 300f
                )
            )
        } else {
            thumbStretch.snapTo(0f)
        }
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - 4.dp else 4.dp,
        animationSpec = if (isLiquidEnabled) {
            spring(dampingRatio = 0.60f, stiffness = 340f)
        } else {
            tokens.motion.motionSpec<Dp>()
        },
        label = "vlswitch_thumb",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) cs.primary else cs.onSurfaceVariant,
        animationSpec = VlTheme.tokens.motion.motionSpec<Color>(),
        label = "vlswitch_color",
    )

    Box(
        modifier = modifier
            .liquidJelly(trackJelly, enabled = isLiquidEnabled)
            .size(width = trackWidth, height = trackHeight)
            .clip(trackShape)
            .background(if (checked) cs.primaryContainer else cs.surfaceContainer, trackShape)
            .vlInset(tokens.structure, trackShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.perform(HapticType.SELECTION, hapticEnabled)
                if (isLiquidEnabled) {
                    trackJelly.press(0.06f)
                }
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .graphicsLayer {
                    if (isLiquidEnabled) {
                        val s = thumbStretch.value
                        scaleX = 1f + s
                        scaleY = 1f - (s * 0.55f)
                    }
                }
                .vlRaised(tokens.structure, tokens.shapes.indicator)
                .clip(tokens.shapes.indicator)
                .background(thumbColor, tokens.shapes.indicator)
        )
    }
}

// ── VlSegmentedControl ───────────────────────────────────────────────────────

/**
 * §4.2 + §7: выбранный сегмент — плоская заливка `*Container` + neumorphic-inset,
 * БЕЗ свечения (выбор сигналится цветом и формой, а не glow). Сам контейнер
 * остаётся плоским, иначе рельеф контейнера и рельеф выбора спорят друг с другом.
 */
@Composable
fun VlSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = if (tokens.isForge) RoundedCornerShape(4.dp) else RoundedCornerShape(percent = 50)
    val itemShape: Shape = if (tokens.isForge) RoundedCornerShape(2.dp) else RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerLow, shape)
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val isSelected = selectedIndex == i
                val targetBg = when {
                    !isSelected -> Color.Transparent
                    // Плотная заливка: primaryContainer с alpha .12 на контейнере
                    // не читался, выбор выглядел как его отсутствие.
                    else -> tokens.selectionFill
                }
                val bgColor by animateColorAsState(targetBg, VlTheme.tokens.motion.motionSpec<Color>(), label = "seg_bg")
                val textColor by animateColorAsState(
                    if (isSelected) cs.primary else cs.onSurfaceVariant, VlTheme.tokens.motion.motionSpec<Color>(), label = "seg_txt"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(itemShape)
                        .background(bgColor, itemShape)
                        .then(
                            if (tokens.structure.enabled && isSelected) {
                                Modifier.vlInset(tokens.structure, itemShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { haptic.perform(HapticType.SELECTION, hapticEnabled); onSelected(i) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ── VlIconTray ───────────────────────────────────────────────────────────

/** §4.1: иконка-кнопка «нажимает» → в Biolume приподнята. */
@Composable
fun VlIconTray(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selected: Boolean = false,
    isError: Boolean = false,
    iconColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val color = iconColor ?: if (isError) cs.error else if (selected) cs.primary else cs.onSurfaceVariant
    val size = 40.dp
    val shape = tokens.shapes.indicator

    Box(
        modifier = modifier
            .size(size)
            .vlStructure(
                tokens = tokens.structure,
                depth = if (tokens.structure.enabled) VlDepth.Raised else VlDepth.Flat,
                shape = shape,
            )
            .clip(shape)
            .background(if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerHigh, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(20.dp))
    }
}

// ── VlTapFeedback ─────────────────────────────────────────────────────────────

@Composable
fun VlTapFeedback(
    onClick: (() -> Unit)?,
    tintColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (isPressed) tintColor else tintColor.copy(alpha = 0f), VlTheme.tokens.motion.motionSpec<Color>(), label = "vltapfeedback_bg"
    )
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, VlTheme.tokens.motion.motionSpec<Float>(), label = "vltapfeedback_scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg, shape)
            .then(
                if (onClick != null) Modifier.combinedClickable(
                    interactionSource = interactionSource, indication = null, onClick = onClick,
                ) else Modifier
            )
    ) { content() }
}

// ── VlSettingsSection ────────────────────────────────────────────────────────

@Composable
fun VlSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val titleColor = if (isPremium) Color(0xFFC5A059) else cs.primary

    Column(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 26.dp, bottom = 8.dp, end = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp
            )
            if (isPremium) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFC5A059),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // §7: карточка = neumorphic-raised + нейтральная грань. Это контейнер всех
        // настроек, поэтому именно здесь рельеф даёт максимум читаемости структуры.
        val tokens = VlTheme.tokens
        val isLiquidEnabled = rememberLiquidEnabled()
        val sectionShape = if (isLiquidEnabled) RoundedCornerShape(32.dp) else RoundedCornerShape(24.dp)
        val isDark = cs.surface.luminance() < 0.5f

        val cardBrush = remember(isLiquidEnabled, isDark, cs) {
            if (isLiquidEnabled) {
                val top = if (isDark) cs.surfaceContainer.copy(alpha = 0.95f) else cs.surfaceContainerLow.copy(alpha = 0.98f)
                val bottom = if (isDark) cs.surfaceContainerLow.copy(alpha = 0.88f) else cs.surfaceContainer.copy(alpha = 0.92f)
                Brush.verticalGradient(listOf(top, bottom))
            } else null
        }
        val cardBorder = remember(isLiquidEnabled, isDark, cs) {
            if (isLiquidEnabled) {
                val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.60f)
                val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
                BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
            } else null
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, sectionShape)
                    else Modifier
                )
                .clip(sectionShape)
                .then(
                    if (cardBrush != null) Modifier.background(cardBrush, sectionShape)
                    else Modifier.background(
                        if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                        sectionShape,
                    )
                )
                .then(
                    if (cardBorder != null) Modifier.border(cardBorder, sectionShape)
                    else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, sectionShape)
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

// ── VlSettingsItem ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VlSettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    index: Int = 0,
    total: Int = 1,
    iconColor: Color? = null,
    hapticEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isLiquidEnabled = rememberLiquidEnabled()
    val iconShape = if (isLiquidEnabled) RoundedCornerShape(14.dp) else CircleShape

    val color = iconColor ?: if (isDestructive) cs.error else cs.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                enabled = onClick != null || onLongClick != null,
                onClick = { if (onClick != null) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() } },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        action()
                    }
                },
            ),
        color = Color.Transparent,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isLiquidEnabled) 15.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (isLiquidEnabled) 42.dp else 40.dp)
                    .background(color.copy(alpha = if (isLiquidEnabled) 0.16f else 0.15f), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(if (isLiquidEnabled) 22.dp else 20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (isDestructive) color else cs.onSurface)
                subtitle?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 13.sp) }
            }
            if (trailing != null) trailing()
            else if (onClick != null) Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open $title",
                tint = cs.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ── VlOptionRow ──────────────────────────────────────────────────────────────

@Composable
fun VlOptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
    index: Int = 0,
    total: Int = 1,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isLiquidEnabled = rememberLiquidEnabled()
    val iconShape = if (isLiquidEnabled) RoundedCornerShape(14.dp) else CircleShape

    val rowShape: Shape = if (isLiquidEnabled) {
        RoundedCornerShape(20.dp)
    } else {
        RoundedCornerShape(12.dp)
    }

    val rowJelly = rememberLiquidJellyState(softness = 0.06f, damping = 0.68f)

    val targetBgColor = if (selected) {
        if (isLiquidEnabled) cs.primary.copy(alpha = 0.15f) else cs.primaryContainer
    } else {
        Color.Transparent
    }
    val animatedBg by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 380f),
        label = "option_row_bg"
    )

    val rowBorder = if (selected && isLiquidEnabled) {
        BorderStroke(1.dp, cs.primary.copy(alpha = 0.30f))
    } else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .liquidJelly(rowJelly, enabled = isLiquidEnabled)
            .clip(rowShape)
            .then(if (rowBorder != null) Modifier.border(rowBorder, rowShape) else Modifier),
        shape = rowShape,
        onClick = {
            haptic.perform(HapticType.CLICK, hapticEnabled)
            if (isLiquidEnabled) rowJelly.pulse(0.06f)
            onClick()
        },
        color = animatedBg,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (isLiquidEnabled) 42.dp else 40.dp)
                    .background(
                        if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHigh,
                        iconShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(if (isLiquidEnabled) 22.dp else 20.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (selected) cs.primary else cs.onSurface
                )
                desc?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = if (selected) cs.primary.copy(alpha = 0.8f) else cs.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 340f)) + fadeIn(),
                exit = scaleOut(spring(dampingRatio = 0.7f, stiffness = 400f)) + fadeOut()
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = cs.primary
                )
            }
        }
    }
}

// ── ProBadge ─────────────────────────────────────────────────────────────────

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFF39C12))),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "PRO",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ── AvatarContent ────────────────────────────────────────────────────────────

@Composable
fun AvatarContent(user: UserProfile, size: Dp) {
    val name = user.displayName.ifEmpty { user.username }
    if (!user.avatarUrl.isNullOrEmpty()) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = "$name avatar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        val initial = name.firstOrNull()?.uppercase() ?: "?"
        Text(
            initial,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = (size.value * 0.4).sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── AdminBadge ───────────────────────────────────────────────────────────────

@Composable
fun AdminBadge() {
    Box(
        modifier = Modifier
            .background(
                Brush.linearGradient(listOf(Color(0xFFFF0055), Color(0xFFFF4B2B))),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin badge", tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "VISORLINK ADMIN",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}
