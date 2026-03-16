package by.iposdev.visorlink.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStickers: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(it) }
    }

    LaunchedEffect(uiState.successMessage) { uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() } }
    LaunchedEffect(uiState.error) { uiState.error?.let { snackbar.showSnackbar("Error: $it"); viewModel.clearMessages() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Profile" else "Profile") },
                navigationIcon = {
                    IconButton(onClick = { if (uiState.isEditing) viewModel.cancelEditing() else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = { viewModel.startEditing() }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { showLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout") }
                    } else {
                        TextButton(onClick = { viewModel.saveProfile() }, enabled = !uiState.isLoading) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            // Avatar
            Box(Modifier.size(100.dp), contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .then(if (uiState.isEditing) Modifier.clickable { avatarPicker.launch("image/*") } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.user?.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(model = uiState.user?.avatarUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(uiState.user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (uiState.isEditing) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (uiState.isLoading) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.height(8.dp)) }

            if (!uiState.isEditing) {
                Text(uiState.user?.displayName ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("@${uiState.user?.username ?: ""}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (!uiState.user?.bio.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.user?.bio ?: "", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
                Card(onClick = onOpenStickers, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEmotions, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("My Stickers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = uiState.editDisplayName, onValueChange = viewModel::onDisplayNameChange,
                        label = { Text("Display Name") }, leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(value = uiState.editBio, onValueChange = viewModel::onBioChange,
                        label = { Text("Bio") }, leadingIcon = { Icon(Icons.Default.Info, null) },
                        supportingText = { Text("${uiState.editBio.length}/160") },
                        maxLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(
                        value = uiState.editUsername, onValueChange = viewModel::onUsernameChange,
                        label = { Text("Username") }, leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                        trailingIcon = {
                            when {
                                uiState.checkingUsername -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                uiState.usernameAvailable == true -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                uiState.usernameAvailable == false -> Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        isError = uiState.usernameAvailable == false,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                    )
                    OutlinedButton(onClick = { viewModel.checkUsername() }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium) { Text("Check username availability") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = { showLogout = false; viewModel.logout(); onLoggedOut() }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Cancel") } }
        )
    }
}