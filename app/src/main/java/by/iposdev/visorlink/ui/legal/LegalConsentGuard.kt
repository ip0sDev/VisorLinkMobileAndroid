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

    val currentUid = currentUser?.uid
    val userProfileFlow = remember(currentUid) {
        if (currentUid != null) userRepository.userProfileFlow(currentUid) else kotlinx.coroutines.flow.flowOf(null)
    }
    val userProfile by userProfileFlow.collectAsState(initial = null)

    val latestVersionFlow = remember { legalRepository.observeLatestVersion() }
    val latestVersion by latestVersionFlow.collectAsState(initial = legalRepository.loadBundledVersion())

    val coroutineScope = rememberCoroutineScope()

    val isTfaRequired by authViewModel.isTfaRequired.collectAsState()

    var locallyAcceptedState by remember { mutableStateOf(false) }

    // Проверяем версию в EncryptedSharedPreferences (п. 2.2 ANDROID_COMPLIANCE)
    val localAcceptedVersion = remember(currentUser?.uid, locallyAcceptedState) {
        legalRepository.getLocalAcceptedVersion()
    }

    // Эффективная принятая версия: берем наибольшую между версией в профиле на сервере (например,
    // принятой при регистрации с ПК) и локальным кэшем в EncryptedSharedPreferences.
    val effectiveAcceptedVersion = remember(userProfile?.acceptedVersion, localAcceptedVersion) {
        val serverVersion = userProfile?.acceptedVersion
        if (serverVersion != null && localAcceptedVersion != null) {
            if (legalRepository.compareSemVer(serverVersion, localAcceptedVersion) >= 0) serverVersion else localAcceptedVersion
        } else {
            serverVersion ?: localAcceptedVersion
        }
    }

    // Если на сервере версия уже зафиксирована (например, пользователь зарегистрировался с ПК),
    // синхронизируем локальный EncryptedSharedPreferences.
    LaunchedEffect(userProfile?.acceptedVersion) {
        val serverVersion = userProfile?.acceptedVersion
        if (!serverVersion.isNullOrBlank()) {
            val local = legalRepository.getLocalAcceptedVersion()
            if (local == null || legalRepository.compareSemVer(serverVersion, local) > 0) {
                legalRepository.saveLocalAcceptedVersion(serverVersion)
            }
        }
    }

    // Согласие требуется согласно п. 2.1 ANDROID_COMPLIANCE ТОЛЬКО если remote.acceptedVersion > local.acceptedVersion:
    val requiresConsent = remember(isVerified, userProfile, latestVersion, isTfaRequired, effectiveAcceptedVersion, locallyAcceptedState) {
        if (!isVerified || currentUser == null || isTfaRequired || locallyAcceptedState) {
            false
        } else if (userProfile == null && localAcceptedVersion != null && !legalRepository.isConsentRequired(latestVersion, localAcceptedVersion)) {
            false
        } else {
            legalRepository.isConsentRequired(latestVersion, effectiveAcceptedVersion)
        }
    }

    Box {
        content()

        if (requiresConsent && currentUser != null) {
            LegalConsentDialog(
                currentVersion = latestVersion,
                legalRepository = legalRepository,
                isReadOnly = false,
                onAccept = { agreeTos, agreePrivacy, agreePersonalData, agreeCrossBorder, agreeAge14 ->
                    try {
                        legalRepository.recordConsent(
                            userId = currentUser.uid,
                            version = latestVersion,
                            agreeTos = agreeTos,
                            agreePrivacy = agreePrivacy,
                            agreePersonalData = agreePersonalData,
                            agreeCrossBorder = agreeCrossBorder,
                            agreeAge14 = agreeAge14
                        )
                        userRepository.updateCachedProfile(currentUser.uid) {
                            it.copy(
                                acceptedVersion = latestVersion,
                                acceptedAt = com.google.firebase.Timestamp.now()
                            )
                        }
                        locallyAcceptedState = true
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("LegalConsentGuard", "Error recording consent", e)
                        false
                    }
                },
                onLogout = {
                    authViewModel.logout()
                }
            )
        }
    }
}
