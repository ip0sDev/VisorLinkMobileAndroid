package org.visorlink.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlKeycapBackground
import org.visorlink.app.ui.theme.vlStructure

/**
 * Кнопка верхней панели (Flutter `wrapButton` в `VlTopAppBar`):
 * приподнятая плитка 12dp со скруглением, световым градиентом клавиши, гранью outlineVariant и отступом 8dp.
 */
@Composable
fun VlTopAppBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val dark = tokens.isDark
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = tokens.shapes.rounded(12.dp)
    val depth = if (tokens.structure.enabled) {
        if (isPressed && enabled) VlDepth.Inset else VlDepth.Raised
    } else VlDepth.Flat
    val color = if (tokens.structure.enabled) {
        if (dark) cs.surfaceContainer else cs.surfaceContainerLow
    } else cs.surfaceContainerHighest.copy(alpha = 0.50f)
    val borderColor = cs.outlineVariant.copy(alpha = if (dark) 0.15f else 0.30f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
            .then(
                if (tokens.structure.enabled) Modifier.vlStructure(tokens.structure, depth, shape)
                else Modifier
            )
            .clip(shape)
            .vlKeycapBackground(tokens.structure, color, shape, depth)
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
        ) {
            content()
        }
    }
}

/**
 * Верхняя панель (порт Flutter `VlTopAppBar`).
 * Полноширинный бар с высотой 56dp, тонким LED-свечением снизу и единым стилем плиточных кнопок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val bgColor = cs.surface

    val titleStyle = if (tokens.isForge) {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Light,
            color = cs.onSurface,
            letterSpacing = 0.sp,
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = cs.onSurface,
            letterSpacing = (-0.2).sp,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        navigationIcon()
                    }
                } else {
                    Spacer(Modifier.width(16.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides titleStyle,
                        LocalContentColor provides cs.onSurface,
                    ) {
                        title()
                    }
                }

                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
        VlBottomLedHairline(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Тонкая нижняя LED-линия со свечением для шапок и панелей (Forge / Biolume с LED).
 */
@Composable
fun VlBottomLedHairline(modifier: Modifier = Modifier) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val ledColor = tokens.led?.color ?: cs.primary
    val hasLed = tokens.led != null || tokens.isForge

    if (hasLed && !tokens.reduceMotion) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .drawBehind {
                    val blurPx = 6.dp.toPx()
                    val dy = 1.dp.toPx()
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = ledColor.copy(alpha = 0.35f).toArgb()
                            maskFilter = BlurMaskFilter(blurPx * 0.57735f + 0.5f, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawRect(0f, dy, size.width, size.height + dy, paint)
                    }
                }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            cs.outlineVariant.copy(alpha = 0.08f),
                            ledColor.copy(alpha = 0.75f),
                            cs.outlineVariant.copy(alpha = 0.08f),
                        )
                    )
                )
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(cs.outlineVariant.copy(alpha = if (tokens.isDark) 0.20f else 0.35f))
        )
    }
}
