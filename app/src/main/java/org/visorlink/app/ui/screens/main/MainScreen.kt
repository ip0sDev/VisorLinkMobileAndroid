package org.visorlink.app.ui.screens.main

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlNavigationBar
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.screens.chatlist.ChatListScreen
import org.visorlink.app.ui.screens.diary.DiaryScreen
import org.visorlink.app.ui.screens.feed.FeedScreen
import org.visorlink.app.ui.screens.music.MusicLibraryScreen
import org.visorlink.app.ui.screens.music.MusicViewModel
import org.visorlink.app.ui.components.music.FullscreenPlayerDialog
import org.visorlink.app.ui.components.music.MusicOnboardingDialog
import org.visorlink.app.ui.components.chat.AudioPlaybackDockBar
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.MusicPlayerManager
import org.visorlink.app.data.repository.MusicRepository
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    onOpenChat: (String, String) -> Unit,
    onOpenTopicList: (String) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateChat: () -> Unit,
    onFindChannel: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenComments: (String, String) -> Unit,
    onOpenImageViewer: (String, String) -> Unit,
    onAddDiaryEntry: () -> Unit,
    onEditDiaryEntry: (String) -> Unit,
    mainViewModel: MainViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity?.intent?.getBooleanExtra("openDiary", false) == true) {
            selectedTab = 2
            activity.intent.removeExtra("openDiary")
        }
    }
    
    val userProfile by mainViewModel.userProfile.collectAsState()
    val diaryEnabled = userProfile?.diaryEnabled ?: false
    val discoverEnabled by themeViewModel.discoverEnabled.collectAsState()
    val musicEnabled by themeViewModel.musicEnabled.collectAsState()
    val showMusicOnboarding by themeViewModel.showMusicOnboarding.collectAsState()

    val musicPlayerManager: MusicPlayerManager = koinInject()
    val musicRepository: MusicRepository = koinInject()
    val showFullscreenPlayer by musicPlayerManager.showFullscreenPlayer.collectAsState()

    val showNavbar = diaryEnabled || discoverEnabled || musicEnabled
    val isOnline by mainViewModel.isOnline.collectAsState()
    val showFallbackPrompt by mainViewModel.showFallbackPrompt.collectAsState()

    if (showFallbackPrompt) {
        org.visorlink.app.ui.components.BackendFallbackOfferDialog(
            onDismissRequest = { mainViewModel.dismissFallbackPrompt() },
            onConfirmFallback = { mainViewModel.confirmFallback() }
        )
    }

    if (showMusicOnboarding) {
        MusicOnboardingDialog(
            onEnable = { themeViewModel.completeMusicOnboarding(true) },
            onDisable = { themeViewModel.completeMusicOnboarding(false) }
        )
    }

    if (showFullscreenPlayer) {
        FullscreenPlayerDialog(
            playerManager = musicPlayerManager,
            musicRepository = musicRepository,
            onDismiss = { musicPlayerManager.closeFullscreenPlayer() }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .windowInsetsPadding(WindowInsets.statusBars),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Offline Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> ChatListScreen(
                            onOpenChat = onOpenChat,
                            onOpenTopicList = onOpenTopicList,
                            onOpenSearch = onOpenSearch,
                            onOpenProfile = onOpenProfile,
                            onOpenSettings = onOpenSettings,
                            onCreateChat = onCreateChat,
                            onFindChannel = onFindChannel,
                            onOpenNotifications = onOpenNotifications,
                            onOpenFeed = { if (discoverEnabled) selectedTab = 1 }
                        )
                        1 -> if (discoverEnabled) {
                            FeedScreen(
                                onNavigateBack = { selectedTab = 0 },
                                onOpenChannel = onOpenChannel,
                                onOpenComments = onOpenComments,
                                onOpenImageViewer = onOpenImageViewer
                            )
                        } else {
                            selectedTab = 0
                        }
                        2 -> if (diaryEnabled) {
                            val chatListEntry = LocalViewModelStoreOwner.current
                            val diaryVm: org.visorlink.app.ui.screens.diary.DiaryViewModel = if (chatListEntry != null) {
                                koinViewModel(viewModelStoreOwner = chatListEntry)
                            } else {
                                koinViewModel()
                            }
                            DiaryScreen(
                                onNavigateBack = { selectedTab = 0 },
                                onAddEntry = onAddDiaryEntry,
                                onEditEntry = onEditDiaryEntry,
                                viewModel = diaryVm
                            )
                        } else {
                            selectedTab = 0
                        }
                        3 -> if (musicEnabled) {
                            val musicVm: MusicViewModel = koinViewModel()
                            MusicLibraryScreen(
                                viewModel = musicVm,
                                onNavigateBack = { selectedTab = 0 }
                            )
                        } else {
                            selectedTab = 0
                        }
                    }
                }

                if (showNavbar) {
                    val musicPlayback by musicPlayerManager.state.collectAsState()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // "Выдувание" мини-плеера из навбара на вкладке музыки или при активном треке
                        AnimatedVisibility(
                            visible = selectedTab == 3 && musicPlayback.currentTrack != null,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            ) + expandVertically(
                                expandFrom = Alignment.Bottom,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            ) + fadeIn(animationSpec = tween(250)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(200)
                            ) + shrinkVertically(
                                shrinkTowards = Alignment.Bottom,
                                animationSpec = tween(200)
                            ) + fadeOut(animationSpec = tween(150))
                        ) {
                            AudioPlaybackDockBar(
                                musicPlayback = musicPlayback,
                                onTogglePlayPause = { musicPlayerManager.togglePlayPause() },
                                onClose = { musicPlayerManager.stop() },
                                onOpenFullscreen = { musicPlayerManager.openFullscreenPlayer() },
                                isFloating = true
                            )
                        }

                        VlNavigationBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            diaryEnabled = diaryEnabled,
                            discoverEnabled = discoverEnabled,
                            musicEnabled = musicEnabled,
                            onOpenDiary = { selectedTab = 2 },
                            onOpenMusic = { selectedTab = 3 }
                        )
                    }
                }
            }
        }
    }
}
