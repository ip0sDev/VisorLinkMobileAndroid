package org.visorlink.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.ThemeViewModel
import coil.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: CustomizationViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by themeViewModel.appTheme.collectAsState()
    
    val profile = uiState.profile ?: return
    val cust = profile.customization
    
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadCustomBackground(it, "bgUrl") }
    }
    
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadCustomBackground(it, "gifUrl") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_custom_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Preview
            VlSettingsSection(title = "Preview") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                ) {
                   ProfilePreview(profile)
                }
            }

            // Accent Color
            VlSettingsSection(title = "Accent Color") {
                val presets = listOf(
                    "default" to ColorPreset.DEFAULT,
                    "purple" to ColorPreset.PURPLE,
                    "blue" to ColorPreset.BLUE,
                    "emerald" to ColorPreset.EMERALD,
                    "crimson" to ColorPreset.CRIMSON
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.forEach { (name, preset) ->
                        ColorPresetCircle(
                            preset = preset,
                            isSelected = (cust["accent"] as? String ?: "default") == name,
                            onClick = { viewModel.updateCustomization("accent", name) }
                        )
                    }
                }
            }

            // Background
            VlSettingsSection(title = "Profile Background") {
                VlSettingsItem(
                    icon = Icons.Default.Image,
                    title = "Upload Image",
                    subtitle = (cust["bgUrl"] as? String)?.takeLast(20) ?: "None",
                    onClick = { bgPicker.launch("image/*") }
                )
                VlSettingsItem(
                    icon = Icons.Default.Gif,
                    title = "Upload GIF",
                    subtitle = (cust["gifUrl"] as? String)?.takeLast(20) ?: "None",
                    onClick = { gifPicker.launch("image/gif") }
                )
            }

            // Font
            VlSettingsSection(title = "Custom Font") {
                val fonts = listOf("default", "mono", "serif", "rounded")
                fonts.forEachIndexed { index, f ->
                    VlOptionRow(
                        icon = Icons.Default.TextFields,
                        label = f.replaceFirstChar { it.uppercase() },
                        selected = (cust["font"] as? String ?: "default") == f,
                        onClick = { viewModel.updateCustomization("font", f) }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProfilePreview(profile: UserProfile) {
    val cust = profile.customization ?: emptyMap()
    val bgUrl = cust["bgUrl"] as? String
    val gifUrl = cust["gifUrl"] as? String
    val bannerUrl = gifUrl ?: bgUrl
    
    VlSurface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bannerUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
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
            
            Box(
                modifier = Modifier
                    .then(if (bannerUrl != null) Modifier.offset(y = (-24).dp) else Modifier.padding(top = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AvatarWithPresence(
                    avatarUrl = profile.avatarUrl,
                    displayName = profile.displayName,
                    isOnline = true,
                    size = 56.dp
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.then(if (bannerUrl != null) Modifier.offset(y = (-16).dp) else Modifier)
            ) {
                Text(
                    profile.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "@${profile.username}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
        }
    }
}