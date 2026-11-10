package org.visorlink.app.ui.screens.signal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.activity.StepCounterModel
import org.visorlink.app.data.model.aegis.LinkEmotion
import org.visorlink.app.data.model.aegis.VisorIcon
import org.visorlink.app.ui.aegis.AnimatedProtogen
import org.visorlink.app.ui.aegis.linkVisorColor
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.components.led.VlLedDot
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import java.util.Locale
import kotlin.math.abs

private fun groupDigits(n: Int): String =
    String.format(Locale.US, "%,d", n).replace(',', ' ')

/**
 * Подстраница «Шаги» во «Сигнале» (этап 1 активностей, флаг enable_step_activities).
 * Всем заправляет Линк — компаньон-протоген: LED-визор и реплика меняются вместе
 * с состоянием дня (пороги, буст стрика, доверие, ошибки), реагирует на boop и глажку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalStepsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StepsViewModel = koinViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    var showConsent by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.enable()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.step_card_title)) },
                navigationIcon = {
                    VlTopAppBarButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.signal_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LinkStepsCard(ui)

            VlCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!ui.enabled) {
                        Text(
                            text = stringResource(R.string.step_card_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        VlButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACTIVITY_RECOGNITION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.enable() else showConsent = true
                        }) {
                            Text(stringResource(R.string.step_enable))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = groupDigits(ui.steps),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.step_today),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )

                        val next = StepCounterModel.nextThreshold(ui.steps, ui.thresholds)
                        val prevThreshold = ui.thresholds.lastOrNull { it <= ui.steps }
                        val progress = when {
                            next == null -> 1f
                            prevThreshold == null -> ui.steps.toFloat() / next
                            else -> (ui.steps - prevThreshold).toFloat() / (next - prevThreshold)
                        }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (next != null) {
                            val perThreshold =
                                if (ui.boost) StepCounterModel.AWARD_BOOST else StepCounterModel.AWARD_BASE
                            Text(
                                text = stringResource(R.string.step_to_next, groupDigits(next - ui.steps), perThreshold),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.step_all_reached),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary
                            )
                        }

                        if (ui.serverAwarded > 0) {
                            Text(
                                text = stringResource(R.string.step_earned, ui.serverAwarded),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface
                            )
                        }
                        if (ui.boost) {
                            Text(
                                text = stringResource(R.string.step_boost_hint, StepCounterModel.AWARD_BOOST),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary
                            )
                        }
                        if (ui.trustReason != null) {
                            Text(
                                text = if (ui.trustDaysLeft > 0) stringResource(R.string.step_trust_pending, ui.trustDaysLeft)
                                else stringResource(R.string.step_trust_pending_short),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        when (ui.errorReason) {
                            "rebind-wait" -> Text(
                                text = stringResource(R.string.step_error_rebind),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.error
                            )
                            "device-unknown", "bad-signature" -> Text(
                                text = stringResource(R.string.step_error_device),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.error
                            )
                            null -> {}
                            else -> Text(
                                text = stringResource(R.string.step_error_generic),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.error
                            )
                        }
                        Text(
                            text = stringResource(R.string.step_reboot_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        VlButton(onClick = { showDisable = true }) {
                            Text(stringResource(R.string.step_disable))
                        }
                    }
                }
            }
        }
    }

    if (showConsent) {
        StepConsentDialog(
            onAllow = {
                showConsent = false
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            },
            onDismiss = { showConsent = false }
        )
    }

    if (showDisable) {
        AlertDialog(
            onDismissRequest = { showDisable = false },
            title = { Text(stringResource(R.string.step_disable_title)) },
            text = { Text(stringResource(R.string.step_disable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisable = false
                    viewModel.disable(deleteServerData = true)
                }) { Text(stringResource(R.string.step_disable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisable = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** Панель Линка: протоген + реплика-пузырь; boop и глажка дают живой отклик. */
@Composable
private fun LinkStepsCard(ui: StepsViewModel.UiState) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()

    val mood = remember(ui) { LinkStepsMood.mood(ui) }

    // Реакции на boop/глажку перекрывают настроение на пару секунд
    var reaction by remember { mutableStateOf<LinkReaction?>(null) }
    LaunchedEffect(reaction) {
        if (reaction != null) {
            delay(1800)
            reaction = null
        }
    }

    val boopLines = remember {
        listOf(R.string.link_step_boop1, R.string.link_step_boop2, R.string.link_step_boop3)
    }

    val shown: LinkMood = reaction?.mood ?: mood
    val activeAccent by animateColorAsState(
        targetValue = linkVisorColor(shown.emotion, shown.visor),
        animationSpec = tween(420),
        label = "link_card_accent"
    )

    VlCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var isPressed by remember { mutableStateOf(false) }
            var isDraggingPet by remember { mutableStateOf(false) }
            var petAccumulator by remember { mutableFloatStateOf(0f) }

            // Бейдж имени компаньона со светодиодом статуса визора
            Row(
                modifier = Modifier
                    .clip(tokens.shapes.chip)
                    .background(cs.surfaceContainerHighest.copy(alpha = 0.75f))
                    .border(1.dp, activeAccent.copy(alpha = 0.32f), tokens.shapes.chip)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                VlLedDot(size = 6.dp, color = activeAccent)
                Text(
                    text = stringResource(R.string.link_step_name),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
            }

            // Интерактивная сцена протогена (тап — boop по носу, свайп — погладить по голове)
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .pointerInput(ui.enabled) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                haptic.perform(HapticType.REACTION, true)
                                reaction = LinkReaction(
                                    mood = LinkMood(LinkEmotion.HAPPY, VisorIcon.HEART, boopLines.random()),
                                    isPet = false
                                )
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                petAccumulator = 0f
                                isDraggingPet = true
                            },
                            onDragEnd = { isDraggingPet = false },
                            onDragCancel = { isDraggingPet = false },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                petAccumulator += abs(dragAmount)
                                if (petAccumulator > 50f) {
                                    petAccumulator = 0f
                                    haptic.perform(HapticType.SELECTION, true)
                                    reaction = LinkReaction(
                                        mood = LinkMood(LinkEmotion.PARTY, VisorIcon.HEART, R.string.link_step_pet),
                                        isPet = true
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedProtogen(
                    emotion = shown.emotion,
                    visorIcon = shown.visor,
                    isPressed = isPressed,
                    isPetting = isDraggingPet || reaction?.isPet == true,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Реплика-пузырь с хвостиком к Линку и плавной сменой текста
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bubbleColor = cs.primaryContainer
                Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
                    val tail = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(tail, color = bubbleColor)
                }

                Surface(
                    color = bubbleColor,
                    shape = tokens.shapes.button,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .border(1.dp, activeAccent.copy(alpha = 0.25f), tokens.shapes.button)
                ) {
                    val messageText = stringResource(shown.messageRes, *shown.args.toTypedArray())
                    AnimatedContent(
                        targetState = messageText,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "link_speech"
                    ) { text ->
                        Text(
                            text = text,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                                .fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Кратковременная эмоция Линка от взаимодействия. */
private data class LinkReaction(val mood: LinkMood, val isPet: Boolean = false)
