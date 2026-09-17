package org.visorlink.app.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.ui.components.VlLiveDot
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.VisorLinkTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.ui.theme.vlSignalGlow

/**
 * Селектор оформления с живыми превью.
 *
 * Превью каждой темы отрисовано настоящими компонентами (`VlSurface`, `VlLiveDot`)
 * внутри настоящего [VisorLinkTheme] — поэтому картинка физически не может
 * разойтись с тем, что увидит пользователь после выбора. Ручных «свотчей» здесь
 * сознательно нет.
 *
 * Живёт в `ui/components`, чтобы экран настроек оставался тема-независимым: он
 * только передаёт текущее значение и колбэк.
 */
@Composable
fun VlThemeSelector(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.DEFAULT,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTheme.entries.forEach { theme ->
            ThemePreviewCard(
                theme = theme,
                isSelected = theme == selected,
                themeMode = themeMode,
                colorPreset = colorPreset,
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    themeMode: ThemeMode,
    colorPreset: ColorPreset,
    onClick: () -> Unit,
) {
    val name = when (theme) {
        AppTheme.MATERIAL3_EXPRESSIVE -> stringResource(R.string.theme_m3e_name)
        AppTheme.BIOLUME -> stringResource(R.string.theme_biolume_name)
        AppTheme.FORGE -> stringResource(R.string.theme_forge_name)
    }
    val description = when (theme) {
        AppTheme.MATERIAL3_EXPRESSIVE -> stringResource(R.string.theme_m3e_desc)
        AppTheme.BIOLUME -> stringResource(R.string.theme_biolume_desc)
        AppTheme.FORGE -> stringResource(R.string.theme_forge_desc)
    }

    // Рамка выбора рисуется во ВНЕШНЕЙ теме — иначе выделение прыгало бы вместе
    // с палитрой превью и перестало бы читаться как элемент настроек.
    val outerCs = MaterialTheme.colorScheme
    val outerShape = VlTheme.tokens.shapes.card

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(outerCs.surfaceContainerLow, outerShape)
            .then(
                if (isSelected) {
                    Modifier.vlHairline(outerCs.primary, outerShape, width = 2.dp)
                } else {
                    Modifier.vlHairline(outerCs.outlineVariant, outerShape)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = outerCs.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = outerCs.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(VlTheme.tokens.shapes.indicator)
                        .background(outerCs.primary, VlTheme.tokens.shapes.indicator),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = outerCs.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Ниже — настоящая тема во всех её токенах.
        VisorLinkTheme(
            appTheme = theme,
            themeMode = themeMode,
            colorPreset = colorPreset,
            setStatusBarColor = false,
        ) {
            ThemePreviewBody()
        }
    }
}

/**
 * Миниатюра интерфейса: показывает именно то, что различает темы — рельеф против
 * плоскости, форму кнопок, сигнальный слой.
 */
@Composable
private fun ThemePreviewBody() {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.card

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.background, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Строка «чата»: карточка + live-точка (в Biolume она пульсирует).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VlSurface(
                modifier = Modifier.size(36.dp),
                customRadius = 18.dp,
            ) {
                VlLiveDot(size = 8.dp)
            }
            VlSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(VlTheme.tokens.shapes.indicator)
                            .background(cs.onSurfaceVariant.copy(alpha = 0.45f), VlTheme.tokens.shapes.indicator)
                    )
                }
            }
        }

        // Поле ввода — единственный элемент, где inset читается наглядно.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(tokens.shapes.field)
                .background(cs.surfaceContainer, tokens.shapes.field)
                .then(
                    if (tokens.structure.enabled) Modifier.vlInset(tokens.structure, tokens.shapes.field)
                    else Modifier
                )
        )

        // Чипы: выбранный — плоская *Container заливка + inset, без glow (§4.2).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewChip(selected = true)
            PreviewChip(selected = false)
        }

        // Filled CTA: stadium в Biolume, 16dp в M3E. В покое не светится (§10),
        // поэтому в превью glow тоже нет — показываем честное состояние покоя.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(tokens.shapes.button)
                .background(cs.primary, tokens.shapes.button),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(54.dp)
                    .height(6.dp)
                    .clip(VlTheme.tokens.shapes.indicator)
                    .background(cs.onPrimary.copy(alpha = 0.85f), VlTheme.tokens.shapes.indicator)
            )
        }

        // FAB — единственный элемент с постоянным (статичным) свечением (§7).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .vlSignalGlow(
                        tokens = tokens.signal,
                        color = cs.primary,
                        shape = tokens.shapes.fab,
                        alphaOverride = tokens.signal.fabRestAlpha,
                    )
                    .then(
                        if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, tokens.shapes.fab)
                        else Modifier
                    )
                    .clip(tokens.shapes.fab)
                    .background(cs.primary, tokens.shapes.fab)
            )
        }
    }
}

@Composable
private fun PreviewChip(selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.chip

    Box(
        modifier = Modifier
            .then(
                if (tokens.structure.enabled && !selected) Modifier.vlRaised(tokens.structure, shape)
                else Modifier
            )
            .clip(shape)
            .background(
                if (selected) tokens.selectionFill else cs.surfaceContainer,
                shape,
            )
            .then(
                if (tokens.structure.enabled && selected) Modifier.vlInset(tokens.structure, shape)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Box(
            Modifier
                .width(26.dp)
                .height(5.dp)
                .clip(VlTheme.tokens.shapes.indicator)
                .background(
                    if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f),
                    VlTheme.tokens.shapes.indicator,
                )
        )
    }
}
