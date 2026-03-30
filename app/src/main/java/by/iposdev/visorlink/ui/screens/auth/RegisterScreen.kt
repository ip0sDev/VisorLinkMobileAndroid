package by.iposdev.visorlink.ui.screens.auth

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import org.koin.compose.viewmodel.koinViewModel

private const val MIN_PASSWORD_LENGTH = 6   // Firebase minimum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    // Called after successful registration → the caller routes to VerifyEmailScreen
    onRegistrationComplete: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val emailFocus    = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus  = remember { FocusRequester() }

    var username  by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var confirm   by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Local validation errors (shown inline in fields before the server call)
    var passwordLengthError by remember { mutableStateOf(false) }
    var passwordMismatchError by remember { mutableStateOf(false) }

    val passwordsMismatch = stringResource(R.string.register_passwords_mismatch)
    val passwordTooShort  = stringResource(R.string.register_password_too_short, MIN_PASSWORD_LENGTH)

    // Navigate to VerifyEmailScreen on success (not to the main app — email unverified)
    LaunchedEffect(uiState.success) {
        if (uiState.success) onRegistrationComplete()
    }

    fun submit() {
        focusManager.clearFocus()
        passwordLengthError   = password.length < MIN_PASSWORD_LENGTH
        passwordMismatchError = password != confirm
        if (passwordLengthError || passwordMismatchError) return
        viewModel.register(email.trim(), password, username.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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

            // ── Username ──────────────────────────────────────────────────────
            // Guideline §3.2: 3–32 chars, [a-zA-Z0-9_]+ only.
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.filter { c -> c.isLetterOrDigit() || c == '_' }
                    viewModel.clearError()
                },
                label = { Text(stringResource(R.string.register_field_username)) },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                // MD3: prefix shows "@" inline without overlapping the label
                prefix = { Text("@") },
                supportingText = { Text(stringResource(R.string.register_username_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            // ── Email ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.login_field_email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                // Show server-side email error (e.g. already-in-use) on this field
                isError = uiState.error?.contains("email", ignoreCase = true) == true,
                supportingText = if (uiState.error?.contains("email", ignoreCase = true) == true)
                    ({ Text(uiState.error!!) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            // ── Password ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordLengthError   = false
                    passwordMismatchError = false
                    viewModel.clearError()
                },
                label = { Text(stringResource(R.string.login_field_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                // Inline validation: password too short
                isError = passwordLengthError,
                supportingText = if (passwordLengthError) ({ Text(passwordTooShort) }) else null,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            // ── Confirm password ──────────────────────────────────────────────
            OutlinedTextField(
                value = confirm,
                onValueChange = {
                    confirm = it
                    passwordMismatchError = false
                    viewModel.clearError()
                },
                label = { Text(stringResource(R.string.register_field_confirm)) },
                leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                // Inline validation: passwords don't match
                isError = passwordMismatchError,
                supportingText = if (passwordMismatchError) ({ Text(passwordsMismatch) }) else null,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(confirmFocus),
                shape = MaterialTheme.shapes.medium
            )

            // ── Generic server error (username taken, network, etc.) ───────────
            // Only shown when the error is NOT an email error (already handled above).
            val genericError = uiState.error?.takeIf {
                !it.contains("email", ignoreCase = true)
            }
            if (genericError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = genericError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Register button ───────────────────────────────────────────────
            Button(
                onClick = { submit() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.register_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}