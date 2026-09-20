package org.visorlink.app.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.visorlink.app.ui.Screen
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.screens.auth.LoginScreen
import org.visorlink.app.ui.screens.auth.RegisterScreen
import org.visorlink.app.ui.screens.auth.TfaScreen
import org.visorlink.app.ui.screens.auth.VerifyEmailScreen

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
