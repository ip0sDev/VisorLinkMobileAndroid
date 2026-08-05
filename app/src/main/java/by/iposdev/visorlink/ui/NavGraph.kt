package by.iposdev.visorlink.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.*
import androidx.navigation.compose.*
import by.iposdev.visorlink.data.repository.AuthState
import by.iposdev.visorlink.ui.screens.diary.DiaryScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryEntryScreen
import by.iposdev.visorlink.ui.screens.diary.DiaryViewModel
import by.iposdev.visorlink.ui.screens.settings.SettingsScreen
import by.iposdev.visorlink.ui.screens.settings.CacheSettingsScreen
import by.iposdev.visorlink.ui.screens.settings.StorageManagerScreen
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.auth.LoginScreen
import by.iposdev.visorlink.ui.screens.auth.RegisterScreen
import by.iposdev.visorlink.ui.screens.auth.VerifyEmailScreen
import by.iposdev.visorlink.ui.screens.chat.ChatScreen
import by.iposdev.visorlink.ui.screens.chat.ImageViewerScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.main.MainScreen
import by.iposdev.visorlink.ui.screens.feed.FeedScreen
import by.iposdev.visorlink.ui.screens.onboarding.OnboardingScreen
import by.iposdev.visorlink.ui.screens.comments.CommentsScreen
import by.iposdev.visorlink.ui.screens.decoy.DecoyHomeScreen
import by.iposdev.visorlink.ui.screens.profile.OtherProfileScreen
import by.iposdev.visorlink.ui.screens.profile.ProfileScreen
import by.iposdev.visorlink.ui.screens.search.SearchScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.utils.StealthManager
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VisorLinkNavGraph(
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel,
    appUpdateViewModel: AppUpdateViewModel
) {
    val navController = rememberNavController()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val context = LocalContext.current
    val stealthManager = remember { StealthManager(context) }

    // Состояние разблокировки режима скрытия для текущей сессии
    var isStealthUnlocked by remember { mutableStateOf(false) }

    // ── Блокировка при уходе в фон ─────────────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Если приложение свернуто, и стелс включен — снова блокируем
                if (stealthManager.isEnabled() && isStealthUnlocked) {
                    isStealthUnlocked = false
                    navController.navigate("decoy") {
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

    // ── Three-state auth guard + Onboarding ──────────────────────────────────
    LaunchedEffect(authState, isStealthUnlocked, showOnboarding) {
        if (stealthManager.isEnabled() && !isStealthUnlocked) {
            return@LaunchedEffect
        }

        if (showOnboarding) {
            navController.navigate(Screen.Onboarding.route) {
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
            is AuthState.Verified   -> navController.navigate(Screen.ChatList.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Start destination resolution
    val start = when {
        stealthManager.isEnabled() && !isStealthUnlocked -> "decoy"
        showOnboarding                    -> Screen.Onboarding.route
        authState is AuthState.Verified   -> Screen.ChatList.route
        authState is AuthState.Unverified -> Screen.VerifyEmail.route
        else                              -> Screen.Login.route
    }

    NavHost(navController = navController, startDestination = start) {

        // ── Decoy (Stealth Mode) ──────────────────────────────────────────────
        composable("decoy") {
            DecoyHomeScreen(
                onUnlockSuccess = {
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
                }
            )
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                // Actual navigation happens reactively via LaunchedEffect(authState).
                onLoginSuccess = { /* handled by authState guard */ },
                viewModel = authViewModel
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                // On success authState becomes Unverified → guard routes to VerifyEmail.
                onRegistrationComplete = { /* handled by authState guard */ },
                viewModel = authViewModel
            )
        }

        // ── Email verification (guideline §5) ─────────────────────────────────
        composable(Screen.VerifyEmail.route) {
            VerifyEmailScreen(
                // On verified authState becomes Verified → guard routes to ChatList.
                onVerified = { /* handled by authState guard */ },
                onLogout   = { authViewModel.logout() },
                viewModel  = authViewModel
            )
        }

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
                onAddDiaryEntry = {
                    navController.navigate(Screen.DiaryEntry.createRoute(null))
                },
                onEditDiaryEntry = { id ->
                    navController.navigate(Screen.DiaryEntry.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId")   { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId   = backStackEntry.arguments?.getString("chatId")   ?: return@composable
            val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable

            ChatScreen(
                chatId             = chatId,
                otherUid           = otherUid,
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
            )
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack      = { navController.popBackStack() },
                onOpenCacheSettings = { navController.navigate(Screen.CacheSettings.route) },
                onOpenStorageManager = { navController.navigate(Screen.StorageManager.route) },
                themeViewModel      = themeViewModel,
                appUpdateViewModel  = appUpdateViewModel
            )
        }

        composable(Screen.CacheSettings.route) {
            CacheSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.StorageManager.route) {
            StorageManagerScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewMedia = { url, type ->
                    navController.navigate(Screen.ImageViewer.createRoute(url, type))
                }
            )
        }

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
            by.iposdev.visorlink.ui.screens.group.CreateChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreated      = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId)) {
                        popUpTo(Screen.CreateChat.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Notifications.route) {
            by.iposdev.visorlink.ui.screens.group.NotificationsScreen(
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
            by.iposdev.visorlink.ui.screens.group.ChatSettingsScreen(
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
            by.iposdev.visorlink.ui.screens.saved.SavedMessagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.SavedMessagesSettings.route) }
            )
        }

        composable(Screen.SavedMessagesSettings.route) {
            by.iposdev.visorlink.ui.screens.saved.SavedMessagesSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}