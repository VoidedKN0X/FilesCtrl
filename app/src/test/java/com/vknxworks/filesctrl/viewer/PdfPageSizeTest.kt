package com.vknxworks.filesctrl.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageSizeTest {
    @Test fun a4AtScreenWidth() {
        val (w, h) = PdfPageSize.of(1080, 595, 842)
        assertEquals(1080, w)
        assertEquals(1528, h)
    }

    @Test fun tallPagesAreCapped() {
        // A long receipt: 1 : 20.
        val (w, h) = PdfPageSize.of(1440, 200, 4000)
        assertTrue(w.toLong() * h <= PdfPageSize.MAX_PIXELS)
        assertTrue(w in 600..640)
        // Absurdly long: still within the cap and at least 1 pixel.
        val (w2, h2) = PdfPageSize.of(1440, 1, 10_000_000)
        assertTrue(w2 >= 1 && h2 >= 1 && w2.toLong() * h2 <= PdfPageSize.MAX_PIXELS)
    }

    @Test fun oddInput() {
        val (w, h) = PdfPageSize.of(0, 0, 0)
        assertTrue(w >= 1 && h >= 1)
        val (w3, h3) = PdfPageSize.of(10_000, 100, 100)
        assertTrue(w3 <= 4096 && w3.toLong() * h3 <= PdfPageSize.MAX_PIXELS)
    }
}
