package by.iposdev.visorlink.ui.screens.decoy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.TotpService
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.delay

@Composable
fun DecoyTotpScreen() {
    var code by remember { mutableStateOf("------") }
    var remaining by remember { mutableIntStateOf(30) }
    val context = LocalContext.current
    val haptic = rememberHaptic()

    LaunchedEffect(Unit) {
        while (true) {
            code = TotpService.currentCode()
            remaining = TotpService.secondsRemaining()
            delay(1000)
        }
    }

    val groupedCode = if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code
    val progress = remaining / TotpService.PERIOD.toFloat()

    Column(
        modifier = Modifier.fillMaxSize().background(DecoyPalette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(DecoyPalette.PanelAlt)
                .border(1.dp, DecoyPalette.AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = DecoyPalette.Accent, modifier = Modifier.size(30.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "КОД ПОДТВЕРЖДЕНИЯ ВХОДА",
            color = DecoyPalette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .background(DecoyPalette.Panel)
                .border(1.dp, DecoyPalette.Border)
                .clickable {
                    haptic.perform(HapticType.CLICK, true)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("totp", code))
                }
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            Text(
                groupedCode,
                color = DecoyPalette.TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
        }

        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = if (progress < 0.2f) DecoyPalette.Danger else DecoyPalette.Accent,
                trackColor = DecoyPalette.Border,
                strokeWidth = 3.dp
            )
            Text(remaining.toString(), color = DecoyPalette.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}