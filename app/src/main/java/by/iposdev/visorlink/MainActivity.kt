package by.iposdev.visorlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import androidx.appcompat.app.AppCompatActivity

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

        setContent {
            val themeViewModel: ThemeViewModel      = koinViewModel()
            val authViewModel: AuthViewModel        = koinViewModel()
            val updateViewModel: AppUpdateViewModel = koinViewModel()

            val appTheme  by themeViewModel.appTheme.collectAsState()
            val themeMode by themeViewModel.themeMode.collectAsState()

            VisorLinkTheme(appTheme = appTheme, themeMode = themeMode) {
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
}