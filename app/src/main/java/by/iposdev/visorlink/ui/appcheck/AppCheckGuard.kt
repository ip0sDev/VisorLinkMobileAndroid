package by.iposdev.visorlink.ui.appcheck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.AppCheckManager
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel

/**
 * Обертка для контента приложения.
 * Показывает диалог, если App Check не подтвердил подлинность клиента,
 * но позволяет закрыть его и продолжить использование (на свой страх и риск).
 */
@Composable
fun AppCheckGuard(
    viewModel: AppCheckViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsState()

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    // Используем rememberSaveable, чтобы после нажатия "I understand"
    // диалог не появлялся снова при повороте экрана или смене темы.
    var isDismissed by rememberSaveable { mutableStateOf(false) }

    // Основной контент приложения рендерится всегда
    content()

    // Показываем диалог только если проверка провалена И пользователь еще не закрыл его
    if (state is AppCheckManager.State.Invalid && !isDismissed) {
        val haptic = rememberHaptic()

        UnofficialClientDialog(
            isExthru = isExthru,
            isDark = isDark,
            onConfirm = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                isDismissed = true
            }
        )
    }
}

@Composable
private fun UnofficialClientDialog(
    isExthru: Boolean,
    isDark: Boolean,
    onConfirm: () -> Unit
) {
    if (isExthru) {
        // Кастомный неоморфный диалог для темы Exthru
        // Используем базовый Dialog, чтобы не обрезались тени
        Dialog(
            onDismissRequest = { /* Оставляем пустым, чтобы нельзя было закрыть кликом мимо */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false // Позволяет контролировать ширину вручную
            )
        ) {
            // Box с padding'ом, чтобы тень карточки поместилась в окно рендера
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .exthruRaisedShadow(isDark)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    // Иконка
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Заголовок (Moniqa Font)
                    Text(
                        text = stringResource(R.string.unofficial_client_detected),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    // Основной текст
                    Text(
                        text = stringResource(R.string.this_build_of_visorlink_has_not_been_verified_by_our_security_system),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.you_may_be_using_a_modified_or_unofficial_version_of_the_app) +
                                stringResource(R.string.for_your_security_and_the_security_of_your_conversations) +
                                stringResource(R.string.please_use_the_official_build),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // Вдавленная (inset) карточка с предупреждением
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nmInsetShadow(isDark, cornerRadius = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.15f else 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.some_features_may_be_unavailable_or_behave_unexpectedly) +
                                    stringResource(R.string.until_you_switch_to_the_official_app),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // Неоморфная кнопка
                    NmButton(
                        text = stringResource(R.string.i_understand),
                        isDestructive = true,
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onConfirm
                    )
                }
            }
        }
    } else {
        // Стандартный M3 / OneUI диалог
        AlertDialog(
            onDismissRequest = {
                // Оставляем пустым, чтобы пользователь не мог закрыть диалог
                // кликом "мимо" или кнопкой "Назад", не нажав на кнопку подтверждения.
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.unofficial_client_detected),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.this_build_of_visorlink_has_not_been_verified_by_our_security_system),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.you_may_be_using_a_modified_or_unofficial_version_of_the_app) +
                                stringResource(R.string.for_your_security_and_the_security_of_your_conversations) +
                                stringResource(R.string.please_use_the_official_build),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.some_features_may_be_unavailable_or_behave_unexpectedly) +
                                    stringResource(R.string.until_you_switch_to_the_official_app),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.i_understand))
                }
            },
            dismissButton = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

// ─── Вспомогательная кнопка для Exthru ─────────────────────────────────────────

@Composable
private fun NmButton(
    text: String,
    isDestructive: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btn_scale"
    )

    // Если кнопка деструктивная - заливаем цветом ошибки (PinkFlash), иначе оставляем цвет поверхности
    val bgColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
    val textColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .scale(scale)
            .exthruSmallRaisedShadow(isDark)
            .background(bgColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 15.sp
        )
    }
}