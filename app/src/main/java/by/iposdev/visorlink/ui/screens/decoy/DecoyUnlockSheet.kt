package by.iposdev.visorlink.ui.screens.decoy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.utils.StealthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyUnlockSheet(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val stealthManager = remember { StealthManager(context) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DecoyPalette.Panel,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(40.dp).height(4.dp).background(DecoyPalette.Border))
            Spacer(Modifier.height(20.dp))

            Text("СЛУЖЕБНЫЙ ДОСТУП", color = DecoyPalette.TextPrimary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
            Text("Введите код доступа", color = DecoyPalette.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(20.dp))

            TextField(
                value = pin,
                onValueChange = { if (it.length <= 8) { pin = it; error = null } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    color = DecoyPalette.TextPrimary,
                    fontSize = 22.sp,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                ),
                isError = error != null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DecoyPalette.PanelAlt,
                    unfocusedContainerColor = DecoyPalette.PanelAlt,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().border(1.dp, if (error != null) DecoyPalette.Danger else Color.Transparent, RoundedCornerShape(4.dp))
            )

            if (error != null) {
                Text(error!!, color = DecoyPalette.Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (stealthManager.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = "Неверный код"
                        pin = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DecoyPalette.Accent,
                    contentColor = Color.Black
                )
            ) {
                Text("ПОДТВЕРДИТЬ", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}