package by.iposdev.visorlink.ui.maintenance

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.data.repository.AuthState
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject

/**
 * Блокирующий экран обслуживания.
 * Если в Firebase Remote Config флаг `service_mode_enabled == true`,
 * приложение показывает полноэкранный блокировщик.
 *
 * Администраторы (isAdmin == true) могут нажать «Bypass» и продолжить работу.
 */
@Composable
fun ServiceModeGuard(
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit
) {
    val themeViewModel: ThemeViewModel = koinInject()
    val userRepository: UserRepository = koinInject()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    // Состояние Remote Config
    var serviceModeEnabled by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    // Флаг, что админ нажал bypass
    var isBypassed by remember { mutableStateOf(false) }

    // Определяем админа
    val authState by authViewModel.authState.collectAsState()
    val userProfile by userRepository.currentUserFlow().collectAsState(initial = null)

    val isAdmin = remember(authState, userProfile) {
        val verifiedUser = if (authState is AuthState.Verified) (authState as AuthState.Verified).user else null
        verifiedUser != null && (userProfile?.isAdmin == true)
    }

    // Инициализация Remote Config и получение флага
    LaunchedEffect(Unit) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()

            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            }
            remoteConfig.setConfigSettingsAsync(configSettings)

            remoteConfig.setDefaultsAsync(mapOf("service_mode_enabled" to false))

            remoteConfig.fetchAndActivate().await()

            serviceModeEnabled = remoteConfig.getBoolean("service_mode_enabled")
            isLoaded = true

            if (BuildConfig.DEBUG) {
                Log.d("ServiceModeGuard", "Remote Config loaded. service_mode_enabled=$serviceModeEnabled")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("ServiceModeGuard", "Failed to load Remote Config", e)
            }
            serviceModeEnabled = false
            isLoaded = true
        }
    }

    // Показываем контент приложения
    Box {
        content()

        // Если сервисный режим включён, админ не нажал bypass — показываем блокировку
        if (serviceModeEnabled && !isBypassed && isLoaded) {
            ServiceModeBlocker(
                isAdmin = isAdmin,
                onBypass = {
                    haptic.perform(HapticType.CLICK, hapticEnabled)
                    isBypassed = true
                }
            )
        }
    }
}

@Composable
private fun ServiceModeBlocker(
    isAdmin: Boolean,
    onBypass: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Неотменяемый — блокирует приложение */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Construction,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "🛠 Сервера на обслуживании",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "В настоящий момент сервера VisorLink проходят плановое обслуживание.\n\nПожалуйста, попробуйте зайти позже.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (isAdmin) {
                    Spacer(Modifier.height(20.dp))

                    HorizontalDivider()

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Вы вошли как администратор.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onBypass,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bypass — продолжить работу")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}