package org.visorlink.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.BuildConfig
import org.visorlink.app.R
import org.visorlink.app.data.auth.GoogleAuthErrors
import org.visorlink.app.data.auth.TfaGate
import org.visorlink.app.data.auth.TfaGateHold
import org.visorlink.app.data.auth.requestGoogleIdToken
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.UiText
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.AuthState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.TfaManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI state ─────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Локализованная ошибка (вход через Google); показывается, если нет [error]. */
    @androidx.annotation.StringRes val errorRes: Int? = null,
    val success: Boolean = false
)

data class VerifyEmailUiState(
    val isPolling: Boolean = false,
    val resendCooldown: Int = 0,       // seconds remaining on resend cooldown
    val resendLoading: Boolean = false,
    val error: String? = null,
    val verified: Boolean = false
)

/** Шаг «Завершите регистрацию» после входа через Google. */
data class GoogleSignupUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val tfaManager: TfaManager,
    private val fcmManager: org.visorlink.app.utils.FcmManager,
    private val tfaHold: TfaGateHold = TfaGateHold(),
) : ViewModel() {

    private val initialAuthState = authRepository.getCurrentAuthState()

    // Three-state auth stream — consumed by the root nav guard
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialAuthState)

    // Reactive user profile that updates when auth state changes
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentUserProfile: Flow<UserProfile?> = authRepository.authState.flatMapLatest { state ->
        val uid = when (state) {
            is AuthState.Verified -> state.user.uid
            is AuthState.Unverified -> state.user.uid
            else -> null
        }
        if (uid != null) userRepository.userProfileFlow(uid) else flowOf(null)
    }

    // ── Профиль: есть / нет / ещё неизвестно ──────────────────────────────────

    private val verifiedUid: Flow<String?> = authState
        .map { (it as? AuthState.Verified)?.user?.uid }
        .distinctUntilChanged()

    /**
     * Есть ли `users/{uid}` — только по ответу сервера (`null` — ответа ещё нет). Аккаунт без
     * профиля — незавершённая регистрация через Google.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileExists: StateFlow<Boolean?> = verifiedUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else userRepository.profileExistsFlow(uid).map<Boolean, Boolean?> { it }.onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Вошёл через Google, а профиля нет: регистрацию нужно завершить (ник + инвайт), и никуда,
     * кроме этого шага, не пускаем. Пароль среди провайдеров — обычный аккаунт, его это не касается
     * (веб — `needsGoogleSignup` в App.jsx).
     */
    val needsGoogleSignup: StateFlow<Boolean> = profileExists
        .map { exists -> exists == false && authRepository.currentUser != null && !authRepository.hasPassword }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── 2FA ───────────────────────────────────────────────────────────────────

    // ── Watchdog: профиль не пришёл за 3,5 с после входа (офлайн без кэша) — интерфейс не блокируем ──
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _watchdogExpired: Flow<Boolean> = verifiedUid.flatMapLatest { uid ->
        if (uid == null) flowOf(false) else flow { emit(false); delay(3500); emit(true) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tfaEnabled: Flow<Boolean?> = currentUserProfile.map { it?.tfaEnabled }.distinctUntilChanged()

    /**
     * Ворота 2FA. Зависят только от сессии, флага 2FA в профиле и локальных событий — не от
     * каждого снимка профиля, иначе экран перескакивал бы при любом его изменении.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tfaGate: StateFlow<TfaGate> = combine(authState, tfaEnabled, profileExists, _watchdogExpired, tfaHold.refreshTick) { state, tfa, exists, watchdog, tick ->
        // Профиль пришёл — сторожевой таймер и «профиля нет» больше ни на что не влияют:
        // иначе их смена через 3,5 с перезапускала бы уже идущую проверку сессии
        val giveUp = tfa == null && (exists == false || watchdog)
        GateInput(state as? AuthState.Verified, tfa, giveUp, tick)
    }
        .distinctUntilChanged()
        .flatMapLatest { input -> gateFlow(input) }
        .combine(tfaHold.held) { gate, held -> if (held) TfaGate.Required() else gate }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TfaGate.Off)

    private data class GateInput(
        val verified: AuthState.Verified?,
        val tfaEnabled: Boolean?,
        /** Профиля нет (по серверу) или он не пришёл за время сторожевого таймера — не ждём. */
        val profileGiveUp: Boolean,
        val refreshTick: Int,
    )

    private fun gateFlow(input: GateInput): Flow<TfaGate> {
        val user = input.verified?.user ?: return flowOf(TfaGate.Off)
        // Профиль ещё не пришёл: ждём, но не дольше сторожевого таймера
        if (input.tfaEnabled == null) {
            return flowOf(if (input.profileGiveUp) TfaGate.Off else TfaGate.Checking)
        }
        if (!input.tfaEnabled) return flowOf(TfaGate.Off)
        return flow {
            val authTime = runCatching { authRepository.getAuthTime() }.getOrNull()
            if (authTime == null) { emit(TfaGate.Required(slow = true)); return@flow }
            // Уже подтверждали эту сессию на устройстве — не ждём сервер
            if (tfaManager.isTfaPassed(authTime)) { emit(TfaGate.Passed); return@flow }
            emit(TfaGate.Checking)
            emitAll(serverGate(user.uid, authTime))
        }
    }

    /** Ответ белого списка; молчание дольше [TfaGate.SLOW_CHECK_MS] — экран кода с пометкой, подписка живёт дальше. */
    private fun serverGate(uid: String, authTime: String): Flow<TfaGate> = channelFlow {
        val slow = launch {
            delay(TfaGate.SLOW_CHECK_MS)
            send(TfaGate.Required(slow = true))
        }
        try {
            authRepository.sessionAuthorizedFlow(uid, authTime).collect { authorized ->
                slow.cancel()
                if (authorized) tfaManager.setTfaPassed(authTime)
                send(if (authorized) TfaGate.Passed else TfaGate.Required())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ошибка чтения — экран кода с пометкой о связи, а не молчаливая блокировка
            slow.cancel()
            if (BuildConfig.DEBUG) android.util.Log.w("AuthViewModel", "2FA session check failed: ${e.message}")
            send(TfaGate.Required(slow = true))
        }
    }

    val isTfaRequired: StateFlow<Boolean> = tfaGate
        .map { it is TfaGate.Required }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Сессия готова: вход подтверждён, профиль есть (или не пришёл за время сторожевого таймера),
     * 2FA пройдена или не нужна.
     */
    val isSessionReady: StateFlow<Boolean> = combine(authState, tfaGate, needsGoogleSignup) { state, gate, signup ->
        state is AuthState.Verified && !signup && (gate == TfaGate.Off || gate == TfaGate.Passed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Включили 2FA в настройках: эта сессия уже авторизована сервером. */
    fun onTfaEnabledHere() {
        viewModelScope.launch {
            authRepository.getAuthTime()?.let { tfaManager.setTfaPassed(it) }
            tfaHold.refresh()
        }
    }

    /**
     * Вызывается когда сессия верифицирована и 2FA пройдена (или не требуется).
     * Настраивает FCM токен строго после 2FA.
     */
    fun onSessionReadyAfter2FA() {
        viewModelScope.launch {
            if (isSessionReady.value) {
                fcmManager.syncTokenAfter2FA()
            } else {
                android.util.Log.w("AuthViewModel", "Cannot sync FCM: session is NOT ready yet (2FA in progress or profile loading)")
            }
        }
    }

    // ── Google ────────────────────────────────────────────────────────────────

    private val _googleSignup = MutableStateFlow(GoogleSignupUiState())
    val googleSignup: StateFlow<GoogleSignupUiState> = _googleSignup.asStateFlow()

    /** Почта, под которой вошли через Google (шаг «Завершите регистрацию»). */
    val currentEmail: String? get() = authRepository.currentUser?.email

    /**
     * «Войти через Google» / «Зарегистрироваться через Google». Если ник и код уже введены на
     * экране регистрации и аккаунт новый — профиль создаётся сразу, без второго шага.
     * [context] — Activity для окна выбора аккаунта.
     */
    fun signInWithGoogle(context: android.content.Context, username: String? = null, inviteCode: String? = null) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, errorRes = null) }
            try {
                val idToken = requestGoogleIdToken(context)
                // Как при входе по паролю: старый FCM-токен отзываем до входа. Ограничено по
                // времени — офлайн-запись в Firestore не должна вешать вход
                kotlinx.coroutines.withTimeoutOrNull(REVOKE_TIMEOUT_MS) { fcmManager.revokeToken() }
                val isNew = authRepository.signInWithGoogle(idToken)
                val name = username?.trim().orEmpty()
                val code = (inviteCode?.trim()?.ifBlank { null } ?: _pendingInviteCode.value?.trim()).orEmpty()
                if (isNew && name.length >= 3 && code.isNotBlank()) {
                    runCatching { authRepository.completeGoogleProfile(name, code) }
                        .onFailure { e -> _googleSignup.update { it.copy(error = signupError(e)) } }
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.w("AuthViewModel", "Google sign-in failed", e)
                _uiState.update { it.copy(isLoading = false, errorRes = GoogleAuthErrors.messageRes(e)) }
            } finally {
                // Отмена, сбой, что угодно — кнопка не должна остаться в «загрузке»
                if (_uiState.value.isLoading) _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** «Создать аккаунт» на шаге завершения регистрации. Ошибка оставляет на шаге — Google-аккаунт не удаляется. */
    fun completeGoogleSignup(username: String, inviteCode: String) {
        val name = username.trim()
        val code = inviteCode.trim()
        if (name.length < 3 || !USERNAME_RE.matches(name)) {
            _googleSignup.update { it.copy(error = UiText.StringResource(R.string.auth_google_bad_username)) }
            return
        }
        if (code.isBlank()) {
            _googleSignup.update { it.copy(error = UiText.StringResource(R.string.auth_google_need_invite)) }
            return
        }
        viewModelScope.launch {
            _googleSignup.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.completeGoogleProfile(name, code) }
                .onSuccess { _googleSignup.update { GoogleSignupUiState() } }
                .onFailure { e -> _googleSignup.update { it.copy(isLoading = false, error = signupError(e)) } }
        }
    }

    /** «Отменить регистрацию»: удалить Google-аккаунт без профиля (при ошибке — выйти). */
    fun cancelGoogleSignup() {
        viewModelScope.launch {
            _googleSignup.update { it.copy(isLoading = true, error = null) }
            runCatching { fcmManager.revokeToken() }
            authRepository.cancelGoogleSignup()
            _googleSignup.value = GoogleSignupUiState()
        }
    }

    fun clearGoogleSignupError() {
        _googleSignup.update { it.copy(error = null) }
    }

    private fun signupError(e: Throwable): UiText {
        val fe = e as? com.google.firebase.functions.FirebaseFunctionsException
        return when (fe?.code) {
            com.google.firebase.functions.FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                UiText.StringResource(R.string.auth_google_username_taken)
            com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                UiText.StringResource(R.string.auth_google_bad_username)
            com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE,
            com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                UiText.StringResource(R.string.auth_google_network)
            // Сервер объясняет сам: «Недействительный код приглашения» и т. п.
            else -> fe?.message?.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) }
                ?: UiText.StringResource(R.string.auth_google_failed)
        }
    }

    // ── Auth (login / register) ───────────────────────────────────────────────

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    fun setPendingInviteCode(code: String?) {
        _pendingInviteCode.value = code
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields", isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, errorRes = null) }
            // Перед логином убеждаемся, что старый FCM токен отозван,
            // чтобы 2FA код не пришёл в пуше на ещё не аутентифицированное устройство
            kotlinx.coroutines.withTimeoutOrNull(REVOKE_TIMEOUT_MS) { fcmManager.revokeToken() }
            runCatching { authRepository.login(email.trim(), password) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, success = true) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = friendlyMessage(error)) } }
        }
    }

    /**
     * On success → success = true signals UI to navigate to VerifyEmailScreen.
     * The Auth account exists but email_verified == false; main app is still gated.
     */
    fun register(email: String, password: String, username: String, inviteCode: String? = null) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields", isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, errorRes = null) }
            val codeToUse = inviteCode?.trim()?.ifBlank { null } ?: _pendingInviteCode.value?.trim()?.ifBlank { null }
            runCatching { authRepository.register(email.trim(), password, username.trim(), codeToUse) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, success = true) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = friendlyMessage(error)) } }
        }
    }

    suspend fun checkRegistrationCode(code: String): Boolean {
        return runCatching { authRepository.checkRegistrationCode(code) }.getOrDefault(false)
    }

    fun requestAccess(
        email: String,
        username: String,
        note: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (email.isBlank() || username.isBlank()) {
            onResult(false, "Пожалуйста, заполните email и имя пользователя")
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.requestAccess(email, username, note) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, friendlyMessage(it)) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorRes = null) }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Укажите email для сброса пароля")
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.sendPasswordResetEmail(email) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, friendlyMessage(it)) }
        }
    }

    // ── Email verification screen ─────────────────────────────────────────────

    private val _verifyState = MutableStateFlow(VerifyEmailUiState())
    val verifyState: StateFlow<VerifyEmailUiState> = _verifyState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    /** Start 5-second polling loop per guideline §5.1. Call from VerifyEmailScreen. */
    fun startVerificationPolling() {
        if (pollingJob?.isActive == true) return
        _verifyState.update { it.copy(isPolling = true) }
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                runCatching { authRepository.reloadAndCheckVerified() }
                    .onSuccess { verified ->
                        if (verified) {
                            _verifyState.update {
                                it.copy(
                                    isPolling = false,
                                    verified = true
                                )
                            }
                            return@launch
                        }
                    }
                    .onFailure { /* silent — keep polling */ }
            }
        }
    }

    fun stopVerificationPolling() {
        pollingJob?.cancel()
        _verifyState.update { it.copy(isPolling = false) }
    }

    /**
     * Resend verification email with a 60-second UX cooldown per guideline §5.2.
     * Firebase enforces its own server-side rate limit; we catch too-many-requests.
     */
    fun resendVerificationEmail() {
        if (_verifyState.value.resendCooldown > 0) return
        viewModelScope.launch {
            _verifyState.update { it.copy(resendLoading = true, error = null) }
            runCatching { authRepository.resendVerificationEmail() }
                .onSuccess { startResendCooldown() }
                .onFailure { e ->
                    val msg = friendlyMessage(e)
                    _verifyState.update {
                        it.copy(
                            resendLoading = false,
                            error = msg
                        )
                    }
                }
        }
    }

    private fun startResendCooldown(seconds: Int = 60) {
        _verifyState.update {
            it.copy(
                resendLoading = false,
                resendCooldown = seconds
            )
        }
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in (seconds - 1) downTo 0) {
                delay(1_000)
                _verifyState.update { it.copy(resendCooldown = remaining) }
            }
        }
    }

    // ── Delete Account (Google Play Compliance) ──────────────────────────────

    fun deleteAccount(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, errorRes = null) }
            runCatching {
                fcmManager.revokeToken()
                authRepository.deleteAccount(password)
            }.onSuccess {
                stopVerificationPolling()
                tfaManager.clearCache()
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false) }
                onError(friendlyMessage(error))
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        stopVerificationPolling()
        tfaManager.clearCache()
        tfaHold.release()
        viewModelScope.launch {
            // FCM токены автоматически отзываются при сбросе приложения / логауте
            fcmManager.revokeToken()
            authRepository.logout()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun friendlyMessage(t: Throwable): String = t.message ?: "Something went wrong"

    companion object {
        private val USERNAME_RE = Regex("^[a-zA-Z0-9_]+$")
        private const val REVOKE_TIMEOUT_MS = 3_000L

        /**
         * Ник по умолчанию для регистрации через Google: часть почты до `@`, недопустимые символы
         * заменены на `_`, не длиннее 24 символов.
         */
        fun defaultUsername(email: String?): String =
            email.orEmpty().substringBefore('@').replace(Regex("[^a-zA-Z0-9_]"), "_").take(24)
    }
}