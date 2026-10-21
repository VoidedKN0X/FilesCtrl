package com.vknxworks.filesctrl.nfs

import android.system.Os
import android.system.OsConstants
import com.vknxworks.filesctrl.data.NetServer
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

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
        if (closed) throw IOException(App.str(R.string.connection_closed))
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
            // Anything that isn't clearly about the file itself may be a dead connection (e.g. dropped while the
            // app was in the background, which often shows up as a plain I/O error): reconnect and try once more.
            if (isFileError(e.errno)) throw e
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
        // Don't keep using a connection that failed for a non-file reason; the next call reconnects.
        if (!isFileError(errno)) disconnectLocked()
        val detail = NfsNative.error(h)
        if (errno == OsConstants.ENOENT) throw FileNotFoundException(path?.let { App.str(R.string.not_found, it) } ?: App.str(R.string.not_found_2))
        throw NfsException(errno, describe(errno, what, path, detail))
    }

    /** Failure of a handle-returning call. */
    fun fail(h: Long, what: String, path: String?): Nothing {
        val rc = NfsNative.lastError(h).let { if (it < 0) it else -OsConstants.EIO }
        ok(h, rc, what, path)
        throw NfsException(OsConstants.EIO, App.str(R.string.failed_2, what))
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
                if (h == 0L) throw IOException(App.str(R.string.couldn_t_start_the_nfs_client))
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
        throw firstError ?: NfsException(OsConstants.EIO, App.str(R.string.couldn_t_connect))
    }

    companion object {
        fun normaliseExport(e: String): String {
            val t = e.trim().ifEmpty { "/" }
            return if (t.startsWith("/")) t else "/$t"
        }

        /** Errors about the file or folder itself, which reconnecting wouldn't fix. */
        fun isFileError(errno: Int) = errno == OsConstants.ENOENT || errno == OsConstants.EACCES ||
            errno == OsConstants.EPERM || errno == OsConstants.EEXIST || errno == OsConstants.ENOTEMPTY ||
            errno == OsConstants.EROFS || errno == OsConstants.ENOSPC || errno == OsConstants.EDQUOT ||
            errno == OsConstants.EISDIR || errno == OsConstants.ENOTDIR || errno == OsConstants.ENAMETOOLONG ||
            errno == OsConstants.EINVAL || errno == OsConstants.EFBIG || errno == OsConstants.EXDEV

        fun isConnectionError(errno: Int) = errno == OsConstants.ETIMEDOUT || errno == OsConstants.ENOTCONN ||
            errno == OsConstants.EPIPE || errno == OsConstants.ECONNRESET || errno == OsConstants.ECONNREFUSED ||
            errno == OsConstants.ESTALE || errno == OsConstants.EHOSTUNREACH || errno == OsConstants.ENETUNREACH

        private fun errName(errno: Int) = try { Os.strerror(errno) } catch (_: Exception) { App.str(R.string.error, errno) }

        fun describe(errno: Int, what: String, path: String?, detail: String?): String {
            val friendly = when (errno) {
                OsConstants.EACCES, OsConstants.EPERM -> App.str(R.string.permission_denied)
                OsConstants.EEXIST -> App.str(R.string.already_exists_2)
                OsConstants.ENOTEMPTY -> App.str(R.string.folder_isn_t_empty)
                OsConstants.EROFS -> App.str(R.string.the_share_is_read_only)
                OsConstants.ENOSPC -> App.str(R.string.no_space_left_on_the_server)
                OsConstants.EDQUOT -> App.str(R.string.quota_exceeded)
                OsConstants.ETIMEDOUT -> App.str(R.string.the_server_stopped_responding)
                OsConstants.ENAMETOOLONG -> App.str(R.string.name_is_too_long)
                OsConstants.EISDIR -> App.str(R.string.that_s_a_folder)
                OsConstants.ENOTDIR -> App.str(R.string.not_a_folder)
                OsConstants.ESTALE -> App.str(R.string.the_file_changed_on_the_server)
                else -> errName(errno)
            }
            val where = path?.let { " ($it)" } ?: ""
            return App.str(R.string.failed_4, what, friendly, where)
        }

        fun describeMount(errno: Int, version: Int, detail: String?): String {
            val v = if (version == 42) "NFSv4.2" else "NFSv$version"
            val hint = when (errno) {
                OsConstants.EACCES, OsConstants.EPERM ->
                    App.str(R.string.nfs_mount_refused)
                OsConstants.ENOENT -> App.str(R.string.the_export_path_doesn_t_exist_on_the)
                OsConstants.ETIMEDOUT -> App.str(R.string.no_answer_from_the_server_check_the)
                OsConstants.ECONNREFUSED -> if (version == 3)
                    App.str(R.string.connection_refused_is_nfs_and)
                else App.str(R.string.connection_refused_on_the_nfs_port)
                OsConstants.EHOSTUNREACH, OsConstants.ENETUNREACH -> App.str(R.string.the_server_can_t_be_reached_from)
                else -> null
            }
            val base = "$v: ${detail?.takeIf { it.isNotBlank() } ?: errName(errno)}"
            return if (hint != null) "$base\n\n$hint" else base
        }
    }
}
