package org.visorlink.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.*
import androidx.navigation.compose.*
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.repository.AuthState
import org.visorlink.app.utils.ChatDataCache
import org.visorlink.app.ui.screens.diary.DiaryScreen
import org.visorlink.app.ui.screens.diary.DiaryEntryScreen
import org.visorlink.app.ui.screens.diary.DiaryViewModel
import org.visorlink.app.ui.aegis.AegisDebugScreen
import org.visorlink.app.ui.screens.settings.SettingsScreen
import org.visorlink.app.ui.screens.settings.CacheSettingsScreen
import org.visorlink.app.ui.screens.settings.StorageManagerScreen
import org.visorlink.app.ui.screens.status.StatusScreen
import org.visorlink.app.ui.screens.settings.CustomizationScreen
import org.visorlink.app.ui.screens.settings.FlagFlipperScreen
import org.visorlink.app.ui.navigation.authNavGraph
import org.visorlink.app.ui.navigation.settingsNavGraph
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.screens.auth.LoginScreen
import org.visorlink.app.ui.screens.auth.RegisterScreen
import org.visorlink.app.ui.screens.auth.VerifyEmailScreen
import org.visorlink.app.ui.screens.auth.TfaScreen
import org.visorlink.app.ui.screens.chat.ChatScreen
import org.visorlink.app.ui.screens.chat.ImageViewerScreen
import org.visorlink.app.ui.screens.chatlist.ChatListViewModel
import org.visorlink.app.ui.screens.main.MainScreen
import org.visorlink.app.ui.screens.feed.FeedScreen
import org.visorlink.app.ui.screens.onboarding.OnboardingScreen
import org.visorlink.app.ui.screens.comments.CommentsScreen
import org.visorlink.app.ui.screens.decoy.DecoyHomeScreen
import org.visorlink.app.ui.screens.profile.OtherProfileScreen
import org.visorlink.app.ui.screens.profile.ProfileScreen
import org.visorlink.app.ui.screens.search.SearchScreen
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.StealthManager
import org.visorlink.app.ui.screens.topics.TopicListScreen
import org.visorlink.app.ui.screens.topics.TaskTrackerScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VisorLinkNavGraph(
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel,
    pendingChatId: String? = null,
    pendingSenderUid: String? = null,
    onPendingChatOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val context = LocalContext.current
    val stealthManager: StealthManager = org.koin.compose.koinInject()

    // Состояние разблокировки режима скрытия для текущей сессии
    var isStealthUnlocked by remember { mutableStateOf(false) }

    // ── Блокировка при уходе в фон ─────────────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Если приложение свернуто, и стелс включен — снова блокируем
                if (stealthManager.isEnabled() && isStealthUnlocked) {
                    stealthManager.isUnlocked = false
                    isStealthUnlocked = false
                    // Безопасный переход на экран маскировки при уходе в фон
                    navController.navigate(Screen.Decoy.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val authState by authViewModel.authState.collectAsState()
    val showOnboarding by themeViewModel.showOnboarding.collectAsState()
    val isTfaRequired by authViewModel.isTfaRequired.collectAsState()
    val isSessionReady by authViewModel.isSessionReady.collectAsState()

    // ── Three-state auth guard + Onboarding + 2FA ─────────────────────────────
    LaunchedEffect(authState, isStealthUnlocked, showOnboarding, isTfaRequired, isSessionReady) {
        if (stealthManager.isEnabled() && !isStealthUnlocked) {
            return@LaunchedEffect
        }

        if (showOnboarding) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
            }
            return@LaunchedEffect
        }

        if (isTfaRequired) {
            navController.navigate(Screen.Tfa.route) {
                popUpTo(0) { inclusive = true }
            }
            return@LaunchedEffect
        }

        when (authState) {
            is AuthState.NoSession  -> navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
            is AuthState.Unverified -> navController.navigate(Screen.VerifyEmail.route) {
                popUpTo(0) { inclusive = true }
            }
            is AuthState.Verified   -> {
                if (isSessionReady) {
                    authViewModel.onSessionReadyAfter2FA()
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // Обработка перехода в чат из Push-уведомления
    LaunchedEffect(isSessionReady, pendingChatId, pendingSenderUid) {
        if (isSessionReady && !pendingChatId.isNullOrBlank()) {
            val myUid = (authState as? AuthState.Verified)?.user?.uid ?: ""
            val otherUid = if (!pendingSenderUid.isNullOrBlank() && pendingSenderUid != pendingChatId) {
                pendingSenderUid
            } else {
                val cached = ChatDataCache.loadChat(context, pendingChatId)
                if (cached != null && cached.chatType() == ChatType.DIRECT) {
                    cached.otherParticipantId(myUid).takeIf { it.isNotBlank() } ?: pendingChatId
                } else {
                    pendingChatId
                }
            }
            navController.navigate(Screen.Chat.createRoute(pendingChatId, otherUid))
            onPendingChatOpened()
        }
    }

    // Start destination resolution
    val start = when {
        stealthManager.isEnabled() && !isStealthUnlocked -> Screen.Decoy.route
        showOnboarding                    -> Screen.Onboarding.route
        isTfaRequired                     -> Screen.Tfa.route
        isSessionReady                    -> Screen.ChatList.route
        authState is AuthState.Unverified -> Screen.VerifyEmail.route
        else                              -> Screen.Login.route
    }

    NavHost(navController = navController, startDestination = start) {

        // ── Decoy (Stealth Mode) ──────────────────────────────────────────────
        composable(Screen.Decoy.route) {
            DecoyHomeScreen(
                onUnlockSuccess = {
                    stealthManager.isUnlocked = true
                    isStealthUnlocked = true
                    // При изменении isStealthUnlocked на true сработает LaunchedEffect
                    // и перенаправит юзера на нужный экран в зависимости от authState
                }
            )
        }

        // ── Onboarding ───────────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    // Reactive guard will handle navigation to ChatList/Login
                },
                authViewModel = authViewModel
            )
        }

        // ── Auth Subgraph ────────────────────────────────────────────────────
        authNavGraph(
            navController = navController,
            authViewModel = authViewModel
        )

        // ── Main app ──────────────────────────────────────────────────────────
        composable(Screen.ChatList.route) {
            MainScreen(
                onOpenChat = { chatId, otherUid ->
                    if (chatId.startsWith("saved_")) {
                        navController.navigate(Screen.SavedMessages.route)
                    } else {
                        navController.navigate(Screen.Chat.createRoute(chatId, otherUid))
                    }
                },
                onOpenTopicList = { chatId ->
                    navController.navigate(Screen.TopicList.createRoute(chatId))
                },
                onOpenSearch        = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenProfile       = { navController.navigate(Screen.Profile.route) },
                onOpenSettings      = { navController.navigate(Screen.Settings.route) },
                onCreateChat        = { navController.navigate(Screen.CreateChat.route) },
                onFindChannel       = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                onOpenChannel       = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId))
                },
                onOpenComments      = { chatId, messageId ->
                    navController.navigate(Screen.Comments.createRoute(chatId, messageId))
                },
                onOpenImageViewer = { url, type ->
                    navController.navigate(Screen.ImageViewer.createRoute(url, type))
                },
                onAddDiaryEntry = {
                    navController.navigate(Screen.DiaryEntry.createRoute(null))
                },
                onEditDiaryEntry = { id ->
                    navController.navigate(Screen.DiaryEntry.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.TopicList.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            TopicListScreen(
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() },
                onOpenTopicChat = { cId, tId ->
                    navController.navigate(Screen.Chat.createRoute(cId, cId, tId))
                },
                onOpenTaskTracker = { cId, tId ->
                    navController.navigate(Screen.TaskTracker.createRoute(cId, tId))
                },
                onOpenSettings = { cId ->
                    navController.navigate(Screen.ChatSettings.createRoute(cId))
                }
            )
        }

        composable(
            route = Screen.TaskTracker.route,
            arguments = listOf(
                navArgument("chatId")  { type = NavType.StringType },
                navArgument("topicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId  = backStackEntry.arguments?.getString("chatId")  ?: return@composable
            val topicId = backStackEntry.arguments?.getString("topicId") ?: return@composable
            TaskTrackerScreen(
                chatId = chatId,
                topicId = topicId,
                onNavigateBack = { navController.popBackStack() },
                onOpenOtherProfile = { uid ->
                    navController.navigate(Screen.OtherProfile.createRoute(uid))
                },
                onOpenImageViewer = { url, type ->
                    navController.navigate(Screen.ImageViewer.createRoute(url, type))
                },
                onOpenChatSettings = { cId ->
                    navController.navigate(Screen.ChatSettings.createRoute(cId))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType },
                navArgument("topicId")  {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val chatId   = backStackEntry.arguments?.getString("chatId")   ?: return@composable
            val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable
            val topicId  = backStackEntry.arguments?.getString("topicId")

            ChatScreen(
                chatId             = chatId,
                otherUid           = otherUid,
                topicId            = topicId,
                onNavigateBack     = { navController.popBackStack() },
                onOpenOtherProfile = { uid ->
                    navController.navigate(Screen.OtherProfile.createRoute(uid))
                },
                onOpenStickers     = { },
                onOpenChatSettings = { cId ->
                    navController.navigate(Screen.ChatSettings.createRoute(cId))
                },
                onOpenImageViewer  = { url, type ->
                    navController.navigate(Screen.ImageViewer.createRoute(url, type))
                },
                onMentionClick     = { usernameOrTag ->
                    navController.navigate(Screen.Search.createRoute(usernameOrTag))
                },
                onOpenComments     = { msgId ->
                    navController.navigate(Screen.Comments.createRoute(chatId, msgId))
                },
                onOpenTopicList = { cId ->
                    navController.navigate(Screen.TopicList.createRoute(cId)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                },
                hapticEnabled = hapticEnabled
            )
        }

        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "image"
                }
            ),
            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) }
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: return@composable
            val type = backStackEntry.arguments?.getString("type") ?: "image"
            ImageViewerScreen(
                url            = url,
                type           = type,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val initialQuery = backStackEntry.arguments?.getString("query")
            SearchScreen(
                initialQuery   = initialQuery,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat     = { chatId, otherUid ->
                    navController.navigate(Screen.Chat.createRoute(chatId, otherUid)) {
                        popUpTo(Screen.Search.route) { inclusive = true }
                    }
                },
                onJoinedGroup  = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId)) {
                        popUpTo(Screen.Search.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                // logout() → authState becomes NoSession → guard routes to Login
                onLoggedOut    = { authViewModel.logout() },
                onOpenStickers = { navController.navigate(Screen.Stickers.route) }
            )
        }

        composable(
            route = Screen.OtherProfile.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStack ->
            val uid = backStack.arguments?.getString("uid") ?: ""
            OtherProfileScreen(
                uid            = uid,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat     = { chatId, otherUid ->
                    navController.navigate(Screen.Chat.createRoute(chatId, otherUid))
                }
            )
        }

        // ── Settings Subgraph ────────────────────────────────────────────────
        settingsNavGraph(
            navController = navController,
            themeViewModel = themeViewModel
        )

        // Diary route removed to prevent duplicate PIN entry since it's displayed in MainScreen

        composable(
            route = Screen.DiaryEntry.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val chatListEntry = remember(navController) {
                try { navController.getBackStackEntry(Screen.ChatList.route) } catch (_: Exception) { null }
            }
            val diaryVm: DiaryViewModel = if (chatListEntry != null) {
                koinViewModel(viewModelStoreOwner = chatListEntry)
            } else {
                koinViewModel()
            }
            
            DiaryEntryScreen(
                entryId = id,
                onNavigateBack = { navController.popBackStack() },
                viewModel = diaryVm
            )
        }

        composable(Screen.CreateChat.route) {
            org.visorlink.app.ui.screens.group.CreateChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreated      = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId)) {
                        popUpTo(Screen.CreateChat.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Notifications.route) {
            org.visorlink.app.ui.screens.group.NotificationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChat     = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId))
                }
            )
        }

        composable(
            route = Screen.ChatSettings.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            org.visorlink.app.ui.screens.group.ChatSettingsScreen(
                chatId         = chatId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Comments.route,
            arguments = listOf(
                navArgument("chatId")    { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId    = backStackEntry.arguments?.getString("chatId")    ?: return@composable
            val messageId = backStackEntry.arguments?.getString("messageId") ?: return@composable

            val chatListEntry = remember(navController) {
                try { navController.getBackStackEntry(Screen.ChatList.route) } catch (_: Exception) { null }
            }
            val chatListVm: ChatListViewModel? = chatListEntry?.let { koinViewModel(viewModelStoreOwner = it) }
            val channel = chatListVm?.chats?.collectAsState()?.value?.firstOrNull { it.id == chatId }

            CommentsScreen(
                chatId            = chatId,
                messageId         = messageId,
                channel           = channel,
                onNavigateBack    = { navController.popBackStack() },
                onOpenImageViewer = { url, type ->
                    navController.navigate(Screen.ImageViewer.createRoute(url, type))
                },
                hapticEnabled = hapticEnabled
            )
        }

        composable(Screen.SavedMessages.route) {
            org.visorlink.app.ui.screens.saved.SavedMessagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.SavedMessagesSettings.route) }
            )
        }

        composable(Screen.SavedMessagesSettings.route) {
            org.visorlink.app.ui.screens.saved.SavedMessagesSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}