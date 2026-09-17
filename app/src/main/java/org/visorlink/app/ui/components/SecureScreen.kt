package org.visorlink.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Обертка для защищенных экранов и компонентов.
 * Активирует WindowManager.LayoutParams.FLAG_SECURE, запрещая создание скриншотов,
 * запись экрана и предпросмотр экрана в диспетчере недавних приложений (Recent Apps).
 */
@Composable
fun SecureScreen(
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(enabled, activity) {
        if (enabled && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (enabled && activity != null) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    content()
}
