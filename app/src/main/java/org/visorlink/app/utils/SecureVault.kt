package org.visorlink.app.utils

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Единая точка доступа к мастер-ключу Android Keystore (TODO §14.3).
 *
 * Мастер-ключ AES-256-GCM не покидает Keystore (StrongBox, где доступен) и используется
 * только для обёртки (wrap) ключей контента — сами ключи контента хранятся локально
 * в виде обёрнутых блобов. Хранение обёрток в обычных SharedPreferences безопасно:
 * блоб бесполезен без аппаратного ключа устройства.
 *
 * Политика fail-closed: при сбое Keystore операции с ключами контента запрещены,
 * плейнтекст-фолбэков нет.
 */
object SecureVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "vl_master_v1"
    private const val GCM_TAG_LENGTH = 128
    private const val KEY_SIZE = 256

    /**
     * Мастер-ключ (свободный — без привязки к аутентификации): используется для
     * прозрачных операций (Vault-синхронизация, контент без PIN-гейта).
     */
    @Synchronized
    fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(KEY_SIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (_: Exception) {
                // StrongBox недоступен на устройстве — используем TEE/Software
            }
        }
        generator.init(builder.build())
        return try {
            generator.generateKey()
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Повтор без StrongBox
                generator.init(
                    KeyGenParameterSpec.Builder(
                        MASTER_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setKeySize(KEY_SIZE)
                        .build()
                )
                generator.generateKey()
            } else throw e
        }
    }

    /** Обёртка сырого ключевого материала мастер-ключом. Формат: "iv:ct" (Base64). */
    fun wrapKey(rawKey: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = cipher.doFinal(rawKey)
        val iv = cipher.iv ?: throw IllegalStateException("Cipher did not generate IV for GCM")
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /** Разворачивание обёртки мастер-ключом. null — сбой Keystore (fail-closed). */
    fun unwrapKey(blob: String): ByteArray? = try {
        val parts = blob.split(":")
        if (parts.size != 2) null else {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ct)
        }
    } catch (_: Exception) {
        null
    }

    fun wrapSecretKey(key: SecretKey): String = wrapKey(key.encoded)

    fun unwrapSecretKey(blob: String): SecretKey? =
        unwrapKey(blob)?.let { javax.crypto.spec.SecretKeySpec(it, "AES") }
}
