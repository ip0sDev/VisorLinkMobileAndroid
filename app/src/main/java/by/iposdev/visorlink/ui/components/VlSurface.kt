package by.iposdev.visorlink.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.*

@Composable
fun VlSurface(
    appTheme: AppTheme,
    modifier: Modifier = Modifier,
    isButton: Boolean = false,
    isInput: Boolean = false,
    customRadius: Dp? = null,
    overrideColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    index: Int = 0,
    total: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val baseRadius = customRadius ?: if (isButton) 20.dp else 24.dp

    val shape: Shape = when {
        appTheme == AppTheme.FORGE || appTheme == AppTheme.FORGE_INDUSTRIAL || appTheme == AppTheme.FORGE_TERMINAL || appTheme == AppTheme.FORGE_COMICS -> {
            if (appTheme == AppTheme.FORGE_COMICS) RoundedCornerShape(if (isButton) 12.dp else 8.dp) else RectangleShape
        }
        total <= 1 -> RoundedCornerShape(baseRadius)
        else -> {
            val smallR = 4.dp
            when {
                index == 0 -> RoundedCornerShape(topStart = baseRadius, topEnd = baseRadius, bottomStart = smallR, bottomEnd = smallR)
                index == total - 1 -> RoundedCornerShape(topStart = smallR, topEnd = smallR, bottomStart = baseRadius, bottomEnd = baseRadius)
                else -> RoundedCornerShape(smallR)
            }
        }
    }

    val pressedScale = if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) 0.96f else 0.93f
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isInput) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "vlsurface_scale",
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    when (appTheme) {
        AppTheme.FORGE_INDUSTRIAL -> {
            val style = rememberExthruStyle(appTheme)
            val bg = overrideColor ?: if (isInput) Color(0xFF000000) else style.cardBg
            
            Box(
                modifier = modifier
                    .background(bg, shape)
                    .border(1.dp, if (isPressed) style.accent else style.darkShadow.copy(alpha = 0.5f), shape)
                    .cyberpunkScanlines(spacing = 4.dp)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        AppTheme.FORGE_TERMINAL -> {
            val style = rememberExthruStyle(appTheme)
            val bg = overrideColor ?: style.cardBg
            
            Box(
                modifier = modifier
                    .background(bg)
                    .win95Panel(isDark = isDark, raised = !isPressed && !isInput)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        AppTheme.FORGE_COMICS -> {
            val style = rememberExthruStyle(appTheme)
            val paper = if (isDark) ForgeComics.Noir else ForgeComics.Newsprint
            val bg = overrideColor ?: if (isInput) paper.Paper else style.cardBg
            
            Box(
                modifier = modifier
                    .scale(scale)
                    .background(bg, shape)
                    .then(if (!isInput) Modifier.comicPanelOutline(color = paper.Stroke, cornerRadius = if (isButton) 12.dp else 8.dp) else Modifier)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        AppTheme.MATERIAL3_EXPRESSIVE, AppTheme.ONE_UI -> {
            val bg = overrideColor ?: if (isInput) cs.surfaceContainerHighest else cs.surfaceContainerLow
            Box(
                modifier = modifier
                    .scale(scale)
                    .clip(shape)
                    .background(bg, shape)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        else -> {
            // Biolume / Default
            val style = rememberExthruStyle(appTheme)
            val isBiolume = appTheme == AppTheme.BIOLUME || appTheme == AppTheme.EXTHRU
            val showInset = isInput || isPressed

            val infiniteTransition = rememberInfiniteTransition(label = "surface_shimmer")
            val shimmerX by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 800f, animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "shimmerX")
            val shimmerY by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 800f, animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shimmerY")
            val pressAlpha by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "press_alpha")

            val biolumeBg = if (isBiolume && overrideColor == null && !isInput) {
                Brush.linearGradient(
                    colors = if (isDark) listOf(style.cardBg.copy(alpha = 0.5f * pressAlpha), style.accent.copy(alpha = 0.08f * pressAlpha), style.cardBg.copy(alpha = 0.25f * pressAlpha))
                    else listOf(Color.White.copy(alpha = 0.95f * pressAlpha), style.accent.copy(alpha = 0.05f * pressAlpha), Color.White.copy(alpha = 0.65f * pressAlpha)),
                    start = Offset(shimmerX, shimmerY),
                    end = Offset(shimmerX + 600f, shimmerY + 600f)
                )
            } else null

            val shadowModifier = if (overrideColor == Color.Transparent && !isBiolume) Modifier else if (!showInset) {
                Modifier.nmRaisedShadow(isDark = isDark, shadowRadius = if (isBiolume) 20.dp else if (isButton) 12.dp else 16.dp, offsetDp = if (isBiolume) 8.dp else if (isButton) 4.dp else 6.dp, cornerRadius = baseRadius)
            } else {
                Modifier.nmInsetShadow(isDark = isDark, cornerRadius = baseRadius)
            }

            Box(
                modifier = modifier
                    .scale(scale)
                    .then(shadowModifier)
                    .clip(shape)
                    .then(if (biolumeBg != null) Modifier.background(biolumeBg) else Modifier.background(overrideColor ?: if (isInput) style.inputBg else style.cardBg))
                    .then(if (isBiolume && !showInset) Modifier.biolumeGlassBorder(shape, isDark, style.accent, isPressed) else Modifier)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}
