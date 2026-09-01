package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

class StealthManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "visorlink_stealth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ENABLED = "stealth_mode_enabled"
        private const val KEY_BIOMETRIC_UNLOCK = "stealth_biometric_unlock_enabled"
        private const val KEY_PIN_HASH = "stealth_pin_hash"
        private const val KEY_SALT = "stealth_pin_salt"
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Открывать режим скрытия по отпечатку (в дополнение к PIN). */
    fun isBiometricUnlockEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_UNLOCK, false)

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_UNLOCK, enabled).apply()
    }

    fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val hash = hashPin(pin, salt)
        return hash == storedHash
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_SALT).apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun hashPin(pin: String, salt: String): String {
        val input = "$salt::$pin::durka_stealth_v1"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}