package com.vknxworks.filesctrl.crypt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** AES-SIV against the test vectors in RFC 5297, appendix A. */
class AesSivTest {
    private fun hex(s: String): ByteArray {
        val t = s.filter { !it.isWhitespace() }
        return ByteArray(t.length / 2) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    @Test fun rfc5297_a1_deterministic() {
        val key = hex("fffefdfc fbfaf9f8 f7f6f5f4 f3f2f1f0 f0f1f2f3 f4f5f6f7 f8f9fafb fcfdfeff")
        val ad = hex("10111213 14151617 18191a1b 1c1d1e1f 20212223 24252627")
        val pt = hex("11223344 55667788 99aabbcc ddee")
        val expected = hex("85632d07 c6e8f37f 950acd32 0a2ecc93 40c02b96 90c4dc04 daef7f6a fe5c")
        val mac = key.copyOfRange(0, 16)
        val ctr = key.copyOfRange(16, 32)
        assertArrayEquals(expected, AesSiv.encrypt(mac, ctr, pt, ad))
        assertArrayEquals(pt, AesSiv.decrypt(mac, ctr, expected, ad))
    }

    @Test fun rfc5297_a2_nonceBased() {
        val key = hex("7f7e7d7c 7b7a7978 77767574 73727170 40414243 44454647 48494a4b 4c4d4e4f")
        val ad1 = hex("00112233 44556677 8899aabb ccddeeff deaddada deaddada ffeeddcc bbaa9988 77665544 33221100")
        val ad2 = hex("10203040 50607080 90a0")
        val nonce = hex("09f91102 9d74e35b d84156c5 635688c0")
        val pt = hex("74686973 20697320 736f6d65 20706c61 696e7465 78742074 6f20656e 63727970 74207573 696e6720 5349562d 414553")
        val expected = hex(
            "7bdb6e3b 432667eb 06f4d14b ff2fbd0f cb900f2f ddbe4043 26601965 c889bf17 " +
                "dba77ceb 094fa663 b7a3f748 ba8af829 ea64ad54 4a272e9c 485b62a3 fd5c0d",
        )
        val mac = key.copyOfRange(0, 16)
        val ctr = key.copyOfRange(16, 32)
        // The nonce is the last associated-data component before the plaintext.
        assertArrayEquals(expected, AesSiv.encrypt(mac, ctr, pt, ad1, ad2, nonce))
        assertArrayEquals(pt, AesSiv.decrypt(mac, ctr, expected, ad1, ad2, nonce))
    }

    @Test fun aes256KeysRoundTripAllLengths() {
        val mac = ByteArray(32) { it.toByte() }
        val ctr = ByteArray(32) { (100 + it).toByte() }
        for (len in 0..70) {
            val pt = ByteArray(len) { (it * 7).toByte() }
            val ct = AesSiv.encrypt(mac, ctr, pt, "dir".toByteArray())
            assertEquals(len + 16, ct.size)
            assertArrayEquals(pt, AesSiv.decrypt(mac, ctr, ct, "dir".toByteArray()))
        }
    }

    @Test fun tamperingIsDetected() {
        val mac = ByteArray(32) { 1 }
        val ctr = ByteArray(32) { 2 }
        val ct = AesSiv.encrypt(mac, ctr, "hello world".toByteArray(), "a".toByteArray())
        for (i in ct.indices) {
            val bad = ct.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            try { AesSiv.decrypt(mac, ctr, bad, "a".toByteArray()); fail("byte $i") } catch (_: AuthenticationFailedException) {}
        }
        // Other associated data (another folder) doesn't decrypt either.
        try { AesSiv.decrypt(mac, ctr, ct, "b".toByteArray()); fail("ad") } catch (_: AuthenticationFailedException) {}
        try { AesSiv.decrypt(mac, ctr, ByteArray(5), "a".toByteArray()); fail("short") } catch (_: AuthenticationFailedException) {}
    }

    @Test fun dbl() {
        // Doubling with and without the carry.
        assertArrayEquals(hex("00000000000000000000000000000002"), AesSiv.dbl(hex("00000000000000000000000000000001")))
        assertArrayEquals(hex("00000000000000000000000000000087"), AesSiv.dbl(hex("80000000000000000000000000000000")))
    }
}
