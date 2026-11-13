package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import com.vknxworks.filesctrl.data.Proto
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** A file opened for reads/writes at any offset (used to stream files to other apps with seeking). */
interface RandomFile : Closeable {
    /** Current size. */
    fun size(): Long
    /** Reads up to [len] bytes at [offset] into [buf]; returns bytes read, 0 at end of file. */
    fun read(offset: Long, buf: ByteArray, len: Int): Int
    fun write(offset: Long, buf: ByteArray, len: Int) { throw IOException(App.str(R.string.read_only)) }
    fun sync() {}
}

/** A file system on a saved network server. */
interface RemoteVfs : Vfs {
    val server: NetServer
    /** True when a connection is open right now (so asking for free space won't start one). */
    val connected: Boolean
    fun close()

    /** Drops the connection without giving up the file system; the next call reconnects. */
    fun drop() = close()

    /** Random access if the protocol supports it natively; null to use the generic fallbacks. */
    fun openRandom(path: String, write: Boolean, truncate: Boolean): RandomFile? = null

    /** Reads starting at [offset]. Protocols with ranged reads override this. */
    fun openRead(path: String, offset: Long): InputStream = openRead(path).also { s ->
        var left = offset
        while (left > 0) {
            val n = s.skip(left)
            if (n <= 0) { if (s.read() < 0) break else left-- } else left -= n
        }
    }

    fun readlink(path: String): String? = null

    override val isRemote: Boolean get() = true
    override fun rootOf(path: String) = "/" to server.title

    companion object {
        const val PREFIX = "net:"
        fun idFor(serverId: String) = PREFIX + serverId

        fun create(server: NetServer, onLearned: (NetServer) -> Unit = {}): RemoteVfs = when (server.type) {
            Proto.NFS -> NfsVfs(server)
            Proto.SMB -> SmbVfs(server)
            Proto.SFTP -> SftpVfs(server, onLearned)
            Proto.FTP -> FtpVfs(server)
            Proto.WEBDAV -> WebDavVfs(server)
        }

        /** True if [e] (or what caused it) is a network/connection failure rather than a "no" from the server. */
        fun looksLikeConnectionError(e: Throwable): Boolean {
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth++ < 8) {
                if (t is java.net.SocketException || t is java.net.SocketTimeoutException || t is java.io.EOFException ||
                    t is java.io.InterruptedIOException
                ) return true
                val n = t.javaClass.name
                if (n.contains("Transport") || n.contains("ConnectionException") || n.contains("SMBRuntimeException") ||
                    n.contains("Socket") || n.contains("Timeout")
                ) return true
                val m = t.message.orEmpty()
                if (m.contains("Broken pipe", true) || m.contains("Connection reset", true) || m.contains("closed", true)) return true
                t = t.cause
            }
            return false
        }

        /** Joins the server's starting folder with a path inside the file system. */
        fun join(base: String, path: String): String {
            val b = "/" + base.trim('/')
            val p = path.trim('/')
            return if (p.isEmpty()) b else if (b == "/") "/$p" else "$b/$p"
        }
    }
}

/**
 * Random reads for protocols that can only stream: keeps one stream open and reopens it at the
 * requested offset when a reader seeks.
 */
class SeekingReader(private val vfs: RemoteVfs, private val path: String, private val length: Long) : RandomFile {
    private var stream: InputStream? = null
    private var pos = 0L

    override fun size() = length

    @Synchronized
    override fun read(offset: Long, buf: ByteArray, len: Int): Int {
        if (offset >= length) return 0
        val s = stream?.takeIf { pos == offset } ?: run {
            stream?.let { try { it.close() } catch (_: Exception) {} }
            vfs.openRead(path, offset).also { stream = it; pos = offset }
        }
        var done = 0
        while (done < len) {
            val n = s.read(buf, done, len - done)
            if (n < 0) break
            done += n
        }
        pos += done
        return done
    }

    @Synchronized
    override fun close() {
        stream?.let { try { it.close() } catch (_: Exception) {} }
        stream = null
    }
}

/**
 * Writes for protocols without random-access writes: edits happen in a local temp copy, uploaded on close.
 */
class TempCopyFile(
    private val vfs: RemoteVfs,
    private val path: String,
    cacheDir: File,
    truncate: Boolean,
) : RandomFile {
    private val tmp = File.createTempFile("edit", ".tmp", cacheDir)
    private val raf: RandomAccessFile
    private var dirty = truncate

    init {
        raf = try {
            if (!truncate) {
                try {
                    vfs.openRead(path).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                } catch (_: java.io.FileNotFoundException) {
                    dirty = true // New file.
                }
            }
            RandomAccessFile(tmp, "rw").also { if (truncate) it.setLength(0) }
        } catch (e: Throwable) {
            // Don't leave a partial copy of the server's file behind.
            tmp.delete()
            throw e
        }
    }

    @Synchronized override fun size() = raf.length()

    @Synchronized
    override fun read(offset: Long, buf: ByteArray, len: Int): Int {
        raf.seek(offset)
        val n = raf.read(buf, 0, len)
        return if (n < 0) 0 else n
    }

    @Synchronized
    override fun write(offset: Long, buf: ByteArray, len: Int) {
        raf.seek(offset)
        raf.write(buf, 0, len)
        dirty = true
    }

    @Synchronized
    override fun sync() = upload()

    private fun upload() {
        if (!dirty) return
        raf.fd.sync()
        tmp.inputStream().use { input -> vfs.openWrite(path).use { input.copyTo(it, 1 shl 20) } }
        dirty = false
    }

    @Synchronized
    override fun close() {
        try { upload() } finally {
            raf.close()
            tmp.delete()
        }
    }
}
