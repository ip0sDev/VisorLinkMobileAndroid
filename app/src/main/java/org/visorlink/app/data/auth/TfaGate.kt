package org.visorlink.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние проверки 2FA для текущей сессии (веб — эффект «Проверка 2FA» в AuthContext.jsx).
 * Решение «нужен код» принимается только по ответу сервера; таймер лишь показывает экран кода
 * с пометкой о связи, но не отписывается: появится документ — экран закроется сам.
 */
sealed interface TfaGate {
    /** 2FA не участвует: нет сессии, нет профиля или защита выключена. */
    data object Off : TfaGate
    /** Ждём ответа сервера (спиннер «Проверяем сессию…»). */
    data object Checking : TfaGate
    /** Нужен код. [slow] — сервер долго молчит или чтение не удалось. */
    data class Required(val slow: Boolean = false) : TfaGate
    /** Сессия в белом списке `authorized_sessions`. */
    data object Passed : TfaGate

    companion object {
        /** Через сколько молчания сервера показать экран кода с пометкой «долго не отвечает». */
        const val SLOW_CHECK_MS = 12_000L
    }
}

/**
 * Экран кода держит ворота закрытыми, пока показывает «Вход подтверждён» (~0,6 с): документ
 * сессии приходит раньше ответа `verify2FA`, и без удержания экран исчез бы на полуслове.
 */
class TfaGateHold {
    private val _held = MutableStateFlow(false)
    val held: StateFlow<Boolean> = _held.asStateFlow()

    private val _refresh = MutableStateFlow(0)
    /** Меняется, когда сессию подтвердили локально (код введён, 2FA включена здесь) — ворота пересчитываются. */
    val refreshTick: StateFlow<Int> = _refresh.asStateFlow()

    fun hold() { _held.value = true }
    fun release() { _held.value = false }
    fun refresh() { _refresh.value++ }
}
