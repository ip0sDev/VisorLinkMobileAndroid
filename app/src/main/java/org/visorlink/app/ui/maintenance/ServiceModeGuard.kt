package org.visorlink.app.ui.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.repository.AuthState
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.screens.auth.AuthViewModel

/** Как часто перепроверять, пока приложение заблокировано: ждём конца работ. */
private const val BLOCKED_REFRESH_MS = 30_000L

/**
 * Режим обслуживания: флаг Aegis `service_mode_enabled` ([org.visorlink.app.data.model.flags.AppFlags.serviceMode]).
 *
 * Пока он включён, вместо приложения показывается экран обслуживания — само приложение
 * не компонуется, поэтому под ним не идут навигация и запросы. Флаги перечитываются при
 * возврате в приложение и по таймеру, так что блокировка включается и снимается без
 * перезапуска. Без сети действует последнее полученное значение.
 *
 * Администраторы (`users.isAdmin`) могут продолжить работу до перезапуска приложения.
 */
@Composable
fun ServiceModeGuard(
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit
) {
    val flagsRepository: FlagsRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val flags by flagsRepository.flags.collectAsState()
    val serviceMode = flags.serviceMode

    val authState by authViewModel.authState.collectAsState()
    val uid = (authState as? AuthState.Verified)?.user?.uid
    val profile by remember(uid) {
        if (uid == null) flowOf(null) else userRepository.userProfileFlow(uid)
    }.collectAsState(initial = null)
    val isAdmin = uid != null && profile?.isAdmin == true

    var isBypassed by rememberSaveable { mutableStateOf(false) }
    // Работы закончились — следующие снова блокируют и админа
    LaunchedEffect(serviceMode) { if (!serviceMode) isBypassed = false }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val blocked = serviceMode && !isBypassed
    LaunchedEffect(resumed, blocked) {
        if (!resumed) return@LaunchedEffect
        // Первая проверка — сразу при возврате (не чаще раза в минуту). Дальше в обычном режиме
        // конфиг перечитывает FlagsRepository.startAutoRefresh (раз в 10 минут), а заблокированный
        // экран проверяет чаще — ждём конца работ
        flagsRepository.refreshIfStale(maxAgeMs = 60_000L)
        while (blocked) {
            delay(BLOCKED_REFRESH_MS)
            flagsRepository.fetchFlags()
        }
    }

    if (blocked) {
        ServiceModeScreen(
            isAdmin = isAdmin,
            onRetry = { flagsRepository.fetchFlags() },
            onBypass = { isBypassed = true },
        )
    } else {
        content()
    }
}

@Composable
private fun ServiceModeScreen(
    isAdmin: Boolean,
    onRetry: suspend () -> Unit,
    onBypass: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Construction,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.service_mode_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.service_mode_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            VlButton(
                onClick = {
                    if (checking) return@VlButton
                    checking = true
                    scope.launch {
                        try { onRetry() } finally { checking = false }
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (checking) R.string.service_mode_checking else R.string.service_mode_retry))
            }

            if (isAdmin) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.service_mode_admin),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                VlDialogButton(onClick = onBypass) {
                    Text(stringResource(R.string.service_mode_bypass))
                }
            }
        }
    }
}
