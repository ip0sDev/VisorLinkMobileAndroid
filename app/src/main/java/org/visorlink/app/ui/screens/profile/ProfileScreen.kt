package org.visorlink.app.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ProfileLayout
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun DebugUidBadge(uid: String, modifier: Modifier = Modifier) {
    if (!LocalShowDebugIds.current || uid.isBlank()) return
    val context = LocalContext.current
    val toastMessage = stringResource(R.string.uid_copied_toast)
    Surface(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UID", uid))
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            },
        shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "UID: $uid",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Свой профиль: отдельный экран или вкладка нижней навигации.
 *
 * @param asTab вкладка «Профиль» (флаг `enable_profile_navbar`): шапка — сама ID-карта вместо
 *   аватара (аватар и имя на ней есть), снизу — место под навбар. Пока профиль редактируется,
 *   шапка обычная: аватар меняется нажатием на него.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStickers: () -> Unit,
    asTab: Boolean = false,
    viewModel: ProfileViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.uploadAvatar(it) } }

    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        topBarJelly.pulse(0.06f)
    }

    val user = uiState.user ?: UserProfile()
    val isPro = user.isProActive()
    // Свой профиль всегда в своём оформлении — см. ProfileAppearance.resolve
    val appearance = ProfileAppearance.resolve(owner = user, viewer = user)
    val bgUrl = appearance.backgroundUrl

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    // Оформление владельца профиля (тема, акцент, шрифт) — поверх настроек зрителя
    UserProfileTheme(profile = user, currentUser = user, applyAccentHex = true) {
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
                        title = {
                            val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                            else stringResource(R.string.profile_title)
                            Text(titleText, fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                topBarJelly.press(0.06f)
                                if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            if (!uiState.isEditing) {
                                IconButton(onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    topBarJelly.press(0.06f)
                                    viewModel.startEditing()
                                }) {
                                    Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                                }

                                IconButton(onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    topBarJelly.press(0.06f)
                                    showLogout = true
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        topBarJelly.press(0.06f)
                                        viewModel.saveProfile()
                                    },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                },
                // На вкладке навбар закрыл бы снекбар
                snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (asTab) 96.dp else 0.dp)) }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (asTab && !uiState.isEditing) {
                        // Вкладка: карта и есть шапка; без карты (не выдана, маска) — аватар
                        org.visorlink.app.ui.idcard.ProfileIdCardHeader(user) {
                            ProfileHeader(user = user, appearance = appearance, isMe = true)
                        }
                    } else {
                        // ID-карта: место выбирает владелец (спека §4a)
                        org.visorlink.app.ui.idcard.ProfileIdCard(user, isMe = true, slot = org.visorlink.app.data.idcard.IdCardPosition.TOP)
                        ProfileHeader(
                            user = user,
                            appearance = appearance,
                            isMe = true,
                            onAvatarClick = if (uiState.isEditing) ({ avatarPicker.launch("image/*") }) else null,
                        )

                        org.visorlink.app.ui.idcard.ProfileIdCard(user, isMe = true, slot = org.visorlink.app.data.idcard.IdCardPosition.AFTER_HEADER)
                    }

                    if (!uiState.isEditing) {
                        ProfileDetails(user, isMe = true, idCardSlot = !asTab)
                    } else {
                        // Editing fields
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                                .liquidPillCardSlideOut(index = 1),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            VlTextField(
                                value = uiState.editDisplayName,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = stringResource(R.string.profile_field_display_name),
                                leading = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            VlTextField(
                                value = uiState.editBio, onValueChange = viewModel::onBioChange,
                                label = stringResource(R.string.profile_field_bio),
                                leading = { Icon(Icons.Default.Info, null) },
                                supportingText = "${uiState.editBio.length}/160",
                                maxLines = 3,
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )

                            VlTextField(
                                value = uiState.editUsername, onValueChange = viewModel::onUsernameChange,
                                label = stringResource(R.string.profile_field_username),
                                leading = { Icon(Icons.Default.AlternateEmail, null) },
                                trailing = {
                                    when {
                                        uiState.checkingUsername -> CircularProgressIndicator(
                                            Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary
                                        )
                                        uiState.usernameAvailable == true -> Icon(
                                            Icons.Default.CheckCircle, null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        uiState.usernameAvailable == false -> Icon(
                                            Icons.Default.Cancel, null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                isError = uiState.usernameAvailable == false,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(4.dp))

                            VlButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    viewModel.checkUsername()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.profile_check_username), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // На вкладке снизу плавает навбар
                    Spacer(Modifier.height(if (asTab) 120.dp else 32.dp))
                }
            }
        }

        if (showLogout) {
            VlAlertDialog(
                onDismissRequest = { showLogout = false },
                title = { Text(stringResource(R.string.dialog_logout_title)) },
                text  = { Text(stringResource(R.string.dialog_logout_body)) },
                confirmButton = {
                    VlDialogButton(onClick = {
                        showLogout = false; viewModel.logout(); onLoggedOut()
                    }, isDestructive = true) {
                        Text(stringResource(R.string.dialog_logout_confirm))
                    }
                },
                dismissButton = {
                    VlDialogButton(onClick = { showLogout = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
