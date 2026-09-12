package by.iposdev.visorlink.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import by.iposdev.visorlink.ui.Screen
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.auth.LoginScreen
import by.iposdev.visorlink.ui.screens.auth.RegisterScreen
import by.iposdev.visorlink.ui.screens.auth.TfaScreen
import by.iposdev.visorlink.ui.screens.auth.VerifyEmailScreen

fun NavGraphBuilder.authNavGraph(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    composable(Screen.Login.route) {
        LoginScreen(
            onNavigateToRegister = { navController.navigate(Screen.Register.route) },
            onLoginSuccess = { /* handled by authState guard in NavGraph */ },
            viewModel = authViewModel
        )
    }

    composable(Screen.Register.route) {
        RegisterScreen(
            onNavigateBack = { navController.popBackStack() },
            onRegistrationComplete = { /* handled by authState guard */ },
            viewModel = authViewModel
        )
    }

    composable(Screen.VerifyEmail.route) {
        VerifyEmailScreen(
            onVerified = { /* handled by authState guard */ },
            onLogout = { authViewModel.logout() },
            viewModel = authViewModel
        )
    }

    composable(Screen.Tfa.route) {
        TfaScreen(
            onTfaPassed = { /* handled by authState/tfa guard */ },
            viewModel = authViewModel
        )
    }
}
