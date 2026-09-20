package org.visorlink.app.ui.screens.profile

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.visorlink.app.utils.CustomizationHelper
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import org.visorlink.app.ui.components.chat.ReportContentDialog
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
    val flagsRepository: FlagsRepository = koinInject()
    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    val isBlocked = currentUser?.blockedUserIds?.contains(uid) == true

    val targetUser = user
    val cust = if (targetUser != null && CustomizationHelper.shouldApplyCustomization(targetUser, currentUser)) {
        targetUser.customization ?: emptyMap()
    } else emptyMap()

    val layout = cust["layout"] as? String ?: "default"
    val bgUrl = (cust["bgUrl"] as? String)?.takeIf { it.isNotBlank() }
    val gifUrl = (cust["gifUrl"] as? String)?.takeIf { it.isNotBlank() }
    val bannerUrl = gifUrl?.takeIf { targetUser?.isProActive() == true } ?: gifUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.40f)
                        else Color.White.copy(alpha = 0.20f)
                    )
            )
        } else {
            VlAmbientGlow()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                VlTopAppBar(
                    modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                    title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            showMenu = true
                        }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_report)) },
                                leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                            if (isBlocked) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_unblock_user)) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            userRepository.unblockUser(uid)
                                            Toast.makeText(context, context.getString(R.string.user_unblocked_toast), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_block_user), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showBlockConfirm = true
                                    }
                                )
                            }
                        }
                    }
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
            if (targetUser == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (layout == "compact") {
                    if (bannerUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            AsyncImage(
                                model = bannerUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = if (bannerUrl != null) 16.dp else 24.dp),
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
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    targetUser.displayName,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
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
                    // Default / Banner Layout: полоска баннера НАД аватаркой
                    if (bannerUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .padding(bottom = 50.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AsyncImage(
                                    model = bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .clip(VlTheme.tokens.shapes.avatar),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(targetUser, 110.dp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = 24.dp)
                                .size(130.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow, VlTheme.tokens.shapes.avatar)
                                .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                .clip(VlTheme.tokens.shapes.avatar),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarContent(targetUser, 130.dp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            targetUser.displayName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val emojis = cust["emojis"] as? String
                        if (!emojis.isNullOrEmpty()) {
                            Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = 22.sp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "@${targetUser.username}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Profile body
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DebugUidBadge(uid = targetUser.uid)
                    if (targetUser.online) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Color.Green, VlTheme.tokens.shapes.indicator))
                            Spacer(Modifier.width(6.dp))
                            Text("Online", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
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
                        VlCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = VlTheme.tokens.shapes.card
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LinkifiedText(
                                    text = targetUser.bio,
                                    color = MaterialTheme.colorScheme.onSurface,
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

    if (showReportDialog) {
        ReportContentDialog(
            targetType = "user",
            targetId = uid,
            targetSenderUid = uid,
            onDismiss = { showReportDialog = false },
            onReportSubmitted = {
                showReportDialog = false
                Toast.makeText(context, context.getString(R.string.report_submitted_toast), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(stringResource(R.string.block_user_confirm_title)) },
            text = { Text(stringResource(R.string.block_user_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    scope.launch {
                        userRepository.blockUser(uid)
                        Toast.makeText(context, context.getString(R.string.user_blocked_toast), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.action_block_user), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
