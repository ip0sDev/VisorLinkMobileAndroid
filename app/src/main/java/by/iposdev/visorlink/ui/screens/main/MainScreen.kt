package by.iposdev.visorlink.ui.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.screens.chatlist.ChatListScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryViewModel
import by.iposdev.visorlink.ui.screens.feed.FeedScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
    onAddDiaryEntry: () -> Unit,
    onEditDiaryEntry: (String) -> Unit,
    mainViewModel: MainViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val appTheme by themeViewModel.appTheme.collectAsState()
    val isM3E = appTheme == AppTheme.MATERIAL3_EXPRESSIVE
    val isForge = appTheme.name == "FORGE"
    
    val userProfile by mainViewModel.userProfile.collectAsState()
    val diaryEnabled = userProfile?.diaryEnabled ?: false

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (isM3E) MaterialTheme.colorScheme.surface 
                     else if (appTheme.isExthruFamily) MaterialTheme.colorScheme.background 
                     else MaterialTheme.colorScheme.surface

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = scaffoldBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Основная область контента - ПОЛНЫЙ ЭКРАН
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    when (selectedTab) {
                        0 -> ChatListScreen(
                            onOpenChat = onOpenChat,
                            onOpenSearch = onOpenSearch,
                            onOpenProfile = onOpenProfile,
                            onOpenSettings = onOpenSettings,
                            onCreateChat = onCreateChat,
                            onFindChannel = onFindChannel,
                            onOpenNotifications = onOpenNotifications,
                            onOpenFeed = { selectedTab = 1 }
                        )
                        1 -> FeedScreen(
                            onNavigateBack = { selectedTab = 0 },
                            onOpenChannel = onOpenChannel,
                            onOpenComments = onOpenComments
                        )
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
                        }
                    }
                }

                // Сама панель навигации
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    CustomVlNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        appTheme = appTheme,
                        hazeState = hazeState,
                        diaryEnabled = diaryEnabled,
                        onOpenDiary = { selectedTab = 2 }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomVlNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    appTheme: AppTheme,
    hazeState: HazeState,
    diaryEnabled: Boolean,
    onOpenDiary: () -> Unit
) {
    val isForge = appTheme.name == "FORGE"
    val isM3E = appTheme == AppTheme.MATERIAL3_EXPRESSIVE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isForge) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(horizontal = if (isForge) 0.dp else 24.dp)
            .padding(bottom = if (isForge) 0.dp else 16.dp, top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        VlSurface(
            appTheme = appTheme,
            isButton = false,
            customRadius = if (isForge) 0.dp else 32.dp,
            modifier = (if (isForge) Modifier.fillMaxWidth() else Modifier.widthIn(min = 220.dp))
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f)),
            overrideColor = if (!isForge && !isM3E) Color.Transparent else null
        ) {
            val blurModifier = if (!isForge && !isM3E) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                ).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            } else Modifier

            Row(
                modifier = Modifier
                    .then(blurModifier)
                    .padding(horizontal = if (isForge) 0.dp else 12.dp, vertical = if (isForge) 0.dp else 4.dp)
                    .height(if (isForge) 64.dp else 60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VlTabItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = Icons.Outlined.ChatBubbleOutline,
                    selectedIcon = Icons.Filled.ChatBubble,
                    label = stringResource(R.string.chatlist_title),
                    isForge = isForge,
                    modifier = if (isForge || diaryEnabled) Modifier.weight(1f) else Modifier
                )
                VlTabItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = Icons.Outlined.Explore,
                    selectedIcon = Icons.Filled.Explore,
                    label = stringResource(R.string.feed_title),
                    isForge = isForge,
                    modifier = if (isForge || diaryEnabled) Modifier.weight(1f) else Modifier
                )
                if (diaryEnabled) {
                    VlTabItem(
                        selected = selectedTab == 2,
                        onClick = onOpenDiary,
                        icon = Icons.Default.Edit,
                        selectedIcon = Icons.Default.Edit,
                        label = stringResource(R.string.diary_title),
                        isForge = isForge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.VlTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isForge: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "tab_scale")
    
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(if (isForge) RectangleShape else CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = if (isForge) 0.dp else 16.dp, vertical = if (isForge) 0.dp else 4.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(if (selected) 26.dp else 24.dp)
            )
            if (!isForge) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (selected) "> ${label.uppercase()}" else label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }

        if (selected && isForge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
