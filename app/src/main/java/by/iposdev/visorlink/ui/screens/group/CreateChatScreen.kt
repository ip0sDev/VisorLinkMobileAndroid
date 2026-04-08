package by.iposdev.visorlink.ui.screens.group

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatScreen(
    onNavigateBack: () -> Unit,
    onCreated: (chatId: String) -> Unit,
    chatRepository: ChatRepository = koinInject(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("group") }
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

    val titleRes = if (type == "group") R.string.create_title_group else R.string.create_title_channel

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        if (isExthru) {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Text(stringResource(titleRes))
                        }
                    },
                    navigationIcon = {
                        val btnModifier = if (isExthru) Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(42.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            },
                            modifier = btnModifier
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                                modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Вкладки
            if (isExthru) {
                ExthruSwitcher(
                    selected = type,
                    onSelect = {
                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                        type = it
                    },
                    isDark = isDark
                )
            } else {
                TabRow(selectedTabIndex = if (type == "group") 0 else 1) {
                    Tab(selected = type == "group", onClick = { type = "group" },
                        text = { Text(stringResource(R.string.create_tab_group)) })
                    Tab(selected = type == "channel", onClick = { type = "channel" },
                        text = { Text(stringResource(R.string.create_tab_channel)) })
                }
            }

            Spacer(Modifier.height(8.dp))

            // Поля ввода
            val inputModifier = if (isExthru) Modifier
                .fillMaxWidth()
                .nmInsetShadow(isDark, cornerRadius = 16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            else Modifier.fillMaxWidth()

            val tfColors = if (isExthru) OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent
            ) else OutlinedTextFieldDefaults.colors()

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = inputModifier,
                shape = RoundedCornerShape(16.dp),
                colors = tfColors
            )

            OutlinedTextField(
                value = tag,
                onValueChange = {
                    tag = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase()
                },
                label = { Text(stringResource(R.string.create_field_tag)) },
                prefix = { Text("@") },
                supportingText = { Text(stringResource(R.string.create_tag_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = inputModifier,
                shape = RoundedCornerShape(16.dp),
                colors = tfColors
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it.take(160) },
                label = { Text(stringResource(R.string.create_field_description)) },
                supportingText = { Text("${description.length}/160") },
                maxLines = 3,
                modifier = inputModifier,
                shape = RoundedCornerShape(16.dp),
                colors = tfColors
            )

            if (type == "channel") {
                val noticeModifier = if (isExthru) Modifier
                    .fillMaxWidth()
                    .nmInsetShadow(isDark, cornerRadius = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier.fillMaxWidth()

                Box(modifier = noticeModifier) {
                    Text(
                        stringResource(R.string.create_channel_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }

            val createLabel = stringResource(
                if (type == "group") R.string.create_button_group else R.string.create_button_channel
            )
            val errorDefault = stringResource(R.string.create_error_default)

            if (isExthru) {
                NmButton(
                    text = createLabel,
                    isLoading = isLoading,
                    isEnabled = name.isNotBlank() && tag.length >= 3 && !isLoading,
                    isDark = isDark,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            isLoading = true; error = null
                            try {
                                val (chatId, _) = chatRepository.createChat(type, name, tag, description)
                                onCreated(chatId)
                            } catch (e: Exception) {
                                error = e.message ?: errorDefault
                            } finally { isLoading = false }
                        }
                    }
                )
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true; error = null
                            try {
                                val (chatId, _) = chatRepository.createChat(type, name, tag, description)
                                onCreated(chatId)
                            } catch (e: Exception) {
                                error = e.message ?: errorDefault
                            } finally { isLoading = false }
                        }
                    },
                    enabled = name.isNotBlank() && tag.length >= 3 && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (isLoading)
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    else Text(createLabel)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Неоморфные примитивы ───────────────────────────────────────────────────

@Composable
private fun ExthruSwitcher(
    selected: String,
    onSelect: (String) -> Unit,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .nmInsetShadow(isDark, cornerRadius = 16.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            SwitcherItem(
                label = stringResource(R.string.create_tab_group),
                isSelected = selected == "group",
                isDark = isDark,
                modifier = Modifier.weight(1f),
                onClick = { onSelect("group") }
            )
            SwitcherItem(
                label = stringResource(R.string.create_tab_channel),
                isSelected = selected == "channel",
                isDark = isDark,
                modifier = Modifier.weight(1f),
                onClick = { onSelect("channel") }
            )
        }
    }
}

@Composable
private fun SwitcherItem(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(250)
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(if (isSelected) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
            .background(bgColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
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

@Composable
private fun NmButton(
    text: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btn_scale"
    )

    val bgColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .scale(scale)
            .then(if (isEnabled) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
            .background(bgColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled && !isLoading,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Text(text, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}