package org.visorlink.app.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Тип защищенного аппаратного или программного хранилища ключей Android KeyStore.
 */
enum class KeystoreSecurityLevel(val title: String, val badge: String) {
    STRONGBOX("Аппаратный изолированный чип безопасности (StrongBox Keymaster)", "StrongBox"),
    TEE("Аппаратная доверенная зона процессора (TEE)", "TEE"),
    SOFTWARE("Программная эмуляция хранилища ключей (Software)", "Software"),
    UNKNOWN("Защищенное хранилище Android KeyStore", "KeyStore")
}

sealed class BiometricSaveResult {
    data class Success(
        val securityLevel: KeystoreSecurityLevel,
        val message: String
    ) : BiometricSaveResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : BiometricSaveResult()
}

sealed class BiometricUnlockResult {
    data class Success(
        val pin: String,
        val securityLevel: KeystoreSecurityLevel
    ) : BiometricUnlockResult()

    data class KeyPermanentlyInvalidated(
        val message: String
    ) : BiometricUnlockResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : BiometricUnlockResult()
}

/**
 * Единый менеджер безопасного хранения PIN-кодов под биометрию с диагностикой
 * аппаратного уровня защиты (StrongBox / TEE / Software) и обработкой инвалидации ключей.
 */
class BiometricPinManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "biometric_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getAlias(uid: String): String = "visorlink_bio_key_$uid"

    /**
     * Определяет тип аппаратного/программного хранилища для сгенерированного ключа.
     */
    fun getKeystoreSecurityLevel(uid: String): KeystoreSecurityLevel {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = getAlias(uid)
            val secretKey = keyStore.getKey(alias, null) as? SecretKey ?: return KeystoreSecurityLevel.UNKNOWN
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeystoreSecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeystoreSecurityLevel.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeystoreSecurityLevel.SOFTWARE
                    else -> if (keyInfo.isInsideSecureHardware) KeystoreSecurityLevel.TEE else KeystoreSecurityLevel.SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) KeystoreSecurityLevel.TEE else KeystoreSecurityLevel.SOFTWARE
            }
        } catch (_: Exception) {
            KeystoreSecurityLevel.UNKNOWN
        }
    }

    /**
     * Проверяет наличие сохраненных данных биометрии для пользователя.
     */
    fun hasSavedPin(uid: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("pin_enc_$uid") && prefs.contains("pin_iv_$uid")
    }

    /**
     * Безопасно сохраняет PIN-код в зашифрованном виде с использованием Android KeyStore.
     */
    fun savePinSecurely(uid: String, pin: String): BiometricSaveResult {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = getAlias(uid)

            // Если ключ уже существовал, но сохраняется новый PIN,
            // пересоздаем ключ для избежания невалидного состояния аппаратного TEE/Strongbox
            if (keyStore.containsAlias(alias)) {
                try { keyStore.deleteEntry(alias) } catch (_: Exception) {}
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()

            val secretKey = keyStore.getKey(alias, null) as SecretKey
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: throw IllegalStateException("Cipher did not generate IV for GCM")
            val encrypted = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("pin_iv_$uid", Base64.encodeToString(iv, Base64.DEFAULT))
                .putString("pin_enc_$uid", Base64.encodeToString(encrypted, Base64.DEFAULT))
                .apply()

            val secLevel = getKeystoreSecurityLevel(uid)
            BiometricSaveResult.Success(
                securityLevel = secLevel,
                message = "Ключ защищен: ${secLevel.title}"
            )
        } catch (e: Exception) {
            android.util.Log.e("BiometricPinManager", "Error saving biometric pin", e)
            BiometricSaveResult.Error(
                message = "Ошибка Keystore при сохранении ключа: ${e.localizedMessage ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }

    /**
     * Расшифровывает сохраненный PIN-код с явной обработкой смены отпечатков (инвалидации).
     */
    fun getPinSecurely(uid: String): BiometricUnlockResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ivStr = prefs.getString("pin_iv_$uid", null)
        val encStr = prefs.getString("pin_enc_$uid", null)
        if (ivStr == null || encStr == null) {
            return BiometricUnlockResult.Error("Ключ биометрии не сохранен на этом устройстве.")
        }

        val alias = getAlias(uid)
        return try {
            val iv = Base64.decode(ivStr, Base64.DEFAULT)
            val encrypted = Base64.decode(encStr, Base64.DEFAULT)

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(alias, null) as? SecretKey
                ?: return BiometricUnlockResult.Error("Аппаратный ключ шифрования не найден в KeyStore.")

            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decoded = cipher.doFinal(encrypted)
            val pin = String(decoded, Charsets.UTF_8)
            val secLevel = getKeystoreSecurityLevel(uid)
            BiometricUnlockResult.Success(pin, secLevel)
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            clearSavedPin(uid)
            BiometricUnlockResult.KeyPermanentlyInvalidated(
                "Биометрические данные в системе изменились. Пожалуйста, введите PIN-код для обновления доступа."
            )
        } catch (e: Exception) {
            clearSavedPin(uid)
            BiometricUnlockResult.Error(
                "Не удалось расшифровать ключ биометрии (${e.localizedMessage ?: e.javaClass.simpleName}). Введите PIN вручную.",
                cause = e
            )
        }
    }

    /**
     * Удаляет сохраненные биометрические данные и ключ из KeyStore.
     */
    fun clearSavedPin(uid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("pin_iv_$uid")
            .remove("pin_enc_$uid")
            .apply()

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = getAlias(uid)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (_: Exception) {}
    }
}

