package by.iposdev.visorlink.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

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

    val currentUser = uiState.user

    UserProfileTheme(profile = currentUser, currentUser = currentUser) {
        val currentTheme by themeViewModel.appTheme.collectAsState()
        val effectiveTheme = LocalAppThemeOverride.current ?: currentTheme
        val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

        val isExthru = effectiveTheme.isExthruFamily
        val style = rememberExthruStyle(effectiveTheme)
        val isForge = style.isForge
        
        val cs = MaterialTheme.colorScheme
        val isDark = cs.surface.luminance() < 0.5f

        val haptic = rememberHaptic()

        val user = uiState.user
        val isPro = user?.isProActive() == true
        val cust = user?.customization ?: emptyMap()
        val layout = cust["layout"] as? String ?: "default"
        val bgUrl = cust["bgUrl"] as? String
        val gifUrl = cust["gifUrl"] as? String
        val bannerUrl = gifUrl ?: bgUrl

        LaunchedEffect(uiState.successMessage) {
            uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
        }
        LaunchedEffect(uiState.error) {
            uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.6f else 1f),
                    modifier = if (!isForge) Modifier.nmDividerBottom(isDark) else Modifier
                ) {
                    TopAppBar(
                        title = {
                            val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                            else stringResource(R.string.profile_title)
                            Text(
                                text = if (isForge) "> ${titleText.uppercase()}_" else titleText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isForge) style.accent else Color.Unspecified
                            )
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

                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 4.dp)
                                    .size(42.dp)
                                    .scale(if (isForge) 1f else scale)
                                    .then(shadowMod)
                                    .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                                    .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                    .clip(shape)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
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
                        actions = {
                            if (!uiState.isEditing) {
                                val intEdit = remember { MutableInteractionSource() }
                                val isEditPressed by intEdit.collectIsPressedAsState()
                                val editScale by animateFloatAsState(if (isEditPressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "edit_scale")

                                val shape = if (isForge) RectangleShape else CircleShape
                                val editShadow = if (isForge) {
                                    Modifier.forgeNeuBrutalism(isEditPressed, isDark, 3.dp)
                                } else if (isEditPressed) {
                                    Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                                } else {
                                    Modifier.exthruSmallRaisedShadow(isDark)
                                }

                                IconButton(
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        viewModel.startEditing()
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .scale(if (isForge) 1f else editScale)
                                        .then(editShadow)
                                        .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                                        .then(if (isForge) Modifier else Modifier.border(1.dp, if (isEditPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                        .clip(shape),
                                    interactionSource = intEdit
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        stringResource(R.string.action_edit),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                val intLogout = remember { MutableInteractionSource() }
                                val isLogoutPressed by intLogout.collectIsPressedAsState()
                                val logoutScale by animateFloatAsState(if (isLogoutPressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "logout_scale")

                                val logoutShadow = if (isForge) {
                                    Modifier.forgeNeuBrutalism(isLogoutPressed, isDark, 3.dp)
                                } else if (isLogoutPressed) {
                                    Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                                } else {
                                    Modifier.exthruSmallRaisedShadow(isDark)
                                }

                                IconButton(
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        showLogout = true
                                    },
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(42.dp)
                                        .scale(if (isForge) 1f else logoutScale)
                                        .then(logoutShadow)
                                        .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                                        .then(if (isForge) Modifier else Modifier.border(1.dp, if (isLogoutPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                        .clip(shape),
                                    interactionSource = intLogout
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                NmButton(
                                    text = stringResource(R.string.action_save),
                                    isEnabled = !uiState.isLoading,
                                    isDark = isDark,
                                    isForge = isForge,
                                    modifier = Modifier.padding(end = 16.dp),
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        viewModel.saveProfile()
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                if (isPro && bgUrl != null) {
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
                        .background(if (isPro && bgUrl != null) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = if (layout == "banner") Alignment.Start else Alignment.CenterHorizontally
                ) {
                    if (isPro && bannerUrl != null) {
                        Box(contentAlignment = Alignment.BottomStart) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(bottom = 40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AsyncImage(
                                    model = bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
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
                                        .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarContent(user!!, 100.dp)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.padding(bottom = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.displayName,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                            color = Color.White
                                        )
                                        val emojis = cust["emojis"] as? String
                                        if (!emojis.isNullOrEmpty()) {
                                            Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                        }
                                    }
                                    Text(
                                        "@${user?.username}",
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
                                    .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(user ?: UserProfile(), 80.dp)
                            }
                            Spacer(Modifier.width(24.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user?.displayName ?: "",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    val emojis = cust["emojis"] as? String
                                    if (!emojis.isNullOrEmpty()) {
                                        Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                    }
                                }
                                Text("@${user?.username}", style = MaterialTheme.typography.bodyLarge, color = style.accent)
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
                                    .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(user ?: UserProfile(), 140.dp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user?.displayName ?: "",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                val emojis = cust["emojis"] as? String
                                if (!emojis.isNullOrEmpty()) {
                                    Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = 22.sp)
                                }
                            }
                            Text("@${user?.username}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = style.accent)
                        }
                    }

                    if (!uiState.isEditing) {
                        // Profile body
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (user?.online == true) {
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

                            if (user?.isAdmin == true) {
                                Spacer(Modifier.height(16.dp))
                                AdminBadge()
                            }

                            if (isPro) {
                                Spacer(Modifier.height(12.dp))
                                ProBadge()
                            }

                            if (!user?.bio.isNullOrEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                LinkifiedText(
                                    text = user?.bio ?: "",
                                    color = if (bgUrl != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Editing fields
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isExthru) 16.dp else 12.dp)
                        ) {
                            val tfShape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)
                            val nmColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent
                            )

                            val buildModifier: @Composable (Modifier) -> Modifier = { m ->
                                if (isForge) {
                                    m.fillMaxWidth()
                                        .forgeNeuBrutalism(false, isDark, 3.dp)
                                        .background(style.inputBg, tfShape)
                                } else if (isExthru) {
                                    m.fillMaxWidth()
                                        .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                                        .background(MaterialTheme.colorScheme.surface, tfShape)
                                } else m.fillMaxWidth()
                            }

                            OutlinedTextField(
                                value = uiState.editDisplayName,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = { Text(stringResource(R.string.profile_field_display_name)) },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = buildModifier(Modifier),
                                shape = tfShape,
                                colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                            )

                            OutlinedTextField(
                                value = uiState.editBio, onValueChange = viewModel::onBioChange,
                                label = { Text(stringResource(R.string.profile_field_bio)) },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                supportingText = { Text("${uiState.editBio.length}/160") },
                                maxLines = 3,
                                modifier = buildModifier(Modifier),
                                shape = tfShape,
                                colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                            )

                            OutlinedTextField(
                                value = uiState.editUsername, onValueChange = viewModel::onUsernameChange,
                                label = { Text(stringResource(R.string.profile_field_username)) },
                                leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                                trailingIcon = {
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
                                modifier = buildModifier(Modifier),
                                shape = tfShape,
                                colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                            )

                            Spacer(Modifier.height(4.dp))

                            NmButton(
                                text = stringResource(R.string.profile_check_username),
                                isDark = isDark,
                                isForge = isForge,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    viewModel.checkUsername()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.dialog_logout_title)) },
            text  = { Text(stringResource(R.string.dialog_logout_body)) },
            containerColor = MaterialTheme.colorScheme.surface,
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
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

// ─── Вспомогательные компоненты ────────────────────────────────────────────────

@Composable
private fun NmButton(
    text: String,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isDark: Boolean,
    isForge: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && isEnabled) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "btn_scale")

    val bgColor = MaterialTheme.colorScheme.surface
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val shape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed && isEnabled, isDark, 3.dp)
    } else if (isPressed && isEnabled) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
    } else if (isEnabled) {
        Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = modifier
            .scale(if(isForge) 1f else scale)
            .then(shadowMod)
            .background(bgColor, shape)
            .then(if(isForge) Modifier else Modifier.border(1.dp, if (isPressed || !isEnabled) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontWeight = FontWeight.Bold, fontFamily = if(isForge) FontFamily.Monospace else null, color = contentColor)
        }
    }
}
