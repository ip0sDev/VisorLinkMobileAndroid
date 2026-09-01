package by.iposdev.visorlink.ui.legal

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.AuthState
import by.iposdev.visorlink.data.repository.LegalRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.legal.LegalConsentDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Неотклоняемый шлюз проверки юридического согласия (Legal Gate).
 * Блокирует интерфейс приложения, если пользователь авторизован,
 * но еще не принял актуальную версию ToS / Privacy Policy.
 */
@Composable
fun LegalConsentGuard(
    authViewModel: AuthViewModel,
    legalRepository: LegalRepository = koinInject(),
    userRepository: UserRepository = koinInject(),
    content: @Composable () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val isVerified = authState is AuthState.Verified
    val currentUser = if (authState is AuthState.Verified) (authState as AuthState.Verified).user else null

    // Наблюдение за профилем текущего пользователя
    val userProfile by userRepository.currentUserFlow().collectAsState(initial = null)

    // Наблюдение за актуальной версией ToS/Privacy на лету через Firestore snapshot
    val latestVersion by legalRepository.observeLatestVersion()
        .collectAsState(initial = legalRepository.loadBundledVersion())

    val coroutineScope = rememberCoroutineScope()

    val isTfaRequired by authViewModel.isTfaRequired.collectAsState()

    // Требуется согласие ТОЛЬКО ПОСЛЕ ДВУХФАКТОРКИ:
    // если двухфакторка активна (isTfaRequired), диалог ToS не показывается, пока пользователь не подтвердит 2FA.
    val requiresConsent = remember(isVerified, userProfile, latestVersion, isTfaRequired) {
        if (!isVerified || currentUser == null || userProfile == null || isTfaRequired) {
            false
        } else {
            userProfile?.acceptedVersion != latestVersion
        }
    }

    Box {
        content()

        if (requiresConsent && currentUser != null) {
            LegalConsentDialog(
                currentVersion = latestVersion,
                legalRepository = legalRepository,
                isReadOnly = false,
                onAccept = {
                    coroutineScope.launch {
                        try {
                            legalRepository.recordConsent(currentUser.uid, latestVersion)
                        } catch (e: Exception) {
                            android.util.Log.e("LegalConsentGuard", "Error recording consent", e)
                        }
                    }
                },
                onLogout = {
                    authViewModel.logout()
                }
            )
        }
    }
}
