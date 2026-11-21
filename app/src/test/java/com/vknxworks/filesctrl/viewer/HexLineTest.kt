package com.vknxworks.filesctrl.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexLineTest {
    @Test fun formatsARow() {
        val data = "Hello, world!\n\u0000AB".toByteArray(Charsets.ISO_8859_1)
        val line = hexLine(0, data, 0, data.size.toLong())
        assertEquals("00000000  48 65 6C 6C 6F 2C 20 77  6F 72 6C 64 21 0A 00 41  Hello, world!..A", line)
    }

    @Test fun shortLastRow() {
        val data = ByteArray(20) { 0x41 }
        val line = hexLine(16, data, 16, 20)
        assertEquals("00000010  41 41 41 41" + " ".repeat(39) + "AAAA", line)
    }

    @Test fun offsets() {
        assertEquals(4096L, parseOffset("4096"))
        assertEquals(4096L, parseOffset("0x1000"))
        assertEquals(31L, parseOffset("1f"))
        assertNull(parseOffset("zz"))
        assertNull(parseOffset(""))
    }
}
