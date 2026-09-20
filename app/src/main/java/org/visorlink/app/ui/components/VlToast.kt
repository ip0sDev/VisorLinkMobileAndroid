package org.visorlink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
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
    val progress = remember { Animatable(0f) }

    LaunchedEffect(message) {
        progress.animateTo(1f, tween(220))
        delay(2200)
        progress.animateTo(0f, tween(160))
        onDismissed()
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
                translationY = (1f - progress.value) * 20.dp.toPx()
            }
        ) {
            VlGlassPanel(radius = 18.dp) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        // success — не M3-роль, поэтому берём из расширения токенов.
                        tint = if (isError) cs.error else VlTheme.tokens.status.success,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(message, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
