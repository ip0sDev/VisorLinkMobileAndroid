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
import androidx.compose.ui.graphics.toArgb
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
import org.visorlink.app.ui.theme.toAppTheme
import org.visorlink.app.ui.theme.proAccent
import org.visorlink.app.ui.theme.vlTerminalBackdrop
import androidx.compose.runtime.remember
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
    private val yandexRelayConfigManager: org.visorlink.app.data.remote.yandex.YandexRelayConfigManager by inject()
    private val maskModeManager: org.visorlink.app.utils.MaskModeManager by inject()
    private val chatThemeController: org.visorlink.app.ui.idcard.ChatThemeController by inject()

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
        extractYandexAuthToken(intent)
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

            // Тема по режиму ID-карты (веб: ModeThemeSync): после входа её решает режим
            // (Biolume / Forge v2), а не выбор в настройках
            val authState by authViewModel.authState.collectAsState()
            val myUid = (authState as? org.visorlink.app.data.repository.AuthState.Verified)?.user?.uid
            val myProfile by remember(myUid) {
                if (myUid == null) kotlinx.coroutines.flow.flowOf(null) else userRepository.userProfileFlow(myUid)
            }.collectAsState(initial = null)
            // Режим и акцент последнего входа: профиль приходит позже первого кадра, и без этого
            // каждый запуск рисовался в Biolume и через мгновение рывком перекрашивался в Forge
            val themePrefs = remember { getSharedPreferences("visorlink_settings", MODE_PRIVATE) }
            LaunchedEffect(myUid, myProfile) {
                val p = myProfile ?: return@LaunchedEffect
                if (myUid == null || p.uid != myUid) return@LaunchedEffect
                themePrefs.edit()
                    .putString(KEY_LAST_ID_MODE + myUid, p.idMode.orEmpty())
                    .putString(KEY_LAST_PRO_ACCENT + myUid, p.proAccent()?.let { "#%06X".format(it.toArgb() and 0xFFFFFF) }.orEmpty())
                    .apply()
            }
            val cachedIdMode = remember(myUid) { myUid?.let { themePrefs.getString(KEY_LAST_ID_MODE + it, null) }?.ifEmpty { null } }
            val cachedProAccent = remember(myUid) {
                myUid?.let { themePrefs.getString(KEY_LAST_PRO_ACCENT + it, null) }?.takeIf { it.length == 7 }
                    ?.let { androidx.compose.ui.graphics.Color(it.substring(1).toLong(16) or 0xFF000000) }
            }
            val maskState by maskModeManager.state.collectAsState()
            val chatThemeContext by chatThemeController.context.collectAsState()
            val modeTheme = org.visorlink.app.data.idcard.ModeThemeRules.resolve(
                idCardsEnabled = myUid != null,
                idMode = if (myProfile != null) myProfile?.idMode else cachedIdMode,
                masked = maskState.active,
                chatTheme = chatThemeContext,
            )
            val idModeState = org.visorlink.app.ui.idcard.IdModeUiState(
                enabled = myUid != null,
                myUid = myUid,
                myMode = org.visorlink.app.data.idcard.IdMode.of(if (myProfile != null) myProfile?.idMode else cachedIdMode),
                mask = maskState,
                theme = modeTheme,
            )
            val viewerTheme = if (idModeState.enabled) {
                org.visorlink.app.ui.theme.ViewerTheme(
                    appTheme = modeTheme.toAppTheme(),
                    themeMode = if (modeTheme.forceDark) org.visorlink.app.data.model.ThemeMode.DARK else themeMode,
                    colorPreset = colorPreset,
                    modeDriven = true,
                    // Цвет профиля PRO — акцент всего интерфейса (Biolume и Forge v2)
                    proAccent = if (myProfile != null) myProfile?.proAccent() else cachedProAccent,
                )
            } else {
                org.visorlink.app.ui.theme.ViewerTheme(appTheme, themeMode, colorPreset)
            }

            LaunchedEffect(Unit) {
                if (firebaseAuth.currentUser != null) {
                    try {
                        fcmManager.syncTokenAfter2FA()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "FCM token sync failed on launch", e)
                    }
                }
                org.visorlink.app.utils.UpdateManager.onAppForegroundCheck(this@MainActivity)
            }

            // Смена темы целиком (Biolume ↔ Forge, светлая ↔ тёмная) — растворением снимка
            org.visorlink.app.ui.theme.ThemeCrossfade(
                value = viewerTheme,
                key = { it.appTheme to it.themeMode },
            ) { shownTheme ->
            VisorLinkTheme(
                appTheme = shownTheme.appTheme,
                themeMode = shownTheme.themeMode,
                colorPreset = shownTheme.colorPreset,
                showDebugIds = showDebugIds,
                animateColors = idModeState.enabled,
                proAccent = shownTheme.proAccent,
            ) {
              androidx.compose.runtime.CompositionLocalProvider(
                  org.visorlink.app.ui.theme.LocalViewerTheme provides shownTheme,
                  org.visorlink.app.ui.idcard.LocalIdModeState provides idModeState,
              ) {
                ServiceModeGuard(authViewModel = authViewModel) {
                    AppCheckGuard {
                        LegalConsentGuard(authViewModel = authViewModel) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .vlTerminalBackdrop(org.visorlink.app.ui.theme.VlTheme.tokens)
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
                                    // Выдача ID-карты: без карты в приложение не пускаем (спека §5)
                                    org.visorlink.app.ui.idcard.IdCardGate()
                                    FlagsOverlay()
                                    // force_update_min_version > versionCode — обновление обязательно
                                    val flagsRepository: org.visorlink.app.data.repository.FlagsRepository = org.koin.compose.koinInject()
                                    val appFlags by flagsRepository.flags.collectAsState()
                                    org.visorlink.app.utils.UpdateManager.UpdateHost(force = appFlags.forceUpdateRequired())
                                }
                            }
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
        extractYandexAuthToken(intent)
        if (intent.getBooleanExtra("open_music_player", false)) {
            musicPlayerManager.openFullscreenPlayer()
        }
    }

    private fun extractYandexAuthToken(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "visorlink" && data.host == "yandex-auth") {
            var token: String? = data.getQueryParameter("access_token")
            if (token.isNullOrBlank()) {
                val fragment = data.fragment
                if (!fragment.isNullOrBlank()) {
                    val params = fragment.split("&").associate { param ->
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                    }
                    token = params["access_token"]
                }
            }
            if (!token.isNullOrBlank()) {
                yandexRelayConfigManager.setCustomToken(token)
                yandexRelayConfigManager.setRelayEnabled(true)
                android.widget.Toast.makeText(this, "Яндекс.Диск подключен!", android.widget.Toast.LENGTH_SHORT).show()
            }
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
        // Срок маски мог истечь, пока приложение было в фоне
        maskModeManager.refresh()
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

    private companion object {
        /** Режим ID-карты и цвет профиля последнего входа (тема до прихода профиля). */
        const val KEY_LAST_ID_MODE = "last_id_mode_"
        const val KEY_LAST_PRO_ACCENT = "last_pro_accent_"
    }
}
