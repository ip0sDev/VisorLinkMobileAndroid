package org.visorlink.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.visorlink.app.ui.theme.VisorLinkTheme
import org.visorlink.app.R
import org.visorlink.app.BuildConfig
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emailState = rememberTextFieldState(initialText = "example@gmail.com")
    val passwordState = rememberTextFieldState(initialText = "password")
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
        Spacer(modifier = Modifier.height(96.dp))
        Box(
            modifier = Modifier
                .size(256.dp)
                .clip(CircleShape)
                .align(Alignment.CenterHorizontally),
            ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(180.dp)
                    .align(Alignment.Center)
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(256.dp)
                    .align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Войдите в аккаунт",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(64.dp))

        val emailState = rememberTextFieldState()
        val passwordState = rememberTextFieldState()
        OutlinedTextField(
            state = emailState,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("Email") },
            placeholder = { Text("user@example.com") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedSecureTextField(
            state = passwordState,
            label = { Text("Пароль") },
            textObfuscationMode = TextObfuscationMode.RevealLastTyped,
            placeholder = { Text("пароль") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.errorText.isNotBlank()) {
            Text(
                text = uiState.errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                viewModel.signIn(
                    email = emailState.text.toString(),
                    password = passwordState.text.toString()
                )
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                uiState.isSuccess -> {
                    Text("Успешно")
                }
                else -> {
                    Text("Войти")
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        var VersionCode = BuildConfig.VERSION_CODE
        Text(
            text = "Версия: $VersionCode",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.secondary
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