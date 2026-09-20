package org.visorlink.app.ui.screens.decoy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

@Composable
fun DecoyHomeScreen(
    onUnlockSuccess: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(1) }
    var tapCount by remember { mutableIntStateOf(0) }
    var showUnlockSheet by remember { mutableStateOf(false) }

    // Статус-бар: маскировочный экран всегда тёмный, поэтому иконки должны быть
    // светлыми независимо от темы приложения. При уходе с экрана возвращаем как было.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightIcons = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previousLightIcons != null) {
                controller.isAppearanceLightStatusBars = previousLightIcons
            }
        }
    }
    // Переутверждаем после SideEffect темы: VisorLinkTheme перекрашивает иконки
    // статус-бара при каждой своей рекомпозиции и перезаписал бы наше значение.
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Сброс тапов через время
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(3000)
            tapCount = 0
        }
    }

    if (showUnlockSheet) {
        DecoyUnlockSheet(
            onDismiss = { showUnlockSheet = false },
            onUnlocked = {
                showUnlockSheet = false
                onUnlockSuccess()
            }
        )
    }

    Scaffold(
        containerColor = DecoyPalette.Background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DecoyPalette.Background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).background(DecoyPalette.Accent))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (tabIndex == 0) "НОВОСТИ" else "GUARD",
                        color = DecoyPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.NotificationsNone, null, tint = DecoyPalette.TextSecondary, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(thickness = 2.dp, color = DecoyPalette.Accent)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DecoyPalette.Background)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(color = DecoyPalette.Border)
                Row(modifier = Modifier.fillMaxWidth().background(DecoyPalette.Panel)) {
                    DecoyTabItem(
                        icon = Icons.Outlined.Article, label = "Новости",
                        selected = tabIndex == 0, onClick = { tabIndex = 0 }, modifier = Modifier.weight(1f)
                    )
                    DecoyTabItem(
                        icon = Icons.Outlined.Shield, label = "Guard",
                        selected = tabIndex == 1, onClick = { tabIndex = 1 }, modifier = Modifier.weight(1f)
                    )
                }
                // FOOTER: 5 тапов по версии открывают шторку ввода PIN
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DecoyPalette.Background)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            tapCount++
                            if (tapCount >= 5) {
                                tapCount = 0
                                showUnlockSheet = true
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "GARAGE COMPANION · v0.9.4",
                        color = Color(0xFF3C3C40),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (tabIndex == 0) {
                DecoyNewsScreen()
            } else {
                DecoyTotpScreen()
            }
        }
    }
}

@Composable
private fun DecoyTabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = if (selected) DecoyPalette.Accent else DecoyPalette.TextSecondary
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/** Поднимаемся по цепочке ContextWrapper до Activity (паттерн из Theme.kt). */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
