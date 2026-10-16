package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * FTP and FTPS (explicit TLS). An FTP connection can only do one transfer at a time, so browsing uses one
 * shared connection and every open file gets its own.
 */
class FtpVfs(override val server: NetServer) : RemoteVfs {
    override val id = RemoteVfs.idFor(server.id)
    override val title: String get() = server.title
    override val canChmod = false

    private val lock = Any()
    private var ctl: FTPClient? = null
    @Volatile private var mlsd: Boolean? = null

    override val connected: Boolean get() = ctl?.isConnected == true

    private fun newClient(): FTPClient {
        val s = server
        val c: FTPClient = if (s.secure) FTPSClient(false).apply {
            if (s.insecureTls) trustManager = TrustManagerUtils.getAcceptAllTrustManager()
        } else FTPClient()
        val t = s.timeoutSec.coerceIn(2, 120)
        c.connectTimeout = t * 1000
        c.setDefaultTimeout(t * 1000)
        c.controlEncoding = "UTF-8"
        c.autodetectUTF8 = true
        c.setDataTimeout(Duration.ofSeconds(t.toLong()))
        try {
            c.connect(s.host.trim(), s.effectivePort)
            if (!FTPReply.isPositiveCompletion(c.replyCode)) throw IOException("The server refused the connection: ${c.replyString.trim()}")
            c.soTimeout = t * 1000
            val user = s.username.ifBlank { "anonymous" }
            val pass = if (s.username.isBlank() && s.password.isBlank()) "filesctrl@" else s.password
            if (!c.login(user, pass)) throw IOException("Wrong username or password")
            if (c is FTPSClient) { c.execPBSZ(0); c.execPROT("P") }
            c.setFileType(FTP.BINARY_FILE_TYPE)
            c.enterLocalPassiveMode()
            if (mlsd == null) mlsd = try { c.hasFeature("MLST") } catch (_: Exception) { false }
            return c
        } catch (e: Exception) {
            try { if (c.isConnected) c.disconnect() } catch (_: Exception) {}
            throw friendly(e, "Connecting")
        }
    }

    private fun quit(c: FTPClient) {
        try { c.logout() } catch (_: Exception) {}
        try { if (c.isConnected) c.disconnect() } catch (_: Exception) {}
    }

    override fun close() = synchronized(lock) { ctl?.let { quit(it) }; ctl = null }

    /** Runs [block] on the shared control connection, reconnecting once if it dropped. */
    private fun <T> call(what: String, path: String, block: (FTPClient) -> T): T = synchronized(lock) {
        val c = ctl?.takeIf { it.isConnected } ?: newClient().also { ctl = it }
        try {
            return block(c)
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: FtpReplyException) {
            throw e
        } catch (e: Exception) {
            quit(c); ctl = null
            val c2 = newClient().also { ctl = it }
            return try { block(c2) } catch (e2: Exception) { throw friendly(e2, what, path) }
        }
    }

    private fun remote(path: String) = RemoteVfs.join(server.path, path)

    private fun entry(path: String, f: FTPFile): FileEntry {
        var mode = 0
        if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION) || f.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            var bits = 0
            val who = intArrayOf(FTPFile.USER_ACCESS, FTPFile.GROUP_ACCESS, FTPFile.WORLD_ACCESS)
            for ((i, w) in who.withIndex()) {
                if (f.hasPermission(w, FTPFile.READ_PERMISSION)) bits = bits or (4 shl (6 - 3 * i))
                if (f.hasPermission(w, FTPFile.WRITE_PERMISSION)) bits = bits or (2 shl (6 - 3 * i))
                if (f.hasPermission(w, FTPFile.EXECUTE_PERMISSION)) bits = bits or (1 shl (6 - 3 * i))
            }
            mode = (if (f.isDirectory) 0x4000 else if (f.isSymbolicLink) 0xA000 else 0x8000) or bits
        }
        return FileEntry(
            vfsId = id, path = path, name = f.name,
            isDir = f.isDirectory,
            size = if (f.isDirectory) 0 else f.size.coerceAtLeast(0),
            modified = f.timestamp?.timeInMillis ?: 0,
            mode = mode,
            isLink = f.isSymbolicLink,
        )
    }

    override fun list(dir: String): List<FileEntry> = call("Listing", dir) { c ->
        val r = remote(dir)
        val files = if (mlsd == true) c.mlistDir(r) else c.listFiles(r)
        if (files == null || (files.isEmpty() && !FTPReply.isPositiveCompletion(c.replyCode))) {
            if (c.replyCode == 550) throw FileNotFoundException("$dir: not found")
            throw FtpReplyException("Listing failed: ${c.replyString.trim()}")
        }
        files.filterNotNull().filter { it.name != "." && it.name != ".." && it.name.isNotEmpty() }
            .map { entry(Paths.join(dir, it.name.substringAfterLast('/')), it) }
    }

    override fun stat(path: String): FileEntry {
        val r = remote(path)
        if (r == "/") return FileEntry(id, path, Paths.name(path), true, 0, 0)
        return call("Reading", path) { c ->
            val f = if (mlsd == true) c.mlistFile(r) else null
            if (f != null) return@call entry(path, f.also { it.name = Paths.name(path) })
            // No MLST: look the name up in its folder.
            val parent = Paths.parent(r)
            val name = Paths.name(r)
            val list = c.listFiles(parent) ?: emptyArray()
            val found = list.firstOrNull { it != null && it.name.substringAfterLast('/') == name }
                ?: throw FileNotFoundException("$path: not found")
            entry(path, found)
        }
    }

    override fun openRead(path: String): InputStream = openRead(path, 0)

    override fun openRead(path: String, offset: Long): InputStream {
        val c = newClient()
        try {
            if (offset > 0) c.restartOffset = offset
            val s = c.retrieveFileStream(remote(path))
            if (s == null) {
                val code = c.replyCode
                val msg = c.replyString.trim()
                quit(c)
                if (code == 550) throw FileNotFoundException("$path: not found")
                throw IOException("Opening failed: $msg")
            }
            return object : FilterInputStream(s) {
                override fun close() {
                    try { super.close(); c.completePendingCommand() } catch (_: Exception) {} finally { quit(c) }
                }
            }
        } catch (e: IOException) {
            quit(c); throw e
        }
    }

    override fun openWrite(path: String): OutputStream {
        if (server.readOnly) throw IOException("The share is read-only")
        val c = newClient()
        val s = c.storeFileStream(remote(path)) ?: run {
            val msg = c.replyString.trim()
            quit(c)
            throw IOException("Creating ${Paths.name(path)} failed: $msg")
        }
        return object : FilterOutputStream(s) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() {
                try {
                    super.close()
                    // The server confirms the upload only after the data connection closes.
                    if (!c.completePendingCommand()) throw IOException("Saving ${Paths.name(path)} failed: ${c.replyString.trim()}")
                } finally { quit(c) }
            }
        }
    }

    private fun check(c: FTPClient, ok: Boolean, what: String, path: String) {
        if (ok) return
        val msg = c.replyString.trim()
        if (c.replyCode == 550 && what != "Creating folder") throw FileNotFoundException("$path: not found")
        throw FtpReplyException("$what ${Paths.name(path)} failed: $msg")
    }

    override fun mkdir(path: String) = call("Creating folder", path) { c ->
        check(c, c.makeDirectory(remote(path)), "Creating folder", path)
    }

    override fun createFile(path: String) {
        if (exists(path)) throw IOException("${Paths.name(path)} already exists")
        openWrite(path).close()
    }

    override fun delete(path: String, isDir: Boolean) = call("Deleting", path) { c ->
        check(c, if (isDir) c.removeDirectory(remote(path)) else c.deleteFile(remote(path)), "Deleting", path)
    }

    override fun rename(from: String, to: String) {
        if (from != to && exists(to)) throw IOException("${Paths.name(to)} already exists")
        call("Renaming", from) { c -> check(c, c.rename(remote(from), remote(to)), "Renaming", from) }
    }

    override fun setModified(path: String, time: Long) {
        try {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            call("Setting time", path) { c -> c.setModificationTime(remote(path), fmt.format(Date(time))) }
        } catch (_: Exception) {}
    }

    override fun space(path: String): Space? = null

    private fun friendly(e: Exception, what: String, path: String? = null): IOException = when (e) {
        is FileNotFoundException -> e
        is javax.net.ssl.SSLException -> IOException(
            if (server.insecureTls) "$what failed: TLS error (${e.message})"
            else "$what failed: the server's certificate isn't trusted. Turn on \"Accept self-signed certificates\" if it's your own server.",
        )
        is java.net.SocketTimeoutException -> IOException("$what failed: the server stopped responding")
        is java.net.ConnectException -> IOException("Couldn't reach ${server.host} on port ${server.effectivePort}. Is the FTP server running?")
        is java.net.UnknownHostException -> IOException("Unknown server address ${server.host}")
        is IOException -> if (e.message.isNullOrBlank()) IOException("$what failed") else e
        else -> IOException("$what failed: ${e.message ?: e.javaClass.simpleName}")
    }
}

/** A clear "no" from the FTP server; not worth reconnecting for. */
class FtpReplyException(message: String) : IOException(message)
