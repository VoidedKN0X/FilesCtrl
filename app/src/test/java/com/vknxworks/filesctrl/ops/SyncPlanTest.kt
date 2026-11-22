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

    @Test fun onlyConfirmedDeletesRun() {
        val dst = mapOf("keep.txt" to f(1), "old.txt" to f(1), "new-extra.txt" to f(1))
        // The dry run: only old.txt was going to be deleted (new-extra.txt was still in the source then).
        val confirmed = SyncPlan.of(mapOf("keep.txt" to f(1), "new-extra.txt" to f(1)), dst, mirror = true)
        assertEquals(listOf("old.txt"), confirmed.delete)
        // Now new-extra.txt is gone from the source too: it isn't deleted, because the user never saw it.
        val fresh = SyncPlan.of(mapOf("keep.txt" to f(1)), dst, mirror = true)
        val (run, skipped) = fresh.deletesAllowedBy(confirmed)
        assertEquals(listOf("old.txt"), run)
        assertEquals(listOf("new-extra.txt"), skipped)
    }

    @Test fun emptySourceDeletesNothing() {
        val dst = mapOf("a.txt" to f(1), "b.txt" to f(1))
        val confirmed = SyncPlan.of(mapOf("a.txt" to f(1), "b.txt" to f(1), "c.txt" to f(1)), dst, mirror = true)
        // The share isn't mounted now and lists as empty: even a confirmed delete list wouldn't be trusted.
        val fresh = SyncPlan.of(emptyMap(), dst, mirror = true)
        val (run, skipped) = fresh.deletesAllowedBy(confirmed)
        assertTrue(run.isEmpty())
        assertEquals(setOf("a.txt", "b.txt"), skipped.toSet())
        // A source that was already empty at the dry run (the user saw everything go) still mirrors.
        val confirmedEmpty = SyncPlan.of(emptyMap(), dst, mirror = true)
        assertEquals(setOf("a.txt", "b.txt"), fresh.deletesAllowedBy(confirmedEmpty).first.toSet())
    }
}
