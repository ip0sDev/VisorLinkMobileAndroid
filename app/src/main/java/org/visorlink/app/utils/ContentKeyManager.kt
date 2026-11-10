package org.visorlink.app.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Жизненный цикл ключей контента (CK) — TODO §14.3.
 *
 * Принцип: CK — случайные 256 бит; PIN/биометрия/recovery только заворачивают его.
 * Низкоэнтропийный PIN больше НЕ является ключом и нигде не хранится, серверный pinHash
 * удалён: офлайн-брутфорс по дампу Firestore невозможен (обёртка от PIN живёт только
 * локально, серверная обёртка — от recovery-кода высокой энтропии).
 *
 * Обёртки CK (три независимых пути разблокировки):
 *  1. Keystore-обёртка (локально, свободный мастер-ключ) — прозрачный доступ, когда PIN-гейта нет;
 *  2. PIN-обёртка (локально, PBKDF2 600k + случайная соль) — вход по PIN;
 *  3. Bio-обёртка (локально, auth-bound ключ Keystore + BiometricPrompt.CryptoObject) — вход по биометрии;
 *  4. Recovery-обёртка (Firestore, PBKDF2 от recovery-кода) — восстановление на новом устройстве.
 *
 * Форматы: keystore/bio-обёртки — "iv:ct" (Base64); PIN-обёртка —
 * "ckv2pin:iterations:salt:iv:ct"; recovery-обёртка — карта в Firestore.
 */
class ContentKeyManager(
    private val context: Context,
    private val flagsRepository: org.visorlink.app.data.repository.FlagsRepository? = null
) {

    companion object {
        const val DOMAIN_SAVED = "saved"
        const val DOMAIN_DIARY = "diary"
        const val DOMAIN_VAULT = "vault"

        val CONTENT_DOMAINS = listOf(DOMAIN_SAVED, DOMAIN_DIARY, DOMAIN_VAULT)

        /**
         * Kill-switch миграции на CK-схему (серверный флаг): миграция запускается только
         * когда веб-версия умеет читать v2. Флаг выключен → приложение работает по-старому.
         */
        const val FLAG_SAVED_CRYPTO_V2 = "enable_saved_crypto_v2"

        private const val PREFS_NAME = "crypto_vault_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 600_000
        private const val MAX_ATTEMPTS = 5

        /** Задержка после исчерпания попыток (мс) — экспоненциальная от номера блокировки. */
        fun lockoutDelayMs(lockCount: Int): Long =
            (30_000L * (1L shl lockCount.coerceIn(0, 6)))

        fun generateRecoveryCode(): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val rnd = SecureRandom()
            val sb = StringBuilder()
            for (group in 0 until 4) {
                if (group > 0) sb.append('-')
                for (i in 0 until 6) sb.append(alphabet[rnd.nextInt(alphabet.length)])
            }
            return sb.toString() // 24 символа ≈ 114 бит энтропии
        }
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Включена ли сервером миграция на CK-схему (веб должен уметь читать v2 до включения). */
    fun migrationEnabled(): Boolean =
        flagsRepository?.flags?.value?.isEnabled(FLAG_SAVED_CRYPTO_V2) == true

    // ─── Наличие и базовые операции CK ────────────────────────────────────────

    fun hasCK(uid: String, domain: String): Boolean =
        prefs.contains("ck_ks_${uid}_$domain") && uid.isNotBlank()

    /**
     * Текущий CK домена: разворачивает локальную Keystore-обёртку или генерирует новый.
     * Вызывать только когда PIN-гейт домена снят (или CK уже получен иным путём).
     */
    @Synchronized
    fun getOrCreateCK(uid: String, domain: String): SecretKey {
        SecureVault.unwrapSecretKey(prefs.getString("ck_ks_${uid}_$domain", null) ?: "")?.let { return it }
        val ck = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
        prefs.edit().putString("ck_ks_${uid}_$domain", SecureVault.wrapSecretKey(ck)).apply()
        return ck
    }

    private fun putKeystoreWrap(uid: String, domain: String, ck: SecretKey) {
        prefs.edit().putString("ck_ks_${uid}_$domain", SecureVault.wrapSecretKey(ck)).apply()
    }

    // ─── PIN-обёртка ──────────────────────────────────────────────────────────

    fun pinWrapExists(uid: String, domain: String): Boolean =
        prefs.getString("ck_pin_$domain", null) != null

    /** Проверка PIN = попытка разворота PIN-обёртки. null — неверный PIN (учёт в lockout на вызывающем). */
    fun ckByPin(uid: String, domain: String, pin: String): SecretKey? {
        val blob = prefs.getString("ck_pin_${uid}_$domain", null) ?: return null
        val parts = blob.split(":")
        if (parts.size != 5 || parts[0] != "ckv2pin") return null
        val iterations = parts[1].toIntOrNull() ?: PBKDF2_ITERATIONS
        val kek = deriveKEK(pin, parts[2], iterations) ?: return null
        val raw = decryptBytes(Base64.decode(parts[4], Base64.NO_WRAP), parts[3], kek) ?: return null
        return SecretKeySpec(raw, "AES")
    }

    /** Создаёт/обновляет PIN-обёртку (600k итераций, случайная соль). */
    fun enablePin(uid: String, domain: String, ck: SecretKey, pin: String): Boolean {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = deriveKEK(pin, Base64.encodeToString(salt, Base64.NO_WRAP), PBKDF2_ITERATIONS) ?: return false
        val raw = ck.encoded
        val (ct, ivB64) = encryptBytes(raw, kek)
        val blob = "ckv2pin:$PBKDF2_ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:$ivB64:" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString("ck_pin_${uid}_$domain", blob).apply()
        return true
    }

    /**
     * Снятие PIN-гейта (новая семантика — TODO §14.4): удаляет PIN- и bio-обёртки,
     * гарантирует свободную Keystore-обёртку. Контент НЕ удаляется и остаётся читаемым.
     */
    fun disablePin(uid: String, domain: String, ck: SecretKey) {
        putKeystoreWrap(uid, domain, ck)
        prefs.edit().remove("ck_pin_${uid}_$domain").apply()
        clearBioWrap(uid, domain)
    }

    fun changePin(uid: String, domain: String, ck: SecretKey, newPin: String): Boolean =
        enablePin(uid, domain, ck, newPin)

    // ─── Ротация CK (сброс ключа восстановления, TODO §14.4) ─────────────────

    /**
     * Начало ротации CK: генерирует НОВЫЙ случайный ключ и прячет его во временную
     * Keystore-обёртку ck_ks_new + флаг rotation_in_progress. Записывается ДО начала
     * пере-шифровки: после сбоя процесса ротация возобновляется
     * (resumeKeyRotationPending + pendingRotationCK).
     */
    @Synchronized
    fun beginKeyRotation(uid: String, domain: String): SecretKey {
        val ck = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
        prefs.edit()
            .putString("ck_ks_new_${uid}_$domain", SecureVault.wrapSecretKey(ck))
            .putBoolean("rotation_in_progress_${uid}_$domain", true)
            .apply()
        return ck
    }

    /** Есть ли незавершённая ротация (после сбоя процесса). */
    fun resumeKeyRotationPending(uid: String, domain: String): Boolean =
        prefs.getBoolean("rotation_in_progress_${uid}_$domain", false) &&
                prefs.contains("ck_ks_new_${uid}_$domain")

    /** Новый CK из временной обёртки — для возобновления прерванной ротации. */
    fun pendingRotationCK(uid: String, domain: String): SecretKey? =
        SecureVault.unwrapSecretKey(prefs.getString("ck_ks_new_${uid}_$domain", null) ?: "")

    /**
     * Завершение ротации: новый CK становится текущим, PIN-обёртка пересоздаётся
     * под тем же PIN, временные обёртки и флаг удаляются, bio-обёртка стирается
     * (пользователь включит биометрию заново). Recovery-обёртку перезаписывает
     * setupRecovery на вызывающей стороне.
     */
    @Synchronized
    fun completeKeyRotation(uid: String, domain: String, pin: String): Boolean {
        val newCk = pendingRotationCK(uid, domain) ?: return false
        putKeystoreWrap(uid, domain, newCk)
        enablePin(uid, domain, newCk, pin)
        prefs.edit()
            .remove("ck_ks_new_${uid}_$domain")
            .remove("rotation_in_progress_${uid}_$domain")
            .apply()
        clearBioWrap(uid, domain)
        clearFailedAttempts(uid, domain)
        return true
    }

    // ─── Lockout (GUI-перебор) ────────────────────────────────────────────────

    /** Возвращает remaining ms, если вход заблокирован, иначе 0. */
    fun lockoutRemainingMs(uid: String, domain: String): Long {
        val until = prefs.getLong("lock_until_${uid}_$domain", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun registerFailedAttempt(uid: String, domain: String) {
        val fails = prefs.getInt("lock_fails_${uid}_$domain", 0) + 1
        prefs.edit().putInt("lock_fails_${uid}_$domain", fails).apply()
        if (fails >= MAX_ATTEMPTS) {
            val delay = lockoutDelayMs(prefs.getInt("lock_wave_${uid}_$domain", 0))
            prefs.edit()
                .putLong("lock_until_${uid}_$domain", System.currentTimeMillis() + delay)
                .putInt("lock_wave_${uid}_$domain", prefs.getInt("lock_wave_${uid}_$domain", 0) + 1)
                .putInt("lock_fails_${uid}_$domain", 0)
                .apply()
        }
    }

    fun clearFailedAttempts(uid: String, domain: String) {
        prefs.edit().putInt("lock_fails_${uid}_$domain", 0).apply()
    }

    // ─── Bio-обёртка (auth-bound ключ + CryptoObject) ─────────────────────────

    private fun bioAlias(uid: String, domain: String) = "vl_bio_${domain}_$uid"

    private fun ensureBioKey(uid: String, domain: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val alias = bioAlias(uid, domain)
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        if (keyStore.containsAlias(alias)) {
            try { keyStore.deleteEntry(alias) } catch (_: Exception) {}
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            // Настоящая привязка к аутентификации: без свежей биометрии ключ не работает (TODO §14.3)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUnlockedDeviceRequired(false)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    fun bioWrapExists(uid: String, domain: String): Boolean =
        prefs.contains("ck_bio_${domain}_$uid")

    /**
     * Создаёт bio-обёртку CK. Вызывается ПОСЛЕ биометрической аутентификации:
     * шифрование выполняется в CryptoObject, т.е. ключ физически нельзя применить без биометрии.
     */
    fun bioWrapCKWithPrompt(
        activity: FragmentActivity,
        uid: String,
        domain: String,
        ck: SecretKey,
        title: String,
        subtitle: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        try {
            val key = ensureBioKey(uid, domain)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            authenticate(activity, cipher, title, subtitle) { success, crypto, err ->
                if (!success) {
                    onResult(false, err)
                    return@authenticate
                }
                try {
                    val ct = crypto?.cryptoObject?.cipher?.doFinal(ck.encoded)
                        ?: throw IllegalStateException("CryptoObject отсутствует")
                    val iv = cipher.iv ?: throw IllegalStateException("IV отсутствует")
                    prefs.edit().putString(
                        "ck_bio_${domain}_$uid",
                        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                                Base64.encodeToString(ct, Base64.NO_WRAP)
                    ).apply()
                    onResult(true, null)
                } catch (e: Exception) {
                    onResult(false, e.message)
                }
            }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    /**
     * Разворачивает CK по биометрии: BiometricPrompt с CryptoObject (DECRYPT),
     * doFinal выполняется только в onSuccess — без живой биометрии ключ недоступен.
     */
    fun bioUnwrapCKWithPrompt(
        activity: FragmentActivity,
        uid: String,
        domain: String,
        title: String,
        subtitle: String,
        onResult: (SecretKey?, String?) -> Unit
    ) {
        val blob = prefs.getString("ck_bio_${domain}_$uid", null)
        if (blob == null) {
            onResult(null, "Биометрия для этого раздела не настроена")
            return
        }
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(bioAlias(uid, domain), null) as? SecretKey
            if (key == null) {
                onResult(null, "Ключ биометрии не найден")
                return
            }

            val parts = blob.split(":")
            if (parts.size != 2) {
                onResult(null, "Повреждена bio-обёртка")
                return
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            authenticate(activity, cipher, title, subtitle) { success, crypto, err ->
                if (!success) {
                    onResult(null, err)
                    return@authenticate
                }
                try {
                    val raw = crypto?.cryptoObject?.cipher?.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
                        ?: throw IllegalStateException("CryptoObject отсутствует")
                    onResult(SecretKeySpec(raw, "AES"), null)
                } catch (e: Exception) {
                    // Инвалидация ключа (смена биометрии) — bio-обёртка больше не действительна
                    clearBioWrap(uid, domain)
                    onResult(null, e.message)
                }
            }
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            clearBioWrap(uid, domain)
            onResult(null, "Биометрические данные изменились, настройте биометрию заново")
        } catch (e: Exception) {
            onResult(null, e.message)
        }
    }

    fun clearBioWrap(uid: String, domain: String) {
        prefs.edit().remove("ck_bio_${domain}_$uid").apply()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = bioAlias(uid, domain)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (_: Exception) {}
    }


    /**
     * Полная очистка локальных обёрток CK аккаунта (logout / смена аккаунта, TODO §14.6).
     * Recovery-обёртка остаётся на сервере — восстановление по коду возможно.
     */
    fun wipeUid(uid: String) {
        val editor = prefs.edit()
        for ((key, _) in prefs.all) {
            if (key.contains(uid)) editor.remove(key)
        }
        editor.apply()
        for (domain in CONTENT_DOMAINS) {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val alias = bioAlias(uid, domain)
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } catch (_: Exception) {}
        }
    }

    /**
     * Локальная очистка обёрток одного домена (стирание «Избранного» при забытом
     * recovery-коде, TODO §14.4): ck_ks/ck_pin/ck_bio, lockout- и rotation-ключи
     * этого uid+domain + bio-ключ из Keystore. Vault-домен и другие аккаунты не
     * затрагиваются; recovery-обёртка на сервере удаляется отдельно.
     */
    fun wipeDomain(uid: String, domain: String) {
        if (uid.isBlank()) return
        val editor = prefs.edit()
        for (key in listOf(
            "ck_ks_${uid}_$domain",
            "ck_ks_new_${uid}_$domain",
            "ck_pin_${uid}_$domain",
            "lock_until_${uid}_$domain",
            "lock_fails_${uid}_$domain",
            "lock_wave_${uid}_$domain",
            "rotation_in_progress_${uid}_$domain",
            "ck_bio_${domain}_$uid"
        )) {
            editor.remove(key)
        }
        editor.apply()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = bioAlias(uid, domain)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (_: Exception) {}
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        onResult: (Boolean, BiometricPrompt.AuthenticationResult?, String?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onResult(true, result, null)

            override fun onAuthenticationError(code: Int, msg: CharSequence) =
                onResult(false, null, msg.toString())

            override fun onAuthenticationFailed() {
                // Оставляем диалог открытым — система даёт несколько попыток сама
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(context.getString(android.R.string.cancel))
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    // ─── Recovery-обёртка (Firestore) ─────────────────────────────────────────

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Генерирует recovery-код, создаёт recovery-обёртки для всех переданных CK
     * и сохраняет их в users/{uid}/crypto/keys. Код возвращается ОДИН раз.
     */
    suspend fun setupRecovery(uid: String, cks: Map<String, SecretKey>): String {
        val code = generateRecoveryCode()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        // Нормализация обязательна: разворот на обеих платформах считает код без дефисов
        val kek = deriveKEK(code.replace("-", ""), saltB64, PBKDF2_ITERATIONS)
            ?: throw IllegalStateException("Не удалось вывести recovery-ключ")
        val wraps = mutableMapOf<String, Any>()
        for ((domain, ck) in cks) {
            val (ct, ivB64) = encryptBytes(ck.encoded, kek)
            wraps["${domain}_recovery"] = mapOf(
                "v" to 1L,
                "kdf" to "pbkdf2-sha256",
                "iterations" to PBKDF2_ITERATIONS.toLong(),
                "salt" to saltB64,
                "iv" to ivB64,
                "ct" to Base64.encodeToString(ct, Base64.NO_WRAP)
            )
        }
        db.collection("users").document(uid)
            .collection("crypto").document("keys")
            .set(wraps, SetOptions.merge())
            .await()
        return code
    }

    suspend fun recoveryWrapExists(uid: String, domain: String): Boolean = runCatching {
        db.collection("users").document(uid).collection("crypto").document("keys")
            .get().await().contains("${domain}_recovery")
    }.getOrDefault(false)

    /** Удаление recovery-обёртки домена из users/{uid}/crypto/keys (стирание домена, TODO §14.4). */
    suspend fun deleteRecoveryWrap(uid: String, domain: String) {
        try {
            db.collection("users").document(uid).collection("crypto").document("keys")
                .update("${domain}_recovery", FieldValue.delete())
                .await()
        } catch (_: Exception) {}
    }

    /** Восстановление CK по recovery-коду (новое устройство). */
    suspend fun ckByRecoveryCode(uid: String, domain: String, code: String): SecretKey? {
        val doc = db.collection("users").document(uid).collection("crypto").document("keys").get().await()
        val wrap = doc.get("${domain}_recovery") as? Map<*, *> ?: return null
        val iterations = (wrap["iterations"] as? Number)?.toInt() ?: PBKDF2_ITERATIONS
        val saltB64 = wrap["salt"] as? String ?: return null
        val kek = deriveKEK(code.replace("-", ""), saltB64, iterations) ?: return null
        val ivB64 = wrap["iv"] as? String ?: return null
        val ctB64 = wrap["ct"] as? String ?: return null
        val raw = decryptBytes(Base64.decode(ctB64, Base64.NO_WRAP), ivB64, kek) ?: return null
        val ck = SecretKeySpec(raw, "AES")
        putKeystoreWrap(uid, domain, ck)
        return ck
    }

    /** Добавляет recovery-обёртку домена под существующим кодом (например, для нового домена). */
    suspend fun addRecoveryWrapForDomain(uid: String, domain: String, ck: SecretKey, code: String) {
        val doc = db.collection("users").document(uid).collection("crypto").document("keys").get().await()
        val existing = doc.get("${DOMAIN_SAVED}_recovery") as? Map<*, *> ?: return
        val iterations = (existing["iterations"] as? Number)?.toInt() ?: PBKDF2_ITERATIONS
        val saltB64 = existing["salt"] as? String ?: return
        val kek = deriveKEK(code.replace("-", ""), saltB64, iterations) ?: return
        val (ct, ivB64) = encryptBytes(ck.encoded, kek)
        db.collection("users").document(uid).collection("crypto").document("keys")
            .set(mapOf("${domain}_recovery" to mapOf(
                "v" to 1L,
                "kdf" to "pbkdf2-sha256",
                "iterations" to iterations.toLong(),
                "salt" to saltB64,
                "iv" to ivB64,
                "ct" to Base64.encodeToString(ct, Base64.NO_WRAP)
            )), SetOptions.merge())
            .await()
    }

    // ─── Низкоуровневые хелперы ───────────────────────────────────────────────

    private fun deriveKEK(password: String, saltB64: String, iterations: Int): SecretKey? = try {
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            Base64.decode(saltB64, Base64.NO_WRAP),
            iterations,
            256
        )
        try {
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    } catch (_: Exception) {
        null
    }

    private fun encryptBytes(plain: ByteArray, key: SecretKey): Pair<ByteArray, String> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ct = cipher.doFinal(plain)
        return Pair(ct, Base64.encodeToString(iv, Base64.NO_WRAP))
    }

    private fun decryptBytes(ct: ByteArray, ivB64: String, key: SecretKey): ByteArray? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivB64, Base64.NO_WRAP)))
        cipher.doFinal(ct)
    } catch (_: Exception) {
        null
    }
}
