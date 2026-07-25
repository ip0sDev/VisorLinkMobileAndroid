package by.iposdev.visorlink.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Порт `VlToast`/`_VlToastEntry` из design_system.dart.
 *
 * В отличие от Flutter (глобальный Overlay), в Compose нет "overlay поверх всего
 * дерева" без хоста — поэтому нужен один [VlToastHost], размещённый один раз у
 * корня экрана (Box сверху над NavHost/Scaffold), плюс глобальный [VlToastController]
 * для вызова `VlToastController.show(...)` из любого места (ViewModel/Composable).
 *
 * Использование в корне приложения:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     VisorLinkNavGraph(...)
 *     VlToastHost(appTheme = appTheme)
 * }
 * ```
 */
data class ToastMessage(val id: String, val text: String, val isError: Boolean)

object VlToastController {
    internal val queue = mutableStateListOf<ToastMessage>()

    fun show(message: String, isError: Boolean = false) {
        queue.add(ToastMessage(UUID.randomUUID().toString(), message, isError))
    }
}

@Composable
fun VlToastHost(
    appTheme: AppTheme,
    modifier: Modifier = Modifier,
) {
    val current = VlToastController.queue.firstOrNull()

    Box(modifier) {
        if (current != null) {
            androidx.compose.runtime.key(current.id) {
                VlToastEntry(
                    appTheme = appTheme,
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
    appTheme: AppTheme,
    message: String,
    isError: Boolean,
    onDismissed: () -> Unit,
) {
    val style = by.iposdev.visorlink.ui.theme.rememberExthruStyle(appTheme)
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
            VlGlassPanel(appTheme = appTheme, radius = if (style.isForge) 0.dp else 18.dp) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) style.destructive else style.accent,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(message, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}