package by.iposdev.visorlink.ui.screens.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.rememberHaptic
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
            VlSettingsSection(appTheme = currentTheme, title = "Preview") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                ) {
                   ProfilePreview(profile, currentTheme)
                }
            }

            // Layout
            VlSettingsSection(appTheme = currentTheme, title = "Layout") {
                val layouts = listOf("default", "banner", "compact")
                layouts.forEachIndexed { index, l ->
                    VlOptionRow(
                        appTheme = currentTheme,
                        icon = when(l) {
                            "banner" -> Icons.Default.ViewDay
                            "compact" -> Icons.Default.ViewStream
                            else -> Icons.Default.Person
                        },
                        label = l.replaceFirstChar { it.uppercase() },
                        selected = (cust["layout"] as? String ?: "default") == l,
                        index = index,
                        total = layouts.size,
                        onClick = { viewModel.updateCustomization("layout", l) }
                    )
                }
            }

            // Background
            VlSettingsSection(appTheme = currentTheme, title = "Profile Background") {
                VlSettingsItem(
                    appTheme = currentTheme,
                    icon = Icons.Default.Image,
                    title = "Upload Image",
                    subtitle = (cust["bgUrl"] as? String)?.takeLast(20) ?: "None",
                    onClick = { bgPicker.launch("image/*") },
                    index = 0, total = 2
                )
                VlSettingsItem(
                    appTheme = currentTheme,
                    icon = Icons.Default.Gif,
                    title = "Upload GIF",
                    subtitle = (cust["gifUrl"] as? String)?.takeLast(20) ?: "None",
                    onClick = { gifPicker.launch("image/gif") },
                    index = 1, total = 2
                )
            }

            // Font
            VlSettingsSection(appTheme = currentTheme, title = "Custom Font") {
                val fonts = listOf("default", "mono", "serif", "rounded")
                fonts.forEachIndexed { index, f ->
                    VlOptionRow(
                        appTheme = currentTheme,
                        icon = Icons.Default.TextFields,
                        label = f.replaceFirstChar { it.uppercase() },
                        selected = (cust["font"] as? String ?: "default") == f,
                        index = index,
                        total = fonts.size,
                        onClick = { viewModel.updateCustomization("font", f) }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProfilePreview(profile: UserProfile, appTheme: AppTheme) {
    val cust = profile.customization
    val bgUrl = cust["bgUrl"] as? String
    val layout = cust["layout"] as? String ?: "default"
    
    VlSurface(
        appTheme = appTheme,
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
