package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.EnumSet

/** Thrown when an SFTP server's host key differs from the one remembered for it. */
class HostKeyChangedException(val fingerprint: String) : IOException(
    "The server's identity changed (host key $fingerprint). If the server was reinstalled, clear the saved key " +
        "in the share's settings; otherwise someone may be intercepting the connection.",
)

/** SFTP (SSH file transfer). [onLearned] saves the host key the first time a server is trusted. */
class SftpVfs(server: NetServer, private val onLearned: (NetServer) -> Unit = {}) : RemoteVfs {
    @Volatile override var server: NetServer = server
        private set
    override val id = RemoteVfs.idFor(server.id)
    override val title: String get() = server.title
    override val canChmod = true

    private val lock = Any()
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    override val connected: Boolean get() = ssh?.isConnected == true && ssh?.isAuthenticated == true

    private fun client(): SFTPClient = synchronized(lock) {
        sftp?.takeIf { connected }?.let { return it }
        dropLocked()
        val s = server
        val cfg = if (System.getProperty("java.vendor").orEmpty().contains("Android", true)) net.schmizz.sshj.AndroidConfig() else DefaultConfig()
        val c = SSHClient(cfg)
        try {
            c.connectTimeout = s.timeoutSec.coerceIn(2, 120) * 1000
            c.timeout = s.timeoutSec.coerceIn(2, 120) * 1000
            c.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
                    val fp = SecurityUtils.getFingerprint(key)
                    val known = server.hostKey
                    if (known.isBlank()) {
                        // Trust on first use, like ssh does after you answer "yes".
                        server = server.copy(hostKey = fp)
                        onLearned(server)
                        return true
                    }
                    if (known != fp) throw HostKeyChangedException(fp)
                    return true
                }

                override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
            })
            c.connect(s.host.trim(), s.effectivePort)
            val user = s.username.ifBlank { "anonymous" }
            when {
                s.privateKey.isNotBlank() -> {
                    val finder = if (s.password.isNotEmpty()) PasswordUtils.createOneOff(s.password.toCharArray()) else null
                    c.authPublickey(user, c.loadKeys(s.privateKey, null, finder))
                }
                else -> c.authPassword(user, s.password)
            }
            val f = c.newSFTPClient()
            ssh = c; sftp = f
            return f
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw friendly(e, "Connecting")
        }
    }

    private fun dropLocked() {
        try { sftp?.close() } catch (_: Exception) {}
        try { ssh?.disconnect() } catch (_: Exception) {}
        sftp = null; ssh = null
    }

    override fun close() = synchronized(lock) { dropLocked() }

    private fun remote(path: String) = RemoteVfs.join(server.path, path)

    private fun <T> call(what: String, path: String, block: (SFTPClient) -> T): T {
        val c = client()
        try {
            return block(c)
        } catch (e: SFTPException) {
            throw friendly(e, what, path)
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: HostKeyChangedException) {
            throw e
        } catch (e: Exception) {
            if (connected) throw friendly(e, what, path)
            close()
            return try { block(client()) } catch (e2: Exception) { throw friendly(e2, what, path) }
        }
    }

    private fun entry(path: String, name: String, a: FileAttributes, linkTarget: FileAttributes? = null): FileEntry {
        val isLink = a.type == FileMode.Type.SYMLINK
        val eff = linkTarget ?: a
        val typeBits = when (eff.type) {
            FileMode.Type.DIRECTORY -> 0x4000
            FileMode.Type.SYMLINK -> 0xA000
            else -> 0x8000
        }
        return FileEntry(
            vfsId = id, path = path, name = name,
            isDir = eff.type == FileMode.Type.DIRECTORY,
            size = eff.size,
            modified = eff.mtime * 1000,
            mode = typeBits or (eff.mode.permissionsMask and 0xFFF),
            uid = eff.uid, gid = eff.gid,
            isLink = isLink,
        )
    }

    override fun list(dir: String): List<FileEntry> = call("Listing", dir) { c ->
        c.ls(remote(dir)).filter { it.name != "." && it.name != ".." }.map { r ->
            val p = Paths.join(dir, r.name)
            val a = r.attributes
            val target = if (a.type == FileMode.Type.SYMLINK) try { c.stat(r.path) } catch (_: Exception) { null } else null
            entry(p, r.name, a, target)
        }
    }

    override fun stat(path: String): FileEntry = call("Reading", path) { c ->
        val l = c.lstat(remote(path))
        val target = if (l.type == FileMode.Type.SYMLINK) try { c.stat(remote(path)) } catch (_: Exception) { null } else null
        entry(path, Paths.name(path), l, target)
    }

    override fun openRead(path: String): InputStream = openRead(path, 0)

    override fun openRead(path: String, offset: Long): InputStream = call("Opening", path) { c ->
        val f = c.open(remote(path), EnumSet.of(OpenMode.READ))
        object : InputStream() {
            val inner = f.ReadAheadRemoteFileInputStream(16, offset)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
            override fun close() { try { inner.close() } finally { f.close() } }
        }
    }

    override fun openWrite(path: String): OutputStream = call("Creating", path) { c ->
        val f = c.open(remote(path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        object : OutputStream() {
            val inner = f.RemoteFileOutputStream(0, 16)
            override fun write(b: Int) = inner.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = inner.write(b, off, len)
            override fun flush() = inner.flush()
            override fun close() { try { inner.close() } finally { f.close() } }
        }
    }

    override fun openRandom(path: String, write: Boolean, truncate: Boolean): RandomFile = call("Opening", path) { c ->
        val modes = when {
            !write -> EnumSet.of(OpenMode.READ)
            truncate -> EnumSet.of(OpenMode.READ, OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            else -> EnumSet.of(OpenMode.READ, OpenMode.WRITE, OpenMode.CREAT)
        }
        val f: RemoteFile = c.open(remote(path), modes)
        object : RandomFile {
            override fun size(): Long = f.length()
            override fun read(offset: Long, buf: ByteArray, len: Int): Int {
                var done = 0
                while (done < len) {
                    val n = f.read(offset + done, buf, done, len - done)
                    if (n <= 0) break
                    done += n
                }
                return done
            }
            override fun write(offset: Long, buf: ByteArray, len: Int) = f.write(offset, buf, 0, len)
            override fun close() = f.close()
        }
    }

    override fun mkdir(path: String) = call("Creating folder", path) { c ->
        if (c.statExistence(remote(path)) != null) throw IOException("${Paths.name(path)} already exists")
        c.mkdir(remote(path))
    }

    override fun createFile(path: String) = call("Creating file", path) { c ->
        c.open(remote(path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).close()
    }

    override fun delete(path: String, isDir: Boolean) = call("Deleting", path) { c ->
        if (isDir) c.rmdir(remote(path)) else c.rm(remote(path))
    }

    override fun rename(from: String, to: String) = call("Renaming", from) { c ->
        if (from != to && c.statExistence(remote(to)) != null) throw IOException("${Paths.name(to)} already exists")
        c.rename(remote(from), remote(to))
    }

    override fun setModified(path: String, time: Long) {
        try {
            call("Setting time", path) { c ->
                val secs = time / 1000
                c.setattr(remote(path), FileAttributes.Builder().withAtimeMtime(secs, secs).build())
            }
        } catch (_: Exception) {}
    }

    override fun chmod(path: String, mode: Int) = call("Changing permissions", path) { c ->
        c.chmod(remote(path), mode and 0xFFF)
    }

    override fun readlink(path: String): String? = try { call("Reading link", path) { c -> c.readlink(remote(path)) } } catch (_: Exception) { null }

    // SFTP has no standard free-space call (the statvfs extension isn't exposed by the client library).
    override fun space(path: String): Space? = null

    private fun friendly(e: Exception, what: String, path: String? = null): IOException {
        val name = path?.let { " (${Paths.name(it)})" } ?: ""
        return when (e) {
            is HostKeyChangedException -> e
            is SFTPException -> when (e.statusCode) {
                Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH -> FileNotFoundException("${path ?: "That"}: not found")
                Response.StatusCode.PERMISSION_DENIED -> IOException("$what failed: permission denied$name")
                Response.StatusCode.FILE_ALREADY_EXISTS -> IOException("$what failed: already exists$name")
                Response.StatusCode.DIR_NOT_EMPTY -> IOException("$what failed: folder isn't empty$name")
                Response.StatusCode.NO_SPACE_ON_FILESYSTEM -> IOException("$what failed: no space left")
                else -> IOException("$what failed: ${e.message ?: e.statusCode}$name")
            }
            is UserAuthException -> IOException(
                if (server.privateKey.isNotBlank()) "The server didn't accept the key" else "Wrong username or password",
            )
            is TransportException -> (e.cause as? HostKeyChangedException) ?: IOException("$what failed: ${e.message}")
            is java.net.SocketTimeoutException -> IOException("$what failed: the server stopped responding")
            is java.net.ConnectException -> IOException("Couldn't reach ${server.host} on port ${server.effectivePort}. Is SSH running?")
            is java.net.UnknownHostException -> IOException("Unknown server address ${server.host}")
            is IOException -> if (e.message.isNullOrBlank()) IOException("$what failed") else e
            else -> IOException("$what failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
