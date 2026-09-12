package by.iposdev.visorlink.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import by.iposdev.visorlink.ui.Screen
import by.iposdev.visorlink.ui.aegis.AegisDebugScreen
import by.iposdev.visorlink.ui.screens.settings.CacheSettingsScreen
import by.iposdev.visorlink.ui.screens.settings.CustomizationScreen
import by.iposdev.visorlink.ui.screens.settings.FlagFlipperScreen
import by.iposdev.visorlink.ui.screens.settings.LiquidGlassTestScreen
import by.iposdev.visorlink.ui.screens.settings.SettingsScreen
import by.iposdev.visorlink.ui.screens.settings.StorageManagerScreen
import by.iposdev.visorlink.ui.screens.status.StatusScreen
import by.iposdev.visorlink.ui.theme.ThemeViewModel

fun NavGraphBuilder.settingsNavGraph(
    navController: NavController,
    themeViewModel: ThemeViewModel
) {
    composable(Screen.Settings.route) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCacheSettings = { navController.navigate(Screen.CacheSettings.route) },
            onOpenStorageManager = { navController.navigate(Screen.StorageManager.route) },
            onOpenStatus = { navController.navigate(Screen.Status.route) },
            onOpenCustomization = { navController.navigate(Screen.Customization.route) },
            onOpenAegisDebug = { navController.navigate(Screen.AegisDebug.route) },
            onOpenFlagFlipper = { navController.navigate(Screen.FlagFlipper.route) },
            onOpenAnimationTest = { navController.navigate(Screen.AnimationTest.route) },
            themeViewModel = themeViewModel
        )
    }

    composable(Screen.AnimationTest.route) {
        LiquidGlassTestScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.AegisDebug.route) {
        AegisDebugScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.FlagFlipper.route) {
        FlagFlipperScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.Customization.route) {
        CustomizationScreen(onNavigateBack = { navController.popBackStack() })
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

    composable(Screen.Status.route) {
        StatusScreen(onNavigateBack = { navController.popBackStack() })
    }
}
