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
    val cust = profile.customization
    val bgUrl = cust["bgUrl"] as? String
    val layout = cust["layout"] as? String ?: "default"
    
    VlSurface(
        modifier = Modifier.fillMaxSize()
    ) {
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = if (layout == "banner") Alignment.BottomStart else Alignment.Center
        ) {
            Column(
                horizontalAlignment = if (layout == "banner") Alignment.Start else Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                AvatarWithPresence(
                    avatarUrl = profile.avatarUrl,
                    displayName = profile.displayName,
                    isOnline = true,
                    size = if (layout == "compact") 48.dp else 64.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (layout == "compact") 16.sp else 20.sp
                )
            }
        }
    }
}