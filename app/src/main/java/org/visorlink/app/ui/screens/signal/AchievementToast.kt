package org.visorlink.app.ui.screens.signal

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.visorlink.app.data.model.Achievement
import org.visorlink.app.data.model.unseenAchievements
import org.visorlink.app.data.repository.SignalRepository
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/**
 * Всплывающее уведомление о новом достижении (веб `AchievementToast.jsx` / Flutter `achievement_toast.dart`):
 * слушает `achievements/{uid}`, фильтрует ещё не виденные через локальное хранилище и показывает
 * плашку вверху экрана с переходом в «Сигнал» по тапу.
 */
@Composable
fun AchievementToastHost(
    onOpenSignal: () -> Unit,
    signalRepository: SignalRepository = koinInject(),
    auth: FirebaseAuth = koinInject()
) {
    val haptic = rememberHaptic()
    val uid = auth.currentUser?.uid.orEmpty()
    var currentAchievement by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        signalRepository.achievements(uid).collect { unlocked ->
            val seen = signalRepository.loadSeen(uid)
            if (seen == null && unlocked.isEmpty()) return@collect
            val fresh = unseenAchievements(unlocked, seen, System.currentTimeMillis())
            val allSeen = (seen ?: unlocked.keys) + fresh.map { it.a.id }
            signalRepository.saveSeen(uid, allSeen)

            for (item in fresh) {
                currentAchievement = item.a
                haptic.perform(HapticType.SUCCESS)
                delay(7000L)
                if (currentAchievement == item.a) {
                    currentAchievement = null
                }
                delay(900L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentAchievement != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(260)) + fadeIn(tween(260)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(260)) + fadeOut(tween(260))
        ) {
            val a = currentAchievement
            if (a != null) {
                val cs = MaterialTheme.colorScheme
                val tokens = VlTheme.tokens
                val shape = tokens.shapes.card
                val iconShape = tokens.shapes.rounded(10.dp)

                Box(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .then(if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape) else Modifier)
                        .clip(shape)
                        .background(cs.surfaceContainerHigh)
                        .vlHairline(tokens.status.warning.copy(alpha = 0.5f), shape)
                        .clickable {
                            currentAchievement = null
                            onOpenSignal()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(iconShape)
                                .background(tokens.status.warning.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                achievementIcon(a.id),
                                contentDescription = null,
                                tint = tokens.status.warning,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "НОВОЕ ДОСТИЖЕНИЕ",
                                style = tokens.data.dataSmall.copy(
                                    color = cs.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.4.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = a.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = cs.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Text(
                            text = "+${a.bits}",
                            style = tokens.data.dataSmall.copy(
                                color = tokens.status.warning,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
