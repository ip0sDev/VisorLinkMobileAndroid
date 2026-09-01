package by.iposdev.visorlink.data.remote.flags

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import by.iposdev.visorlink.BuildConfig
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class AegisKeyManager {

    companion object {
        private const val ALIAS = "hermes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    fun hasKey(): Boolean {
        return keyStore.containsAlias(ALIAS)
    }

    fun generatePublicKeyPem(): String {
        try {
            if (!keyStore.containsAlias(ALIAS)) {
                if (BuildConfig.DEBUG) Log.d("AegisKey", "Alias $ALIAS not found, generating new key pair...")
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                val parameterSpec = KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()

                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()
                if (BuildConfig.DEBUG) Log.d("AegisKey", "Key pair generated successfully.")
            }

            val publicKey = keyStore.getCertificate(ALIAS).publicKey
            val pubKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            return "-----BEGIN PUBLIC KEY-----\n$pubKeyBase64\n-----END PUBLIC KEY-----"
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("AegisKey", "Error generating/obtaining public key", e)
            throw e
        }
    }

    fun signPayload(deviceId: String, timestamp: Long, nonce: String): String {
        val payloadString = "$deviceId$timestamp$nonce"
        val privateKey = keyStore.getKey(ALIAS, null) as PrivateKey

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payloadString.toByteArray(Charsets.UTF_8))
        val sigBytes = signature.sign()

        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }
}
