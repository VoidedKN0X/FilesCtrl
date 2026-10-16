package com.vknxworks.filesctrl.fs

import android.system.OsConstants
import com.vknxworks.filesctrl.data.NfsServer
import com.vknxworks.filesctrl.nfs.NfsException
import com.vknxworks.filesctrl.nfs.NfsNative
import com.vknxworks.filesctrl.nfs.NfsSession
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A saved NFS share. Paths are absolute inside the export ("/" is the export itself). */
class NfsVfs(val server: NfsServer) : Vfs {
    val session = NfsSession(server)
    override val id = idFor(server.id)
    override val title: String get() = server.title
    override val isRemote = true
    override val canChmod = true

    override fun rootOf(path: String) = "/" to server.title

    private fun fromStat(path: String, name: String, st: LongArray, at: Int = 0, linkType: Boolean = false): FileEntry {
        // Some servers put extra bits above the file type; keep type + permissions only.
        val mode = (st[at + NfsNative.ST_MODE] and 0xFFFF).toInt()
        return FileEntry(
            vfsId = id, path = path, name = name,
            isDir = (mode and 0xF000) == 0x4000,
            size = st[at + NfsNative.ST_SIZE],
            modified = st[at + NfsNative.ST_MTIME],
            mode = mode,
            uid = st[at + NfsNative.ST_UID].toInt(),
            gid = st[at + NfsNative.ST_GID].toInt(),
            isLink = linkType || (mode and 0xF000) == 0xA000,
        )
    }

    override fun list(dir: String): List<FileEntry> = session.callRetry { h ->
        val res = NfsNative.list(h, dir) ?: session.fail(h, "Listing", dir)
        @Suppress("UNCHECKED_CAST")
        val names = res[0] as Array<String>
        val rows = res[1] as LongArray
        val count = (res[2] as IntArray)[0]
        val per = NfsNative.LIST_FIELDS
        val st = LongArray(NfsNative.STAT_FIELDS)
        (0 until count).map { i ->
            val name = names[i]
            val path = Paths.join(dir, name)
            val isLink = rows[i * per + NfsNative.STAT_FIELDS] == NfsNative.TYPE_SYMLINK ||
                ((rows[i * per + NfsNative.ST_MODE] and 0xF000) == 0xA000L)
            if (isLink && NfsNative.stat(h, path, true, st) == 0) {
                // Show a link as what it points to (folder or file), marked as a link.
                fromStat(path, name, st, linkType = true)
            } else fromStat(path, name, rows, i * per, isLink)
        }
    }

    override fun stat(path: String): FileEntry = session.callRetry { h ->
        val st = LongArray(NfsNative.STAT_FIELDS)
        session.ok(h, NfsNative.stat(h, path, true, st), "Reading", path)
        val e = fromStat(path, Paths.name(path), st)
        if (path != "/") {
            val lst = LongArray(NfsNative.STAT_FIELDS)
            if (NfsNative.stat(h, path, false, lst) == 0 && (lst[0] and 0xF000) == 0xA000L) return@callRetry e.copy(isLink = true)
        }
        e
    }

    override fun openRead(path: String): InputStream = NfsInputStream(session, path)
    override fun openWrite(path: String): OutputStream = NfsOutputStream(session, path)

    override fun mkdir(path: String) = session.callRetry { h ->
        session.ok(h, NfsNative.mkdir(h, path), "Creating folder", Paths.name(path)); Unit
    }

    override fun createFile(path: String) = session.callRetry { h ->
        if (NfsNative.stat(h, path, false, LongArray(NfsNative.STAT_FIELDS)) == 0)
            throw NfsException(OsConstants.EEXIST, "${Paths.name(path)} already exists")
        val fh = NfsNative.creat(h, path, 0b110_100_100)
        if (fh == 0L) session.fail(h, "Creating file", Paths.name(path))
        NfsNative.close(h, fh); Unit
    }

    override fun delete(path: String, isDir: Boolean) = session.callRetry { h ->
        val rc = if (isDir) NfsNative.rmdir(h, path) else NfsNative.unlink(h, path)
        session.ok(h, rc, "Deleting", Paths.name(path)); Unit
    }

    override fun rename(from: String, to: String) = session.callRetry { h ->
        if (!from.equals(to, ignoreCase = true) && NfsNative.stat(h, to, false, LongArray(NfsNative.STAT_FIELDS)) == 0)
            throw NfsException(OsConstants.EEXIST, "${Paths.name(to)} already exists")
        session.ok(h, NfsNative.rename(h, from, to), "Renaming", Paths.name(from)); Unit
    }

    override fun setModified(path: String, time: Long) {
        try { session.call { h -> NfsNative.utimes(h, path, time, time) } } catch (_: IOException) {}
    }

    override fun chmod(path: String, mode: Int) = session.callRetry { h ->
        session.ok(h, NfsNative.chmod(h, path, mode and 0xFFF), "Changing permissions", Paths.name(path)); Unit
    }

    fun chown(path: String, uid: Int, gid: Int) = session.callRetry { h ->
        session.ok(h, NfsNative.chown(h, path, uid, gid), "Changing owner", Paths.name(path)); Unit
    }

    fun symlink(target: String, link: String) = session.callRetry { h ->
        session.ok(h, NfsNative.symlink(h, target, link), "Creating link", Paths.name(link)); Unit
    }

    fun readlink(path: String): String? = session.callRetry { h -> NfsNative.readlink(h, path) }

    override fun space(path: String): Space? = try {
        session.callRetry { h ->
            val out = LongArray(4)
            if (NfsNative.statvfs(h, path, out) != 0 || out[0] <= 0) null
            else Space(out[0] * out[1], out[0] * out[3])
        }
    } catch (_: IOException) { null }

    fun close() = session.close()

    companion object {
        const val PREFIX = "nfs:"
        fun idFor(serverId: String) = PREFIX + serverId
    }
}

private const val CHUNK = 1024 * 1024

/** Reads a file sequentially; reopens it transparently if the connection was re-established. */
class NfsInputStream(private val session: NfsSession, private val path: String) : InputStream() {
    private var fh = 0L
    private var gen = -1
    private var pos = 0L
    private var closed = false
    private val one = ByteArray(1)

    private fun handle(h: Long): Long {
        if (fh == 0L || gen != session.generation) {
            fh = NfsNative.open(h, path, NfsNative.O_READ, 0)
            if (fh == 0L) session.fail(h, "Opening", Paths.name(path))
            gen = session.generation
        }
        return fh
    }

    override fun read(): Int = if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) throw IOException("Stream closed")
        if (len == 0) return 0
        val n = session.callRetry { h ->
            val f = handle(h)
            session.ok(h, NfsNative.pread(h, f, pos, b, off, minOf(len, CHUNK)), "Reading", Paths.name(path))
        }
        if (n <= 0) return -1
        pos += n
        return n
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        pos += n
        return n
    }

    override fun close() {
        if (closed) return
        closed = true
        if (fh != 0L && gen == session.generation) {
            try { session.call { h -> NfsNative.close(h, fh) } } catch (_: IOException) {}
        }
        fh = 0L
    }
}

/** Creates/truncates a file and writes it sequentially, batching small writes into 1 MiB requests. */
class NfsOutputStream(private val session: NfsSession, private val path: String) : OutputStream() {
    private var fh = 0L
    private var gen = -1
    private var pos = 0L
    private val buf = ByteArray(CHUNK)
    private var used = 0
    private var closed = false

    init {
        session.callRetry { h ->
            fh = NfsNative.open(h, path, NfsNative.O_WRITE or NfsNative.O_CREATE or NfsNative.O_TRUNC, 0b110_100_100)
            if (fh == 0L) session.fail(h, "Creating", Paths.name(path))
            gen = session.generation
        }
    }

    override fun write(b: Int) {
        if (used == buf.size) flushBuffer()
        buf[used++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var left = len
        while (left > 0) {
            if (used == 0 && left >= buf.size) {
                // Big writes go straight through.
                writeOut(b, o, buf.size)
                o += buf.size; left -= buf.size
                continue
            }
            val n = minOf(left, buf.size - used)
            System.arraycopy(b, o, buf, used, n)
            used += n; o += n; left -= n
            if (used == buf.size) flushBuffer()
        }
    }

    private fun flushBuffer() {
        if (used > 0) writeOut(buf, 0, used)
        used = 0
    }

    private fun writeOut(b: ByteArray, off: Int, len: Int) {
        if (closed) throw IOException("Stream closed")
        session.call { h ->
            if (gen != session.generation) {
                // Reconnected mid-write: reopen without truncating and carry on at the same offset.
                fh = NfsNative.open(h, path, NfsNative.O_WRITE, 0)
                if (fh == 0L) session.fail(h, "Writing", Paths.name(path))
                gen = session.generation
            }
            session.ok(h, NfsNative.pwrite(h, fh, pos, b, off, len), "Writing", Paths.name(path))
        }
        pos += len
    }

    override fun flush() = flushBuffer()

    override fun close() {
        if (closed) return
        try {
            flushBuffer()
        } finally {
            closed = true
            if (fh != 0L && gen == session.generation) {
                try { session.call { h -> NfsNative.close(h, fh) } } catch (_: IOException) {}
            }
            fh = 0L
        }
    }
}
