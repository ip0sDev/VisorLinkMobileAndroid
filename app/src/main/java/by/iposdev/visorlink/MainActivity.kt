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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.VisorLinkNavGraph
import by.iposdev.visorlink.ui.appcheck.AppCheckGuard
import by.iposdev.visorlink.ui.components.FlagsOverlay
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.VisorLinkTheme
import com.ipos.store.sdk.IposStoreUpdates
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.functions.FirebaseFunctions
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : AppCompatActivity() {

    private val userRepository: UserRepository by inject()
    private val functions: FirebaseFunctions by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("FCM", "Notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── FIX: Check auth state BEFORE setting content to avoid login flash ──────
        val firebaseAuth = FirebaseAuth.getInstance()
        val isUserLoggedIn = firebaseAuth.currentUser != null
        
        // If user is already logged in and email verified, skip login screen
        if (isUserLoggedIn) {
            // Quick verification - will be confirmed in nav graph
        }
        
        requestNotificationPermissionIfNeeded()

        // Вызываем нашу проверку официального клиента
        verifyAppCheckStatus()

        setContent {
            val themeViewModel: ThemeViewModel = koinViewModel()
            val authViewModel: AuthViewModel   = koinViewModel()

            val appTheme    by themeViewModel.appTheme.collectAsState()
            val themeMode   by themeViewModel.themeMode.collectAsState()
            val colorPreset by themeViewModel.colorPreset.collectAsState()

            LaunchedEffect(Unit) {
                IposStoreUpdates.checkUpdate()
            }

            VisorLinkTheme(appTheme = appTheme, themeMode = themeMode, colorPreset = colorPreset) {
                AppCheckGuard {
                    by.iposdev.visorlink.ui.legal.LegalConsentGuard(authViewModel = authViewModel) {
                        Box {
                            VisorLinkNavGraph(
                                authViewModel = authViewModel,
                                themeViewModel = themeViewModel
                            )
                            FlagsOverlay()
                            IposStoreUpdates.IposUpdateHost()
                        }
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
                ) == PackageManager.PERMISSION_GRANTED -> { /* Разрешение уже есть */ }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                else ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        by.iposdev.visorlink.utils.ActiveChatTracker.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        by.iposdev.visorlink.utils.ActiveChatTracker.isAppInForeground = false
    }

    // --- НОВАЯ ФУНКЦИЯ ДЛЯ APP CHECK ---
    private fun verifyAppCheckStatus() {
        // Если юзер не авторизован, функцию дергать бессмысленно (она всё равно требует auth)
        if (FirebaseAuth.getInstance().currentUser == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("VisorLink", "Verifying official client status via Cloud Functions...")

                functions.getHttpsCallable("verifyOfficialClient")
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