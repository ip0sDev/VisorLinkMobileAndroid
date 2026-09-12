package by.iposdev.visorlink.ui.screens.profile

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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

    val currentUser = uiState.user
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (uiState.isEditing) stringResource(R.string.profile_edit_title)
                    else stringResource(R.string.profile_title)
                    Text(titleText, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            viewModel.startEditing()
                        }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                        }

                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            showLogout = true
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                viewModel.saveProfile()
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val user = uiState.user ?: UserProfile()
        val isPro = user.isProActive()
        val cust = user.customization ?: emptyMap()
        val layout = cust["layout"] as? String ?: "default"
        val bgUrl = cust["bgUrl"] as? String
        val gifUrl = cust["gifUrl"] as? String
        val bannerUrl = gifUrl ?: bgUrl

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
                    .background(if (isPro && bgUrl != null) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
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
                                    .background(MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .border(4.dp, MaterialTheme.colorScheme.surface, VlTheme.tokens.shapes.avatar)
                                    .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                    .clip(VlTheme.tokens.shapes.avatar),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarContent(user, 100.dp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.padding(bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user.displayName,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
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
                                .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                .clip(VlTheme.tokens.shapes.avatar),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarContent(user, 80.dp)
                        }
                        Spacer(Modifier.width(24.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.displayName,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                val emojis = cust["emojis"] as? String
                                if (!emojis.isNullOrEmpty()) {
                                    Text(emojis, modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
                                }
                            }
                            Text("@${user.username}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
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
                                .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier)
                                .clip(VlTheme.tokens.shapes.avatar),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarContent(user, 140.dp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = if (bgUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            val emojis = cust["emojis"] as? String
                            if (!emojis.isNullOrEmpty()) {
                                Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = 22.sp)
                            }
                        }
                        Text("@${user.username}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!uiState.isEditing) {
                    // Profile body
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DebugUidBadge(uid = user.uid)
                        if (user.online) {
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
                                    .clip(VlTheme.tokens.shapes.card),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(32.dp))
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
                            LinkifiedText(
                                text = user.bio,
                                color = if (bgUrl != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                linkColor = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Telegram Section
                        Spacer(Modifier.height(32.dp))
                        VlCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { /* Do nothing */ }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Link,
                                        null,
                                        tint = Color(0xFF24A1DE),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Telegram",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))

                                if (user.tg_username != null) {
                                    Text(
                                        "Привязан: @${user.tg_username}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    VlButton(
                                        onClick = {
                                            haptic.perform(HapticType.CLICK, hapticEnabled)
                                            viewModel.unbindTelegram()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        isDestructive = true
                                    ) {
                                        if (uiState.isLoading) {
                                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text("Отвязать", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Text(
                                        "Telegram не привязан. Используйте код для привязки через бота.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))

                                    if (uiState.tgCode != null) {
                                        SelectionContainer {
                                            Text(
                                                uiState.tgCode ?: "",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Text(
                                            "Введите этот код боту @VisorLinkBot",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        VlButton(
                                            onClick = {
                                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                                viewModel.generateTgCode()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !uiState.isGeneratingCode
                                        ) {
                                            if (uiState.isGeneratingCode) {
                                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                            } else {
                                                Text("Привязать Telegram", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Editing fields
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
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
