package org.visorlink.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VisorLinkTheme
import org.visorlink.app.R
import org.visorlink.app.BuildConfig

@Composable
fun AuthScreen(
    onLoginClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 16.dp)
    ) {
        Text(
            text = "VisorLink",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(256.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(128.dp))

        val state = rememberTextFieldState(initialText = "example@gmail.com")
        OutlinedTextField(
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("Email") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val statePassword = rememberTextFieldState(initialText = "password")
        OutlinedTextField(
            state = statePassword,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("Пароль") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLoginClick, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Войти")
        }
        Spacer(modifier = Modifier.height(128.dp))
        var VersionCode = BuildConfig.VERSION_CODE
        Text(
            text = "Версия: $VersionCode",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    VisorLinkTheme {
        AuthScreen()
    }
}