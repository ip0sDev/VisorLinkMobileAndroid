package org.visorlink.app.utils

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Зашифрованное key-value хранилище на SecureVault (TODO §14.5) — замена deprecated
 * androidx.security-crypto (EncryptedSharedPreferences) без плейнтекст-fallback'ов.
 *
 * Политика fail-closed: при сбое Keystore чтение возвращает default/null, запись
 * игнорируется — секреты никогда не оказываются в открытом виде.
 *
 * Формат значения: "enc1:<ivB64>:<ctB64>", AES-256-GCM мастер-ключом SecureVault.
 * Миграция legacy-хранилищ — однократная, см. [migrateAllFrom] / [migrateKeysFrom].
 */
class SecurePrefs(context: Context, name: String) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sv_$name", Context.MODE_PRIVATE)

    @Volatile
    private var keystoreBroken = false

    private fun cipherFor(mode: Int, iv: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, SecureVault.masterKey())
        } else {
            cipher.init(mode, SecureVault.masterKey(), GCMParameterSpec(128, iv!!))
        }
        return cipher
    }

    private fun encryptToBlob(plain: String): String? = try {
        val cipher = cipherFor(Cipher.ENCRYPT_MODE)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv ?: return null
        "enc1:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(ct, Base64.NO_WRAP)}"
    } catch (_: Exception) {
        keystoreBroken = true
        null
    }

    private fun decryptBlob(blob: String): String? = try {
        if (!blob.startsWith("enc1:")) null else {
            val parts = blob.split(":")
            if (parts.size != 3) null else {
                val cipher = cipherFor(Cipher.DECRYPT_MODE, Base64.decode(parts[1], Base64.NO_WRAP))
                String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
            }
        }
    } catch (_: Exception) {
        keystoreBroken = true
        null
    }

    // ─── Строки ───────────────────────────────────────────────────────────────

    fun getString(key: String, defValue: String? = null): String? {
        if (keystoreBroken) return defValue
        val blob = prefs.getString("k_$key", null) ?: return defValue
        return decryptBlob(blob) ?: defValue
    }

    fun putString(key: String, value: String?) {
        if (keystoreBroken) return
        val editor = prefs.edit()
        if (value == null) editor.remove("k_$key") else {
            val blob = encryptToBlob(value) ?: return
            editor.putString("k_$key", blob)
        }
        editor.apply()
    }

    // ─── Логические ───────────────────────────────────────────────────────────

    fun getBoolean(key: String, defValue: Boolean = false): Boolean =
        getString(key)?.let { it == "1" } ?: defValue

    fun putBoolean(key: String, value: Boolean) = putString(key, if (value) "1" else "0")

    // ─── Числа ────────────────────────────────────────────────────────────────

    fun getLong(key: String, defValue: Long = 0L): Long =
        getString(key)?.toLongOrNull() ?: defValue

    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun getInt(key: String, defValue: Int = 0): Int =
        getString(key)?.toIntOrNull() ?: defValue

    fun putInt(key: String, value: Int) = putString(key, value.toString())

    // ─── Служебное ────────────────────────────────────────────────────────────

    fun contains(key: String): Boolean =
        !keystoreBroken && prefs.contains("k_$key")

    fun remove(key: String) {
        prefs.edit().remove("k_$key").apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** true, если Keystore сломан: хранилище недоступно (fail-closed). */
    fun isUnavailable(): Boolean = keystoreBroken

    // ─── Однократная миграция из legacy-хранилищ ──────────────────────────────

    /** Legacy-файл: encrypted=true → читается как EncryptedSharedPreferences, иначе как обычный prefs. */
    class LegacyFile(val name: String, val encrypted: Boolean) {
        internal fun open(context: Context): android.content.SharedPreferences? = try {
            if (encrypted) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Переносит ВСЕ значения из legacy-файлов (для динамических ключей).
     * Порядок файлов = приоритет. Вызывается один раз; повторные вызовы — no-op.
     */
    fun migrateAllFrom(legacyFiles: List<LegacyFile>) {
        if (prefs.getBoolean("migrated_all", false)) return
        for (lf in legacyFiles) {
            val sp = lf.open(appContext) ?: continue
            for ((rawKey, value) in sp.all) {
                if (rawKey !in setOf("migrated_all") && prefs.contains("k_$rawKey")) continue
                when (value) {
                    is String -> putString(rawKey, value)
                    is Boolean -> putBoolean(rawKey, value)
                    is Int -> putInt(rawKey, value)
                    is Long -> putLong(rawKey, value)
                }
            }
        }
        prefs.edit().putBoolean("migrated_all", true).apply()
    }

    /**
     * Перенос конкретных ключей (для фиксированных ключей и файлов, где имя совпадало
     * и у ESP, и у plaintext-fallback — например, google_drive_secure_prefs).
     * Для каждого ключа берётся первый файл, который его отдаёт.
     */
    fun migrateKeysFrom(legacyFiles: List<LegacyFile>, keys: List<String>) {
        if (prefs.getBoolean("migrated_keys", false)) return
        for (key in keys) {
            if (prefs.contains("k_$key")) continue
            for (lf in legacyFiles) {
                val sp = lf.open(appContext) ?: continue
                val value = runCatching { sp.all[key] }.getOrNull() ?: continue
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                }
                break
            }
        }
        prefs.edit().putBoolean("migrated_keys", true).apply()
    }
}
