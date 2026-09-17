package org.visorlink.app.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.visorlink.app.ui.Screen
import org.visorlink.app.ui.aegis.AegisDebugScreen
import org.visorlink.app.ui.screens.settings.CacheSettingsScreen
import org.visorlink.app.ui.screens.settings.CustomizationScreen
import org.visorlink.app.ui.screens.settings.FlagFlipperScreen
import org.visorlink.app.ui.screens.settings.LiquidGlassTestScreen
import org.visorlink.app.ui.screens.settings.SettingsScreen
import org.visorlink.app.ui.screens.settings.AppCheckDiagnosticScreen
import org.visorlink.app.ui.screens.settings.StorageManagerScreen
import org.visorlink.app.ui.screens.status.StatusScreen
import org.visorlink.app.ui.theme.ThemeViewModel

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
            onNavigateToAppCheckDiagnostic = { navController.navigate(Screen.AppCheckDiagnostic.route) },
            themeViewModel = themeViewModel
        )
    }

    composable(Screen.AppCheckDiagnostic.route) {
        AppCheckDiagnosticScreen(onNavigateBack = { navController.popBackStack() })
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
