package by.iposdev.visorlink.ui.screens.main

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
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlNavigationBar
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.screens.chatlist.ChatListScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryViewModel
import by.iposdev.visorlink.ui.screens.feed.FeedScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    onOpenChat: (String, String) -> Unit,
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

    val showNavbar = diaryEnabled || discoverEnabled
    val isOnline by mainViewModel.isOnline.collectAsState()

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
                            val diaryVm: DiaryViewModel = if (chatListEntry != null) {
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
                    }
                }

                if (showNavbar) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        VlNavigationBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            diaryEnabled = diaryEnabled,
                            discoverEnabled = discoverEnabled,
                            onOpenDiary = { selectedTab = 2 }
                        )
                    }
                }
            }
        }
    }
}
