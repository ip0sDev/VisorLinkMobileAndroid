package by.iposdev.visorlink.ui

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import by.iposdev.visorlink.data.repository.AuthState
import by.iposdev.visorlink.ui.screens.SettingsScreen
import by.iposdev.visorlink.ui.screens.settings.CacheSettingsScreen
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.auth.LoginScreen
import by.iposdev.visorlink.ui.screens.auth.RegisterScreen
import by.iposdev.visorlink.ui.screens.auth.VerifyEmailScreen
import by.iposdev.visorlink.ui.screens.chat.ChatScreen
import by.iposdev.visorlink.ui.screens.chat.ImageViewerScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.comments.CommentsScreen
import by.iposdev.visorlink.ui.screens.profile.OtherProfileScreen
import by.iposdev.visorlink.ui.screens.profile.ProfileScreen
import by.iposdev.visorlink.ui.screens.search.SearchScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VisorLinkNavGraph(
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel
) {
    val navController = rememberNavController()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    // ── Three-state auth guard (guideline §6) ─────────────────────────────────
    // Reacts to every AuthState change and replaces the entire back-stack,
    // so the user can never press Back into a screen they shouldn't see.
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
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

    // Start destination is resolved synchronously so the first frame is correct.
    val start = when (authState) {
        is AuthState.Verified   -> Screen.ChatList.route
        is AuthState.Unverified -> Screen.VerifyEmail.route
        else                    -> Screen.Login.route
    }

    NavHost(navController = navController, startDestination = start) {

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
            ChatListScreen(
                onOpenChat = { chatId, otherUid ->
                    // ПРОВЕРКА: Если это наше Избранное — идем на отдельный экран
                    if (chatId.startsWith("saved_")) {
                        navController.navigate(Screen.SavedMessages.route)
                    } else {
                        // Обычный чат
                        navController.navigate(Screen.Chat.createRoute(chatId, otherUid))
                    }
                },
                onOpenSearch        = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenProfile       = { navController.navigate(Screen.Profile.route) },
                onOpenSettings      = { navController.navigate(Screen.Settings.route) },
                onCreateChat        = { navController.navigate(Screen.CreateChat.route) },
                onFindChannel       = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) }
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
                onOpenImageViewer  = { url ->
                    navController.navigate(Screen.ImageViewer.createRoute(url))
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
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: return@composable
            ImageViewerScreen(
                url            = url,
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
                themeViewModel      = themeViewModel
            )
        }

        composable(Screen.CacheSettings.route) {
            CacheSettingsScreen(onNavigateBack = { navController.popBackStack() })
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
                onOpenImageViewer = { url ->
                    navController.navigate(Screen.ImageViewer.createRoute(url))
                },
                hapticEnabled = hapticEnabled
            )
        }
        composable(Screen.SavedMessages.route) {
            // Импортируй свой экран (пакет может отличаться)
            by.iposdev.visorlink.ui.screens.saved.SavedMessagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.SavedMessagesSettings.route) }
            )
        }

// Экран настроек Избранного
        composable(Screen.SavedMessagesSettings.route) {
            by.iposdev.visorlink.ui.screens.saved.SavedMessagesSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}