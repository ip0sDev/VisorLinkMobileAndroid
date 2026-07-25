package by.iposdev.visorlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.VisorLinkNavGraph
import by.iposdev.visorlink.ui.appcheck.AppCheckGuard
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.VisorLinkTheme
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.ui.update.AppUpdateWrapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : AppCompatActivity() {

    private val userRepository: UserRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("FCM", "Notification permission granted: $granted")
        if (granted) fetchAndSaveFcmToken()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        fetchAndSaveFcmToken()

        // Вызываем нашу проверку официального клиента
        verifyAppCheckStatus()

        setContent {
            val themeViewModel: ThemeViewModel      = koinViewModel()
            val authViewModel: AuthViewModel        = koinViewModel()
            val updateViewModel: AppUpdateViewModel = koinViewModel()

            val appTheme    by themeViewModel.appTheme.collectAsState()
            val themeMode   by themeViewModel.themeMode.collectAsState()
            val colorPreset by themeViewModel.colorPreset.collectAsState()

            VisorLinkTheme(appTheme = appTheme, themeMode = themeMode, colorPreset = colorPreset) {
                AppCheckGuard {
                    AppUpdateWrapper(viewModel = updateViewModel) {
                        VisorLinkNavGraph(
                            authViewModel  = authViewModel,
                            themeViewModel = themeViewModel
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> fetchAndSaveFcmToken()

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                else ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun fetchAndSaveFcmToken() {
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
            .addOnFailureListener { e -> Log.e("FCM", "Failed to get FCM token", e) }
    }

    // --- НОВАЯ ФУНКЦИЯ ДЛЯ APP CHECK ---
    private fun verifyAppCheckStatus() {
        // Если юзер не авторизован, функцию дергать бессмысленно (она всё равно требует auth)
        if (FirebaseAuth.getInstance().currentUser == null) return

        scope.launch {
            try {
                Log.d("VisorLink", "Verifying official client status via Cloud Functions...")

                Firebase.functions.getHttpsCallable("verifyOfficialClient")
                    .call()
                    .await()

                Log.d("VisorLink", "Official client status verified successfully!")
            } catch (e: Exception) {
                // Если AppCheck не пропустит или функция упадет, логируем ошибку
                Log.e("VisorLink", "App Check verification failed: ${e.message}", e)
            }
        }
    }
}