package org.visorlink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.VisorLinkNavGraph
import org.visorlink.app.ui.appcheck.AppCheckGuard
import org.visorlink.app.ui.components.FlagsOverlay
import org.visorlink.app.ui.maintenance.ServiceModeGuard
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.ui.theme.VisorLinkTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.functions.FirebaseFunctions
import androidx.lifecycle.lifecycleScope
import org.visorlink.app.ui.legal.LegalConsentGuard
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
    private val authViewModel: AuthViewModel by inject()
    private val fcmManager: org.visorlink.app.utils.FcmManager by inject()
    private val musicPlayerManager: org.visorlink.app.utils.MusicPlayerManager by inject()

    private val pendingOpenChatId = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val pendingOpenSenderUid = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("FCM", "Notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        extractOpenChatId(intent)
        extractInviteCode(intent)
        if (intent?.getBooleanExtra("open_music_player", false) == true) {
            musicPlayerManager.openFullscreenPlayer()
        }

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
            val showDebugIds by themeViewModel.showDebugIds.collectAsState()

            LaunchedEffect(Unit) {
                if (firebaseAuth.currentUser != null) {
                    try {
                        fcmManager.syncTokenAfter2FA()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "FCM token sync failed on launch", e)
                    }
                }
            }

            VisorLinkTheme(
                appTheme = appTheme,
                themeMode = themeMode,
                colorPreset = colorPreset,
                showDebugIds = showDebugIds
            ) {
                ServiceModeGuard(authViewModel = authViewModel) {
                    AppCheckGuard {
                        LegalConsentGuard(authViewModel = authViewModel) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // Изолированная полоска статусбара на уровне всего приложения
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsTopHeight(WindowInsets.statusBars)
                                        .background(MaterialTheme.colorScheme.background)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .consumeWindowInsets(WindowInsets.statusBars)
                                ) {
                                    VisorLinkNavGraph(
                                        authViewModel = authViewModel,
                                        themeViewModel = themeViewModel,
                                        pendingChatId = pendingOpenChatId.value,
                                        pendingSenderUid = pendingOpenSenderUid.value,
                                        onPendingChatOpened = {
                                            pendingOpenChatId.value = null
                                            pendingOpenSenderUid.value = null
                                        }
                                    )
                                    FlagsOverlay()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractOpenChatId(intent)
        extractInviteCode(intent)
        if (intent.getBooleanExtra("open_music_player", false)) {
            musicPlayerManager.openFullscreenPlayer()
        }
    }

    private fun extractInviteCode(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        // https://visorlink.org/invite?code=... OR visorlink://invite?code=...
        val code = data.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            authViewModel.setPendingInviteCode(code.trim())
        }
    }

    private fun extractOpenChatId(intent: android.content.Intent?) {
        val chatId = intent?.getStringExtra("openChatId")
            ?: intent?.getStringExtra("chatId")
        val senderUid = intent?.getStringExtra("senderUid")
        if (!chatId.isNullOrBlank()) {
            pendingOpenChatId.value = chatId
            pendingOpenSenderUid.value = senderUid
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
        org.visorlink.app.utils.ActiveChatTracker.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        org.visorlink.app.utils.ActiveChatTracker.isAppInForeground = false
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