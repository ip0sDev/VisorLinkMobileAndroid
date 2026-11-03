package org.visorlink.app.ui.screens.signal

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
 * Всплывающее уведомление о новом достижении. Слушает `achievements/{uid}`, фильтрует
 * ещё не виденные через `seenKey` и показывает карточку вверху экрана с переходом в «Сигнал».
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
                delay(6000L)
                if (currentAchievement == item.a) {
                    currentAchievement = null
                }
                delay(600L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentAchievement != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)) + fadeOut()
        ) {
            val a = currentAchievement
            if (a != null) {
                val cs = MaterialTheme.colorScheme
                val tokens = VlTheme.tokens
                val shape = tokens.shapes.card

                Box(
                    modifier = Modifier
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
                                .clip(RoundedCornerShape(10.dp))
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "НОВОЕ ДОСТИЖЕНИЕ",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                a.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "+${a.bits}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = tokens.status.warning
                        )
                    }
                }
            }
        }
    }
}
