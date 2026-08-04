package by.iposdev.visorlink.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
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
import by.iposdev.visorlink.data.model.isExthruFamily
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

    val isExthru = currentTheme.isExthruFamily
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val haptic = rememberHaptic()

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isForge) style.cardBg else if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru && !isForge) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        val titleText = user?.displayName ?: stringResource(R.string.profile_title)
                        if (isExthru) {
                            Text(
                                text = if (isForge) "> ${titleText.uppercase()}_" else titleText,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = if (isForge) 28.sp else 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isForge) FontFamily.Monospace else null
                                ),
                                color = if (isForge) style.accent else Color.Unspecified
                            )
                        } else {
                            Text(titleText)
                        }
                    },
                    navigationIcon = {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "back_scale")

                        val shape = if (isForge) RectangleShape else CircleShape
                        val shadowMod = if (isForge) {
                            Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
                        } else if (isPressed) {
                            Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                        } else {
                            Modifier.exthruSmallRaisedShadow(isDark)
                        }

                        val btnModifier = if (isExthru) Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(42.dp)
                            .scale(if(isForge) 1f else scale)
                            .then(shadowMod)
                            .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface, shape)
                            .then(if (isForge) Modifier else Modifier.border(1.dp, if(isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                            .clip(shape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            },
                            modifier = btnModifier,
                            interactionSource = interactionSource
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                                modifier = if (isExthru) Modifier.size(20.dp) else Modifier,
                                tint = if (isExthru) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
                        isForge = isForge,
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

            // ── Avatar ──
            val avatarShape = if (isForge) RectangleShape else CircleShape
            val avatarModifier = if (isForge) {
                Modifier
                    .size(120.dp)
                    .forgeNeuBrutalism(false, isDark, 6.dp)
                    .background(style.inputBg, avatarShape)
            } else if (isExthru) {
                Modifier
                    .size(110.dp)
                    .exthruSmallRaisedShadow(isDark)
                    .background(MaterialTheme.colorScheme.surfaceVariant, avatarShape)
                    .clip(avatarShape)
            } else {
                Modifier
                    .size(100.dp)
                    .clip(avatarShape)
            }

            Box(avatarModifier) {
                if (!u.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(model = u.avatarUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(avatarShape), contentScale = ContentScale.Crop)
                } else {
                    Surface(
                        color = if (isExthru && !isForge) Color.Transparent else if (isForge) style.inputBg else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                u.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineLarge,
                                fontFamily = if (isForge) FontFamily.Monospace else null,
                                color = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(u.displayName,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                fontFamily = if (isForge) FontFamily.Monospace else null,
                color = if (isExthru) MaterialTheme.colorScheme.onSurface else Color.Unspecified)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${u.username}", style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.primary)

                if (u.online) {
                    Spacer(Modifier.width(8.dp))

                    val badgeBg = if (isExthru) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
                    val badgeColor = if (isExthru) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

                    val badgeShape = if (isForge) RectangleShape else RoundedCornerShape(8.dp)
                    val badgeModifier = if (isForge) {
                        Modifier
                            .forgeNeuBrutalism(false, isDark, 2.dp)
                            .background(badgeBg, badgeShape)
                    } else if (isExthru) {
                        Modifier
                            .exthruSmallRaisedShadow(isDark)
                            .background(badgeBg, badgeShape)
                    } else {
                        Modifier.background(badgeBg, MaterialTheme.shapes.extraSmall)
                    }

                    Box(modifier = badgeModifier) {
                        Text(
                            stringResource(R.string.other_profile_online),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = if (isForge) FontFamily.Monospace else null,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (u.bio.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(u.bio, style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }
}

// ─── Неоморфный FAB для Exthru / Forge ────────────────────────────────────────

@Composable
private fun NmExtendedFab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    isForge: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "fab_scale"
    )

    val shape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 4.dp)
    } else if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = Modifier
            .scale(if(isForge) 1f else scale)
            .then(shadowMod)
            .background(MaterialTheme.colorScheme.surface, shape)
            .then(if(isForge) Modifier else Modifier.border(1.dp, if(isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
            .clip(shape)
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
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isForge) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}