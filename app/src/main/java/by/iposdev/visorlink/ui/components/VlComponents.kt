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
import by.iposdev.visorlink.ui.theme.*
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.TextStyle

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

    val c1 = accent.copy(alpha = if (isDark) 0.20f else 0.25f)
    val c2 = cs.tertiary.copy(alpha = if (isDark) 0.15f else 0.12f)
    val c3 = cs.secondary.copy(alpha = if (isDark) 0.15f else 0.12f)

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
    val style = rememberExthruStyle(appTheme)

    VlSurface(
        appTheme = appTheme,
        customRadius = radius,
        modifier = modifier,
        overrideColor = if (simplifiedGraphics || appTheme == AppTheme.FORGE || !appTheme.isExthruFamily) style.cardBg else null
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

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        Button(
            onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
            modifier = modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) cs.error else cs.primary,
                contentColor = if (isDestructive) cs.onError else cs.onPrimary
            )
        ) {
            content()
        }
        return
    }

    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge

    VlSurface(
        appTheme = appTheme,
        isButton = true,
        customRadius = if (isForge) 0.dp else 16.dp,
        overrideColor = if (isDestructive) style.destructive else style.cardBg,
        modifier = modifier.fillMaxWidth().height(56.dp),
        onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() }
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (isDestructive) Color.White else style.accent
        ) {
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
    val thumbOffset by animateDpAsState(
        if (checked) (if (isForge) 40.dp else 24.dp) else 4.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "vlswitch_thumb"
    )
    val dotColor by animateColorAsState(if (checked) style.accent else cs.onSurfaceVariant.copy(alpha = 0.5f), tween(200), label = "vlswitch_dot")

    if (isForge) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        Box(
            modifier = modifier
                .width(68.dp)
                .height(32.dp)
                .background(style.cardBg)
                .win95Panel(isDark = isDark, raised = true)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(!checked) },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(if (isDark) Color.Black else Color.Gray.copy(alpha = 0.2f))
                    .win95Panel(isDark = isDark, raised = false, thickness = 1.dp)
            )

            // Thumb
            Box(
                Modifier
                    .offset(x = thumbOffset - 2.dp)
                    .size(24.dp, 24.dp)
                    .background(style.cardBg)
                    .win95Panel(isDark = isDark, raised = !isPressed)
            ) {
                // Indicator square
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(8.dp)
                        .background(if (checked) style.accent else (if (isDark) Color(0xFF222222) else Color(0xFFCCCCCC)))
                        .border(1.dp, Color.Black.copy(alpha = 0.5f))
                )
            }
        }
        return
    }

    val trackShape = RoundedCornerShape(14.dp)
    val activeTrackBrush = Brush.linearGradient(
        colors = listOf(Biolume.IridescentStart.copy(alpha = 0.5f), Biolume.IridescentMid.copy(alpha = 0.5f))
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .nmInsetShadow(isDark, cornerRadius = 14.dp, blurRadiusDp = 4.dp, darkAlpha = if(isDark) 0.6f else 0.45f)
            .then(
                if (checked)
                    Modifier.background(activeTrackBrush, trackShape, alpha = 0.15f)
                else
                    Modifier.background(cs.surface.copy(alpha = if (isDark) 0.2f else 0.5f), trackShape)
            )
            .clip(trackShape)
            .then(
                if (checked)
                    Modifier.border(0.8.dp, activeTrackBrush, trackShape)
                else
                    Modifier.border(0.8.dp, Color.White.copy(alpha = if (isDark) 0.03f else 0.15f), trackShape)
            )
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
            Box(
                Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
                    .then(if (checked) Modifier.accentGlowShadow(dotColor, true, 5.dp) else Modifier)
            )
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowMod)
            .background(if (isForge) style.inputBg else cs.surface.copy(alpha = if(isDark) 0.2f else 0.6f), shape)
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
        } else {
            // ИСПРАВЛЕНИЕ: Используем VlSurface для единообразия теней и фонов!
            VlSurface(
                appTheme = appTheme,
                customRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentPadding = PaddingValues(0.dp) // Внутренний контент сам разберется
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isForge) 8.dp else 0.dp)
                ) {
                    content()
                }
            }
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

    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
        val color = iconColor ?: if (isDestructive) cs.error else cs.primary

        Surface(
            modifier = modifier.fillMaxWidth(),
            onClick = { if (onClick != null) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() } },
            color = cs.surfaceContainerLow,
            shape = when {
                total <= 1 -> RoundedCornerShape(20.dp)
                index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                else -> RoundedCornerShape(4.dp)
            }
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Surface(
            modifier = modifier.fillMaxWidth(),
            onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
            color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
            shape = when {
                total <= 1 -> RoundedCornerShape(20.dp)
                index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                else -> RoundedCornerShape(4.dp)
            }
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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

        if (isForge) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, RectangleShape)
                    .background(style.inputBg),
                contentAlignment = Alignment.Center
            ) {
                if (selected || dotScale > 0f) {
                    Box(modifier = Modifier.size((12 * dotScale).dp).background(style.accent))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .nmInsetShadow(isDark, cornerRadius = 12.dp, blurRadiusDp = 4.dp, lineWidthDp = 2.dp, darkAlpha = if(isDark) 0.5f else 0.4f)
                    .background(if (isDark) Color(0xFF0F0F0F).copy(alpha = 0.5f) else Color(0xFFDAE2E9), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected || dotScale > 0f) {
                    Box(
                        modifier = Modifier
                            .size((12 * dotScale).dp)
                            .background(
                                Brush.radialGradient(listOf(style.accent.copy(alpha = 0.7f), style.accent)),
                                CircleShape
                            )
                            .accentGlowShadow(style.accent, false, 8.dp)
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