package org.visorlink.app.ui.screens.profile

import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ProfileLayout
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
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        topBarJelly.pulse(0.06f)
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
    // PRO-проверка и ignoreCustomizations — внутри resolve, для всех полей сразу
    val appearance = ProfileAppearance.resolve(owner = targetUser, viewer = currentUser)
    val bgUrl = appearance.backgroundUrl

    // Оформление владельца профиля (тема, акцент, шрифт) — поверх настроек зрителя
    // Смотрящий с особым режимом видит чужой профиль в гамме владельца (спека §7)
    val idModeState = org.visorlink.app.ui.idcard.LocalIdModeState.current
    val ownerModeTheme = org.visorlink.app.data.idcard.ModeThemeRules.ownerProfileTheme(
        viewerSpecial = idModeState.viewerSpecial,
        isMe = targetUser?.uid == idModeState.myUid,
        ownerMode = targetUser?.idMode,
    )
    UserProfileTheme(profile = targetUser, currentUser = currentUser, applyAccentHex = true, ownerModeTheme = ownerModeTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ProfileBackdrop(bgUrl)

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    VlTopAppBar(
                        modifier = Modifier.liquidJelly(topBarJelly),
                        title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                topBarJelly.press(0.06f)
                                onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                topBarJelly.press(0.06f)
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
                                if (flags.isEnabled("enable_alternative_outbox")) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.emergency_chat_title)) },
                                        leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showMenu = false
                                            scope.launch {
                                                try {
                                                    val emerChatId = viewModel.openOrCreateEmergencyChat()
                                                    onOpenChat(emerChatId, viewModel.targetUid)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, e.message ?: "Failed to open emergency chat", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
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
                        // ID-карта: место выбирает владелец (спека §4a)
                        org.visorlink.app.ui.idcard.ProfileIdCard(targetUser, isMe = false, slot = org.visorlink.app.data.idcard.IdCardPosition.TOP, viewer = currentUser)
                        ProfileHeader(user = targetUser, appearance = appearance, isMe = false)
                        org.visorlink.app.ui.idcard.ProfileIdCard(targetUser, isMe = false, slot = org.visorlink.app.data.idcard.IdCardPosition.AFTER_HEADER, viewer = currentUser)
                        ProfileDetails(targetUser, isMe = false, viewer = currentUser)
                        // Запас под кнопку «Написать»: без него FAB закрывал конец описания и карту
                        Spacer(Modifier.height(96.dp))
                    }
            }
        }
    }

        if (showReportDialog) {
            ReportContentDialog(
                targetType = "user",
                targetId = uid,
                chatId = null,
                onDismiss = { showReportDialog = false },
                onReportSubmitted = {
                    showReportDialog = false
                    Toast.makeText(context, context.getString(R.string.report_submitted_toast), Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showBlockConfirm) {
            VlAlertDialog(
                onDismissRequest = { showBlockConfirm = false },
                title = { Text(stringResource(R.string.block_user_confirm_title)) },
                text = { Text(stringResource(R.string.block_user_confirm_desc)) },
                confirmButton = {
                    VlDialogButton(onClick = {
                        showBlockConfirm = false
                        scope.launch {
                            userRepository.blockUser(uid)
                            Toast.makeText(context, context.getString(R.string.user_blocked_toast), Toast.LENGTH_SHORT).show()
                        }
                    }, isDestructive = true) {
                        Text(stringResource(R.string.action_block_user))
                    }
                },
                dismissButton = {
                    VlDialogButton(onClick = { showBlockConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
