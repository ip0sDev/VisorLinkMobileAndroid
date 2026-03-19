package by.iposdev.visorlink.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val passwordsMismatch = stringResource(R.string.register_passwords_mismatch)
    LaunchedEffect(uiState.success) { if (uiState.success) onRegisterSuccess() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.filter { c -> c.isLetterOrDigit() || c == '_' }
                    viewModel.clearError()
                },
                label = { Text(stringResource(R.string.register_field_username)) },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
                supportingText = { Text(stringResource(R.string.register_username_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(12.dp))

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
                value = password,
                onValueChange = { password = it; passwordError = null; viewModel.clearError() },
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
                    imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; passwordError = null; viewModel.clearError() },
                label = { Text(stringResource(R.string.register_field_confirm)) },
                leadingIcon = { Icon(Icons.Default.LockOpen, null) },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (password == confirm) viewModel.register(email, password, username)
                    else passwordError = passwordsMismatch
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
                onClick = {
                    if (password != confirm) { passwordError = passwordsMismatch; return@Button }
                    viewModel.register(email, password, username)
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.register_button),
                    style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}