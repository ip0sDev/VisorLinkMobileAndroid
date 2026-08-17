package by.iposdev.visorlink.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages 2FA session status using EncryptedSharedPreferences.
 * We cache the status of 2FA for the current session (auth_time).
 */
class TfaManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "visorlink_tfa_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Mark 2FA as passed for the given [authTime].
     */
    fun setTfaPassed(authTime: String) {
        sharedPreferences.edit().putBoolean("tfa_$authTime", true).apply()
    }

    /**
     * Check if 2FA was already passed for the given [authTime].
     */
    fun isTfaPassed(authTime: String): Boolean {
        return sharedPreferences.getBoolean("tfa_$authTime", false)
    }

    /**
     * Clear all 2FA session cache (e.g. on logout or for security).
     */
    fun clearCache() {
        sharedPreferences.edit().clear().apply()
    }
}
