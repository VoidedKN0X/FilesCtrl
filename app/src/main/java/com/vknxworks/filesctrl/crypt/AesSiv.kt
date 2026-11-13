package com.vknxworks.filesctrl.crypt

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The ciphertext was changed, or the key or associated data is wrong. */
class AuthenticationFailedException : GeneralSecurityException("Authentication failed")

/**
 * AES-SIV (RFC 5297), deterministic authenticated encryption, as Cryptomator uses it for file names and directory
 * IDs. In RFC terms the key is K1 || K2: [macKey] (K1) drives S2V, [ctrKey] (K2) drives AES-CTR. Output is V || C.
 *
 * S2V's CMAC comes from Bouncy Castle (already in the app for SFTP, MIT licence); CTR is the platform's AES.
 * Tested against the RFC 5297 appendix vectors.
 */
object AesSiv {
    private const val BLOCK = 16

    fun encrypt(macKey: ByteArray, ctrKey: ByteArray, plaintext: ByteArray, vararg associatedData: ByteArray): ByteArray {
        require(associatedData.size <= 126) { "Too many associated data components" }
        val v = s2v(macKey, associatedData, plaintext)
        val c = ctr(ctrKey, v, plaintext, 0, plaintext.size)
        return v + c
    }

    /** Throws [AuthenticationFailedException] if anything doesn't match. */
    fun decrypt(macKey: ByteArray, ctrKey: ByteArray, ciphertext: ByteArray, vararg associatedData: ByteArray): ByteArray {
        if (ciphertext.size < BLOCK) throw AuthenticationFailedException()
        require(associatedData.size <= 126) { "Too many associated data components" }
        val v = ciphertext.copyOfRange(0, BLOCK)
        val p = ctr(ctrKey, v, ciphertext, BLOCK, ciphertext.size - BLOCK)
        val t = s2v(macKey, associatedData, p)
        if (!MessageDigest.isEqual(t, v)) {
            p.fill(0)
            throw AuthenticationFailedException()
        }
        return p
    }

    private fun ctr(key: ByteArray, v: ByteArray, data: ByteArray, off: Int, len: Int): ByteArray {
        // Q = V with the 31st and 63rd bit (from the right) cleared, so implementations with 32/64-bit counters agree.
        val q = v.copyOf()
        q[8] = (q[8].toInt() and 0x7F).toByte()
        q[12] = (q[12].toInt() and 0x7F).toByte()
        if (len == 0) return ByteArray(0)
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(q))
        return c.doFinal(data, off, len)
    }

    private fun s2v(key: ByteArray, ad: Array<out ByteArray>, plaintext: ByteArray): ByteArray {
        val mac = CMac(AESEngine.newInstance())
        mac.init(KeyParameter(key))
        fun cmac(b: ByteArray): ByteArray {
            mac.update(b, 0, b.size)
            return ByteArray(BLOCK).also { mac.doFinal(it, 0) }
        }
        var d = cmac(ByteArray(BLOCK))
        for (s in ad) d = xor(dbl(d), cmac(s))
        val t = if (plaintext.size >= BLOCK) {
            // xorend: D into the last 16 bytes.
            val x = plaintext.copyOf()
            val o = x.size - BLOCK
            for (i in 0 until BLOCK) x[o + i] = (x[o + i].toInt() xor d[i].toInt()).toByte()
            x
        } else {
            val padded = ByteArray(BLOCK)
            plaintext.copyInto(padded)
            padded[plaintext.size] = 0x80.toByte()
            xor(dbl(d), padded)
        }
        val out = cmac(t)
        if (t !== plaintext) t.fill(0)
        return out
    }

    /** Doubling in GF(2^128) (multiply by x, reduce with 0x87). */
    internal fun dbl(b: ByteArray): ByteArray {
        val out = ByteArray(BLOCK)
        var carry = 0
        for (i in BLOCK - 1 downTo 0) {
            val v = b[i].toInt() and 0xFF
            out[i] = ((v shl 1) or carry).toByte()
            carry = v ushr 7
        }
        if (carry != 0) out[BLOCK - 1] = (out[BLOCK - 1].toInt() xor 0x87).toByte()
        return out
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(BLOCK) { (a[it].toInt() xor b[it].toInt()).toByte() }
}
