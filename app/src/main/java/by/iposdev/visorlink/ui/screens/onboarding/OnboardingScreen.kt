package by.iposdev.visorlink.ui.screens.onboarding

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
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
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
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                if (pagerState.currentPage < 3) {
                    VlButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_next), fontWeight = FontWeight.Bold)
                    }
                } else {
                    VlButton(
                        onClick = {
                            themeViewModel.completeOnboarding()
                            onFinish()
                        }
                    ) {
                        Text(stringResource(R.string.intro_start_btn), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                    3 -> FinalPage(appTheme)
                }
            }

            // Indicator
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { i ->
                    val color = if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    Box(
                        Modifier
                            .size(if (pagerState.currentPage == i) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(color)
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
            VlOptionRow(icon = Icons.Default.AutoAwesome, label = "Expressive", desc = "Material 3 Next", selected = appTheme == AppTheme.MATERIAL3_EXPRESSIVE, index = 0, total = 1, onClick = { themeViewModel.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) })
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
fun FinalPage(appTheme: AppTheme) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✨", fontSize = 80.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.intro_final_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.intro_final_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
