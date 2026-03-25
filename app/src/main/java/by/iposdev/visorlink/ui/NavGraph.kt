package by.iposdev.visorlink.ui

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import by.iposdev.visorlink.ui.screens.SettingsScreen
import by.iposdev.visorlink.ui.screens.settings.CacheSettingsScreen
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.auth.LoginScreen
import by.iposdev.visorlink.ui.screens.auth.RegisterScreen
import by.iposdev.visorlink.ui.screens.chat.ChatScreen
import by.iposdev.visorlink.ui.screens.chat.ImageViewerScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.comments.CommentsScreen
import by.iposdev.visorlink.ui.screens.profile.OtherProfileScreen
import by.iposdev.visorlink.ui.screens.profile.ProfileScreen
import by.iposdev.visorlink.ui.screens.search.SearchScreen
import by.iposdev.visorlink.ui.screens.stickers.StickersScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VisorLinkNavGraph(
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val start = if (currentUser != null) Screen.ChatList.route else Screen.Login.route

    NavHost(navController = navController, startDestination = start) {

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onOpenChat = { chatId, otherUid ->
                    navController.navigate(Screen.Chat.createRoute(chatId, otherUid))
                },
                onOpenSearch = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onCreateChat = { navController.navigate(Screen.CreateChat.route) },
                onFindChannel = { navController.navigate(Screen.Search.createRoute(null)) },
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val otherUid = backStackEntry.arguments?.getString("otherUid") ?: return@composable

            ChatScreen(
                chatId = chatId,
                otherUid = otherUid,
                onNavigateBack = { navController.popBackStack() },
                onOpenOtherProfile = { uid ->
                    navController.navigate(Screen.OtherProfile.createRoute(uid))
                },
                onOpenStickers = { },
                onOpenChatSettings = { cId ->
                    navController.navigate(Screen.ChatSettings.createRoute(cId))
                },
                onOpenImageViewer = { url ->
                    navController.navigate(Screen.ImageViewer.createRoute(url))
                },
                onMentionClick = { usernameOrTag ->
                    navController.navigate(Screen.Search.createRoute(usernameOrTag))
                },
                // ─── NEW v4: open comments for a channel post ─────────────────
                onOpenComments = { msgId ->
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
                url = url,
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
                initialQuery = initialQuery,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { chatId, otherUid ->
                    navController.navigate(Screen.Chat.createRoute(chatId, otherUid)) {
                        popUpTo(Screen.Search.route) { inclusive = true }
                    }
                },
                onJoinedGroup = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId)) {
                        popUpTo(Screen.Search.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenStickers = { navController.navigate(Screen.Stickers.route) }
            )
        }

        composable(
            route = Screen.OtherProfile.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStack ->
            val uid = backStack.arguments?.getString("uid") ?: ""
            OtherProfileScreen(
                uid = uid,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { chatId, otherUid ->
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

        composable(Screen.Stickers.route) {
            StickersScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.CreateChat.route) {
            by.iposdev.visorlink.ui.screens.group.CreateChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreated = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId, chatId)) {
                        popUpTo(Screen.CreateChat.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Notifications.route) {
            by.iposdev.visorlink.ui.screens.group.NotificationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { chatId ->
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
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── NEW v4: Comments screen ──────────────────────────────────────────
        composable(
            route = Screen.Comments.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId    = backStackEntry.arguments?.getString("chatId")    ?: return@composable
            val messageId = backStackEntry.arguments?.getString("messageId") ?: return@composable

            // Resolve the Chat object so CommentsScreen can check allowComments.
            // ChatListViewModel is already scoped to the NavBackStack entry for ChatList,
            // so we look it up from the back-stack entry instead of creating a new instance.
            val chatListEntry = remember(navController) {
                try { navController.getBackStackEntry(Screen.ChatList.route) } catch (_: Exception) { null }
            }
            val chatListVm: ChatListViewModel? = chatListEntry?.let { koinViewModel(viewModelStoreOwner = it) }
            val channel = chatListVm?.chats?.collectAsState()?.value?.firstOrNull { it.id == chatId }

            CommentsScreen(
                chatId = chatId,
                messageId = messageId,
                channel = channel,
                onNavigateBack = { navController.popBackStack() },
                onOpenImageViewer = { url ->
                    navController.navigate(Screen.ImageViewer.createRoute(url))
                },
                hapticEnabled = hapticEnabled
            )
        }
    }
}