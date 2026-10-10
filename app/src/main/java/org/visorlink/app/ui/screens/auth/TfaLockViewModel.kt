package org.visorlink.app.ui.screens.auth

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.visorlink.app.data.auth.TfaCodeInfo
import org.visorlink.app.data.auth.TfaError
import org.visorlink.app.data.auth.TfaGateHold
import org.visorlink.app.data.auth.TfaMethod
import org.visorlink.app.data.auth.tfaMethods
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.SessionRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.UiText
import org.visorlink.app.utils.FcmManager
import org.visorlink.app.utils.TfaManager

enum class TfaSendStatus { IDLE, SENDING, SENT, FAILED }
enum class TfaVerifyStatus { IDLE, CHECKING, OK, FAILED }

data class TfaLockUiState(
    /** Последний выбранный способ (и тот, которым сейчас отправляем). */
    val method: TfaMethod = TfaMethod.BOT,
    /** Каким способом попросили в последний раз — с ним сравнивается [TfaCodeInfo.method]. */
    val requested: TfaMethod? = null,
    val send: TfaSendStatus = TfaSendStatus.IDLE,
    val info: TfaCodeInfo? = null,
    val sendError: UiText? = null,
    val verify: TfaVerifyStatus = TfaVerifyStatus.IDLE,
    val verifyError: UiText? = null,
    /** После `expired` / `locked` / `missing` «Подтвердить» ждёт нового кода. */
    val needsNewCodeByError: Boolean = false,
    /** Растёт при неверном коде — экран «встряхивает» поле. */
    val shake: Int = 0,
) {
    val busy: Boolean get() = send == TfaSendStatus.SENDING || verify == TfaVerifyStatus.CHECKING || verify == TfaVerifyStatus.OK
}

/**
 * Экран кода 2FA при входе (веб `TwoFactorLock.jsx`). Всегда видно, что происходит: код
 * отправляется / отправлен (куда и до какого времени) / не ушёл (почему), идёт проверка.
 * Сервер идемпотентен, поэтому код запрашивается при каждом открытии экрана.
 */
class TfaLockViewModel(
    private val auth: AuthRepository,
    private val sessions: SessionRepository,
    users: UserRepository,
    private val tfaManager: TfaManager,
    private val fcmManager: FcmManager,
    private val hold: TfaGateHold,
    private val prefs: SharedPreferences,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = (auth.currentUid?.let { users.userProfileFlow(it) } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(TfaLockUiState(method = savedMethod(listOf(TfaMethod.BOT, TfaMethod.EMAIL))))
    val state: StateFlow<TfaLockUiState> = _state.asStateFlow()

    private var started = false

    /** Код — сразу при открытии экрана, тем способом, что выбирали в прошлый раз. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            // Карточка сессии — до request2FA: по ней сервер описывает устройство в сообщении с кодом
            withTimeoutOrNull(REGISTER_TIMEOUT_MS) { runCatching { sessions.ensureRegistered() } }
            // Telegram в списке, только если профиль уже говорит, что коды туда включены
            val methods = withTimeoutOrNull(PROFILE_WAIT_MS) { profile.first { it != null } }.tfaMethods()
            sendCode(savedMethod(methods))
        }
    }

    fun sendCode(method: TfaMethod) {
        if (_state.value.send == TfaSendStatus.SENDING) return
        runCatching { prefs.edit().putString(KEY_METHOD, method.id).apply() }
        _state.update {
            it.copy(
                method = method, requested = method, send = TfaSendStatus.SENDING, sendError = null,
                verify = TfaVerifyStatus.IDLE, verifyError = null, needsNewCodeByError = false,
            )
        }
        viewModelScope.launch {
            runCatching { auth.request2FA(method) }
                .onSuccess { info -> _state.update { it.copy(send = TfaSendStatus.SENT, info = info) } }
                .onFailure { e ->
                    _state.update { it.copy(send = TfaSendStatus.FAILED, sendError = TfaTexts.sendError(TfaError.of(e), method)) }
                }
        }
    }

    /** Ввели или вставили 6 цифр — проверяем сразу. */
    fun verify(code: String) {
        val s = _state.value
        if (code.length != 6 || s.busy || s.needsNewCodeByError) return
        _state.update { it.copy(verify = TfaVerifyStatus.CHECKING, verifyError = null) }
        // Документ сессии придёт раньше ответа — держим экран, пока не покажем «Вход подтверждён»
        hold.hold()
        viewModelScope.launch {
            runCatching { auth.verify2FA(code) }
                .onSuccess {
                    _state.update { it.copy(verify = TfaVerifyStatus.OK) }
                    runCatching { auth.getAuthTime()?.let(tfaManager::setTfaPassed) }
                    launch { runCatching { fcmManager.syncTokenAfter2FA() } }
                    delay(SUCCESS_PAUSE_MS)
                    hold.refresh()
                    hold.release()
                }
                .onFailure { e ->
                    hold.release()
                    val err = TfaError.of(e)
                    _state.update {
                        it.copy(
                            verify = TfaVerifyStatus.FAILED,
                            verifyError = TfaTexts.verifyError(err),
                            needsNewCodeByError = err.needsNewCode,
                            shake = it.shake + 1,
                        )
                    }
                }
        }
    }

    /** Пользователь меняет код после ошибки — красная рамка уходит. */
    fun onCodeEdited() {
        if (_state.value.verify == TfaVerifyStatus.FAILED) _state.update { it.copy(verify = TfaVerifyStatus.IDLE, verifyError = null) }
    }

    override fun onCleared() {
        hold.release()
    }

    private fun savedMethod(allowed: List<TfaMethod>): TfaMethod =
        runCatching { TfaMethod.of(prefs.getString(KEY_METHOD, null)) }.getOrNull()?.takeIf { it in allowed } ?: TfaMethod.BOT

    companion object {
        /** Последний выбранный способ — удобство, не секрет (веб `vl_tfa_method`). */
        const val KEY_METHOD = "tfa_last_method"
        private const val REGISTER_TIMEOUT_MS = 5_000L
        private const val PROFILE_WAIT_MS = 1_500L
        private const val SUCCESS_PAUSE_MS = 650L
    }
}
