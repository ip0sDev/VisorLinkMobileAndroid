package by.iposdev.visorlink.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.TagSearchResult
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.screens.chatlist.GroupChannelAvatar
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit, // Для юзеров
    onJoinedGroup: (chatId: String) -> Unit,                // Для каналов
    viewModel: SearchViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU || currentTheme == AppTheme.BIOLUME
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

    LaunchedEffect(initialQuery) {
        viewModel.initSearch(initialQuery)
    }

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize().background(scaffoldBg)) {
            Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
                VlAmbientGlow(appTheme = currentTheme)

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        if (isExthru) {
                            TopAppBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hazeChild(
                                        state = hazeState,
                                        style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                                    )
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)),
                                title = {
                                    Text(
                                        text = stringResource(R.string.search_title),
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                },
                                navigationIcon = {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()

                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.9f else 1f,
                                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                                        label = "back_btn_scale"
                                    )

                                    val shadowMod = if (isPressed) {
                                        Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                                    } else {
                                        Modifier.exthruSmallRaisedShadow(isDark)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 4.dp)
                                            .size(42.dp)
                                            .scale(scale)
                                            .then(shadowMod)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                                            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = {
                                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                                    onNavigateBack()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent
                                )
                            )
                        } else {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.search_title)) },
                                    navigationIcon = {
                                        IconButton(onClick = {
                                            haptic.perform(HapticType.CLICK, hapticEnabled)
                                            onNavigateBack()
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(16.dp))

                        // ── Вдавленное поле поиска ──
                        val tfShape = RoundedCornerShape(16.dp)
                        val tfModifier = if (isExthru) {
                            Modifier
                                .fillMaxWidth()
                                .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.2f else 0.4f), tfShape)
                        } else Modifier.fillMaxWidth()

                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChange,
                            label = { Text("Поиск пользователей или каналов") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty())
                                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                                        Icon(Icons.Default.Clear, null)
                                    }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus(); viewModel.search()
                            }),
                            modifier = tfModifier,
                            shape = if (isExthru) tfShape else MaterialTheme.shapes.medium,
                            colors = if (isExthru) OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ) else OutlinedTextFieldDefaults.colors()
                        )

                        Spacer(Modifier.height(16.dp))

                        // ── Кнопка поиска ──
                        if (isExthru) {
                            NmButton(
                                text = stringResource(R.string.action_search),
                                icon = Icons.Default.Search,
                                isLoading = uiState.isLoading,
                                isEnabled = uiState.query.isNotEmpty() && !uiState.isLoading,
                                isDark = isDark,
                                hapticEnabled = hapticEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { focusManager.clearFocus(); viewModel.search() }
                            )
                        } else {
                            Button(
                                onClick = { focusManager.clearFocus(); viewModel.search() },
                                enabled = uiState.query.isNotEmpty() && !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                if (uiState.isLoading)
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else {
                                    Icon(Icons.Default.Search, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_search))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))

                        uiState.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Пользователь
                            if (uiState.userResult != null) {
                                item {
                                    UserResultCard(
                                        user = uiState.userResult!!,
                                        isExthru = isExthru,
                                        isDark = isDark,
                                        hapticEnabled = hapticEnabled,
                                        onChatClick = {
                                            scope.launch {
                                                val chatId = viewModel.openOrCreateChat(uiState.userResult!!)
                                                onOpenChat(chatId, uiState.userResult!!.uid)
                                            }
                                        }
                                    )
                                }
                            }

                            // Канал/Группа
                            if (uiState.chatResult != null) {
                                item {
                                    GroupResultCard(
                                        result = uiState.chatResult!!,
                                        isJoining = uiState.isJoining,
                                        isExthru = isExthru,
                                        isDark = isDark,
                                        hapticEnabled = hapticEnabled,
                                        onJoinClick = {
                                            scope.launch {
                                                val chatId = viewModel.joinChatByTag(uiState.chatResult!!.tag)
                                                onJoinedGroup(chatId)
                                            }
                                        }
                                    )
                                }
                            }

                            // Не найдено
                            if (uiState.notFound && uiState.error == null) {
                                item {
                                    if (uiState.query.length > 10) {
                                        InviteTokenCard(
                                            token = uiState.query,
                                            isJoining = uiState.isJoining,
                                            isExthru = isExthru,
                                            isDark = isDark,
                                            hapticEnabled = hapticEnabled,
                                            onJoinClick = {
                                                scope.launch {
                                                    val chatId = viewModel.joinByInviteToken(uiState.query)
                                                    onJoinedGroup(chatId)
                                                }
                                            }
                                        )
                                    } else {
                                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (isExthru) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(80.dp)
                                                            .exthruSmallRaisedShadow(isDark)
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                                                            .border(1.dp, Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                } else {
                                                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                                }
                                                Spacer(Modifier.height(16.dp))
                                                Text(stringResource(R.string.search_not_found),
                                                    style = if (isExthru) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Cards & Components ───────────────────────────────────────────────────────

@Composable
private fun SearchResultCardWrapper(
    isExthru: Boolean,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isExthru) {
        val hazeState = LocalHazeState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .exthruRaisedShadow(isDark)
                .clip(RoundedCornerShape(20.dp))
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                )
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f))
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.15f else 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.4f else 0.05f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                ),
            content = content
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            content()
        }
    }
}

@Composable
private fun UserResultCard(
    user: UserProfile,
    isExthru: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onChatClick: () -> Unit
) {
    SearchResultCardWrapper(isExthru = isExthru, isDark = isDark) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarWithPresence(avatarUrl = user.avatarUrl, displayName = user.displayName,
                isOnline = user.online, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user.bio.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Spacer(Modifier.width(12.dp))

            if (isExthru) {
                NmButton(
                    text = stringResource(R.string.search_action_chat),
                    icon = Icons.Default.Chat,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    onClick = onChatClick
                )
            } else {
                Button(onClick = onChatClick, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.search_action_chat))
                }
            }
        }
    }
}

@Composable
private fun GroupResultCard(
    result: TagSearchResult,
    isJoining: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onJoinClick: () -> Unit
) {
    SearchResultCardWrapper(isExthru = isExthru, isDark = isDark) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupChannelAvatar(
                    avatarUrl = result.avatarUrl,
                    name = result.name,
                    isChannel = result.type == "channel",
                    isExthru = isExthru,
                    size = 48.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("@${result.tag}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (result.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(result.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("${result.memberCount} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (isExthru) {
                NmButton(
                    text = if (result.joinByTag) "Join" else "Join disabled",
                    isLoading = isJoining,
                    isEnabled = result.joinByTag && !isJoining,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onJoinClick
                )
            } else {
                Button(
                    onClick = onJoinClick,
                    enabled = result.joinByTag && !isJoining,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(if (result.joinByTag) "Join" else "Join disabled")
                }
            }
        }
    }
}

@Composable
private fun InviteTokenCard(
    token: String,
    isJoining: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onJoinClick: () -> Unit
) {
    SearchResultCardWrapper(isExthru = isExthru, isDark = isDark) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isExthru) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .exthruSmallRaisedShadow(isDark)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))
            Text("Nothing found by tag.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Try to join using this as an invite token?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (isExthru) {
                NmButton(
                    text = "Join via invite link",
                    isLoading = isJoining,
                    isEnabled = !isJoining,
                    isDark = isDark,
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onJoinClick
                )
            } else {
                Button(
                    onClick = onJoinClick,
                    enabled = !isJoining,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Join via invite link")
                }
            }
        }
    }
}

// ─── Вспомогательная физически нажимаемая неоморфная кнопка ──────────────────

@Composable
private fun NmButton(
    text: String,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    isDark: Boolean,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btn_scale"
    )

    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.6f)
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val shadowMod = if (isPressed && isEnabled) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = modifier
            .scale(scale)
            .then(if (isEnabled) shadowMod else Modifier)
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = if (isPressed || !isEnabled) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled && !isLoading,
                onClick = {
                    haptic.perform(HapticType.CLICK, hapticEnabled)
                    onClick()
                }
            )
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, fontWeight = FontWeight.Bold, color = contentColor)
            }
        }
    }
}