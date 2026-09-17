package org.visorlink.app.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.components.settings.VlThemeSelector
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.AppLanguage
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.visorlink.app.ui.screens.auth.AuthCard
import org.visorlink.app.ui.screens.auth.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    auth: FirebaseAuth = koinInject(),
    db: FirebaseFirestore = koinInject()
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val appTheme by themeViewModel.appTheme.collectAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val scaffoldBg = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        val isCurrent = pagerState.currentPage == i
                        val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        val width by animateDpAsState(
                            targetValue = if (isCurrent) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            label = "onboarding_indicator_width"
                        )
                        Box(
                            Modifier
                                .size(width = width, height = 8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (pagerState.currentPage < 3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    VlButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_next), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VlAmbientGlow()

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> WelcomePage(themeViewModel)
                    1 -> AppearancePage(themeViewModel)
                    2 -> FeaturesPage(themeViewModel, auth, db)
                    3 -> AuthPage(
                        authViewModel = authViewModel,
                        onSuccess = {
                            themeViewModel.completeOnboarding()
                            onFinish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomePage(themeViewModel: ThemeViewModel) {
    val currentLang by themeViewModel.language.collectAsState()
    val appTheme by themeViewModel.appTheme.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👋", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.intro_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.intro_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        VlSettingsSection(title = stringResource(R.string.settings_section_language)) {
            VlOptionRow(icon = Icons.Default.Language, label = stringResource(R.string.settings_language_system), selected = currentLang == AppLanguage.SYSTEM, index = 0, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.SYSTEM) })
            VlOptionRow(icon = Icons.Default.Translate, label = stringResource(R.string.settings_language_en), selected = currentLang == AppLanguage.EN, index = 1, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.EN) })
            VlOptionRow(icon = Icons.Default.GTranslate, label = stringResource(R.string.settings_language_ru), selected = currentLang == AppLanguage.RU, index = 2, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.RU) })
        }
    }
}

@Composable
fun AppearancePage(themeViewModel: ThemeViewModel) {
    val appTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val currentPreset by themeViewModel.colorPreset.collectAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎨", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_section_appearance),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        VlSettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            VlThemeSelector(
                selected = appTheme,
                onSelect = { themeViewModel.setTheme(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                themeMode = currentMode,
                colorPreset = currentPreset,
            )
        }

        Spacer(Modifier.height(16.dp))

        VlSettingsSection(title = stringResource(R.string.settings_section_accent)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ColorPreset.entries.forEach { preset ->
                    ColorPresetCircle(
                        preset = preset,
                        isSelected = currentPreset == preset,
                        isDark = isDark,
                        onClick = { themeViewModel.setColorPreset(preset) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturesPage(themeViewModel: ThemeViewModel, auth: FirebaseAuth, db: FirebaseFirestore) {
    val appTheme by themeViewModel.appTheme.collectAsState()
    val discoverEnabled by themeViewModel.discoverEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val uid = auth.currentUser?.uid

    var diaryEnabledLocal by remember { mutableStateOf(true) }
    // Fetch initial state from Firestore if possible, but for onboarding we usually set defaults
    // Since it's intro, we can assume we want to SET them.

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚀", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.intro_features_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        VlSettingsSection(title = stringResource(R.string.intro_features_section)) {
            VlSettingsItem(
                icon = Icons.Default.Explore,
                iconColor = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feed_title),
                subtitle = "Лента новостей и каналов",
                index = 0, total = 2,
                trailing = {
                    VlSwitch(
                        checked = discoverEnabled,
                        onCheckedChange = { themeViewModel.setDiscoverEnabled(it) }
                    )
                }
            )
            VlSettingsItem(
                icon = Icons.Default.Book,
                iconColor = Color(0xFF10B981),
                title = stringResource(R.string.diary_title),
                subtitle = "Личные ежедневные заметки",
                index = 1, total = 2,
                trailing = {
                    VlSwitch(
                        checked = diaryEnabledLocal,
                        onCheckedChange = { v ->
                            diaryEnabledLocal = v
                            if (uid != null) {
                                scope.launch {
                                    db.collection("users").document(uid).update("diaryEnabled", v)
                                }
                            }
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun AuthPage(
    authViewModel: AuthViewModel,
    onSuccess: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AuthCard(
            viewModel = authViewModel,
            onLoginSuccess = onSuccess,
            onRegistrationComplete = onSuccess
        )
    }
}
