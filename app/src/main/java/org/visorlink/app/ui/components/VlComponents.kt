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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
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
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
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
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { layoutResult.value = it; onTextLayout(it) },
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
    /** Цвет профиля PRO: «По умолчанию» тогда и есть он — кружок закрашен им, а не радугой. */
    defaultColor: Color? = null,
    onClick: () -> Unit
) {
    val isDefault = preset == ColorPreset.DEFAULT && defaultColor == null
    val color = (if (preset == ColorPreset.DEFAULT) defaultColor else preset.seedColor) ?: Color.Transparent
    val cs = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 320f),
        label = "scale"
    )

    val shape = VlTheme.tokens.shapes.adapt(CircleShape)

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
    val shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(radius))

    if (tokens.structure.enabled) {
        Box(
            modifier = modifier
                .vlRaised(tokens.structure, shape)
                .clip(shape)
                // §3.1: sheet/диалог живут на surfaceContainerHigh.
                .vlKeycapBackground(tokens.structure, cs.surfaceContainerHigh, shape, VlDepth.Raised)
                .vlHairline(cs.outlineVariant, shape)
        ) {
            content()
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = cs.surface.copy(alpha = if (simplifiedGraphics) 1f else 0.7f)
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
/** Форма, интерполирующая скругления между двумя CornerBasedShape (морф кнопки при нажатии). */
private class VlMorphShape(private val from: Shape, private val to: Shape, private val t: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (t <= 0.001f) return from.createOutline(size, layoutDirection, density)
        if (t >= 0.999f) return to.createOutline(size, layoutDirection, density)
        val a = from.createOutline(size, layoutDirection, density) as? Outline.Rounded
            ?: return if (t < 0.5f) from.createOutline(size, layoutDirection, density) else to.createOutline(size, layoutDirection, density)
        val b = to.createOutline(size, layoutDirection, density) as? Outline.Rounded
            ?: return if (t < 0.5f) from.createOutline(size, layoutDirection, density) else to.createOutline(size, layoutDirection, density)
        fun lerpRadius(p: CornerRadius, q: CornerRadius) = CornerRadius(
            p.x + (q.x - p.x) * t,
            p.y + (q.y - p.y) * t,
        )
        return Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = lerpRadius(a.roundRect.topLeftCornerRadius, b.roundRect.topLeftCornerRadius),
                topRight = lerpRadius(a.roundRect.topRightCornerRadius, b.roundRect.topRightCornerRadius),
                bottomRight = lerpRadius(a.roundRect.bottomRightCornerRadius, b.roundRect.bottomRightCornerRadius),
                bottomLeft = lerpRadius(a.roundRect.bottomLeftCornerRadius, b.roundRect.bottomLeftCornerRadius),
            )
        )
    }
}

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
    val tt = MaterialTheme.typography
    val tokens = VlTheme.tokens

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = if (isDestructive) cs.error else cs.primary
    val onContainer = if (isDestructive) cs.onError else cs.onPrimary

    val keycap = tokens.structure.keycap
    val restGlow = keycap && tokens.signal.buttonRestAlpha > 0f
    val m3 = !tokens.structure.enabled

    // Плавный прогресс нажатия: морф формы stadium→buttonPressed, сжатие, свечение и тень
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 480f),
        label = "vlbutton_press",
    )
    val animatedShape = VlMorphShape(tokens.shapes.button, tokens.shapes.buttonPressed, press)
    val keycapDepth = if (press > 0.5f) VlDepth.Inset else VlDepth.Raised

    // Сжатие: M3E pressScale из токенов, в остальных темах — лёгкие 0.97
    val pressScale = if (tokens.motion.pressStyle == VlPressStyle.SCALE) tokens.motion.pressScale else 0.97f
    val scale = 1f - (1f - pressScale) * press

    // Свечение: плавный разгон от покоя к нажатию и обратно
    val glowA by animateFloatAsState(
        targetValue = if (isPressed) tokens.signal.glowAlpha else tokens.signal.buttonRestAlpha,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "vlbutton_glow",
    )
    val glowActive = enabled && (isPressed || restGlow || glowA > 0.005f)

    val fill = if (enabled) container else cs.onSurface.copy(alpha = 0.12f)
    val fg = if (enabled) onContainer else cs.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // Тень покоя: чёрная 18%, offset (0,1); Biolume blur 4, M3E blur 2 (F vl_button.dart:48-50)
            // — тает по мере нажатия
            .drawBehind {
                if ((m3 || tokens.isBiolume) && enabled && press < 0.999f) {
                    val ds = this
                    val path = Path().apply { addOutline(animatedShape.createOutline(size, layoutDirection, ds)) }
                    drawSoftShadow(
                        path = path,
                        color = Color.Black.copy(alpha = 0.18f * (1f - press)),
                        blurPx = (if (m3) 2.dp else 4.dp).toPx(),
                        dx = 0f,
                        dy = 1.dp.toPx(),
                    )
                }
            }
            .vlSignalGlow(
                tokens = tokens.signal,
                color = container,
                shape = animatedShape,
                active = glowActive,
                alphaOverride = if (isPressed) null else glowA,
                counted = isPressed,
            )
            .vlSignalBorder(
                tokens = tokens.signal,
                color = onContainer,
                shape = animatedShape,
                active = enabled && isFocused,
            )
            .then(
                if (keycap && enabled) {
                    Modifier.vlStructure(tokens.structure, keycapDepth, animatedShape)
                } else {
                    Modifier
                }
            )
            .vlKeycapBackground(
                tokens.structure,
                fill,
                animatedShape,
                if (keycap && enabled) keycapDepth else VlDepth.Flat,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides (tt.labelLarge ?: TextStyle.Default).copy(color = fg, fontWeight = FontWeight.Bold),
        ) {
            content()
        }
    }
}

// ── VlEntrance ───────────────────────────────────────────────────────────────

/**
 * Появление с задержкой: въезд снизу на [offsetDp] + fade + лёгкий scale
 * (порт Flutter `VlEntrance`, `vl_motion.dart:293-350`). При reduce-motion —
 * контент без анимации.
 */
@Composable
fun VlEntrance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    offsetDp: Dp = 18.dp,
    durationMs: Int = 300,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = VlTheme.tokens
    if (!enabled || tokens.reduceMotion) {
        Box(modifier) { content() }
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetPx = with(density) { offsetDp.toPx() }
    // Ход въезда стартует после задержки; fade занимает 70 % оставшегося пути
    val move = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        move.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = EaseOutCubic))
        if (fade.value < 1f) fade.snapTo(1f)
    }
    LaunchedEffect(enabled) {
        fade.animateTo(1f, tween((durationMs * 0.7f).toInt(), delayMillis = delayMs, easing = EaseOut))
    }
    Box(
        modifier = modifier.graphicsLayer {
            translationY = (1f - move.value) * offsetPx
            scaleX = 0.97f + 0.03f * move.value
            scaleY = 0.97f + 0.03f * move.value
            alpha = fade.value
        },
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

    val cs = MaterialTheme.colorScheme
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val sw = tokens.switch
    val trackShape = sw.track ?: tokens.shapes.pill
    val thumbShape = sw.thumb ?: tokens.shapes.indicator

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val structure = tokens.structure
    // M3E (структура выключена) рисуется по спецификации M3 Switch: включённый —
    // трек primary и бегунок onPrimary, выключенный — контурный трек и маленький
    // бегунок outline. Раньше выключенный тёмный бегунок на светлом треке
    // читался как «включено».
    val m3Style = !structure.enabled
    val thumbWidthTarget = if (m3Style && !checked) 16.dp else sw.thumbWidth
    val thumbHeightTarget = if (m3Style && !checked) 16.dp else sw.thumbHeight
    val thumbWidth by animateDpAsState(thumbWidthTarget, tokens.motion.motionSpec<Dp>(), label = "vlswitch_thumb_w")
    val thumbHeight by animateDpAsState(thumbHeightTarget, tokens.motion.motionSpec<Dp>(), label = "vlswitch_thumb_h")
    // Зазор до края трека одинаковый со всех сторон: (высота трека − высота бегунка) / 2
    val inset = (trackHeight - sw.thumbHeight) / 2

    // Ход бегунка как во Flutter: пружинным темам — easeOutBack 220 мс, Forge — ход клавиши 160 мс
    val moveDuration = if (tokens.motion.useSpring) 220 else tokens.motion.durationMs
    val moveEasing = if (tokens.motion.useSpring) EaseOutBack else VlCurves.Key
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - sw.thumbWidth - inset else (trackHeight - thumbHeightTarget) / 2,
        animationSpec = tween(durationMillis = moveDuration, easing = moveEasing),
        label = "vlswitch_thumb",
    )
    val thumbColor by animateColorAsState(
        targetValue = sw.thumbColor ?: when {
            m3Style && checked -> cs.onPrimary
            m3Style -> cs.outline
            checked -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = VlTheme.tokens.motion.motionSpec<Color>(),
        label = "vlswitch_color",
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(trackShape)
            .background(
                when {
                    checked && sw.trackOnColor != null -> sw.trackOnColor!!
                    m3Style && checked -> cs.primary
                    m3Style -> cs.surfaceContainerHighest
                    checked -> cs.primaryContainer
                    else -> cs.surfaceContainer
                },
                trackShape,
            )
            .vlInset(structure, trackShape)
            .then(
                if (m3Style && !checked) Modifier.border(2.dp, cs.outline, trackShape) else Modifier
            )
            // Фокус-контур на треке (F vl_controls.dart:60 signalBorder)
            .vlSignalBorder(
                tokens = tokens.signal,
                color = cs.primary,
                shape = trackShape,
                active = isFocused,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptic.perform(HapticType.SELECTION, hapticEnabled)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val led = if (sw.ledDots) tokens.led else null
        if (led != null) {
            val dotsColor = if (checked) led.color else led.off
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(dotsColor, CircleShape)
                            .then(
                                if (checked && led.glowBlur > 0.dp) {
                                    Modifier.vlSignalGlow(
                                        tokens = tokens.signal,
                                        color = led.color,
                                        shape = CircleShape,
                                        active = true,
                                        counted = false,
                                    )
                                } else Modifier
                            )
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = thumbWidth, height = thumbHeight)
                .vlSignalGlow(
                    tokens = tokens.signal,
                    color = thumbColor,
                    shape = thumbShape,
                    active = checked && sw.thumbGlow > 0f,
                    alphaOverride = sw.thumbGlow,
                    counted = false,
                )
                .vlRaised(structure, thumbShape)
                .clip(thumbShape)
                .vlKeycapBackground(structure, thumbColor, thumbShape, VlDepth.Raised)
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
    enabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.pill
    val itemShape: Shape = tokens.shapes.pill

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .background(cs.surfaceContainerLow, shape)
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val isSelected = selectedIndex == i
                val itemSource = remember { MutableInteractionSource() }
                val itemFocused by itemSource.collectIsFocusedAsState()
                // Смена фона — линейный tween хода темы, без пружинного перелёта (F AnimatedContainer)
                val bgDuration = if (tokens.motion.useSpring) 220 else tokens.motion.durationMs
                val targetBg = if (isSelected) tokens.selectionFill else Color.Transparent
                val bgColor by animateColorAsState(
                    targetBg,
                    tween(durationMillis = bgDuration, easing = LinearEasing),
                    label = "seg_bg",
                )
                val textColor by animateColorAsState(
                    if (isSelected) cs.primary else cs.onSurfaceVariant, VlTheme.tokens.motion.motionSpec<Color>(), label = "seg_txt"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bgColor, itemShape)
                        .then(
                            if (tokens.structure.enabled && isSelected) {
                                Modifier.vlInset(tokens.structure, itemShape)
                            } else {
                                Modifier
                            }
                        )
                        // Фокус-контур выбранного сегмента (F vl_controls.dart:172)
                        .vlSignalBorder(
                            tokens = tokens.signal,
                            color = cs.primary,
                            shape = itemShape,
                            active = itemFocused,
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = itemSource, indication = null
                        ) {
                            if (i != selectedIndex) {
                                haptic.perform(HapticType.SELECTION, hapticEnabled)
                            }
                            onSelected(i)
                        }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Forge: у выбранной клавиши горит светодиод
                    if (isSelected && tokens.led != null) {
                        org.visorlink.app.ui.components.led.VlLedDot(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-8).dp),
                            size = 4.dp,
                            color = tokens.led!!.color,
                        )
                    }
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ── VlIconTray ───────────────────────────────────────────────────────────

/** §4.1: иконка-кнопка «нажимает» → в Biolume утапливается (F vl_surface.dart:143-174). */
@Composable
fun VlIconTray(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selected: Boolean = false,
    isError: Boolean = false,
    iconColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val color = iconColor ?: if (isError) cs.error else if (selected) cs.primary else cs.onSurfaceVariant
    val size = 40.dp
    val shape = tokens.shapes.indicator

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val depth = when {
        !tokens.structure.enabled -> VlDepth.Flat
        onClick != null && isPressed -> VlDepth.Inset
        else -> VlDepth.Raised
    }
    val trayFill = if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerHigh
    val tray: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .vlStructure(
                    tokens = tokens.structure,
                    depth = depth,
                    shape = shape,
                )
                .clip(shape)
                .vlKeycapBackground(tokens.structure, trayFill, shape, depth),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(20.dp))
        }
    }
    if (onClick == null) {
        Box(modifier = modifier) { tray() }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) { tray() }
    }
}

// ── VlTapFeedback ─────────────────────────────────────────────────────────────

@Composable
fun VlTapFeedback(
    onClick: (() -> Unit)?,
    tintColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(0.dp)),
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
            val tokens = VlTheme.tokens
            val led = tokens.led
            if (tokens.isForge) {
                org.visorlink.app.ui.components.led.VlLedDot(
                    size = 5.dp,
                    color = if (isPremium) Color(0xFFC5A059) else (led?.color ?: cs.primary),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    title.uppercase(),
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    title,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (isPremium) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFC5A059),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        val tokens = VlTheme.tokens
        val sectionShape = tokens.shapes.rounded(32.dp)
        val isDark = cs.surface.luminance() < 0.5f

        val cardBrush = remember(isDark, cs) {
            val top = if (isDark) cs.surfaceContainer.copy(alpha = 0.95f) else cs.surfaceContainerLow.copy(alpha = 0.98f)
            val bottom = if (isDark) cs.surfaceContainerLow.copy(alpha = 0.88f) else cs.surfaceContainer.copy(alpha = 0.92f)
            Brush.verticalGradient(listOf(top, bottom))
        }
        val cardBorder = remember(isDark, cs) {
            val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.60f)
            val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
            BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
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
                .background(cardBrush, sectionShape)
                .border(cardBorder, sectionShape)
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
    val tokens = VlTheme.tokens
    val iconShape = tokens.shapes.rounded(14.dp)

    val color = iconColor ?: if (isDestructive) cs.error else cs.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Нажатая/фокусная строка подсвечивается заливкой, без ряби (F vl_settings.dart:124)
            .background(if (isPressed || isFocused) cs.onSurface.copy(alpha = 0.06f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null || onLongClick != null,
                onClick = { if (onClick != null) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() } },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                        action()
                    }
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Forge: градиентный значок; Biolume/M3E — иконка в плашке 42×42 (F vl_settings.dart:130-136)
            if (tokens.isForge) {
                VlGlyph(
                    icon = icon,
                    color = iconColor,
                    destructive = isDestructive,
                    size = 42.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(iconShape)
                        .background(color.copy(alpha = 0.16f), iconShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
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
    val tokens = VlTheme.tokens
    val iconShape = tokens.shapes.rounded(14.dp)

    // Концентричность: строка отступает от края секции на 8dp, поэтому её радиус =
    // радиус секции (32 в «жидком» режиме, 24 иначе) минус этот отступ
    val rowShape: Shape = tokens.shapes.rounded(24.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Фон мгновенный: выбранная — primary 15 %, нажатая — onSurface 5 % (F vl_settings.dart:184)
    val bgColor = when {
        selected -> cs.primary.copy(alpha = 0.15f)
        isPressed -> cs.onSurface.copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    val rowBorder = if (selected) {
        BorderStroke(1.dp, cs.primary.copy(alpha = 0.30f))
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Крайние строки группы отступают от края секции на те же 8dp, что и по бокам:
            // иначе у угла отступ 8×3 и дуга строки не идёт параллельно дуге секции
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (total > 1 && index == 0) 8.dp else 3.dp,
                bottom = if (total > 1 && index == total - 1) 8.dp else 3.dp,
            )
            .clip(rowShape)
            .background(bgColor, rowShape)
            .then(if (rowBorder != null) Modifier.border(rowBorder, rowShape) else Modifier)
            .vlSignalBorder(
                tokens = tokens.signal,
                color = cs.primary,
                shape = rowShape,
                active = isFocused,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Forge: градиентный значок; Biolume/M3E — плашка (F vl_settings.dart:191-201)
            if (tokens.isForge) {
                VlGlyph(
                    icon = icon,
                    muted = !selected,
                    size = 42.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(iconShape)
                        .background(
                            if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHigh,
                            iconShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
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
            // Галочка вырастает из нуля (F vl_settings.dart:212-217: AnimatedScale easeOutBack)
            val checkDuration = if (tokens.motion.useSpring) 220 else tokens.motion.durationMs
            val checkEasing = if (tokens.motion.useSpring) EaseOutBack else VlCurves.Key
            val checkScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(durationMillis = checkDuration, easing = checkEasing),
                label = "option_row_check",
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = cs.primary,
                modifier = Modifier
                    .alpha(if (checkScale > 0.01f) 1f else 0f)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    },
            )
        }
    }
}

// ── ProBadge ─────────────────────────────────────────────────────────────────

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .wrapContentSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFF39C12))),
                VlTheme.tokens.shapes.adapt(RoundedCornerShape(6.dp))
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "PRO",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
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
                VlTheme.tokens.shapes.adapt(RoundedCornerShape(6.dp))
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
