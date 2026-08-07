package by.iposdev.visorlink.ui.components

import android.os.Build
import android.util.Patterns
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.ui.theme.ExthruStyle
import by.iposdev.visorlink.ui.theme.accentGlowShadow
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.forgeNeuBrutalism
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.theme.nmRaisedShadow
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import by.iposdev.visorlink.data.model.UserProfile
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.TextStyle

// Провайдер состояния Haze для размытия заднего фона (определен 1 раз для всего приложения)
val LocalHazeState = compositionLocalOf { HazeState() }

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

    val (urlList, mentionList) = remember(text) {
        val urls = mutableListOf<Triple<String, Int, Int>>()
        val urlMatcher = Patterns.WEB_URL.matcher(text)
        while (urlMatcher.find()) {
            var url = urlMatcher.group() ?: continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            urls.add(Triple(url, urlMatcher.start(), urlMatcher.end()))
        }
        val mentions = mutableListOf<Triple<String, Int, Int>>()
        val mentionRegex = Regex("(?<!\\w)@[a-zA-Z0-9_]+")
        mentionRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val isInsideUrl = urls.any { start >= it.second && end <= it.third }
            if (!isInsideUrl) mentions.add(Triple(match.value, start, end))
        }
        Pair(urls, mentions)
    }

    val annotatedString = remember(text, color, linkColor) {
        buildAnnotatedString {
            append(text)
            urlList.forEach { (url, start, end) ->
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("URL", url, start, end)
            }
            mentionList.forEach { (mention, start, end) ->
                addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), start, end)
                addStringAnnotation("MENTION", mention, start, end)
            }
        }
    }

    Text(
        text = annotatedString,
        color = color,
        style = style,
        textAlign = textAlign,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var upEvent: PointerInputChange? = null
                var isTap = true

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (change.isConsumed) {
                        isTap = false
                    }

                    if (!change.pressed) {
                        upEvent = change
                        break
                    }
                }

                if (isTap && upEvent != null) {
                    val pos = upEvent.position
                    layoutResult.value?.let { layout ->
                        if (pos.x >= 0 && pos.x <= layout.size.width && pos.y >= 0 && pos.y <= layout.size.height) {
                            val offset = layout.getOffsetForPosition(pos)
                            annotatedString.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                                    upEvent.consume()
                                    return@awaitEachGesture
                                }
                            annotatedString.getStringAnnotations("MENTION", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    onMentionClick(annotation.item.removePrefix("@"))
                                    upEvent.consume()
                                    return@awaitEachGesture
                                }
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
    appTheme: AppTheme,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val isDefault = preset == ColorPreset.DEFAULT
    val color = preset.seedColor ?: Color.Transparent
    val cs = MaterialTheme.colorScheme

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val shape = if (isForge) RectangleShape else CircleShape

    val bgModifier = if (isDefault) {
        Modifier.background(Brush.sweepGradient(listOf(Color.Blue, Color.Magenta, Color.Red, Color(0xFFFFA500), Color.Blue)), shape)
    } else {
        Modifier.background(color, shape)
    }

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
    } else if (appTheme.isExthruFamily) {
        if (isSelected || isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
        else Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(if(isForge) 1f else scale)
            .then(shadowMod)
            .then(bgModifier)
            .border(
                width = if (isSelected && !appTheme.isExthruFamily) 3.dp else 1.dp,
                color = if (isSelected && !appTheme.isExthruFamily) cs.onSurface else if (appTheme.isExthruFamily) Color.White.copy(alpha = if (isDark) 0.05f else 0.3f) else cs.outlineVariant.copy(alpha = 0.3f),
                shape = shape
            )
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDefault) Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(20.dp))
        else if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun monoFamily(style: ExthruStyle): FontFamily? =
    if (style.isForge) FontFamily.Monospace else null

// ── VlAmbientGlow — Анимированное фоновое свечение ─────────────────────────

@Composable
fun VlAmbientGlow(
    appTheme: AppTheme,
    modifier: Modifier = Modifier,
    overrideAccent: Color? = null,
    simplifiedGraphics: Boolean = false
) {
    if (!appTheme.isExthruFamily || appTheme == AppTheme.FORGE || simplifiedGraphics) return

    val style = rememberExthruStyle(appTheme)
    val accent = overrideAccent ?: style.accent
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    // Значительно уменьшена интенсивность для светлой темы
    val c1 = accent.copy(alpha = if (isDark) 0.20f else 0.10f)
    val c2 = cs.tertiary.copy(alpha = if (isDark) 0.15f else 0.06f)
    val c3 = cs.secondary.copy(alpha = if (isDark) 0.15f else 0.06f)

    val infiniteTransition = rememberInfiniteTransition(label = "glow_mesh")

    val o1x by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 80f, animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "o1x")
    val o1y by infiniteTransition.animateFloat(initialValue = -50f, targetValue = 30f, animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "o1y")

    val o2x by infiniteTransition.animateFloat(initialValue = -40f, targetValue = 40f, animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Reverse), label = "o2x")
    val o2y by infiniteTransition.animateFloat(initialValue = 60f, targetValue = -20f, animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "o2y")

    val o3x by infiniteTransition.animateFloat(initialValue = -30f, targetValue = 60f, animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Reverse), label = "o3x")
    val o3y by infiniteTransition.animateFloat(initialValue = -30f, targetValue = 50f, animationSpec = infiniteRepeatable(tween(8500, easing = LinearEasing), RepeatMode.Reverse), label = "o3y")

    Box(modifier = modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = o1x.dp, y = o1y.dp).size(350.dp).background(Brush.radialGradient(listOf(c1, Color.Transparent)), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = o2x.dp, y = o2y.dp).size(400.dp).background(Brush.radialGradient(listOf(c2, Color.Transparent)), CircleShape))
        Box(Modifier.align(Alignment.CenterStart).offset(x = o3x.dp, y = o3y.dp).size(300.dp).background(Brush.radialGradient(listOf(c3, Color.Transparent)), CircleShape))
    }
}

// ── VlGlassPanel — Настоящее матовое стекло через Haze ──────────────────────

@Composable
fun VlGlassPanel(
    appTheme: AppTheme,
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    simplifiedGraphics: Boolean = false,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val style = rememberExthruStyle(appTheme)

    if (!appTheme.isExthruFamily || appTheme == AppTheme.FORGE) {
        val style = rememberExthruStyle(appTheme)
        Box(
            modifier
                .clip(if (appTheme == AppTheme.FORGE) RectangleShape else RoundedCornerShape(radius))
                .background(
                    if (appTheme == AppTheme.FORGE) style.cardBg else cs.surface,
                    if (appTheme == AppTheme.FORGE) RectangleShape else RoundedCornerShape(radius)
                )
        ) { content() }
        return
    }

    val shape = RoundedCornerShape(radius)
    val hazeState = LocalHazeState.current

    val glassModifier = if (simplifiedGraphics || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Modifier
            .clip(shape)
            .background((if (isDark) cs.surface else Color.White).copy(alpha = if (isDark) 0.8f else 0.9f))
    } else {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = 32.dp, noiseFactor = 0.02f, tint = null)
            )
            .background((if (isDark) cs.surface else Color.White).copy(alpha = if (isDark) 0.25f else 0.35f)) // Сквозная прозрачность
    }

    Box(
        modifier = modifier
            .scale(1f) // Just to trigger recomposition if needed
            .then(if (simplifiedGraphics) Modifier else Modifier.nmRaisedShadow(isDark, cornerRadius = radius, shadowRadius = 12.dp))
            .then(glassModifier)
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.6f),
                        Color.Transparent,
                        if (isDark) Color.Black.copy(alpha = 0.4f) else style.accent.copy(alpha = 0.2f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

// ── VlButton — Механическая кнопка с физическим вдавливанием ────────────────

@Composable
fun VlButton(
    appTheme: AppTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    hapticEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        VlSurface(
            appTheme = appTheme,
            modifier = modifier.fillMaxWidth().height(56.dp),
            isButton = true,
            overrideColor = if (isDestructive) cs.error else cs.primary,
            onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        ) {
            CompositionLocalProvider(
                LocalContentColor provides (if (isDestructive) cs.onError else cs.onPrimary)
            ) { content() }
        }
        return
    }

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDestructive) style.destructive.copy(alpha = 0.8f) else if (isForge) style.cardBg else style.cardBg.copy(alpha = if(isDark) 0.4f else 0.6f)
    val textColor = if (isDestructive) Color.White else style.accent
    val shape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "vl_btn_scale"
    )

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, offsetDp = 4.dp)
    } else if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(if (isForge) 1f else scale)
            .then(shadowMod)
            .background(bgColor, shape)
            .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if(isDark) 0.05f else 0.3f), shape))
            .clip(shape)
            .clickable(
                interactionSource = interactionSource, indication = null,
                onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            content()
        }
    }
}

// ── VlSwitch ─────────────────────────────────────────────────────────────────

@Composable
fun VlSwitch(
    appTheme: AppTheme,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        Switch(
            checked = checked,
            onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(it) },
            modifier = modifier,
        )
        return
    }

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "vlswitch_thumb")
    val dotColor by animateColorAsState(if (checked) style.accent else cs.onSurfaceVariant.copy(alpha = 0.5f), tween(200), label = "vlswitch_dot")

    if (isForge) {
        val trackBorder = if (isDark) Color(0xFF333333) else Color.Black
        Box(
            modifier = modifier
                .width(52.dp)
                .height(28.dp)
                .forgeNeuBrutalism(isPressed = false, isDark = isDark, offsetDp = 2.dp)
                .background(if (checked) style.accent.copy(alpha = 0.2f) else cs.surfaceVariant, RectangleShape)
                .clickable { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(!checked) }
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(24.dp)
                    .background(style.cardBg)
                    .border(2.dp, trackBorder)
            )
        }
        return
    }

    // Трек свитча - вдавленный (Biolume)
    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .nmInsetShadow(isDark, cornerRadius = 14.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
            .background(cs.surface.copy(alpha = if (isDark) 0.2f else 0.4f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(24.dp)
                .exthruSmallRaisedShadow(isDark)
                .background(style.cardBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        }
    }
}

// ── VlSegmentedControl ───────────────────────────────────────────────────────

@Composable
fun VlSegmentedControl(
    appTheme: AppTheme,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val shape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)
    val itemShape = if (isForge) RectangleShape else RoundedCornerShape(12.dp)

    val shadowMod = if (isForge) {
        Modifier.border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, shape)
    } else {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
    }

    // Основной трек
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowMod)
            .background(if (isForge) style.inputBg else cs.surface.copy(alpha = if(isDark) 0.2f else 0.4f), shape)
            .padding(if (isForge) 2.dp else 4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val isSelected = selectedIndex == i
                val bgColor by animateColorAsState(if (isSelected) style.cardBg else Color.Transparent, tween(250), label = "seg_bg")
                val textColor by animateColorAsState(if (isSelected) style.accent else cs.onSurfaceVariant, tween(250), label = "seg_txt")

                val itemShadow = if (isForge && isSelected) {
                    Modifier.border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, RectangleShape)
                } else if (appTheme.isExthruFamily && isSelected && !isForge) {
                    Modifier.exthruSmallRaisedShadow(isDark)
                } else Modifier

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(itemShadow)
                        .background(bgColor, itemShape)
                        .clip(itemShape)
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
                        fontSize = 14.sp,
                        fontFamily = monoFamily(style)
                    )
                }
            }
        }
    }
}

// ── ExthruIconTray ───────────────────────────────────────────────────────────

@Composable
fun ExthruIconTray(
    appTheme: AppTheme,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isError: Boolean = false,
    iconColor: Color? = null,
) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val color = iconColor ?: if (isError) style.destructive else if (selected) style.accent else cs.onSurfaceVariant
    val size = if (isForge) 36.dp else 38.dp

    val bgColor = if (isForge) style.cardBg else cs.surface.copy(alpha = if (isDark) 0.5f else 0.7f)
    val shape = if (isForge) RectangleShape else CircleShape

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed = false, isDark = isDark, offsetDp = 2.dp)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(shadowMod)
            .background(bgColor, shape)
            .then(if (isForge) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = if(isDark) 0.05f else 0.3f), shape)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (isForge) 18.dp else 20.dp))
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
        if (isPressed) tintColor else tintColor.copy(alpha = 0f), tween(160), label = "vltapfeedback_bg"
    )
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, spring(dampingRatio = 0.6f), label = "vltapfeedback_scale")

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
    appTheme: AppTheme,
    title: String,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isForge = appTheme == AppTheme.FORGE
    val style = rememberExthruStyle(if (appTheme.isExthruFamily) appTheme else AppTheme.BIOLUME)
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val titleColor = when {
        isPremium -> Color(0xFFC5A059)
        appTheme.isExthruFamily -> style.accent
        else -> cs.primary
    }

    Column(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (isForge) 16.dp else 28.dp, top = 26.dp, bottom = 8.dp, end = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isForge) "> ${title.uppercase()}_" else title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (appTheme.isExthruFamily) 22.sp else 13.sp,
                letterSpacing = if (isForge) 1.sp else if (appTheme.isExthruFamily) 0.sp else 1.2.sp,
                fontFamily = if (isForge) FontFamily.Monospace else null,
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

        if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
        } else if (isForge) {
            // Терминальный квадратный контейнер
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .forgeNeuBrutalism(isPressed = false, isDark = isDark, offsetDp = 4.dp)
                    .background(style.cardBg)
            ) { content() }
        } else {
            // Glassmorphism Container (Biolume)
            val hazeState = LocalHazeState.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .exthruSmallRaisedShadow(isDark)
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                    )
                    .background((if (isDark) cs.surface else Color.White).copy(alpha = if (isDark) 0.4f else 0.75f), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.15f else 0.5f),
                                if (isDark) Color.Transparent else style.accent.copy(alpha = 0.06f),
                                Color.Black.copy(alpha = if (isDark) 0.4f else 0.05f)
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    ),
            ) { content() }
        }
    }
}

// ── VlSettingsItem ───────────────────────────────────────────────────────────

@Composable
fun VlSettingsItem(
    appTheme: AppTheme,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    index: Int = 0,
    total: Int = 1,
    iconColor: Color? = null,
    hapticEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        val color = iconColor ?: if (isDestructive) cs.error else cs.primary
        VlSurface(
            appTheme = appTheme,
            modifier = modifier.fillMaxWidth(),
            onClick = onClick?.let { { haptic.perform(HapticType.CLICK, hapticEnabled); it() } },
            index = index, total = total,
            overrideColor = cs.surfaceContainerLow,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (isDestructive) color else cs.onSurface)
                    subtitle?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 13.sp) }
                }
                if (trailing != null) trailing()
                else if (onClick != null) Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val style = rememberExthruStyle(appTheme)
    val titleColor = if (isDestructive) style.destructive else cs.onSurface

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && onClick != null) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "item_scale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (style.isForge) 1f else scale)
            .clickable(
                interactionSource = interactionSource, indication = null,
                enabled = onClick != null,
                onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick?.invoke() }
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExthruIconTray(appTheme = appTheme, icon = icon, isError = isDestructive, iconColor = iconColor)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = titleColor, fontFamily = monoFamily(style))
            subtitle?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 12.sp, fontFamily = monoFamily(style)) }
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = cs.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ── VlOptionRow ──────────────────────────────────────────────────────────────

@Composable
fun VlOptionRow(
    appTheme: AppTheme,
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
    val isDark = cs.surface.luminance() < 0.5f

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        VlSurface(
            appTheme = appTheme,
            modifier = modifier.fillMaxWidth(),
            onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
            overrideColor = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
            index = index, total = total,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp)
                        .background(if (selected) cs.primary.copy(alpha = 0.15f) else cs.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = if (selected) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                    desc?.let { Text(it, fontSize = 13.sp, color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.7f) else cs.onSurfaceVariant) }
                }
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = cs.primary)
            }
        }
        return
    }

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val dotScale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "dot_scale")
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "row_scale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isForge) 1f else scale)
            .clickable(
                interactionSource = interactionSource, indication = null,
                onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() }
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExthruIconTray(appTheme = appTheme, icon = icon, selected = selected)
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) style.accent else cs.onSurface, fontFamily = monoFamily(style))
            desc?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 12.sp, fontFamily = monoFamily(style)) }
        }

        // NmRadio (Чекбокс)
        if (isForge) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, RectangleShape)
                    .background(style.inputBg),
                contentAlignment = Alignment.Center
            ) {
                if (selected || dotScale > 0f) {
                    Box(
                        modifier = Modifier
                            .size((12 * dotScale).dp)
                            .background(style.accent)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .nmInsetShadow(isDark, cornerRadius = 12.dp)
                    .background(cs.surface.copy(alpha = if (isDark) 0.2f else 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected || dotScale > 0f) {
                    Box(
                        modifier = Modifier
                            .size((12 * dotScale).dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(style.accent, CircleShape)
                    )
                }
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
    if (!user.avatarUrl.isNullOrEmpty()) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        val name = user.displayName.ifEmpty { user.username }
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
            Icon(Icons.Default.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(12.dp))
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
