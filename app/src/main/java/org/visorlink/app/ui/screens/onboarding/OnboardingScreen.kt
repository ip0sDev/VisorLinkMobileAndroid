package org.visorlink.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.ui.components.ColorPresetCircle
import org.visorlink.app.ui.components.NeumorphicLiquidSegmentedControl
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlBrandText
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.components.terminalTitleReveal
import org.visorlink.app.ui.screens.auth.AuthCard
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.AppLanguage
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    auth: FirebaseAuth = koinInject(),
    db: FirebaseFirestore = koinInject(),
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsStateWithLifecycle()

    var diaryEnabledLocal by remember { mutableStateOf(true) }
    var diaryTouched by remember { mutableStateOf(false) }

    val scaffoldBg = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Неоморфная кнопка «Назад» появляется со 2-й страницы
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 2 },
                    exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 2 },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    VlTopAppBarButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK, enabled = hapticEnabled)
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Непрерывный жидкий прогресс-трек, плавно следующий за свайпом пальца
                OnboardingLiquidProgressTrack(
                    pagerState = pagerState,
                    pageCount = 4,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Неоморфная кнопка «Пропустить» переносит сразу к авторизации
                AnimatedVisibility(
                    visible = pagerState.currentPage < 3,
                    enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 2 },
                    exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 2 },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    VlTopAppBarButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK, enabled = hapticEnabled)
                            scope.launch {
                                pagerState.animateScrollToPage(3)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.intro_skip),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = pagerState.currentPage < 3,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                ) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .navigationBarsPadding(),
                ) {
                    VlButton(
                        onClick = {
                            haptic.perform(HapticType.CLICK, enabled = hapticEnabled)
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedContent(
                            targetState = pagerState.currentPage == 2,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 }) togetherWith
                                    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
                            },
                            label = "onboarding_next_label",
                        ) { isLastSetupPage ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = if (isLastSetupPage) {
                                        stringResource(R.string.intro_continue_to_auth)
                                    } else {
                                        stringResource(R.string.action_next)
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            VlAmbientGlow()

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                val pageOffset = pagerState.pageOffsetFor(page)
                val isVisible = pagerState.currentPage == page
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - absOffset * 0.05f
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - absOffset * 0.25f
                        },
                ) {
                    when (page) {
                        0 -> WelcomePage(
                            themeViewModel = themeViewModel,
                            hapticEnabled = hapticEnabled,
                            isVisible = isVisible,
                            pageOffset = pageOffset,
                        )
                        1 -> AppearancePage(
                            themeViewModel = themeViewModel,
                            hapticEnabled = hapticEnabled,
                            isVisible = isVisible,
                            pageOffset = pageOffset,
                        )
                        2 -> FeaturesPage(
                            themeViewModel = themeViewModel,
                            diaryEnabled = diaryEnabledLocal,
                            onDiaryChanged = { enabled ->
                                diaryEnabledLocal = enabled
                                diaryTouched = true
                                val uid = auth.currentUser?.uid
                                if (uid != null) {
                                    scope.launch {
                                        try {
                                            db.collection("users").document(uid).update("diaryEnabled", enabled)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            },
                            hapticEnabled = hapticEnabled,
                            isVisible = isVisible,
                            pageOffset = pageOffset,
                        )
                        3 -> AuthPage(
                            authViewModel = authViewModel,
                            isVisible = isVisible,
                            onSuccess = {
                                if (diaryTouched) {
                                    val uid = auth.currentUser?.uid
                                    if (uid != null) {
                                        scope.launch {
                                            try {
                                                db.collection("users").document(uid)
                                                    .update("diaryEnabled", diaryEnabledLocal)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                                themeViewModel.completeOnboarding()
                                onFinish()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomePage(
    themeViewModel: ThemeViewModel,
    hapticEnabled: Boolean = true,
    isVisible: Boolean = true,
    pageOffset: Float = 0f,
) {
    val currentLang by themeViewModel.language.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()

    val langOptions = listOf(AppLanguage.SYSTEM, AppLanguage.EN, AppLanguage.RU)
    val langLabels = listOf(
        stringResource(R.string.intro_lang_auto),
        "English",
        "Русский",
    )
    val selectedLangIdx = langOptions.indexOf(currentLang).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingStaggeredItem(visible = isVisible, delayMs = 0) {
            OnboardingBrandEmblem(
                pageOffset = pageOffset,
                hapticEnabled = hapticEnabled,
            )
        }
        Spacer(Modifier.height(10.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 50) {
            VlBrandText(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.terminalTitleReveal(key = "welcome_brand"),
            )
        }
        Spacer(Modifier.height(6.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 95) {
            AnimatedContent(
                targetState = currentLang,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "welcome_tagline",
            ) {
                Text(
                    text = stringResource(R.string.intro_welcome_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 140) {
            OnboardingPillarShowcase(hapticEnabled = hapticEnabled)
        }
        Spacer(Modifier.height(24.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 185) {
            NeumorphicLiquidSegmentedControl(
                tabs = langLabels,
                selectedIndex = selectedLangIdx,
                onTabSelected = { idx ->
                    val targetLang = langOptions[idx]
                    if (targetLang != currentLang) {
                        haptic.perform(HapticType.SELECTION, enabled = hapticEnabled)
                        themeViewModel.setLanguage(targetLang)
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun AppearancePage(
    themeViewModel: ThemeViewModel,
    hapticEnabled: Boolean = true,
    isVisible: Boolean = true,
    pageOffset: Float = 0f,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val currentPreset by themeViewModel.colorPreset.collectAsStateWithLifecycle()
    val isDark = cs.surface.luminance() < 0.1f

    val modeOptions = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val modeLabels = listOf(
        stringResource(R.string.settings_dark_system),
        stringResource(R.string.settings_dark_light),
        stringResource(R.string.settings_dark_dark),
    )
    val selectedModeIdx = modeOptions.indexOf(themeMode).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingStaggeredItem(visible = isVisible, delayMs = 0) {
            Text(
                text = stringResource(R.string.intro_appearance_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.terminalTitleReveal(key = "appearance_title"),
            )
        }
        Spacer(Modifier.height(6.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 50) {
            Text(
                text = stringResource(R.string.intro_appearance_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))

        // Живой интерактивный макет чата VisorLink
        OnboardingStaggeredItem(
            visible = isVisible,
            delayMs = 95,
            parallaxOffsetX = -pageOffset * 18f,
        ) {
            OnboardingLiveMessengerMock(
                hapticEnabled = hapticEnabled,
                pulseKey = themeMode to currentPreset,
            )
        }
        Spacer(Modifier.height(20.dp))

        // Желейный переключатель светлой/тёмной темы
        OnboardingStaggeredItem(visible = isVisible, delayMs = 140) {
            NeumorphicLiquidSegmentedControl(
                tabs = modeLabels,
                selectedIndex = selectedModeIdx,
                onTabSelected = { idx ->
                    val mode = modeOptions[idx]
                    if (mode != themeMode) {
                        haptic.perform(HapticType.SELECTION, enabled = hapticEnabled)
                        themeViewModel.setThemeMode(mode)
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        // Неоморфная палитра акцентов
        OnboardingStaggeredItem(visible = isVisible, delayMs = 185) {
            val dockShape = tokens.shapes.adapt(RoundedCornerShape(22.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .vlRaised(tokens.structure, dockShape)
                    .clip(dockShape)
                    .background(cs.surfaceContainerLow)
                    .vlHairline(cs.outlineVariant.copy(alpha = 0.25f), dockShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorPreset.entries.forEach { preset ->
                    ColorPresetCircle(
                        preset = preset,
                        isSelected = currentPreset == preset,
                        isDark = isDark,
                        onClick = {
                            if (currentPreset != preset) {
                                haptic.perform(HapticType.SELECTION, enabled = hapticEnabled)
                                themeViewModel.setColorPreset(preset)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturesPage(
    themeViewModel: ThemeViewModel,
    diaryEnabled: Boolean,
    onDiaryChanged: (Boolean) -> Unit,
    hapticEnabled: Boolean = true,
    isVisible: Boolean = true,
    pageOffset: Float = 0f,
) {
    val discoverEnabled by themeViewModel.discoverEnabled.collectAsStateWithLifecycle()
    val musicEnabled by themeViewModel.musicEnabled.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingStaggeredItem(visible = isVisible, delayMs = 0) {
            Text(
                text = stringResource(R.string.intro_features_title_studio),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.terminalTitleReveal(key = "features_title"),
            )
        }
        Spacer(Modifier.height(6.dp))

        OnboardingStaggeredItem(visible = isVisible, delayMs = 45) {
            Text(
                text = stringResource(R.string.intro_features_subtitle_studio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))

        // Интерактивная витрина главного экрана с живым плавающим доком
        OnboardingStaggeredItem(visible = isVisible, delayMs = 90) {
            OnboardingDockStage(
                feedEnabled = discoverEnabled,
                diaryEnabled = diaryEnabled,
                musicEnabled = musicEnabled,
                hapticEnabled = hapticEnabled,
                pageOffset = pageOffset,
            )
        }
        Spacer(Modifier.height(18.dp))

        // Тактильные модульные карточки (подняты при вкл, утоплены при выкл)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingStaggeredItem(visible = isVisible, delayMs = 135) {
                OnboardingModuleTile(
                    icon = Icons.Default.Explore,
                    accentColor = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.feed_title),
                    subtitle = stringResource(R.string.intro_feature_feed_sub),
                    enabled = discoverEnabled,
                    hapticEnabled = hapticEnabled,
                    onToggle = { themeViewModel.setDiscoverEnabled(!discoverEnabled) },
                )
            }
            OnboardingStaggeredItem(visible = isVisible, delayMs = 175) {
                OnboardingModuleTile(
                    icon = Icons.Default.Book,
                    accentColor = Color(0xFF10B981),
                    title = stringResource(R.string.diary_title),
                    subtitle = stringResource(R.string.intro_feature_diary_sub),
                    enabled = diaryEnabled,
                    hapticEnabled = hapticEnabled,
                    onToggle = { onDiaryChanged(!diaryEnabled) },
                )
            }
            OnboardingStaggeredItem(visible = isVisible, delayMs = 215) {
                OnboardingModuleTile(
                    icon = Icons.Default.MusicNote,
                    accentColor = Color(0xFFFFB020),
                    title = stringResource(R.string.nav_tab_music),
                    subtitle = stringResource(R.string.intro_feature_music_sub),
                    enabled = musicEnabled,
                    hapticEnabled = hapticEnabled,
                    onToggle = { themeViewModel.setMusicEnabled(!musicEnabled) },
                )
            }
        }
    }
}

@Composable
fun AuthPage(
    authViewModel: AuthViewModel,
    isVisible: Boolean = true,
    onSuccess: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        OnboardingStaggeredItem(visible = isVisible, delayMs = 40) {
            AuthCard(
                viewModel = authViewModel,
                onLoginSuccess = onSuccess,
                onRegistrationComplete = onSuccess,
            )
        }
    }
}
