package com.vknxworks.filesctrl.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchRenameTest {
    @Test fun findReplaceKeepsExtension() {
        val r = RenameRules(find = "img", replace = "Holiday")
        assertEquals("Holiday_0001.jpg", BatchRename.newName("IMG_0001.jpg", false, 0, r))
        assertEquals("IMG_0001.jpg", BatchRename.newName("IMG_0001.jpg", false, 0, r.copy(matchCase = true)))
        // The extension itself is never touched.
        assertEquals("photo.jpg", BatchRename.newName("photo.jpg", false, 0, RenameRules(find = "jpg", replace = "png")))
    }

    @Test fun templateAndCounter() {
        val r = RenameRules(template = "Holiday ###", start = 7)
        assertEquals("Holiday 007.jpg", BatchRename.newName("a.jpg", false, 0, r))
        assertEquals("Holiday 012.jpg", BatchRename.newName("b.jpg", false, 5, r))
        assertEquals("a - 1.txt", BatchRename.newName("a.txt", false, 0, RenameRules(template = "{name} - #")))
        assertEquals("1000", BatchRename.newName("x", false, 999, RenameRules(template = "##")))
    }

    @Test fun caseAndFolders() {
        assertEquals("photo.JPG", BatchRename.newName("Photo.JPG", false, 0, RenameRules(case = RenameCase.Lower)))
        assertEquals("MY.FOLDER", BatchRename.newName("my.folder", true, 0, RenameRules(case = RenameCase.Upper)))
        assertEquals(".bashrc", BatchRename.newName(".bashrc", false, 0, RenameRules()))
    }

    @Test fun planFindsProblems() {
        val names = listOf("a.jpg", "b.jpg", "c.jpg")
        val dirs = listOf(false, false, false)
        val dup = BatchRename.plan(names, dirs, RenameRules(template = "x"), emptySet()) { true }
        assertEquals(BatchRename.Problem.Duplicate, dup[0].second)
        val exists = BatchRename.plan(names, dirs, RenameRules(template = "n#"), setOf("n2.jpg")) { true }
        assertNull(exists[0].second)
        assertEquals(BatchRename.Problem.Exists, exists[1].second)
        // Taking a name another renamed item has now is allowed ("1" -> "2", "2" -> "3").
        val shift = BatchRename.plan(listOf("1", "2"), listOf(false, false), RenameRules(template = "#", start = 2), emptySet()) { true }
        assertNull(shift[0].second)
        assertNull(shift[1].second)
        // An item that keeps its name still blocks it.
        val keep = BatchRename.plan(listOf("a", "b"), listOf(false, false), RenameRules(find = "a", replace = "b"), emptySet()) { true }
        assertEquals(BatchRename.Problem.Duplicate, keep[0].second)
        val invalid = BatchRename.plan(listOf("a"), listOf(false), RenameRules(template = "x/y"), emptySet()) { !it.contains('/') }
        assertEquals(BatchRename.Problem.Invalid, invalid[0].second)
    }
}
