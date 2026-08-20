package by.iposdev.visorlink.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import by.iposdev.visorlink.utils.CustomizationHelper
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        floatingActionButton = {
            if (user != null) {
                VlFab(
                    text = stringResource(R.string.other_profile_message),
                    icon = Icons.Default.Chat,
                    onClick = {
                        scope.launch {
                            val chatId = viewModel.openOrCreateChat()
                            onOpenChat(chatId, viewModel.targetUid)
                        }
                    },
                    hapticEnabled = hapticEnabled
                )
            }
        }
    ) { padding ->
        val targetUser = user
        if (targetUser == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val cust = if (CustomizationHelper.shouldApplyCustomization(targetUser, currentUser)) {
                targetUser.customization ?: emptyMap()
            } else emptyMap()

            val layout = cust["layout"] as? String ?: "default"
            val bgUrl = cust["bgUrl"] as? String
            val gifUrl = cust["gifUrl"] as? String
            val bannerUrl = gifUrl ?: bgUrl

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
                        .background(if (bgUrl != null) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = if (layout == "banner") Alignment.Start else Alignment.CenterHorizontally
                ) {
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
                                        .background(MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                        .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                        .clip(VlTheme.tokens.shapes.avatar),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarContent(targetUser, 100.dp)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.padding(bottom = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            targetUser.displayName,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                            color = Color.White
                                        )
                                        val emojis = cust["emojis"] as? String
                                        if (!emojis.isNullOrEmpty()) {
                                            Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                        }
                                    }
                                    Text(
                                        "@${targetUser.username}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
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
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow, VlTheme.tokens.shapes.avatar)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .clip(VlTheme.tokens.shapes.avatar),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(targetUser, 80.dp)
                            }
                            Spacer(Modifier.width(24.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        targetUser.displayName,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    val emojis = cust["emojis"] as? String
                                    if (!emojis.isNullOrEmpty()) {
                                        Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                    }
                                }
                                Text("@${targetUser.username}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
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
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow, VlTheme.tokens.shapes.avatar)
                                    .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .clip(VlTheme.tokens.shapes.avatar),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(targetUser, 140.dp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    targetUser.displayName,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                val emojis = cust["emojis"] as? String
                                if (!emojis.isNullOrEmpty()) {
                                    Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = 22.sp)
                                }
                            }
                            Text("@${targetUser.username}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Profile body
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (targetUser.online) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Color.Green, VlTheme.tokens.shapes.indicator))
                                Spacer(Modifier.width(6.dp))
                                Text("Online", style = MaterialTheme.typography.bodyMedium, color = if (bgUrl != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    .clip(VlTheme.tokens.shapes.button),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(32.dp))
                        }

                        if (targetUser.isAdmin) {
                            Spacer(Modifier.height(16.dp))
                            AdminBadge()
                        }

                        if (targetUser.isProActive()) {
                            Spacer(Modifier.height(12.dp))
                            ProBadge()
                        }

                        if (targetUser.bio.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            LinkifiedText(
                                text = targetUser.bio,
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
