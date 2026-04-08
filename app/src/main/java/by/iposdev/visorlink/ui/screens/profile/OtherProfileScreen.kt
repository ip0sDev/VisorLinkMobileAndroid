package by.iposdev.visorlink.ui.screens.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    uid: String,
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val viewModel: OtherProfileViewModel = koinViewModel(parameters = { parametersOf(uid) })
    val user by viewModel.user.collectAsState()
    val scope = rememberCoroutineScope()

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        val titleText = user?.displayName ?: stringResource(R.string.profile_title)
                        if (isExthru) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Text(titleText)
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
        },
        floatingActionButton = {
            if (user != null) {
                if (isExthru) {
                    NmExtendedFab(
                        text = stringResource(R.string.other_profile_message),
                        icon = Icons.Default.Chat,
                        isDark = isDark,
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            scope.launch {
                                val chatId = viewModel.openOrCreateChat()
                                onOpenChat(chatId, viewModel.targetUid)
                            }
                        }
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            scope.launch {
                                val chatId = viewModel.openOrCreateChat()
                                onOpenChat(chatId, viewModel.targetUid)
                            }
                        },
                        icon = { Icon(Icons.Default.Chat, null) },
                        text = { Text(stringResource(R.string.other_profile_message)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        if (user == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        val u = user!!
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Avatar
            val avatarModifier = if (isExthru) {
                Modifier
                    .size(110.dp)
                    .exthruSmallRaisedShadow(isDark)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clip(CircleShape)
            } else {
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            }

            Box(avatarModifier) {
                if (!u.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(model = u.avatarUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Surface(
                        color = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                u.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineLarge,
                                color = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(u.displayName,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = if (isExthru) MaterialTheme.colorScheme.onSurface else Color.Unspecified)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${u.username}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)

                if (u.online) {
                    Spacer(Modifier.width(8.dp))

                    val badgeBg = if (isExthru) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
                    // Для онлайна в Exthru используем MangoGlow (он же tertiary в теме)
                    val badgeColor = if (isExthru) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

                    val badgeModifier = if (isExthru) {
                        Modifier
                            .exthruSmallRaisedShadow(isDark)
                            .background(badgeBg, RoundedCornerShape(8.dp))
                    } else {
                        Modifier.background(badgeBg, MaterialTheme.shapes.extraSmall)
                    }

                    Box(modifier = badgeModifier) {
                        Text(
                            stringResource(R.string.other_profile_online),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (u.bio.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(u.bio, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}

// ─── Неоморфный FAB для Exthru ────────────────────────────────────────────────

@Composable
private fun NmExtendedFab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "fab_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .exthruSmallRaisedShadow(isDark)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}