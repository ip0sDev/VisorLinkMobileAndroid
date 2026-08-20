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
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import by.iposdev.visorlink.data.model.UserProfile

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
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val isDefault = preset == ColorPreset.DEFAULT
    val color = preset.seedColor ?: Color.Transparent
    val cs = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
        if (isDefault) Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(20.dp))
        else if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// ── VlAmbientGlow — Анимированное фоновое свечение ─────────────────────────

@Composable
fun VlAmbientGlow(
    modifier: Modifier = Modifier,
    simplifiedGraphics: Boolean = false
) {
    if (simplifiedGraphics) return

    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val c1 = cs.primary.copy(alpha = if (isDark) 0.10f else 0.15f)
    val c2 = cs.tertiary.copy(alpha = if (isDark) 0.08f else 0.10f)
    val c3 = cs.secondary.copy(alpha = if (isDark) 0.08f else 0.10f)

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

// ── VlGlassPanel ─────────────────────────────────────────────────────────────

@Composable
fun VlGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    simplifiedGraphics: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (simplifiedGraphics) 1f else 0.7f),
        tonalElevation = 2.dp
    ) {
        content()
    }
}

// ── VlButton — Механическая кнопка с физическим вдавливанием ────────────────

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

    Button(
        onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        modifier = modifier.fillMaxWidth().height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDestructive) cs.error else cs.primary,
            contentColor = if (isDestructive) cs.onError else cs.onPrimary
        )
    ) {
        content()
    }
}

// ── VlSwitch ─────────────────────────────────────────────────────────────────

@Composable
fun VlSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    Switch(
        checked = checked,
        onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(it) },
        modifier = modifier,
    )
}

// ── VlSegmentedControl ───────────────────────────────────────────────────────

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
    val shape = RoundedCornerShape(16.dp)
    val itemShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerLow, shape)
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val isSelected = selectedIndex == i
                val bgColor by animateColorAsState(if (isSelected) cs.surfaceContainerHigh else Color.Transparent, tween(250), label = "seg_bg")
                val textColor by animateColorAsState(if (isSelected) cs.primary else cs.onSurfaceVariant, tween(250), label = "seg_txt")

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ── VlIconTray ───────────────────────────────────────────────────────────

@Composable
fun VlIconTray(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isError: Boolean = false,
    iconColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val color = iconColor ?: if (isError) cs.error else if (selected) cs.primary else cs.onSurfaceVariant
    val size = 40.dp
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(size)
            .background(cs.surfaceContainerHigh, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            color = cs.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp)
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

@Composable
fun VlSettingsItem(
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

    val color = iconColor ?: if (isDestructive) cs.error else cs.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = { if (onClick != null) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() } },
        color = Color.Transparent,
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        color = if (selected) cs.primaryContainer else Color.Transparent,
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
