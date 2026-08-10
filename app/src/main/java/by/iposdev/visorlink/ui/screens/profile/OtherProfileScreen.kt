package by.iposdev.visorlink.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.utils.CustomizationHelper
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    uid: String,
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String) -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val viewModel: OtherProfileViewModel = koinViewModel(parameters = { parametersOf(uid) })
    val user by viewModel.user.collectAsState()
    val currentUser by userRepository.currentUserFlow().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    UserProfileTheme(profile = user, currentUser = currentUser) {
        val currentTheme by themeViewModel.appTheme.collectAsState()
        val effectiveTheme = LocalAppThemeOverride.current ?: currentTheme
        
        val cs = MaterialTheme.colorScheme
        val isDarkTheme = cs.surface.luminance() < 0.5f
        val style = rememberExthruStyle(effectiveTheme)
        val isForgeTheme = style.isForge
        val haptic = rememberHaptic()
        val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

        val targetUser = user
        val cust = if (CustomizationHelper.shouldApplyCustomization(targetUser, currentUser)) {
            targetUser?.customization ?: emptyMap()
        } else emptyMap()

        val layout = cust["layout"] as? String ?: "default"
        val bgUrl = cust["bgUrl"] as? String
        val gifUrl = cust["gifUrl"] as? String
        val bannerUrl = gifUrl ?: bgUrl

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = if (isForgeTheme) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (bannerUrl != null) 0.6f else 1f),
                    modifier = if (!isForgeTheme) Modifier.nmDividerBottom(isDarkTheme) else Modifier
                ) {
                    TopAppBar(
                        title = {
                            val titleText = stringResource(R.string.profile_title)
                            Text(
                                text = if (isForgeTheme) "> ${titleText.uppercase()}_" else titleText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isForgeTheme) style.accent else Color.Unspecified
                            )
                        },
                        navigationIcon = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "back_scale")

                            val shape = if (isForgeTheme) RectangleShape else CircleShape
                            val shadowMod = if (isForgeTheme) {
                                Modifier.forgeNeuBrutalism(isPressed, isDarkTheme, 3.dp)
                            } else if (isPressed) {
                                Modifier.nmInsetShadow(isDarkTheme, cornerRadius = 21.dp, darkAlpha = if (isDarkTheme) 0.6f else 0.35f)
                            } else {
                                Modifier.exthruSmallRaisedShadow(isDarkTheme)
                            }

                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 4.dp)
                                    .size(42.dp)
                                    .scale(if (isForgeTheme) 1f else scale)
                                    .then(shadowMod)
                                    .background(if (isForgeTheme) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (bannerUrl != null) 0.5f else 1f), shape)
                                    .then(if (isForgeTheme) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDarkTheme) 0.05f else 0.3f), shape))
                                    .clip(shape)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        onNavigateBack()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.action_back),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            floatingActionButton = {
                if (targetUser != null) {
                    NmExtendedFab(
                        text = stringResource(R.string.other_profile_message),
                        icon = Icons.Default.Chat,
                        isDark = isDarkTheme,
                        isForge = isForgeTheme,
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            scope.launch {
                                val chatId = viewModel.openOrCreateChat()
                                onOpenChat(chatId, viewModel.targetUid)
                            }
                        }
                    )
                }
            }
        ) { padding ->
            if (targetUser == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val u = targetUser
                Box(Modifier.fillMaxSize()) {
                    if (bgUrl != null) {
                        AsyncImage(
                            model = bgUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(if (bgUrl != null) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = if (layout == "banner") Alignment.Start else Alignment.CenterHorizontally
                    ) {
                        val avatarShape = if (isForgeTheme) RectangleShape else CircleShape

                        if (layout == "banner") {
                            Box(contentAlignment = Alignment.BottomStart) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(bottom = 40.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    if (bannerUrl != null) {
                                        AsyncImage(
                                            model = bannerUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.padding(start = 24.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(style.accent.copy(alpha = 0.2f), CircleShape)
                                            .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AvatarContent(u, 100.dp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.padding(bottom = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                u.displayName,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                                color = Color.White
                                            )
                                            val emojis = cust["emojis"] as? String
                                            if (!emojis.isNullOrEmpty()) {
                                                Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                            }
                                        }
                                        Text(
                                            "@${u.username}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = style.accent
                                        )
                                    }
                                }
                            }
                        } else if (layout == "compact") {
                            Row(
                                modifier = Modifier
                                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(style.accent.copy(alpha = 0.2f), CircleShape)
                                        .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarContent(u, 80.dp)
                                }
                                Spacer(Modifier.width(24.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            u.displayName,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                            color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        val emojis = cust["emojis"] as? String
                                        if (!emojis.isNullOrEmpty()) {
                                            Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                        }
                                    }
                                    Text("@${u.username}", style = MaterialTheme.typography.bodyLarge, color = style.accent)
                                }
                            }
                        } else {
                            // Default Layout
                            Column(
                                modifier = Modifier.padding(top = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(style.accent.copy(alpha = 0.2f), CircleShape)
                                        .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarContent(u, 140.dp)
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        u.displayName,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    val emojis = cust["emojis"] as? String
                                    if (!emojis.isNullOrEmpty()) {
                                        Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = 22.sp)
                                    }
                                }
                                Text("@${u.username}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = style.accent)
                            }
                        }

                        // Profile body
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (u.online) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(Color.Green, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text("В сети", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            if (layout != "banner" && !gifUrl.isNullOrEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                AsyncImage(
                                    model = gifUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(32.dp))
                            }

                            if (u.isAdmin) {
                                Spacer(Modifier.height(16.dp))
                                AdminBadge()
                            }

                            if (u.isProActive()) {
                                Spacer(Modifier.height(12.dp))
                                ProBadge()
                            }

                            if (u.bio.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                LinkifiedText(
                                    text = u.bio,
                                    color = if (bgUrl != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NmExtendedFab(
    text: String,
    icon: ImageVector,
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
