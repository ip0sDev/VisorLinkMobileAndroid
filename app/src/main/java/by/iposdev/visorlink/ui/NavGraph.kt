package by.iposdev.visorlink.ui

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.Sticker
import by.iposdev.visorlink.ui.screens.SettingsScreen
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.auth.LoginScreen
import by.iposdev.visorlink.ui.screens.auth.RegisterScreen
import by.iposdev.visorlink.ui.screens.chat.ChatScreen
import by.iposdev.visorlink.ui.screens.chatlist.ChatListScreen
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
    var stickerCallback by remember { mutableStateOf<((Sticker) -> Unit)?>(null) }

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
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType }
            )
        ) { backStack ->
            val chatId = backStack.arguments?.getString("chatId") ?: ""
            val otherUid = backStack.arguments?.getString("otherUid") ?: ""
            ChatScreen(
                chatId = chatId,
                otherUid = otherUid,
                onNavigateBack = { navController.popBackStack() },
                onOpenOtherProfile = { uid ->
                    navController.navigate(Screen.OtherProfile.createRoute(uid))
                },
                onOpenStickers = { callback ->
                    stickerCallback = callback
                    navController.navigate(Screen.Stickers.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { chatId, otherUid ->
                    navController.navigate(Screen.Chat.createRoute(chatId, otherUid)) {
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
                onNavigateBack = { navController.popBackStack() },
                themeViewModel = themeViewModel
            )
        }

        composable(Screen.Stickers.route) {
            StickersScreen(
                onNavigateBack = {
                    stickerCallback = null
                    navController.popBackStack()
                },
                onSelectSticker = stickerCallback?.let { cb ->
                    { sticker ->
                        cb(sticker)
                        stickerCallback = null
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}