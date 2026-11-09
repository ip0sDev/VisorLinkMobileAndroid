package org.visorlink.app.utils

import android.util.Base64
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2EE-хендшейк «ключ контента на новый браузер по одобрению с телефона» (TODO §14.3–14.4).
 *
 * Веб (новый браузер) генерирует одноразовую пару ECDH P-256 и пишет заявку
 * users/{uid}/ck_requests/{requestId} = { ephemeralPub, createdAt, status:'pending', client }.
 * Data-пуш type=ck_handshake приводит пользователя в диалог одобрения; по «Разрешить» мы:
 *  1. показываем код сверки — SHA-256(ephemeralPub)[0..2] → «482 913» (тот же расчёт, что на вебе);
 *  2. импортируем ephemeralPub (65-байт uncompressed точка secp256r1 — формат WebCrypto exportKey('raw'));
 *  3. генерируем СВОЮ одноразовую пару, KeyAgreement "ECDH" → X-координата, выровненная до
 *     32 байт (WebCrypto deriveBits всегда 32; Java может отдать 31 без ведущего 0x00 —
 *     см. padTo32, паддинг согласован с вебом);
 *  4. AES-GCM шифруем текущий CK домена и кладём в заявку ТРАНЗАКЦИЕЙ конверт
 *     { status:'approved', payload:{ ephemeralPub, iv, ct } } — только если заявка всё ещё
 *     'pending' и моложе 5 минут. Сервер и правила Firestore видят только ciphertext.
 *
 * Ключи, секреты и ciphertext в логи не попадают — только факты («approved», «stale»).
 * Веб после успеха удаляет заявку — чистить её со стороны телефона не обязательно.
 */
class CkHandshakeManager {

    /** Результат одобрения заявки. */
    sealed class ApproveResult {
        /** Конверт записан — веб развернёт ключ. */
        object Approved : ApproveResult()
        /** Заявка уже изменена другой стороной или старше 5 минут. */
        object Stale : ApproveResult()
        /** Сеть/крипто-сбой — заявка не тронута. */
        object Error : ApproveResult()
    }

    companion object {
        private const val TAG = "CkHandshake"

        /** Возраст заявки, после которого одобрение невозможно (синхронно с вебом и notifyCkHandshake). */
        const val REQUEST_TTL_MS = 5 * 60_000L

        private const val GCM_TAG_LENGTH = 128

        /**
         * Код сверки хендшейка: первые 3 байта SHA-256(pubRaw) → три пары цифр «482 913»
         * (байт % 100 с нулём-паддингом). Locale.US обязателен — иначе локаль с другими
         * цифрами разойдётся с вебом.
         */
        fun verificationCode(ephemeralPub: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(ephemeralPub)
            val digits = StringBuilder()
            for (i in 0 until 3) {
                val b = digest[i].toInt() and 0xFF
                digits.append(String.format(Locale.US, "%02d", b % 100))
            }
            return "${digits.substring(0, 3)} ${digits.substring(3)}"
        }
    }

    /** Снимок заявки на момент загрузки (финальная валидность — в транзакции одобрения). */
    data class CkRequestInfo(
        val status: String?,
        val createdAt: Timestamp?,
        val client: String?,
        val ephemeralPubB64: String
    ) {
        /** Устарела/обработана: диалог одобрения для неё открывать нельзя. */
        val expired: Boolean
            get() = status != "pending" || createdAt == null ||
                    System.currentTimeMillis() - createdAt.toDate().time > REQUEST_TTL_MS
    }

    private val db get() = FirebaseFirestore.getInstance()

    /** Загружает заявку (null — документа нет/сбой сети). */
    suspend fun loadRequest(uid: String, requestId: String): CkRequestInfo? = try {
        val snap = db.collection("users").document(uid)
            .collection("ck_requests").document(requestId).get().await()
        if (!snap.exists()) null
        else CkRequestInfo(
            status = snap.getString("status"),
            createdAt = snap.getTimestamp("createdAt"),
            client = snap.getString("client"),
            ephemeralPubB64 = snap.getString("ephemeralPub").orEmpty()
        )
    } catch (e: Exception) {
        Log.w(TAG, "loadRequest failed: ${e.message}")
        null
    }

    /**
     * Одобрение заявки: конверт AES-GCM(CK) на ECDH-секрете. Транзакция гарантирует,
     * что одобрим только 'pending' и свежую (≤5 мин) заявку — при гонке двух устройств
     * или позднем нажатии вернётся Stale, ключ не перезаписывается.
     */
    suspend fun approveRequest(uid: String, requestId: String, info: CkRequestInfo, ck: SecretKey): ApproveResult {
        val docRef = db.collection("users").document(uid)
            .collection("ck_requests").document(requestId)
        return try {
            // Своя одноразовая пара ECDH P-256 (namedCurve secp256r1 — как WebCrypto P-256)
            val kp = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            }.generateKeyPair()
            val browserPub = importUncompressedPoint(Base64.decode(info.ephemeralPubB64, Base64.NO_WRAP))
            val shared = ecdhShared32(kp.private, browserPub)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(shared, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ct = cipher.doFinal(ck.encoded)
            shared.fill(0) // секрет ECDH больше не нужен
            val payload = mapOf(
                "ephemeralPub" to Base64.encodeToString(exportUncompressedPoint(kp.public), Base64.NO_WRAP),
                "iv" to Base64.encodeToString(iv, Base64.NO_WRAP),
                "ct" to Base64.encodeToString(ct, Base64.NO_WRAP),
            )
            val outcome = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val createdAt = snap.getTimestamp("createdAt")
                val fresh = createdAt != null &&
                        System.currentTimeMillis() - createdAt.toDate().time <= REQUEST_TTL_MS
                if (snap.getString("status") == "pending" && fresh) {
                    tx.update(docRef, mapOf(
                        "status" to "approved",
                        "payload" to payload,
                        "approvedAt" to FieldValue.serverTimestamp(),
                    ))
                    "approved"
                } else {
                    "stale" // уже одобрено другим клиентом или старше 5 минут
                }
            }.await()
            if (outcome == "approved") {
                Log.d(TAG, "handshake approved")
                ApproveResult.Approved
            } else {
                Log.d(TAG, "handshake rejected: stale")
                ApproveResult.Stale
            }
        } catch (e: Exception) {
            // Ошибки сети/крипты — без деталей конверта (ключи не логируем)
            Log.e(TAG, "handshake approve failed: ${e.javaClass.simpleName}")
            ApproveResult.Error
        }
    }

    /** Отклонение заявки пользователем: статус rejected, если она всё ещё pending. */
    suspend fun rejectRequest(uid: String, requestId: String) {
        val docRef = db.collection("users").document(uid)
            .collection("ck_requests").document(requestId)
        try {
            db.runTransaction { tx ->
                if (tx.get(docRef).getString("status") == "pending") {
                    tx.update(docRef, mapOf(
                        "status" to "rejected",
                        "rejectedAt" to FieldValue.serverTimestamp(),
                    ))
                }
                null
            }.await()
            Log.d(TAG, "handshake rejected by user")
        } catch (e: Exception) {
            Log.w(TAG, "handshake reject failed: ${e.message}")
        }
    }

    // ─── ECDH P-256 — совместимость с WebCrypto ──────────────────────────────

    /**
     * Импорт 65-байтной uncompressed точки (0x04||X||Y) — ровно тот формат, что отдаёт
     * WebCrypto exportKey('raw') для P-256. Параметры кривой берём из NamedCurve secp256r1.
     */
    private fun importUncompressedPoint(raw: ByteArray): PublicKey {
        if (raw.size != 65 || raw[0] != 0x04.toByte()) {
            throw IllegalArgumentException("ephemeralPub: ожидалась uncompressed точка P-256 (65 байт)")
        }
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }

    /** Экспорт своего публичного ключа в 65-байтную uncompressed точку 0x04||X(32)||Y(32). */
    private fun exportUncompressedPoint(pub: PublicKey): ByteArray {
        val w = (pub as? ECPublicKey)?.w ?: throw IllegalArgumentException("Не EC-публичный ключ")
        val out = ByteArray(65)
        out[0] = 0x04
        putCoordinate(out, 1, w.affineX)
        putCoordinate(out, 33, w.affineY)
        return out
    }

    /** Координата ровно 32 байта со сдвигом вправо (toByteArray даёт знаковый минимальный формат). */
    private fun putCoordinate(dst: ByteArray, offset: Int, value: BigInteger) {
        val arr = value.toByteArray()
        val take = arr.size.coerceAtMost(32)
        System.arraycopy(arr, arr.size - take, dst, offset + 32 - take, take)
    }

    /**
     * Секрет ECDH — X-координата общего ключа. ВНИМАНИЕ (выравнивание с вебом): Java-провайдер
     * может отдать 31 байт (обрезав ведущий 0x00), тогда как WebCrypto deriveBits(256) всегда
     * возвращает ровно 32 байта. Дополняем слева нулями до 32 — обе стороны получают
     * одинаковые байты AES-ключа.
     */
    private fun ecdhShared32(priv: PrivateKey, peerPub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(peerPub, true)
        val secret = ka.generateSecret()
        return padTo32(secret)
    }

    private fun padTo32(secret: ByteArray): ByteArray {
        val out = ByteArray(32)
        val take = secret.size.coerceAtMost(32)
        System.arraycopy(secret, secret.size - take, out, 32 - take, take)
        return out
    }
}
