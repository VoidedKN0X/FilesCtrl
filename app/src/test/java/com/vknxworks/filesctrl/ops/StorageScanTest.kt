package com.vknxworks.filesctrl.ops

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageScanTest {
    private fun file(name: String, size: Long) = ScanNode(name, "/d/$name", false, "local").also { it.size = size; it.files = 1 }

    @Test fun keepsOnlyTheBiggestFilesPerFolder() {
        val dir = ScanNode("d", "/d", true, "local")
        val files = (1..40).map { file("f$it", it.toLong()) }
        ScanTotals.keepBiggest(dir, files)
        assertEquals(ScanTotals.FILES_PER_FOLDER, dir.children.size)
        assertEquals(40L, dir.children.first().size)
        assertEquals(10, dir.otherFiles)
        assertEquals((1..10).sum().toLong(), dir.otherBytes)
    }

    @Test fun largestAndTypes() {
        val t = ScanTotals(keepLargest = 2)
        t.addFile(file("a.jpg", 5), "jpg")
        t.addFile(file("b.jpg", 7), "jpg")
        t.addFile(file("c", 9), "")
        assertEquals(listOf(9L, 7L), t.largestFiles().map { it.size })
        val types = t.types()
        assertEquals("jpg", types[0].first)
        assertEquals(12L, types[0].second)
        assertEquals(2, types[0].third)
        assertEquals("—", types[1].first)
    }
}
