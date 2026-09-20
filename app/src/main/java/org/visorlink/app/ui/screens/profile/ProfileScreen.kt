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
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import coil.compose.AsyncImage
import org.koin.compose.koinInject
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
        shape = RoundedCornerShape(8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStickers: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.uploadAvatar(it) } }

    val flagsRepository: FlagsRepository = koinInject()
    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    val user = uiState.user ?: UserProfile()
    val isPro = user.isProActive()
    val cust = user.customization ?: emptyMap()
    val layout = cust["layout"] as? String ?: "default"
    val bgUrl = (cust["bgUrl"] as? String)?.takeIf { it.isNotBlank() }
    val gifUrl = (cust["gifUrl"] as? String)?.takeIf { it.isNotBlank() }
    val bannerUrl = gifUrl?.takeIf { isPro }

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

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
                    title = {
                        val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                        else stringResource(R.string.profile_title)
                        Text(titleText, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (!uiState.isEditing) {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                if (isLiquidEnabled) topBarJelly.press(0.06f)
                                viewModel.startEditing()
                            }) {
                                Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                            }

                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                if (isLiquidEnabled) topBarJelly.press(0.06f)
                                showLogout = true
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    if (isLiquidEnabled) topBarJelly.press(0.06f)
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
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
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
                            .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                            .clip(VlTheme.tokens.shapes.avatar),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarContent(user, 80.dp)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val emojis = cust["emojis"] as? String
                            if (!emojis.isNullOrEmpty()) {
                                Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                            }
                        }
                        Text(
                            "@${user.username}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                                .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                .clip(VlTheme.tokens.shapes.avatar),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarContent(user, 110.dp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .size(130.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, VlTheme.tokens.shapes.avatar)
                            .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                            .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                            .clip(VlTheme.tokens.shapes.avatar),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarContent(user, 130.dp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName,
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
                    "@${user.username}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

                if (!uiState.isEditing) {
                    // Profile body
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DebugUidBadge(uid = user.uid)
                        if (user.online) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Color.Green, VlTheme.tokens.shapes.indicator))
                                Spacer(Modifier.width(6.dp))
                                Text("Online", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        if (user.isAdmin) {
                            Spacer(Modifier.height(16.dp))
                            AdminBadge()
                        }

                        if (isPro) {
                            Spacer(Modifier.height(12.dp))
                            ProBadge()
                        }

                        if (user.bio.isNotEmpty()) {
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
                                        text = user.bio,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Editing fields
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled),
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
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.dialog_logout_title)) },
            text  = { Text(stringResource(R.string.dialog_logout_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false; viewModel.logout(); onLoggedOut()
                }) {
                    Text(stringResource(R.string.dialog_logout_confirm),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
