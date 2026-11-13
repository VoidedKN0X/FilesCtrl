package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.crypt.Cryptomator
import com.vknxworks.filesctrl.crypt.Masterkey
import com.vknxworks.filesctrl.crypt.VaultCryptor
import com.vknxworks.filesctrl.crypt.VaultDataException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import kotlin.random.Random

/**
 * Cryptomator vaults: a reference vault is written from the specification by [ReferenceVault], then read, changed
 * and written through [CryptoVfs]; whatever FilesCtrl writes is read back by the reference reader.
 */
class CryptoVfsTest {
    private val tmp: File = Files.createTempDirectory("vault").toFile()
    private val big = ByteArray(100_000).also { Random(7).nextBytes(it) }
    private val longName = "a very long file name that is long enough to need shortening because its encrypted form passes the threshold " +
        "and keeps on going for quite a while longer still.txt"

    private fun reference(): ReferenceVault = ReferenceVault(File(tmp, "Vault")).apply {
        create()
        addFile("hello.txt", "", "hello vault".toByteArray())
        addFile("empty.bin", "", ByteArray(0))
        val docs = addDir("Docs ü", "")
        addFile("big.bin", docs, big)
        addFile(longName, docs, "long".toByteArray())
        addDir("Sub", docs)
    }

    private fun open(v: ReferenceVault, unlock: Boolean = true): CryptoVfs {
        val vfs = DirVfs(tmp).let { d -> CryptoVfs("crypt:test", { d }, "/" + v.dir.name) }
        if (unlock) vfs.unlock(v.password.toCharArray())
        return vfs
    }

    @Test fun readsReferenceVault() {
        val ref = reference()
        val v = open(ref)
        assertEquals(setOf("hello.txt", "empty.bin", "Docs ü"), v.list("/").map { it.name }.toSet())
        assertTrue(v.stat("/Docs ü").isDir)
        assertEquals(11L, v.stat("/hello.txt").size)
        assertEquals(0L, v.stat("/empty.bin").size)
        assertEquals(big.size.toLong(), v.stat("/Docs ü/big.bin").size)
        assertEquals("hello vault", v.openRead("/hello.txt").use { String(it.readBytes()) })
        assertEquals(0, v.openRead("/empty.bin").use { it.readBytes().size })
        assertArrayEquals(big, v.openRead("/Docs ü/big.bin").use { it.readBytes() })
        // The shortened (.c9s) name lists and reads like any other.
        assertEquals(setOf("big.bin", longName, "Sub"), v.list("/Docs ü").map { it.name }.toSet())
        assertEquals("long", v.openRead("/Docs ü/$longName").use { String(it.readBytes()) })
        assertTrue(v.stat("/Docs ü/Sub").isDir)
        assertEquals(emptyList<FileEntry>(), v.list("/Docs ü/Sub"))
        // Reading from an offset, across chunk borders.
        for (off in listOf(1L, 32767L, 32768L, 32769L, 70000L, 99999L)) {
            assertArrayEquals("offset $off", big.copyOfRange(off.toInt(), big.size), v.openRead("/Docs ü/big.bin", off).use { it.readBytes() })
        }
        assertEquals(0, v.openRead("/Docs ü/big.bin", big.size.toLong()).use { it.readBytes().size })
    }

    @Test fun writesWhatTheReferenceReads() {
        val ref = reference()
        val v = open(ref)
        val data = ByteArray(32768 * 2).also { Random(1).nextBytes(it) } // Exactly two chunks: no empty third one.
        v.openWrite("/new.bin").use { it.write(data) }
        val stored = File(ref.dirFor(""), ref.encName("new.bin", ""))
        assertEquals(Cryptomator.ciphertextSize(data.size.toLong()), stored.length())
        assertArrayEquals(data, ref.decryptContent(stored.readBytes()))
        assertEquals(data.size.toLong(), v.stat("/new.bin").size)

        v.mkdir("/Made")
        val madeNode = File(ref.dirFor(""), ref.encName("Made", ""))
        val madeId = File(madeNode, "dir.c9r").readText()
        assertTrue(ref.dirFor(madeId).isDirectory)
        // The directory ID backup inside the new folder decrypts to its ID.
        assertEquals(madeId, String(ref.decryptContent(File(ref.dirFor(madeId), "dirid.c9r").readBytes())))
        v.createFile("/Made/x.txt")
        assertEquals(setOf("x.txt"), v.list("/Made").map { it.name }.toSet())
        assertEquals(0, ref.decryptContent(File(ref.dirFor(madeId), ref.encName("x.txt", madeId)).readBytes()).size)

        // A long name written by FilesCtrl: .c9s folder with name.c9s and contents.c9r.
        v.openWrite("/Made/$longName").use { it.write("x".toByteArray()) }
        val c9s = ref.dirFor(madeId).listFiles()!!.single { it.name.endsWith(".c9s") }
        assertEquals(ref.encName(longName, madeId), File(c9s, "name.c9s").readText())
        assertEquals("x", String(ref.decryptContent(File(c9s, "contents.c9r").readBytes())))
        assertEquals(setOf("x.txt", longName), v.list("/Made").map { it.name }.toSet())
    }

    @Test fun renameMoveAndDelete() {
        val ref = reference()
        val v = open(ref)
        v.rename("/hello.txt", "/hi.txt")
        assertEquals("hello vault", v.openRead("/hi.txt").use { String(it.readBytes()) })
        // Into another folder: re-encrypted with that folder's ID.
        v.rename("/hi.txt", "/Docs ü/Sub/hi.txt")
        assertEquals("hello vault", v.openRead("/Docs ü/Sub/hi.txt").use { String(it.readBytes()) })
        // Short -> long -> short, for a file and for a folder.
        v.rename("/Docs ü/Sub/hi.txt", "/Docs ü/Sub/$longName")
        assertEquals("hello vault", v.openRead("/Docs ü/Sub/$longName").use { String(it.readBytes()) })
        v.rename("/Docs ü/Sub/$longName", "/back.txt")
        assertEquals("hello vault", v.openRead("/back.txt").use { String(it.readBytes()) })
        v.rename("/Docs ü/Sub", "/Docs ü/$longName-dir")
        assertTrue(v.stat("/Docs ü/$longName-dir").isDir)
        v.createFile("/Docs ü/$longName-dir/inside")
        v.rename("/Docs ü/$longName-dir", "/Moved")
        assertEquals(listOf("inside"), v.list("/Moved").map { it.name })
        // Renaming a folder keeps everything inside it.
        v.rename("/Docs ü", "/Docs")
        assertEquals(setOf("big.bin", longName), v.list("/Docs").map { it.name }.toSet())
        try { v.rename("/back.txt", "/empty.bin"); fail("existing target") } catch (_: IOException) {}

        try { v.delete("/Moved", true); fail("not empty") } catch (_: IOException) {}
        v.delete("/Moved/inside", false)
        v.delete("/Moved", true)
        assertFalse(v.exists("/Moved"))
        v.delete("/Docs/$longName", false)
        assertEquals(setOf("big.bin"), v.list("/Docs").map { it.name }.toSet())
        // Nothing is left of the deleted shortened node.
        assertTrue(ref.dir.walk().none { it.name.endsWith(".c9s") })
    }

    @Test fun wrongPasswordAndLocking() {
        val ref = reference()
        val v = open(ref, unlock = false)
        try { v.list("/"); fail("locked") } catch (_: VaultLockedException) {}
        try { v.unlock("nope".toCharArray()); fail("wrong") } catch (e: VaultPasswordException) { assertTrue(e.wrong) }
        v.unlock(ref.password.toCharArray())
        val s = v.openRead("/Docs ü/big.bin")
        s.read(ByteArray(10))
        v.lock()
        assertFalse(v.unlocked)
        try { v.list("/"); fail("locked again") } catch (_: VaultLockedException) {}
        // An open stream stops at the next chunk.
        try { s.readBytes(); fail("stream after lock") } catch (_: IOException) {}
    }

    @Test fun refusesOtherFormatsAndDamagedFiles() {
        for ((format, combo) in listOf(7 to "SIV_GCM", 8 to "SIV_CTRMAC")) {
            val ref = ReferenceVault(File(tmp, "V$format$combo")).apply { create(format, combo) }
            try { open(ref); fail("$format $combo") } catch (e: IOException) { assertFalse(e is VaultPasswordException) }
        }
        val badMac = ReferenceVault(File(tmp, "BadMac")).apply { create(tamperVersionMac = true) }
        try { open(badMac); fail("version mac") } catch (e: IOException) { assertFalse(e is VaultPasswordException) }
        // A changed signature on vault.cryptomator.
        val ref = reference()
        val f = File(ref.dir, "vault.cryptomator")
        f.writeText(f.readText().dropLast(2) + "AA")
        try { open(ref); fail("signature") } catch (e: IOException) { assertFalse(e is VaultPasswordException) }
    }

    @Test fun tamperedContentIsDetected() {
        val ref = reference()
        val v = open(ref)
        val docsId = File(File(ref.dirFor(""), ref.encName("Docs ü", "")), "dir.c9r").readText()
        val f = File(ref.dirFor(docsId), ref.encName("big.bin", docsId))
        val b = f.readBytes()
        b[68 + 32796 + 20] = (b[68 + 32796 + 20].toInt() xor 1).toByte()
        f.writeBytes(b)
        try { v.openRead("/Docs ü/big.bin").use { it.readBytes() }; fail("tampered") } catch (_: VaultDataException) {}
        // Two chunks swapped are caught too (the chunk number is authenticated).
        val ok = ref.encryptContent(big)
        val swapped = ok.copyOf()
        System.arraycopy(ok, 68, swapped, 68 + 32796, 32796)
        System.arraycopy(ok, 68 + 32796, swapped, 68, 32796)
        f.writeBytes(swapped)
        try { v.openRead("/Docs ü/big.bin").use { it.readBytes() }; fail("swapped") } catch (_: VaultDataException) {}
    }

    @Test fun unsafeDecryptedNamesAreSkipped() {
        val ref = reference()
        // Names that decrypt fine but would climb out of the folder or aren't one path segment.
        for (bad in listOf("..", ".", "a/b", "x\u0000y")) ref.addFile(bad, "", "evil".toByteArray())
        // Garbage that isn't an encrypted name at all.
        File(ref.dirFor(""), "garbage.c9r").writeText("x")
        val v = open(ref)
        assertEquals(setOf("hello.txt", "empty.bin", "Docs ü"), v.list("/").map { it.name }.toSet())
        assertTrue(v.skipped >= 5)
        try { v.stat("/../x"); fail() } catch (_: FileNotFoundException) {}
    }

    @Test fun namesAndSizes() {
        val c = VaultCryptor(Masterkey(ByteArray(32) { 1 }, ByteArray(32) { 2 }))
        val enc = c.encryptName("Ünïcode name.txt", "dir-id")
        assertTrue(enc.endsWith(".c9r"))
        assertEquals("Ünïcode name.txt", c.decryptName(enc, "dir-id"))
        assertNull(c.decryptName(enc, "other-dir"))
        // NFD input is stored as NFC, like Cryptomator.
        assertEquals(enc, c.encryptName(java.text.Normalizer.normalize("Ünïcode name.txt", java.text.Normalizer.Form.NFD), "dir-id"))
        assertEquals(32, c.hashDirId("").length)
        assertTrue(c.dirPath("").matches(Regex("d/[A-Z2-7]{2}/[A-Z2-7]{30}")))
        assertEquals("MZXW6YTBOI======", Cryptomator.base32("foobar".toByteArray()))
        assertEquals("MZXW6===", Cryptomator.base32("foo".toByteArray()))
        for (len in listOf(0L, 1L, 32767L, 32768L, 32769L, 65536L, 1_000_000L)) {
            assertEquals(len, Cryptomator.cleartextSize(Cryptomator.ciphertextSize(len)))
            val data = ByteArray(len.toInt()).also { Random(len.toInt()).nextBytes(it) }
            val ct = c.encryptFile(data)
            assertEquals(Cryptomator.ciphertextSize(len), ct.size.toLong())
            assertArrayEquals(data, c.decryptFile(ct))
        }
        assertEquals(-1L, Cryptomator.cleartextSize(10))
        assertEquals(-1L, Cryptomator.cleartextSize(68 + 5))
    }
}
