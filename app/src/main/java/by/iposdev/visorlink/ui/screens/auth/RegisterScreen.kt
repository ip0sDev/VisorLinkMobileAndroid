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
import by.iposdev.visorlink.ui.components.VlTextField
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

    val pendingCode by viewModel.pendingInviteCode.collectAsState()
    var inviteCode by remember { mutableStateOf(pendingCode ?: "") }
    LaunchedEffect(pendingCode) {
        if (!pendingCode.isNullOrBlank() && inviteCode.isBlank()) {
            inviteCode = pendingCode!!
        }
    }

    var showRequestAccessDialog by remember { mutableStateOf(false) }
    var requestEmail by remember { mutableStateOf("") }
    var requestUsername by remember { mutableStateOf("") }
    var requestNote by remember { mutableStateOf("") }
    var isSubmittingRequest by remember { mutableStateOf(false) }
    var requestSuccessMessage by remember { mutableStateOf<String?>(null) }
    var requestErrorMessage by remember { mutableStateOf<String?>(null) }

    val inviteFocus   = remember { FocusRequester() }
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
        viewModel.register(email.trim(), password, username.trim(), inviteCode.trim().ifBlank { null })
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
            VlTextField(
                value = username,
                onValueChange = {
                    username = it.filter { c -> c.isLetterOrDigit() || c == '_' }
                    viewModel.clearError()
                },
                label = stringResource(R.string.register_field_username),
                placeholder = stringResource(R.string.register_field_username),
                leading = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                trailing = { Text("@", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                supportingText = stringResource(R.string.register_username_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { inviteFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ── Invite code (Optional / Required per server rules) ───────────
            VlTextField(
                value = inviteCode,
                onValueChange = {
                    inviteCode = it.trim()
                    viewModel.clearError()
                },
                label = stringResource(R.string.register_invite_code),
                placeholder = stringResource(R.string.register_invite_code_hint),
                leading = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inviteFocus)
            )

            Spacer(Modifier.height(12.dp))

            // ── Email ─────────────────────────────────────────────────────────
            VlTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = stringResource(R.string.login_field_email),
                leading = { Icon(Icons.Default.Email, contentDescription = null) },
                // Show server-side email error (e.g. already-in-use) on this field
                isError = uiState.error?.contains("email", ignoreCase = true) == true,
                supportingText = if (uiState.error?.contains("email", ignoreCase = true) == true)
                    uiState.error else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
            )

            Spacer(Modifier.height(12.dp))

            // ── Password ──────────────────────────────────────────────────────
            VlTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordLengthError   = false
                    passwordMismatchError = false
                    viewModel.clearError()
                },
                label = stringResource(R.string.login_field_password),
                leading = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailing = {
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
                supportingText = if (passwordLengthError) passwordTooShort else null,
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
                    .focusRequester(passwordFocus)
            )

            Spacer(Modifier.height(12.dp))

            // ── Confirm password ──────────────────────────────────────────────
            VlTextField(
                value = confirm,
                onValueChange = {
                    confirm = it
                    passwordMismatchError = false
                    viewModel.clearError()
                },
                label = stringResource(R.string.register_field_confirm),
                leading = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                // Inline validation: passwords don't match
                isError = passwordMismatchError,
                supportingText = if (passwordMismatchError) passwordsMismatch else null,
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
                    .focusRequester(confirmFocus)
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

            Spacer(Modifier.height(16.dp))

            // ── Request access button ─────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    requestEmail = email.trim()
                    requestUsername = username.trim()
                    requestSuccessMessage = null
                    requestErrorMessage = null
                    showRequestAccessDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(R.string.register_request_access_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showRequestAccessDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmittingRequest) {
                        showRequestAccessDialog = false
                    }
                },
                title = { Text(stringResource(R.string.register_request_access_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.register_request_access_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        VlTextField(
                            value = requestEmail,
                            onValueChange = { requestEmail = it; requestErrorMessage = null },
                            label = stringResource(R.string.login_field_email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        VlTextField(
                            value = requestUsername,
                            onValueChange = { requestUsername = it; requestErrorMessage = null },
                            label = stringResource(R.string.register_field_username),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        VlTextField(
                            value = requestNote,
                            onValueChange = { requestNote = it },
                            label = stringResource(R.string.register_request_note_label),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (requestSuccessMessage != null) {
                            Text(
                                text = requestSuccessMessage!!,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (requestErrorMessage != null) {
                            Text(
                                text = requestErrorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isSubmittingRequest = true
                            requestErrorMessage = null
                            viewModel.requestAccess(
                                email = requestEmail,
                                username = requestUsername,
                                note = requestNote
                            ) { success, err ->
                                isSubmittingRequest = false
                                if (success) {
                                    requestSuccessMessage = "Заявка успешно отправлена! Мы свяжемся с вами."
                                } else {
                                    requestErrorMessage = err ?: "Ошибка отправки заявки"
                                }
                            }
                        },
                        enabled = !isSubmittingRequest && requestEmail.isNotBlank() && requestUsername.isNotBlank()
                    ) {
                        if (isSubmittingRequest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.register_request_submit))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRequestAccessDialog = false },
                        enabled = !isSubmittingRequest
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}