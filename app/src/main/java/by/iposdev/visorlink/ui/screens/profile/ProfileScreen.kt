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
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
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

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val isExthru = currentTheme.isExthruFamily
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    val haptic = rememberHaptic()

    val user = uiState.user
    val isPro = user?.isProActive() == true
    val cust = user?.customization ?: emptyMap()
    val layout = cust["layout"] as? String ?: "default"
    val bgUrl = cust["bgUrl"] as? String
    val gifUrl = cust["gifUrl"] as? String
    val bannerUrl = gifUrl ?: bgUrl
    
    val fontName = cust["font"] as? String
    val customFont = when(fontName) {
        "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> if (isForge) FontFamily.Monospace else null
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isForge) style.cardBg else if (isExthru) MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.6f else 1f) else Color.Transparent,
                modifier = if (isExthru && !isForge) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                        else stringResource(R.string.profile_title)
                        if (isExthru) {
                            Text(
                                text = if (isForge) "> ${titleText.uppercase()}_" else titleText,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = if (isForge) 28.sp else 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = customFont
                                ),
                                color = if (isForge) style.accent else Color.Unspecified
                            )
                        } else {
                            Text(titleText, fontFamily = customFont)
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
                            .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                            .then(if(isForge) Modifier else Modifier.border(1.dp, if(isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                            .clip(shape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
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

                            val editMod = if (isExthru) Modifier
                                .size(42.dp)
                                .scale(if(isForge) 1f else editScale)
                                .then(editShadow)
                                .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                                .then(if(isForge) Modifier else Modifier.border(1.dp, if(isEditPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                .clip(shape)
                            else Modifier

                            IconButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    viewModel.startEditing()
                                },
                                modifier = editMod,
                                interactionSource = intEdit
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    stringResource(R.string.action_edit),
                                    modifier = if (isExthru) Modifier.size(20.dp) else Modifier,
                                    tint = if (isExthru) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }

                            if (isExthru) Spacer(Modifier.width(10.dp))

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

                            val logoutMod = if (isExthru) Modifier
                                .then(if(isExthru) Modifier.padding(end = 12.dp) else Modifier)
                                .size(42.dp)
                                .scale(if(isForge) 1f else logoutScale)
                                .then(logoutShadow)
                                .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isPro && bannerUrl != null) 0.5f else 1f), shape)
                                .then(if(isForge) Modifier else Modifier.border(1.dp, if(isLogoutPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                .clip(shape)
                            else Modifier

                            IconButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    showLogout = true
                                },
                                modifier = logoutMod,
                                interactionSource = intLogout
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    null,
                                    tint = if (isExthru) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                                )
                            }
                        } else {
                            if (isExthru) {
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
                            } else {
                                TextButton(
                                    onClick = { viewModel.saveProfile() },
                                    enabled = !uiState.isLoading
                                ) {
                                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
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
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height((-40).dp))
                } else {
                    Spacer(Modifier.height(32.dp))
                }

                // ── Avatar ──
                val avatarShape = if (isForge) RectangleShape else CircleShape
                val avatarModifier = if (isForge) {
                    Modifier
                        .size(if (layout == "compact") 80.dp else 120.dp)
                        .forgeNeuBrutalism(false, isDark, 6.dp)
                        .background(style.inputBg, avatarShape)
                } else if (isExthru) {
                    Modifier
                        .size(if (layout == "compact") 80.dp else 110.dp)
                        .exthruSmallRaisedShadow(isDark)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (bgUrl != null) 0.5f else 1f), avatarShape)
                        .clip(avatarShape)
                } else {
                    Modifier
                        .size(if (layout == "compact") 70.dp else 100.dp)
                        .clip(avatarShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                }

                val avatarContainerPadding = if (layout == "banner") 20.dp else 0.dp

                Box(Modifier.size(if (layout == "compact") 80.dp else 120.dp).padding(start = avatarContainerPadding), contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = avatarModifier
                            .then(if (uiState.isEditing)
                                Modifier.clickable { avatarPicker.launch("image/*") }
                            else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!uiState.user?.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(model = uiState.user?.avatarUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(avatarShape), contentScale = ContentScale.Crop)
                        } else {
                            Text(uiState.user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                style = if (layout == "compact") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                                fontFamily = customFont,
                                color = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    if (uiState.isEditing) {
                        val cameraBg = if (isExthru) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                        val cameraFg = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        val cameraShape = if (isForge) RectangleShape else CircleShape

                        val cameraMod = if (isForge) {
                            Modifier.size(38.dp).forgeNeuBrutalism(false, isDark, 3.dp).background(cameraBg, cameraShape)
                        } else if (isExthru) {
                            Modifier.size(36.dp).exthruSmallRaisedShadow(isDark).background(cameraBg, cameraShape)
                        } else {
                            Modifier.size(32.dp).background(cameraBg, cameraShape).border(2.dp, MaterialTheme.colorScheme.surface, cameraShape)
                        }

                        Box(modifier = cameraMod, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = cameraFg,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (uiState.isLoading) { CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)) }

                if (!uiState.isEditing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(uiState.user?.displayName ?: "",
                            style = if (layout == "compact") MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            color = if (bgUrl != null) Color.White else if (isExthru) MaterialTheme.colorScheme.onSurface else Color.Unspecified)
                        
                        if (user?.isProActive() == true) {
                            Spacer(Modifier.width(8.dp))
                            ProBadge()
                        }
                    }

                    Text("@${uiState.user?.username ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = customFont,
                        color = if (bgUrl != null) Color.White.copy(0.7f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp))

                    if (!uiState.user?.bio.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.user?.bio ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = customFont,
                            color = if (bgUrl != null) Color.White.copy(0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }

                    Spacer(Modifier.height(36.dp))
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isExthru) 16.dp else 12.dp)) {

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
                                    .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
                                    .background(MaterialTheme.colorScheme.surface, tfShape)
                            } else m.fillMaxWidth()
                        }

                        val textStyle = if (isForge) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface) else LocalTextStyle.current

                        OutlinedTextField(
                            value = uiState.editDisplayName,
                            onValueChange = viewModel::onDisplayNameChange,
                            label = { Text(stringResource(R.string.profile_field_display_name), fontFamily = if(isForge) FontFamily.Monospace else null) },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true,
                            textStyle = textStyle,
                            modifier = buildModifier(Modifier),
                            shape = tfShape,
                            colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                        )

                        OutlinedTextField(
                            value = uiState.editBio, onValueChange = viewModel::onBioChange,
                            label = { Text(stringResource(R.string.profile_field_bio), fontFamily = if(isForge) FontFamily.Monospace else null) },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            supportingText = { Text("${uiState.editBio.length}/160", fontFamily = if(isForge) FontFamily.Monospace else null) },
                            maxLines = 3,
                            textStyle = textStyle,
                            modifier = buildModifier(Modifier),
                            shape = tfShape,
                            colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                        )

                        OutlinedTextField(
                            value = uiState.editUsername, onValueChange = viewModel::onUsernameChange,
                            label = { Text(stringResource(R.string.profile_field_username), fontFamily = if(isForge) FontFamily.Monospace else null) },
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                            textStyle = textStyle,
                            trailingIcon = {
                                when {
                                    uiState.checkingUsername -> CircularProgressIndicator(
                                        Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    uiState.usernameAvailable == true -> Icon(
                                        Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    uiState.usernameAvailable == false -> Icon(
                                        Icons.Default.Cancel, null,
                                        tint = MaterialTheme.colorScheme.error)
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

                        if (isExthru) {
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
                        } else {
                            OutlinedButton(onClick = { viewModel.checkUsername() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium) {
                                Text(stringResource(R.string.profile_check_username))
                            }
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
            containerColor = if (isExthru) MaterialTheme.colorScheme.surface else AlertDialogDefaults.containerColor,
            shape = if (isForge) RectangleShape else AlertDialogDefaults.shape,
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
