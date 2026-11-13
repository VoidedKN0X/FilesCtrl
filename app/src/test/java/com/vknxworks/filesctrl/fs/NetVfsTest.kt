package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import com.vknxworks.filesctrl.data.Proto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.random.Random

/**
 * Runs the same file operations against real servers of every protocol. CI starts the servers in Docker and
 * sets TEST_NET=1; without it these tests are skipped.
 */
class NetVfsTest {
    private fun enabled() = assumeTrue("Set TEST_NET=1 with test servers running", System.getenv("TEST_NET") == "1")

    private fun server(type: Proto, port: Int, path: String = "", user: String = "test", pass: String = "test") =
        NetServer(type = type, host = "127.0.0.1", port = port, path = path, username = user, password = pass, timeoutSec = 15)

    @Test fun smb() { enabled(); exercise(server(Proto.SMB, 445, "share")) }

    @Test fun smbListsShares() {
        enabled()
        val v = RemoteVfs.create(server(Proto.SMB, 445, ""))
        try {
            val shares = v.list("/").map { it.name }
            println("SMB shares: $shares")
            assertTrue("share missing from $shares", "share" in shares)
            // Browsing into a share from the share list works too.
            v.list("/share")
        } finally { v.close() }
    }

    @Test fun sftp() {
        enabled()
        // The first connection must stop and ask before trusting the host key.
        val first = RemoteVfs.create(server(Proto.SFTP, 2222, "/upload"))
        val fp = try { first.list("/"); fail("connected to an unknown server without asking"); "" } catch (e: HostKeyUnknownException) {
            e.fingerprint
        } finally { first.close() }
        assertTrue("not a SHA-256 fingerprint: $fp", fp.startsWith("SHA256:"))
        exercise(server(Proto.SFTP, 2222, "/upload").copy(hostKey = fp))
        // A different remembered key must be refused.
        val wrong = server(Proto.SFTP, 2222, "/upload").copy(hostKey = "00:11:22:33")
        val v = RemoteVfs.create(wrong)
        try { v.list("/"); fail("connected despite a changed host key") } catch (e: Exception) {
            assertTrue("unexpected error: $e", e.message.orEmpty().contains("identity changed"))
        } finally { v.close() }
    }

    @Test fun ftp() { enabled(); exercise(server(Proto.FTP, 21, "")) }

    /** The test server speaks plain HTTP: accepted for its address, as after the "Use HTTP" warning. */
    @Test fun webdav() { enabled(); exercise(server(Proto.WEBDAV, 8080, "/").copy(httpAcceptedHost = "127.0.0.1")) }

    /** Plain HTTP that wasn't accepted (or was accepted for another address) sends nothing. */
    @Test fun webdavRefusesUnacceptedHttp() {
        enabled()
        for (s in listOf(server(Proto.WEBDAV, 8080, "/"), server(Proto.WEBDAV, 8080, "/").copy(httpAcceptedHost = "nas.local"))) {
            val v = RemoteVfs.create(s) {}
            try {
                v.list("/")
                fail("[WEBDAV] listed over HTTP without acceptance")
            } catch (e: java.io.IOException) {
                // Expected: refused before connecting.
            } finally { v.close() }
        }
    }

    private fun exercise(s: NetServer, learned: (NetServer) -> Unit = {}) {
        val v = RemoteVfs.create(s, learned)
        val tag = "[${s.type}]"
        try {
            println("$tag root: ${v.list("/").map { it.name }}")
            val dir = "/fc_test_" + Random.nextInt(100000, 999999)
            v.mkdir(dir)
            assertTrue("$tag folder not created", v.stat(dir).isDir)
            try { v.mkdir(dir); fail("$tag mkdir on existing folder succeeded") } catch (_: Exception) {}

            // Write 3 MB in odd-sized chunks.
            val data = ByteArray(3 * 1024 * 1024 + 123).also { Random(7).nextBytes(it) }
            val big = "$dir/big file ü.bin"
            v.openWrite(big).use { out ->
                var o = 0
                while (o < data.size) { val n = minOf(70_001, data.size - o); out.write(data, o, n); o += n }
            }
            val st = v.stat(big)
            assertEquals("$tag size", data.size.toLong(), st.size)
            assertFalse("$tag file reported as folder", st.isDir)
            assertTrue("$tag modified time missing", st.modified > 0)

            val listed = v.list(dir)
            assertEquals("$tag listing ${listed.map { it.name }}", listOf("big file ü.bin"), listed.map { it.name })
            assertEquals("$tag listed size", data.size.toLong(), listed[0].size)

            // Whole read, ranged read, random reads.
            assertArrayEquals("$tag read back", data, v.openRead(big).use { it.readBytes() })
            val off = 1_000_003L
            val tail = v.openRead(big, off).use { it.readBytes() }
            assertArrayEquals("$tag ranged read", data.copyOfRange(off.toInt(), data.size), tail)
            val rf = v.openRandom(big, false, false) ?: SeekingReader(v, big, st.size)
            rf.use { r ->
                assertEquals("$tag random size", data.size.toLong(), r.size())
                for (o in listOf(2_000_000L, 5L, 3_145_000L)) {
                    val buf = ByteArray(4096)
                    val n = r.read(o, buf, buf.size)
                    val want = minOf(4096, data.size - o.toInt())
                    assertEquals("$tag random read length at $o", want, n)
                    assertArrayEquals("$tag random read at $o", data.copyOfRange(o.toInt(), o.toInt() + n), buf.copyOf(n))
                }
            }

            // Edit in place: native random writes, or the edit-a-copy fallback.
            val tmpDir = Files.createTempDirectory("fc").toFile()
            val small = "$dir/note.txt"
            v.createFile(small)
            assertTrue("$tag createFile", v.exists(small))
            val w = v.openRandom(small, true, false) ?: TempCopyFile(v, small, tmpDir, false)
            w.use { it.write(0, "hello world".toByteArray(), 11); it.write(6, "WORLD".toByteArray(), 5) }
            assertEquals("$tag edited content", "hello WORLD", v.openRead(small).use { String(it.readBytes()) })

            // Rename, then delete everything.
            val renamed = "$dir/renamed.bin"
            v.rename(big, renamed)
            assertEquals("$tag renamed size", data.size.toLong(), v.stat(renamed).size)
            try { v.stat(big); fail("$tag old name still exists") } catch (_: FileNotFoundException) {}
            try { v.rename(small, renamed); fail("$tag rename overwrote an existing file") } catch (_: Exception) {}

            println("$tag space: ${v.space("/")}")
            v.delete(renamed, false)
            v.delete(small, false)
            v.delete(dir, true)
            try { v.stat(dir); fail("$tag folder still exists") } catch (_: FileNotFoundException) {}
            println("$tag all checks passed")
        } finally {
            v.close()
        }
    }
}
