package org.visorlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlCurves
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import kotlinx.coroutines.delay
import java.util.UUID

data class ToastMessage(val id: String, val text: String, val isError: Boolean)

object VlToastController {
    internal val queue = mutableStateListOf<ToastMessage>()

    fun show(message: String, isError: Boolean = false) {
        queue.add(ToastMessage(UUID.randomUUID().toString(), message, isError))
    }
}

@Composable
fun VlToastHost(
    modifier: Modifier = Modifier,
) {
    val current = VlToastController.queue.firstOrNull()

    Box(modifier) {
        if (current != null) {
            key(current.id) {
                VlToastEntry(
                    message = current.text,
                    isError = current.isError,
                    onDismissed = { VlToastController.queue.remove(current) },
                )
            }
        }
    }
}

@Composable
private fun VlToastEntry(
    message: String,
    isError: Boolean,
    onDismissed: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val progress = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        progress.animateTo(1f, tween(220))
        delay(2200)
        exiting = true
        progress.animateTo(0f, tween(160))
        onDismissed()
    }

    // inverseSurface как у Flutter VlSnackBar (vl_overlays.dart:205-207)
    val bg = cs.inverseSurface
    val fg = if (bg.luminance() < 0.5f) Color.White else Color(0xFF15171C)
    val shape = tokens.shapes.rounded(18.dp)
    var barHeight by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.onSizeChanged { barHeight = it.height.toFloat() }
                .graphicsLayer {
                    // Порт анимации Flutter VlSnackBar (vl_overlays.dart:257-281):
                    // вход — dy 1.1→0, scale 0.92→1, fade на 40 %; выход — dy 0.9e, scale −0.04e.
                    if (exiting) {
                        val e = 1f - progress.value
                        translationY = 0.9f * e * barHeight
                        val s = 1f - 0.04f * e
                        scaleX = s; scaleY = s
                        alpha = (1f - e).coerceIn(0f, 1f)
                    } else {
                        val t = (if (tokens.motion.useSpring) VlCurves.Spring else VlCurves.Key).transform(progress.value)
                        translationY = 1.1f * (1f - t) * barHeight
                        val s = 0.92f + 0.08f * t
                        scaleX = s; scaleY = s
                        alpha = androidx.compose.animation.core.EaseOut.transform((progress.value / 0.4f).coerceIn(0f, 1f))
                    }
                }
        ) {
            Box(
                Modifier
                    .widthIn(max = 560.dp)
                    .then(if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, shape) else Modifier)
                    .clip(shape)
                    .background(bg, shape)
                    .vlHairline(Color.White.copy(alpha = if (tokens.isDark) 0.06f else 0.12f), shape)
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) cs.error else tokens.status.success,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        color = fg,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = fg,
                            lineHeight = (MaterialTheme.typography.bodyMedium.fontSize.value * 1.3f).sp,
                        ),
                    )
                }
            }
        }
    }
}
