package org.visorlink.app.utils

import android.content.Context

/**
 * Manages 2FA session status using SecurePrefs (TODO §14.5 — замена deprecated
 * EncryptedSharedPreferences, fail-closed: без плейнтекст-fallback).
 * We cache the status of 2FA for the current session (auth_time).
 */
class TfaManager(context: Context) {

    private val sharedPreferences by lazy {
        SecurePrefs(context.applicationContext, "tfa").apply {
            migrateAllFrom(
                listOf(
                    SecurePrefs.LegacyFile("visorlink_tfa_prefs", encrypted = true),
                    SecurePrefs.LegacyFile("visorlink_tfa_prefs_fallback", encrypted = false)
                )
            )
        }
    }

    /**
     * Mark 2FA as passed for the given [authTime].
     */
    fun setTfaPassed(authTime: String) {
        sharedPreferences.putBoolean("tfa_$authTime", true)
    }

    /**
     * Check if 2FA was already passed for the given [authTime].
     * Fail-closed: сбой Keystore → false (пользователь пройдёт 2FA заново).
     */
    fun isTfaPassed(authTime: String): Boolean {
        return sharedPreferences.getBoolean("tfa_$authTime", false)
    }

    /**
     * Clear all 2FA session cache (e.g. on logout or for security).
     */
    fun clearCache() {
        sharedPreferences.clear()
    }
}
