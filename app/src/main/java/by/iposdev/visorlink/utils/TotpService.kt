package by.iposdev.visorlink.utils

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpService {
    private const val DEMO_SECRET_BASE32 = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
    const val PERIOD = 30
    private const val DIGITS = 6

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = input.uppercase().replace("=", "")
        val bitsBuffer = StringBuilder()
        for (char in clean) {
            val valIndex = alphabet.indexOf(char)
            if (valIndex < 0) continue
            bitsBuffer.append(valIndex.toString(2).padStart(5, '0'))
        }
        val bits = bitsBuffer.toString()
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i + 8 <= bits.length) {
            bytes.add(bits.substring(i, i + 8).toInt(2).toByte())
            i += 8
        }
        return bytes.toByteArray()
    }

    fun currentCode(timeMillis: Long = System.currentTimeMillis()): String {
        val counter = (timeMillis / 1000) / PERIOD
        val key = base32Decode(DEMO_SECRET_BASE32)

        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))

        val hash = mac.doFinal(counterBytes)
        val offset = hash[hash.size - 1].toInt() and 0x0f

        val binCode = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val code = binCode % 10.0.pow(DIGITS.toDouble()).toInt()
        return code.toString().padStart(DIGITS, '0')
    }

    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val secondsInPeriod = ((timeMillis / 1000) % PERIOD).toInt()
        return PERIOD - secondsInPeriod
    }
}