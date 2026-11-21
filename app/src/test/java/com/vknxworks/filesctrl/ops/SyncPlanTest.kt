package com.vknxworks.filesctrl.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {
    private fun f(size: Long, t: Long = 1_000_000) = SyncMeta(false, size, t)
    private val dir = SyncMeta(true, 0, 0)

    @Test fun copiesNewAndChanged() {
        val src = mapOf("a.txt" to f(1), "b.txt" to f(2), "c.txt" to f(3, 2_000_000), "sub" to dir, "sub/d.txt" to f(4), "same.txt" to f(5))
        val dst = mapOf("b.txt" to f(9), "c.txt" to f(3), "same.txt" to f(5, 1_001_000), "old.txt" to f(1))
        val p = SyncPlan.of(src, dst, mirror = false)
        assertEquals(listOf("sub"), p.create)
        assertEquals(setOf("a.txt", "sub/d.txt"), p.copy.toSet())
        assertEquals(setOf("b.txt", "c.txt"), p.update.toSet())
        assertTrue(p.delete.isEmpty())
        assertEquals(1L + 4 + 2 + 3, p.bytes)
    }

    @Test fun newerDestinationWithSameSizeIsLeftAlone() {
        val p = SyncPlan.of(mapOf("a" to f(1, 1_000)), mapOf("a" to f(1, 9_000_000)), mirror = false)
        assertTrue(p.isEmpty)
        // Within the 2 s clock slack nothing changes.
        assertTrue(SyncPlan.of(mapOf("a" to f(1, 3_000)), mapOf("a" to f(1, 1_500)), mirror = false).isEmpty)
    }

    @Test fun mirrorDeletesExtrasDeepestFirstAndOnlyTopFolders() {
        val src = mapOf("keep.txt" to f(1))
        val dst = mapOf("keep.txt" to f(1), "x" to dir, "x/y" to dir, "x/y/z.txt" to f(1), "w.txt" to f(1))
        val p = SyncPlan.of(src, dst, mirror = true)
        assertEquals(setOf("x", "w.txt"), p.delete.toSet())
        assertTrue(SyncPlan.of(src, dst, mirror = false).delete.isEmpty())
    }

    @Test fun conflictsReplacedOnlyWithMirror() {
        val src = mapOf("n" to f(3))
        val dst = mapOf("n" to dir, "n/inner.txt" to f(1))
        val plain = SyncPlan.of(src, dst, mirror = false)
        assertEquals(listOf("n"), plain.conflicts)
        assertTrue(plain.copy.isEmpty() && plain.delete.isEmpty())
        val mirror = SyncPlan.of(src, dst, mirror = true)
        assertEquals(listOf("n"), mirror.copy)
        assertEquals(listOf("n"), mirror.delete)
    }
}
