package by.iposdev.visorlink.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
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
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

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
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                        else stringResource(R.string.profile_title)
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
                                if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
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
                    actions = {
                        val btnModifier = if (isExthru) Modifier
                            .size(42.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                        else Modifier

                        if (!uiState.isEditing) {
                            IconButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    viewModel.startEditing()
                                },
                                modifier = btnModifier
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    stringResource(R.string.action_edit),
                                    modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                                )
                            }

                            if (isExthru) Spacer(Modifier.width(10.dp))

                            IconButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    showLogout = true
                                },
                                modifier = btnModifier.then(if (isExthru) Modifier.padding(end = 12.dp) else Modifier)
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
                        containerColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()),
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
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            }

            Box(Modifier.size(110.dp), contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = avatarModifier
                        .then(if (uiState.isEditing)
                            Modifier.clickable { avatarPicker.launch("image/*") }
                        else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.user?.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(model = uiState.user?.avatarUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(uiState.user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                if (uiState.isEditing) {
                    val cameraBg = if (isExthru) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                    val cameraFg = if (isExthru) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                    val cameraMod = if (isExthru) {
                        Modifier.size(36.dp).exthruSmallRaisedShadow(isDark)
                    } else {
                        Modifier.size(32.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    }

                    Surface(shape = CircleShape, color = cameraBg, modifier = cameraMod) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = cameraFg,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (uiState.isLoading) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.height(8.dp)) }

            if (!uiState.isEditing) {
                Text(uiState.user?.displayName ?: "",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (isExthru) MaterialTheme.colorScheme.onSurface else Color.Unspecified)

                Text("@${uiState.user?.username ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)

                if (!uiState.user?.bio.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.user?.bio ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(36.dp))
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isExthru) 16.dp else 12.dp)) {

                    val tfShape = RoundedCornerShape(16.dp)
                    val nmColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent
                    )

                    val buildModifier: @Composable (Modifier) -> Modifier = { m ->
                        if (isExthru) {
                            m.fillMaxWidth()
                                .nmInsetShadow(isDark, cornerRadius = 16.dp)
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
                        shape = if (isExthru) tfShape else MaterialTheme.shapes.medium,
                        colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = uiState.editBio, onValueChange = viewModel::onBioChange,
                        label = { Text(stringResource(R.string.profile_field_bio)) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        supportingText = { Text("${uiState.editBio.length}/160") },
                        maxLines = 3,
                        modifier = buildModifier(Modifier),
                        shape = if (isExthru) tfShape else MaterialTheme.shapes.medium,
                        colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = uiState.editUsername, onValueChange = viewModel::onUsernameChange,
                        label = { Text(stringResource(R.string.profile_field_username)) },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                        trailingIcon = {
                            when {
                                uiState.checkingUsername -> CircularProgressIndicator(
                                    Modifier.size(20.dp), strokeWidth = 2.dp)
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
                        shape = if (isExthru) tfShape else MaterialTheme.shapes.medium,
                        colors = if (isExthru) nmColors else OutlinedTextFieldDefaults.colors()
                    )

                    Spacer(Modifier.height(4.dp))

                    if (isExthru) {
                        NmButton(
                            text = stringResource(R.string.profile_check_username),
                            isDark = isDark,
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

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.dialog_logout_title)) },
            text  = { Text(stringResource(R.string.dialog_logout_body)) },
            containerColor = if (isExthru) MaterialTheme.colorScheme.surface else AlertDialogDefaults.containerColor,
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

// ─── Вспомогательные компоненты для Exthru ────────────────────────────────────

@Composable
private fun NmActionCard(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "action_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .scale(scale)
            .exthruRaisedShadow(isDark)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NmButton(
    text: String,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && isEnabled) 0.95f else 1f, label = "btn_scale")

    val bgColor = MaterialTheme.colorScheme.surface
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .scale(scale)
            .then(if (isEnabled) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
            .background(bgColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
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
            Text(text, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}