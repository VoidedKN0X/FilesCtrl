package com.vknxworks.filesctrl.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractGuardTest {
    @Test fun nothingIsCheckedBelowTheLimit() {
        var asked = 0
        val g = ExtractGuard({ asked++; 0L }, limit = 1000, every = 100, reserve = 50)
        // 999 bytes with no free space at all: still not this guard's business.
        repeat(999) { assertFalse(g.wrote(1)) }
        assertEquals(0, asked)
        assertTrue(g.wrote(1))
        assertEquals(1, asked)
    }

    @Test fun plentyOfSpaceNeverStops() {
        val g = ExtractGuard({ Long.MAX_VALUE }, limit = 1000, every = 100, reserve = 50)
        repeat(100_000) { assertFalse(g.wrote(64)) }
    }

    @Test fun stopsOnceSpaceRunsOutAfterTheLimit() {
        // A disk with 5000 bytes: every byte written is one byte less free.
        var free = 5000L
        val g = ExtractGuard({ free }, limit = 1000, every = 100, reserve = 50)
        var written = 0L
        var stopped = false
        while (written < 10_000) {
            free -= 10; written += 10
            if (g.wrote(10)) { stopped = true; break }
        }
        assertTrue(stopped)
        // Stopped before the disk was full, and not before the reserve was reached.
        assertTrue(free in 0 until 50)
        assertTrue(written > 1000)
    }

    @Test fun looksAgainOnlyEveryStep() {
        var asked = 0
        val g = ExtractGuard({ asked++; 1_000_000L }, limit = 1000, every = 100, reserve = 50)
        repeat(2000) { g.wrote(1) }
        // At 1000, 1100, ... 2000.
        assertEquals(11, asked)
    }

    @Test fun unknownFreeSpaceNeverStops() {
        val g = ExtractGuard({ null }, limit = 10, every = 10, reserve = 50)
        repeat(1000) { assertFalse(g.wrote(7)) }
    }

    @Test fun defaults() {
        assertEquals(1024L * 1024 * 1024, ExtractGuard.LIMIT)
        assertEquals(8L * 1024 * 1024, ExtractGuard.EVERY)
        assertEquals(16L * 1024 * 1024, ExtractGuard.RESERVE)
        // A big chunk straight past the limit is checked at once.
        var free = 1L
        val g = ExtractGuard({ free })
        assertFalse(g.wrote(1 shl 20))
        free = Long.MAX_VALUE
        var stopped = false
        repeat(1024) { if (g.wrote(1 shl 20)) stopped = true }
        assertFalse(stopped)
        free = 0
        repeat(8) { if (g.wrote(1 shl 20)) stopped = true }
        assertTrue(stopped)
    }
}
