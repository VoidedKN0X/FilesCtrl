package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.ops.ArchiveFormat
import com.vknxworks.filesctrl.ops.ArchiveSink
import com.vknxworks.filesctrl.ops.SevenZSink
import com.vknxworks.filesctrl.ops.TarSink
import com.vknxworks.filesctrl.ops.Zip4jSink
import com.vknxworks.filesctrl.ops.ZipSink
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** Builds archives in every supported format and reads them back through [ArchiveVfs]. Runs on the JVM. */
class ArchiveTest {
    private val dir: File = Files.createTempDirectory("arc").toFile()
    private val big = ByteArray(1_500_000).also { Random(3).nextBytes(it) }
    private val text = "héllo wörld\n".repeat(200).toByteArray()

    private class FileSource(private val f: File) : ArchiveSource {
        override val name: String = f.name
        override val size: Long get() = f.length()
        override fun openStream() = f.inputStream()
        override fun localFile() = f
    }

    private fun vfs(f: File, password: String? = null) = ArchiveVfs("arc:test", FileSource(f)).also { v -> password?.let { v.password = it.toCharArray() } }

    /** The same small tree written by one of FilesCtrl's own writers. */
    private fun write(sink: ArchiveSink) = sink.use { s ->
        s.dir("docs/", 1_600_000_000_000)
        s.file("docs/notes ü.txt", text.size.toLong(), 1_600_000_000_000, ByteArrayInputStream(text)) {}
        s.dir("docs/empty/", 0)
        s.file("big.bin", big.size.toLong(), 0, ByteArrayInputStream(big)) {}
    }

    private fun checkTree(v: ArchiveVfs, tag: String) {
        assertEquals("$tag top", setOf("docs", "big.bin"), v.list("/").map { it.name }.toSet())
        assertEquals("$tag docs", setOf("notes ü.txt", "empty"), v.list("/docs").map { it.name }.toSet())
        assertTrue("$tag empty dir", v.stat("/docs/empty").isDir)
        assertEquals("$tag size", big.size.toLong(), v.stat("/big.bin").size)
        assertArrayEquals("$tag big", big, v.openRead("/big.bin").use { it.readBytes() })
        assertArrayEquals("$tag text", text, v.openRead("/docs/notes ü.txt").use { it.readBytes() })
        // Reading from an offset (used when another app seeks in a file).
        assertArrayEquals("$tag offset", big.copyOfRange(1_000_000, big.size), v.openRead("/big.bin", 1_000_000).use { it.readBytes() })
        try { v.stat("/nope"); fail("$tag missing file found") } catch (_: FileNotFoundException) {}
        // One pass over everything, as "Extract here" does.
        val seen = HashMap<String, Int>()
        v.forEachEntry { e, s -> seen[e.path] = s?.readBytes()?.size ?: -1 }
        assertEquals("$tag pass big", big.size, seen["/big.bin"])
        assertEquals("$tag pass text", text.size, seen["/docs/notes ü.txt"])
        assertEquals("$tag pass dir", -1, seen["/docs/empty"])
        try { v.mkdir("/x"); fail("$tag archive writable") } catch (_: java.io.IOException) {}
        println("$tag: ${v.formatLabel} OK")
    }

    @Test fun zip() {
        val f = File(dir, "a.zip"); write(ZipSink(f.outputStream())); checkTree(vfs(f), "zip")
    }

    @Test fun zipAes() {
        val f = File(dir, "aes.zip"); write(Zip4jSink(f.outputStream(), "s3cret".toCharArray()))
        try { vfs(f).list("/"); fail("encrypted zip listed without a password") } catch (e: ArchivePasswordException) { assertFalse(e.wrong) }
        try { vfs(f, "nope").openRead("/big.bin").use { it.readBytes() }; fail("wrong password accepted") } catch (e: ArchivePasswordException) { assertTrue(e.wrong) }
        checkTree(vfs(f, "s3cret"), "zip AES")
    }

    @Test fun sevenZ() {
        val f = File(dir, "a.7z")
        SevenZOutputFile(f, null).use { z -> z.setContentCompression(SevenZMethod.LZMA2); write(SevenZSink(z)); z.finish() }
        checkTree(vfs(f), "7z")
    }

    @Test fun sevenZPassword() {
        val f = File(dir, "p.7z")
        SevenZOutputFile(f, "s3cret".toCharArray()).use { z -> z.setContentCompression(SevenZMethod.LZMA2); write(SevenZSink(z)); z.finish() }
        try { vfs(f).list("/"); fail("encrypted 7z listed without a password") } catch (e: ArchivePasswordException) { assertFalse(e.wrong) }
        try { vfs(f, "nope").let { it.list("/"); it.openRead("/big.bin").use { s -> s.readBytes() } }; fail("wrong 7z password accepted") } catch (e: ArchivePasswordException) { assertTrue(e.wrong) }
        checkTree(vfs(f, "s3cret"), "7z AES")
    }

    @Test fun tarFormats() {
        for (fmt in listOf(ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ)) {
            val f = File(dir, "a" + fmt.ext)
            write(TarSink(f.outputStream(), fmt))
            checkTree(vfs(f), fmt.label)
        }
        // bzip2 isn't offered when compressing, but is read.
        val f = File(dir, "a.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(f.outputStream())).use { t ->
            t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for ((n, d) in listOf("docs/notes ü.txt" to text, "big.bin" to big)) {
                t.putArchiveEntry(TarArchiveEntry(n).apply { size = d.size.toLong() }); t.write(d); t.closeArchiveEntry()
            }
            t.putArchiveEntry(TarArchiveEntry("docs/empty/")); t.closeArchiveEntry()
        }
        checkTree(vfs(f), "tar.bz2")
    }

    @Test fun misnamedArchiveIsDetectedByContent() {
        val f = File(dir, "really-a-zip.dat"); write(ZipSink(f.outputStream())); checkTree(vfs(f), "misnamed zip")
    }

    @Test fun singleCompressedFile() {
        val f = File(dir, "notes.txt.gz")
        GzipCompressorOutputStream(f.outputStream()).use { it.write(text) }
        val v = vfs(f)
        assertTrue(v.isSingleFile)
        assertEquals(listOf("notes.txt"), v.list("/").map { it.name })
        assertEquals(text.size.toLong(), v.stat("/notes.txt").size)
        assertArrayEquals(text, v.openRead("/notes.txt").use { it.readBytes() })
        assertEquals("notes.txt", v.baseName)
    }

    @Test fun unsafeNamesAreLeftOut() {
        val f = File(dir, "evil.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for (n in listOf("../escape.txt", "ok/../../up.txt", "/abs.txt", "good.txt", "./also-good.txt")) {
                z.putNextEntry(ZipEntry(n)); z.write("x".toByteArray()); z.closeEntry()
            }
        }
        val v = vfs(f)
        val names = v.list("/").map { it.name }.toSet()
        // "/abs.txt" is cleaned up to "abs.txt"; anything with ".." is refused.
        assertEquals(setOf("good.txt", "also-good.txt", "abs.txt"), names)
        assertEquals(2, v.skippedUnsafe)
        val paths = ArrayList<String>()
        v.forEachEntry { e, _ -> paths += e.path }
        assertTrue("escaping path extracted: $paths", paths.none { it.contains("..") })
    }

    @Test fun rar4() {
        val f = File(dir, "rar4.rar")
        javaClass.getResourceAsStream("/archives/rar4.rar")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        val v = vfs(f)
        assertEquals(setOf("FILE1.TXT", "FILE2.TXT"), v.list("/").map { it.name }.toSet())
        assertEquals(7, v.openRead("/FILE1.TXT").use { it.readBytes() }.size)
        var n = 0
        v.forEachEntry { _, s -> if (s != null) { assertEquals(7, s.readBytes().size); n++ } }
        assertEquals(2, n)
        println("rar4: ${v.formatLabel} OK")
    }

    @Test fun rar5IsReportedClearly() {
        val f = File(dir, "rar5.rar")
        javaClass.getResourceAsStream("/archives/rar5-solid.rar")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        try { vfs(f).list("/"); fail("RAR5 opened") } catch (e: java.io.IOException) {
            assertTrue(e.message, e.message.orEmpty().contains("RAR5"))
        }
    }

    @Test fun notAnArchive() {
        val f = File(dir, "plain.txt"); f.writeBytes(text)
        try { vfs(f).list("/"); fail("plain text opened as an archive") } catch (e: java.io.IOException) {
            assertTrue(e.message, e.message.orEmpty().contains("isn't an archive"))
        }
    }
}
