package org.visorlink.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.components.VlAmbientGlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * Переработанный экран авторизации VisorLink в стиле Biolume:
 * - Использует реальную иконку приложения ic_launcher
 * - Фоновый биолюминесцентный неон VlAmbientGlow
 * - Единая карточка авторизации AuthCard с вкладками Вход / Регистрация
 */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit = {},
    onLoginSuccess: () -> Unit = {},
    viewModel: AuthViewModel = koinViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            VlAmbientGlow()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AuthCard(
                    viewModel = viewModel,
                    initialTab = 0,
                    onLoginSuccess = onLoginSuccess,
                    onRegistrationComplete = {
                        // Регистрация завершена, реактивный AuthState.Unverified переведет на VerifyEmail
                    }
                )
            }
        }
    }
}