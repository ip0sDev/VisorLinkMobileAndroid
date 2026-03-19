package by.iposdev.visorlink.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success) { if (uiState.success) onLoginSuccess() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_logo),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.login_field_email)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.login_field_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.login(email, password)
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.login(email, password) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.login_button),
                    style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text(stringResource(R.string.login_no_account))
                Text(stringResource(R.string.login_register_link),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}