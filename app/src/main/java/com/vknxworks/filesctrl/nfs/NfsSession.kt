package com.vknxworks.filesctrl.nfs

import android.system.Os
import android.system.OsConstants
import com.vknxworks.filesctrl.data.NetServer
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** An NFS failure with the errno behind it and a message fit to show. */
class NfsException(val errno: Int, message: String) : IOException(message)

/**
 * One connection (libnfs context) to a saved server. Connects lazily, serialises every call with a lock
 * (libnfs contexts aren't thread safe) and reconnects after connection-level failures.
 */
class NfsSession(val server: NetServer) {
    private val lock = ReentrantLock()
    private var ctx = 0L
    @Volatile private var closed = false

    /** Bumped on every (re)connect; file handles from an older generation are dead. */
    @Volatile var generation = 0
        private set

    /** The NFS version actually in use (3, 4 or 42), once connected. */
    @Volatile var activeVersion = 0
        private set

    val isConnected: Boolean get() = ctx != 0L

    /** Runs [block] with a connected context, holding the lock. */
    fun <T> call(block: (Long) -> T): T = lock.withLock {
        if (closed) throw IOException("Connection closed")
        if (ctx == 0L) connect()
        try {
            block(ctx)
        } finally {
            // A call hit a dead connection: drop it so the next call reconnects.
            if (pendingDisconnect) {
                pendingDisconnect = false
                disconnect()
            }
        }
    }

    /** Like [call], but retries once on a fresh connection if the first attempt hit a dead connection. */
    fun <T> callRetry(block: (Long) -> T): T {
        try {
            return call(block)
        } catch (e: NfsException) {
            if (!isConnectionError(e.errno)) throw e
            disconnect()
            return call(block)
        }
    }

    /** Runs [block] only if a connection is open (used to release handles; never reconnects). */
    fun callIfConnected(block: (Long) -> Unit) {
        lock.withLock {
            if (ctx == 0L) return
            try { block(ctx) } catch (_: Exception) {}
        }
    }

    fun disconnect() = lock.withLock {
        if (ctx != 0L) {
            NfsNative.destroy(ctx)
            ctx = 0L
        }
    }

    fun close() {
        closed = true
        disconnect()
    }

    /** Throws an [NfsException] (or FileNotFoundException for ENOENT) for a negative result. */
    fun ok(h: Long, rc: Int, what: String, path: String? = null): Int {
        if (rc >= 0) return rc
        val errno = -rc
        if (isConnectionError(errno)) disconnectLocked()
        val detail = NfsNative.error(h)
        if (errno == OsConstants.ENOENT) throw FileNotFoundException(path?.let { "$it: not found" } ?: "Not found")
        throw NfsException(errno, describe(errno, what, path, detail))
    }

    /** Failure of a handle-returning call. */
    fun fail(h: Long, what: String, path: String?): Nothing {
        val rc = NfsNative.lastError(h).let { if (it < 0) it else -OsConstants.EIO }
        ok(h, rc, what, path)
        throw NfsException(OsConstants.EIO, "$what failed")
    }

    // Set from inside call() when a result shows the connection is dead; acted on when the call ends.
    private fun disconnectLocked() { pendingDisconnect = true }
    @Volatile private var pendingDisconnect = false

    private fun connect() {
        val s = server
        val versions = when (s.version) {
            3, 4, 42 -> listOf(s.version)
            else -> listOf(3, 4) // Auto: v3 is the most widely exported; v4 for servers without rpcbind.
        }
        var firstError: NfsException? = null
        for (v in versions) {
            // Try following nested exports first; minimal servers without the EXPORT call need it off.
            for (traverse in listOf(true, false)) {
                val h = NfsNative.create()
                if (h == 0L) throw IOException("Couldn't start the NFS client")
                val rc = NfsNative.mount(
                    h, s.host.trim(), normaliseExport(s.export), v, s.uid, s.gid,
                    s.nfsPort, s.mountPort, s.timeoutSec.coerceIn(2, 120) * 1000, s.readOnly, traverse,
                )
                if (rc == 0) {
                    ctx = h
                    activeVersion = v
                    generation++
                    return
                }
                val err = NfsException(-rc, describeMount(-rc, v, NfsNative.error(h)))
                NfsNative.destroy(h)
                if (firstError == null) firstError = err
                // Only a missing EXPORT procedure is worth retrying without traversal.
                if (!(traverse && v == 3 && (err.message ?: "").contains("Procedure not available", true))) break
            }
        }
        throw firstError ?: NfsException(OsConstants.EIO, "Couldn't connect")
    }

    companion object {
        fun normaliseExport(e: String): String {
            val t = e.trim().ifEmpty { "/" }
            return if (t.startsWith("/")) t else "/$t"
        }

        fun isConnectionError(errno: Int) = errno == OsConstants.ETIMEDOUT || errno == OsConstants.ENOTCONN ||
            errno == OsConstants.EPIPE || errno == OsConstants.ECONNRESET || errno == OsConstants.ECONNREFUSED ||
            errno == OsConstants.ESTALE || errno == OsConstants.EHOSTUNREACH || errno == OsConstants.ENETUNREACH

        private fun errName(errno: Int) = try { Os.strerror(errno) } catch (_: Exception) { "error $errno" }

        fun describe(errno: Int, what: String, path: String?, detail: String?): String {
            val friendly = when (errno) {
                OsConstants.EACCES, OsConstants.EPERM -> "Permission denied"
                OsConstants.EEXIST -> "Already exists"
                OsConstants.ENOTEMPTY -> "Folder isn't empty"
                OsConstants.EROFS -> "The share is read-only"
                OsConstants.ENOSPC -> "No space left on the server"
                OsConstants.EDQUOT -> "Quota exceeded"
                OsConstants.ETIMEDOUT -> "The server stopped responding"
                OsConstants.ENAMETOOLONG -> "Name is too long"
                OsConstants.EISDIR -> "That's a folder"
                OsConstants.ENOTDIR -> "Not a folder"
                OsConstants.ESTALE -> "The file changed on the server; refresh and try again"
                else -> errName(errno)
            }
            val where = path?.let { " ($it)" } ?: ""
            return "$what failed: $friendly$where"
        }

        fun describeMount(errno: Int, version: Int, detail: String?): String {
            val v = if (version == 42) "NFSv4.2" else "NFSv$version"
            val hint = when (errno) {
                OsConstants.EACCES, OsConstants.EPERM ->
                    "The server refused the mount. Check that the export allows this phone's IP address and has the " +
                        "\"insecure\" option, since Android apps can't use the privileged ports some servers require."
                OsConstants.ENOENT -> "The export path doesn't exist on the server."
                OsConstants.ETIMEDOUT -> "No answer from the server. Check the address and that you're on the same network."
                OsConstants.ECONNREFUSED -> if (version == 3)
                    "Connection refused. Is NFS (and rpcbind/portmap on port 111) running? An NFSv4-only server needs version NFSv4."
                else "Connection refused on the NFS port (usually 2049)."
                OsConstants.EHOSTUNREACH, OsConstants.ENETUNREACH -> "The server can't be reached from this network."
                else -> null
            }
            val base = "$v: ${detail?.takeIf { it.isNotBlank() } ?: errName(errno)}"
            return if (hint != null) "$base\n\n$hint" else base
        }
    }
}
