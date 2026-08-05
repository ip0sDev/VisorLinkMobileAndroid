// utils/CryptoUtils.kt
package by.iposdev.visorlink.utils

import android.util.Base64
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ─── PIN Hashing ──────────────────────────────────────────────────────────────

fun hashPin(pin: String, uid: String): String {
    val input = "$pin:$uid"
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

// ─── Key Derivation (PBKDF2) ─────────────────────────────────────────────────

fun deriveKey(pin: String, uid: String): SecretKey {
    val salt = uid.padEnd(16, '0').substring(0, 16).toByteArray(Charsets.UTF_8)
    // Согласовано с WEB-версией: используем "$pin:$uid" в качестве пароля для PBKDF2
    val password = "$pin:$uid"
    val spec = PBEKeySpec(password.toCharArray(), salt, 200_000, 256)
    return try {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        SecretKeySpec(keyBytes, "AES")
    } finally {
        spec.clearPassword()
    }
}

// ─── AES-GCM-256 Encrypt/Decrypt (Text) ──────────────────────────────────────

private const val GCM_TAG_LENGTH = 128
private const val GCM_IV_LENGTH  = 12

fun encryptText(plaintext: String, key: SecretKey): Pair<String, String> {
    val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    return Pair(
        Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        Base64.encodeToString(iv, Base64.NO_WRAP)
    )
}

fun decryptText(ciphertextB64: String, ivB64: String, key: SecretKey): String? = try {
    val ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP)
    val iv         = Base64.decode(ivB64, Base64.NO_WRAP)
    val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    String(cipher.doFinal(ciphertext), Charsets.UTF_8)
} catch (_: Exception) { null }

// ─── AES-GCM-256 Encrypt/Decrypt (Bytes for Files - In Memory) ───────────────

fun encryptBytes(plaintext: ByteArray, key: SecretKey): Pair<ByteArray, String> {
    val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val ciphertext = cipher.doFinal(plaintext)
    return Pair(ciphertext, Base64.encodeToString(iv, Base64.NO_WRAP))
}

fun decryptBytes(ciphertext: ByteArray, ivB64: String, key: SecretKey): ByteArray? = try {
    val iv = Base64.decode(ivB64, Base64.NO_WRAP)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    cipher.doFinal(ciphertext)
} catch (_: Exception) { null }

// ─── AES-GCM-256 Encrypt/Decrypt (Files / Streaming) ─────────────────────────

/**
 * Потоковое шифрование файла.
 * Читает из [inputFile], шифрует и пишет в [outputFile].
 * Возвращает сгенерированный IV в Base64.
 */
fun encryptFile(inputFile: File, outputFile: File, key: SecretKey): String {
    val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

    inputFile.inputStream().use { fis ->
        CipherOutputStream(outputFile.outputStream(), cipher).use { cos ->
            fis.copyTo(cos)
        }
    }
    return Base64.encodeToString(iv, Base64.NO_WRAP)
}

/**
 * Потоковая расшифровка из InputStream (например, сети) в локальный файл.
 */
fun decryptStreamToFile(inputStream: InputStream, outputFile: File, ivB64: String, key: SecretKey) {
    val iv = Base64.decode(ivB64, Base64.NO_WRAP)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

    CipherInputStream(inputStream, cipher).use { cis ->
        outputFile.outputStream().use { fos ->
            cis.copyTo(fos)
        }
    }
}