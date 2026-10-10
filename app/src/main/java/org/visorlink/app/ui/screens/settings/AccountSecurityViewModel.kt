package org.visorlink.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.data.auth.GoogleAuthErrors
import org.visorlink.app.data.auth.TfaCodeInfo
import org.visorlink.app.data.auth.TfaError
import org.visorlink.app.data.auth.TfaGateHold
import org.visorlink.app.data.auth.TfaMethod
import org.visorlink.app.data.auth.requestGoogleIdToken
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.NoPasswordException
import org.visorlink.app.ui.UiText
import org.visorlink.app.ui.screens.auth.TfaTexts
import org.visorlink.app.utils.TfaManager

/** Способы входа текущего аккаунта (`providerData`). */
data class SignInMethods(
    val email: String = "",
    val hasPassword: Boolean = false,
    /** Почта Google-аккаунта; `null` — Google не привязан. */
    val google: String? = null,
)

enum class TfaManageStep { IDLE, METHOD, CODE }

data class TfaManageState(
    val step: TfaManageStep = TfaManageStep.IDLE,
    /** Что делаем: true — включаем 2FA, false — выключаем. */
    val enabling: Boolean = true,
    val method: TfaMethod = TfaMethod.BOT,
    val info: TfaCodeInfo? = null,
    val sending: Boolean = false,
    val confirming: Boolean = false,
    val error: UiText? = null,
)

/** Что сейчас выполняется в разделе «Способы входа». */
enum class SignInBusy { LINK, UNLINK, PASSWORD, CHANGE_PASSWORD }

/**
 * Настройки → Аккаунт: способы входа (веб `SignInMethodsSection.jsx`), двухфакторная защита и
 * коды в Telegram (`TwoFactorSection.jsx`). Всё, что меняет `auth_time` (привязка Google, пароль,
 * повторный вход), идёт через [AuthRepository.keepSession] — 2FA переносится на новую сессию.
 */
class AccountSecurityViewModel(
    private val auth: AuthRepository,
    private val tfaManager: TfaManager,
    private val tfaHold: TfaGateHold,
) : ViewModel() {

    private val _methods = MutableStateFlow(readMethods())
    val methods: StateFlow<SignInMethods> = _methods.asStateFlow()

    private val _busy = MutableStateFlow<SignInBusy?>(null)
    val busy: StateFlow<SignInBusy?> = _busy.asStateFlow()

    /** Ошибка раздела «Способы входа» (показывается в его диалогах или тостом). */
    private val _signInError = MutableStateFlow<UiText?>(null)
    val signInError: StateFlow<UiText?> = _signInError.asStateFlow()

    /** Сообщение об успехе — экран показывает тост и сбрасывает [consumeNotice]. */
    private val _notice = MutableStateFlow<UiText?>(null)
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    private val _tfa = MutableStateFlow(TfaManageState())
    val tfa: StateFlow<TfaManageState> = _tfa.asStateFlow()

    private val _telegramBusy = MutableStateFlow(false)
    val telegramBusy: StateFlow<Boolean> = _telegramBusy.asStateFlow()

    fun consumeNotice() { _notice.value = null }
    fun clearSignInError() { _signInError.value = null }

    fun refreshMethods() { _methods.value = readMethods() }

    private fun readMethods() = SignInMethods(
        email = auth.currentUser?.email.orEmpty(),
        hasPassword = auth.hasPassword,
        google = auth.googleAccountLabel,
    )

    // ── Способы входа ─────────────────────────────────────────────────────────

    /** «Привязать» Google: выбор аккаунта → `linkWithCredential` → перенос 2FA. */
    fun linkGoogle(context: Context) = runSignIn(SignInBusy.LINK, R.string.signin_google_linked) {
        auth.linkGoogle(requestGoogleIdToken(context))
    }

    /** «Отвязать» Google (только при наличии пароля). */
    fun unlinkGoogle(onDone: () -> Unit) = runSignIn(SignInBusy.UNLINK, R.string.signin_google_unlinked, onDone) {
        auth.unlinkGoogle()
    }

    /** «Задать пароль» аккаунту из Google; свежий вход — повторным выбором Google-аккаунта. */
    fun setPassword(context: Context, password: String, confirm: String, onDone: () -> Unit) {
        if (password.length < MIN_PASSWORD) { _signInError.value = UiText.StringResource(R.string.signin_weak_password); return }
        if (password != confirm) { _signInError.value = UiText.StringResource(R.string.signin_password_mismatch); return }
        runSignIn(SignInBusy.PASSWORD, R.string.signin_password_set, onDone) {
            auth.setPassword(password) { requestGoogleIdToken(context) }
        }
    }

    /** Смена пароля: повторный вход + `updatePassword` в [AuthRepository.keepSession]. */
    fun changePassword(current: String, newPassword: String, onDone: () -> Unit) =
        runSignIn(SignInBusy.CHANGE_PASSWORD, R.string.dialog_password_success, onDone) {
            auth.changePassword(current, newPassword)
        }

    private fun runSignIn(kind: SignInBusy, okRes: Int, onDone: () -> Unit = {}, action: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = kind
        _signInError.value = null
        viewModelScope.launch {
            try {
                action()
                refreshMethods()
                _notice.value = UiText.StringResource(okRes)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshMethods()
                _signInError.value = signInErrorText(e, kind)
            } finally {
                _busy.value = null
            }
        }
    }

    private fun signInErrorText(e: Exception, kind: SignInBusy): UiText? = when {
        e is NoPasswordException -> UiText.StringResource(R.string.signin_need_password)
        e is FirebaseAuthWeakPasswordException -> UiText.StringResource(R.string.signin_weak_password)
        kind == SignInBusy.CHANGE_PASSWORD -> e.message?.let { UiText.DynamicString(it) }
            ?: UiText.StringResource(R.string.auth_google_failed)
        else -> GoogleAuthErrors.messageRes(e)?.let { UiText.StringResource(it) }
    }

    // ── Двухфакторная защита ──────────────────────────────────────────────────

    /** «Включить» / «Выключить»: сначала выбор, куда прислать код. */
    fun startTfa(currentlyEnabled: Boolean) {
        _tfa.value = TfaManageState(step = TfaManageStep.METHOD, enabling = !currentlyEnabled)
    }

    fun cancelTfa() { _tfa.value = TfaManageState() }

    fun sendTfaCode(method: TfaMethod) {
        if (_tfa.value.sending) return
        _tfa.update { it.copy(method = method, sending = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.request2FA(method) }
                .onSuccess { info -> _tfa.update { it.copy(step = TfaManageStep.CODE, info = info, sending = false) } }
                .onFailure { e -> _tfa.update { it.copy(sending = false, error = TfaTexts.sendError(TfaError.of(e), method)) } }
        }
    }

    /** Подтверждение кодом: `set2FAEnabled({ enabled, code })`. */
    fun confirmTfa(code: String) {
        val s = _tfa.value
        if (code.length != 6 || s.confirming) return
        _tfa.update { it.copy(confirming = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.set2FAEnabled(s.enabling, code) }
                .onSuccess {
                    if (s.enabling) {
                        // Сервер уже авторизовал эту сессию — экран кода здесь не нужен
                        runCatching { auth.getAuthTime()?.let(tfaManager::setTfaPassed) }
                    } else {
                        tfaManager.clearCache()
                    }
                    tfaHold.refresh()
                    _tfa.value = TfaManageState()
                    _notice.value = UiText.StringResource(if (s.enabling) R.string.tfa_manage_enabled else R.string.tfa_manage_disabled)
                }
                .onFailure { e -> _tfa.update { it.copy(confirming = false, error = TfaTexts.verifyError(TfaError.of(e))) } }
        }
    }

    // ── Коды в Telegram ───────────────────────────────────────────────────────

    /** Вкл/выкл кодов в Telegram. При включении сервер сразу пишет в Telegram — проверка, что бот не заблокирован. */
    fun setTelegramCodes(enable: Boolean) {
        if (_telegramBusy.value) return
        _telegramBusy.value = true
        viewModelScope.launch {
            runCatching { auth.setTfaTelegram(enable) }
                .onSuccess { _notice.value = UiText.StringResource(if (enable) R.string.tfa_tg_enabled else R.string.tfa_tg_disabled) }
                .onFailure { e -> _notice.value = TfaTexts.telegramError(TfaError.of(e)) }
            _telegramBusy.value = false
        }
    }

    /** Отвязка Telegram (`unbindTelegram`); сервер сам выключает и коды в Telegram. */
    fun unbindTelegram() {
        if (_telegramBusy.value) return
        _telegramBusy.value = true
        viewModelScope.launch {
            runCatching { auth.unbindTelegram() }
                .onSuccess { _notice.value = UiText.StringResource(R.string.settings_tg_unlinked_toast) }
                .onFailure { e -> _notice.value = UiText.DynamicString(e.message ?: "") }
            _telegramBusy.value = false
        }
    }

    companion object {
        private const val MIN_PASSWORD = 6
    }
}
