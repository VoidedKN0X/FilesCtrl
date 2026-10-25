package com.vknxworks.filesctrl.ops

import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.FileEntry
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

/** Hashes FilesCtrl can work out for a file. All of them are computed in one read of the file. */
enum class HashAlgo(val label: String, val hexLength: Int) {
    MD5("MD5", 32), SHA1("SHA-1", 40), SHA256("SHA-256", 64), SHA512("SHA-512", 128), CRC32("CRC32", 8),
}

object Checksums {
    /**
     * Reads [e] once (from any file system, streamed) and returns every hash in lower-case hex. [onProgress]
     * gets the bytes read so far. Cancelling the coroutine stops the read.
     */
    suspend fun compute(e: FileEntry, onProgress: (Long) -> Unit): Map<HashAlgo, String> {
        val digests = mapOf(
            HashAlgo.MD5 to MessageDigest.getInstance("MD5"),
            HashAlgo.SHA1 to MessageDigest.getInstance("SHA-1"),
            HashAlgo.SHA256 to MessageDigest.getInstance("SHA-256"),
            HashAlgo.SHA512 to MessageDigest.getInstance("SHA-512"),
        )
        val crc = CRC32()
        val buf = ByteArray(1024 * 1024)
        var done = 0L
        var lastUi = 0L
        App.vfs.get(e.vfsId).openRead(e.path).use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                digests.values.forEach { it.update(buf, 0, n) }
                crc.update(buf, 0, n)
                done += n
                val now = System.nanoTime()
                if (now - lastUi > 100_000_000) { onProgress(done); lastUi = now }
            }
        }
        onProgress(done)
        val out = LinkedHashMap<HashAlgo, String>()
        for (a in HashAlgo.entries) {
            out[a] = if (a == HashAlgo.CRC32) "%08x".format(crc.value) else hex(digests.getValue(a).digest())
        }
        return out
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /**
     * Cleans up a pasted hash: spaces, colons and dashes removed, lower case. Also accepts a line from a
     * checksum file ("<hash>  file.iso") by keeping the first word.
     */
    fun normalize(pasted: String): String {
        val first = pasted.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return first.replace(":", "").replace("-", "").lowercase()
    }

    /** Which computed hash [pasted] matches, or null if none. */
    fun match(results: Map<HashAlgo, String>, pasted: String): HashAlgo? {
        val p = normalize(pasted)
        if (p.isEmpty()) return null
        return results.entries.firstOrNull { (_, v) -> v == p }?.key
    }

    /** The algorithm a pasted hash looks like (by its length), so a mismatch can say "this is a SHA-256". */
    fun guess(pasted: String): HashAlgo? {
        val p = normalize(pasted)
        if (p.isEmpty() || !p.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return HashAlgo.entries.firstOrNull { it.hexLength == p.length }
    }
}
