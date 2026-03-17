package by.iposdev.visorlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.VisorLinkNavGraph
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.VisorLinkTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {

    private val userRepository: UserRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Лаунчер запроса разрешения уведомлений
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("FCM", "Notification permission granted: $granted")
        if (granted) fetchAndSaveFcmToken()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Принудительно получаем FCM токен при каждом запуске
        // (onNewToken вызывается только при смене токена, не при каждом запуске)
        fetchAndSaveFcmToken()

        setContent {
            val themeViewModel: ThemeViewModel = koinViewModel()
            val authViewModel: AuthViewModel = koinViewModel()
            val appTheme by themeViewModel.appTheme.collectAsState()
            val themeMode by themeViewModel.themeMode.collectAsState()

            authViewModel.initPresenceIfLoggedIn()

            VisorLinkTheme(appTheme = appTheme, themeMode = themeMode) {
                VisorLinkNavGraph(
                    authViewModel = authViewModel,
                    themeViewModel = themeViewModel
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Уже есть — просто получаем токен
                    fetchAndSaveFcmToken()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Пользователь уже отказал однажды — запрашиваем снова
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Первый запрос
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        // На Android < 13 разрешение не нужно
    }

    private fun fetchAndSaveFcmToken() {
        // Только если пользователь авторизован
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM", "Got token: $token")
                scope.launch {
                    try {
                        userRepository.saveFcmToken(token)
                        Log.d("FCM", "Token saved to Firestore")
                    } catch (e: Exception) {
                        Log.e("FCM", "Failed to save FCM token", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Failed to get FCM token", e)
            }
    }
}