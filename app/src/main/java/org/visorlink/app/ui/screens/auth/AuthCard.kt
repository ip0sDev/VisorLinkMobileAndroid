package org.visorlink.app.ui.screens.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlBrandText
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlSignalGlow

private const val MIN_PASSWORD_LENGTH = 6

/**
 * Премиальный компонент авторизации в дизайн-системе Biolume:
 * - Реальная иконка приложения в светящемся контейнере
 * - Переключатель табов «Вход» / «Регистрация» (VlSegmentedControl)
 * - Стилизованные поля VlTextField и кнопка VlButton
 * - Диалог сброса пароля («Забыли пароль?»)
 */
@Composable
fun AuthCard(
    viewModel: AuthViewModel,
    initialTab: Int = 0,
    onLoginSuccess: () -> Unit = {},
    onRegistrationComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0: Login, 1: Register

    // Поля входа
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // Поля регистрации
    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regConfirmVisible by remember { mutableStateOf(false) }

    // Локальные ошибки валидации регистрации
    var regPasswordError by remember { mutableStateOf<String?>(null) }

    // Диалог восстановления пароля
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }

    val passwordFocusRequester = remember { FocusRequester() }
    val regEmailFocus = remember { FocusRequester() }
    val regPasswordFocus = remember { FocusRequester() }
    val regConfirmFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            if (selectedTab == 0) {
                onLoginSuccess()
            } else {
                onRegistrationComplete()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // ── ЛОГОТИП С ЭФФЕКТОМ СВЕЧЕНИЯ ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            cs.primary.copy(alpha = 0.70f),
                            cs.primary.copy(alpha = 0.20f)
                        )
                    ),
                    shape = CircleShape
                )
                .vlSignalGlow(
                    tokens = tokens.signal,
                    color = cs.primary,
                    shape = CircleShape,
                    active = true
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── ЗАГОЛОВОК И БРЕНДИНГ ────────────────────────────────────────────────
        VlBrandText(
            text = stringResource(R.string.app_name),
            fontSize = 28.sp,
            color = cs.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (selectedTab == 0) "Войдите в свою учетную запись" else "Создайте новый профиль",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // ── ПЕРЕКЛЮЧАТЕЛЬ ТАБОВ (ПЛАВНЫЙ СТАДИУМ-ПИЛЛ) ───────────────────────────
        VlSegmentedControl(
            labels = listOf("Вход", "Регистрация"),
            selectedIndex = selectedTab,
            onSelected = {
                selectedTab = it
                viewModel.clearError()
                regPasswordError = null
            }
        )

        Spacer(Modifier.height(24.dp))

        // ── ФОРМА БЕЗ ГРОМОЗДКИХ ВНЕШНИХ РАМОК ──────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Вывод общей ошибки (если есть)
            if (uiState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.error.copy(alpha = 0.12f))
                        .border(1.dp, cs.error.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                        Text(
                            text = uiState.error!!,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = cs.error,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "auth_form_switch"
                ) { tab ->
                    if (tab == 0) {
                        // ── ФОРМА ВХОДА ─────────────────────────────────────────
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            VlTextField(
                                value = loginEmail,
                                onValueChange = { loginEmail = it; viewModel.clearError() },
                                label = stringResource(R.string.login_field_email),
                                placeholder = "name@example.com",
                                leading = {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    autoCorrectEnabled = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { passwordFocusRequester.requestFocus() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            VlTextField(
                                value = loginPassword,
                                onValueChange = { loginPassword = it; viewModel.clearError() },
                                label = stringResource(R.string.login_field_password),
                                placeholder = "••••••••",
                                leading = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                trailing = {
                                    IconButton(
                                        onClick = { loginPasswordVisible = !loginPasswordVisible }
                                    ) {
                                        Icon(
                                            imageVector = if (loginPasswordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = cs.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (loginPasswordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        viewModel.login(loginEmail, loginPassword)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(passwordFocusRequester)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        forgotEmail = loginEmail.trim()
                                        showForgotPasswordDialog = true
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "Забыли пароль?",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.primary
                                        )
                                    )
                                }
                            }

                            VlButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.login(loginEmail, loginPassword)
                                },
                                enabled = !uiState.isLoading && loginEmail.isNotBlank() && loginPassword.isNotBlank()
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = cs.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(R.string.login_button),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // ── ФОРМА РЕГИСТРАЦИИ ───────────────────────────────────
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            VlTextField(
                                value = regUsername,
                                onValueChange = {
                                    regUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' }
                                    viewModel.clearError()
                                    regPasswordError = null
                                },
                                label = "Имя пользователя",
                                placeholder = "username",
                                leading = {
                                    Icon(
                                        Icons.Default.AlternateEmail,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                supportingText = if (regUsername.isNotEmpty() && regUsername.length < 3) "Минимум 3 символа" else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next,
                                    autoCorrectEnabled = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { regEmailFocus.requestFocus() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            VlTextField(
                                value = regEmail,
                                onValueChange = {
                                    regEmail = it
                                    viewModel.clearError()
                                    regPasswordError = null
                                },
                                label = stringResource(R.string.login_field_email),
                                placeholder = "name@example.com",
                                leading = {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    autoCorrectEnabled = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { regPasswordFocus.requestFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(regEmailFocus)
                            )

                            VlTextField(
                                value = regPassword,
                                onValueChange = {
                                    regPassword = it
                                    viewModel.clearError()
                                    regPasswordError = null
                                },
                                label = stringResource(R.string.login_field_password),
                                placeholder = "Минимум $MIN_PASSWORD_LENGTH символов",
                                leading = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                trailing = {
                                    IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                        Icon(
                                            imageVector = if (regPasswordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = cs.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (regPasswordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { regConfirmFocus.requestFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(regPasswordFocus)
                            )

                            VlTextField(
                                value = regConfirmPassword,
                                onValueChange = {
                                    regConfirmPassword = it
                                    viewModel.clearError()
                                    regPasswordError = null
                                },
                                label = "Повторите пароль",
                                placeholder = "••••••••",
                                leading = {
                                    Icon(
                                        Icons.Default.LockReset,
                                        contentDescription = null,
                                        tint = cs.primary
                                    )
                                },
                                trailing = {
                                    IconButton(onClick = { regConfirmVisible = !regConfirmVisible }) {
                                        Icon(
                                            imageVector = if (regConfirmVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = cs.onSurfaceVariant
                                        )
                                    }
                                },
                                isError = regPasswordError != null,
                                supportingText = regPasswordError,
                                visualTransformation = if (regConfirmVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (validateAndRegister(viewModel, regEmail, regPassword, regConfirmPassword, regUsername) { regPasswordError = it }) {
                                            regPasswordError = null
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(regConfirmFocus)
                            )

                            VlButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (validateAndRegister(viewModel, regEmail, regPassword, regConfirmPassword, regUsername) { regPasswordError = it }) {
                                        regPasswordError = null
                                    }
                                },
                                enabled = !uiState.isLoading && regUsername.length >= 3 && regEmail.isNotBlank() && regPassword.isNotBlank()
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = cs.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = "Создать аккаунт",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

        Spacer(Modifier.height(24.dp))
    }

    // ── ДИАЛОГ СБРОСА ПАРОЛЯ ────────────────────────────────────────────────────
    if (showForgotPasswordDialog) {
        VlAlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Сброс пароля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Введите адрес электронной почты, привязанный к вашему аккаунту. Мы отправим ссылку для восстановления пароля.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    VlTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = "Email",
                        placeholder = "name@example.com",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            actions = {
                VlDialogButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Отмена")
                }
                VlDialogButton(
                    isPrimary = true,
                    onClick = {
                        isSendingReset = true
                        viewModel.sendPasswordReset(forgotEmail) { success, error ->
                            isSendingReset = false
                            if (success) {
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Письмо со ссылкой отправлено на почту", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, error ?: "Ошибка сброса пароля", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = cs.onPrimary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Отправить")
                }
            }
        )
    }
}

private fun validateAndRegister(
    viewModel: AuthViewModel,
    email: String,
    pass: String,
    confirm: String,
    username: String,
    onError: (String) -> Unit
): Boolean {
    if (username.trim().length < 3) {
        onError("Имя пользователя должно содержать минимум 3 символа")
        return false
    }
    if (pass.length < MIN_PASSWORD_LENGTH) {
        onError("Пароль должен содержать не менее $MIN_PASSWORD_LENGTH символов")
        return false
    }
    if (pass != confirm) {
        onError("Пароли не совпадают")
        return false
    }
    viewModel.register(email.trim(), pass, username.trim())
    return true
}
