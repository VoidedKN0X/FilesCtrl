package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
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
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.EnumSet
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** Thrown when an SFTP server's host key differs from the one remembered for it. */
class HostKeyChangedException(val fingerprint: String) : IOException(
    App.str(R.string.host_key_changed, fingerprint),
)

/** SSH host key fingerprints, written like OpenSSH does ("SHA256:base64"). */
object HostKeys {
    fun sha256(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** Old versions saved MD5 fingerprints ("12:ab:…"). */
    fun isLegacyMd5(fp: String) = Regex("^([0-9a-fA-F]{2}:){15}[0-9a-fA-F]{2}$").matches(fp)
}

/**
 * SFTP (SSH file transfer). An unknown server fails with [HostKeyUnknownException] until the user confirms its
 * key; [onLearned] saves a remembered key upgraded from MD5 to SHA-256.
 */
class SftpVfs(server: NetServer, private val onLearned: (NetServer) -> Unit = {}) : RemoteVfs {
    @Volatile override var server: NetServer = server
        private set
    override val id = RemoteVfs.idFor(server.id)
    override val title: String get() = server.title
    override val canChmod = true

    private val lock = Any()
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null
    /** The home folder, used as the starting point when no folder is set. */
    @Volatile private var home: String? = null

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
                    val fp = HostKeys.sha256(key)
                    val known = server.hostKey
                    // Like ssh: the first time, ask the user (the UI shows the fingerprint and retries).
                    if (known.isBlank()) throw HostKeyUnknownException(fp)
                    if (known == fp) return true
                    if (HostKeys.isLegacyMd5(known) && known.equals(SecurityUtils.getFingerprint(key), ignoreCase = true)) {
                        // Same key, remembered by an older version as MD5: store it as SHA-256 from now on.
                        server = server.copy(hostKey = fp)
                        onLearned(server)
                        return true
                    }
                    throw HostKeyChangedException(fp)
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
            if (home == null) home = try { f.canonicalize(".") } catch (_: Exception) { "/" }
            ssh = c; sftp = f
            return f
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw friendly(e, App.str(R.string.connecting))
        }
    }

    private fun dropLocked() {
        try { sftp?.close() } catch (_: Exception) {}
        try { ssh?.disconnect() } catch (_: Exception) {}
        sftp = null; ssh = null
    }

    override fun close() = synchronized(lock) { dropLocked() }

    private fun remote(path: String) = RemoteVfs.join(server.path.ifBlank { home ?: "/" }, path)

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
        } catch (e: UntrustedIdentityException) {
            throw e
        } catch (e: Exception) {
            // The connection may look open but be dead (e.g. after time in the background): start fresh once.
            if (connected && !RemoteVfs.looksLikeConnectionError(e)) throw friendly(e, what, path)
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

    override fun list(dir: String): List<FileEntry> = call(App.str(R.string.listing), dir) { c ->
        // Drop "." / ".." and names with "/" or NUL: a hostile server could use them to escape the destination
        // folder when files are copied to the phone.
        c.ls(remote(dir)).filter { Paths.isSafeName(it.name) }.map { r ->
            val p = Paths.join(dir, r.name)
            val a = r.attributes
            val target = if (a.type == FileMode.Type.SYMLINK) try { c.stat(r.path) } catch (_: Exception) { null } else null
            entry(p, r.name, a, target)
        }
    }

    override fun stat(path: String): FileEntry = call(App.str(R.string.reading), path) { c ->
        val l = c.lstat(remote(path))
        val target = if (l.type == FileMode.Type.SYMLINK) try { c.stat(remote(path)) } catch (_: Exception) { null } else null
        entry(path, Paths.name(path), l, target)
    }

    override fun openRead(path: String): InputStream = openRead(path, 0)

    override fun openRead(path: String, offset: Long): InputStream = call(App.str(R.string.opening), path) { c ->
        val f = c.open(remote(path), EnumSet.of(OpenMode.READ))
        object : InputStream() {
            val inner = f.ReadAheadRemoteFileInputStream(16, offset)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
            override fun close() { try { inner.close() } finally { f.close() } }
        }
    }

    override fun openWrite(path: String): OutputStream = call(App.str(R.string.creating), path) { c ->
        val f = c.open(remote(path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        object : OutputStream() {
            val inner = f.RemoteFileOutputStream(0, 16)
            override fun write(b: Int) = inner.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = inner.write(b, off, len)
            override fun flush() = inner.flush()
            override fun close() { try { inner.close() } finally { f.close() } }
        }
    }

    override fun openRandom(path: String, write: Boolean, truncate: Boolean): RandomFile = call(App.str(R.string.opening), path) { c ->
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

    override fun mkdir(path: String) = call(App.str(R.string.creating_folder), path) { c ->
        if (c.statExistence(remote(path)) != null) throw IOException(App.str(R.string.already_exists, Paths.name(path)))
        c.mkdir(remote(path))
    }

    override fun createFile(path: String) = call(App.str(R.string.creating_file), path) { c ->
        c.open(remote(path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).close()
    }

    override fun delete(path: String, isDir: Boolean) = call(App.str(R.string.deleting), path) { c ->
        if (isDir) c.rmdir(remote(path)) else c.rm(remote(path))
    }

    override fun rename(from: String, to: String) = call(App.str(R.string.renaming), from) { c ->
        if (from != to && c.statExistence(remote(to)) != null) throw IOException(App.str(R.string.already_exists, Paths.name(to)))
        c.rename(remote(from), remote(to))
    }

    override fun setModified(path: String, time: Long) {
        try {
            call(App.str(R.string.setting_time), path) { c ->
                val secs = time / 1000
                c.setattr(remote(path), FileAttributes.Builder().withAtimeMtime(secs, secs).build())
            }
        } catch (_: Exception) {}
    }

    override fun chmod(path: String, mode: Int) = call(App.str(R.string.changing_permissions), path) { c ->
        c.chmod(remote(path), mode and 0xFFF)
    }

    override fun readlink(path: String): String? = try { call(App.str(R.string.reading_link), path) { c -> c.readlink(remote(path)) } } catch (_: Exception) { null }

    // SFTP has no standard free-space call (the statvfs extension isn't exposed by the client library).
    override fun space(path: String): Space? = null

    private fun friendly(e: Exception, what: String, path: String? = null): IOException {
        val name = path?.let { " (${Paths.name(it)})" } ?: ""
        TlsPin.identityError(e)?.let { return it }
        return when (e) {
            is SFTPException -> when (e.statusCode) {
                Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH -> FileNotFoundException(App.str(R.string.not_found, path ?: "That"))
                Response.StatusCode.PERMISSION_DENIED -> IOException(App.str(R.string.failed_permission_denied, what, name))
                Response.StatusCode.FILE_ALREADY_EXISTS -> IOException(App.str(R.string.failed_already_exists, what, name))
                Response.StatusCode.DIR_NOT_EMPTY -> IOException(App.str(R.string.failed_folder_isn_t_empty, what, name))
                Response.StatusCode.NO_SPACE_ON_FILESYSTEM -> IOException(App.str(R.string.failed_no_space_left, what))
                else -> IOException(App.str(R.string.failed_4, what, e.message ?: e.statusCode, name))
            }
            is UserAuthException -> IOException(
                if (server.privateKey.isNotBlank()) App.str(R.string.the_server_didn_t_accept_the_key) else App.str(R.string.wrong_username_or_password),
            )
            is TransportException -> IOException(App.str(R.string.failed_3, what, e.message))
            is java.net.SocketTimeoutException -> IOException(App.str(R.string.failed_the_server_stopped_responding, what))
            is java.net.ConnectException -> IOException(App.str(R.string.couldn_t_reach_on_port_is_ssh, server.host, server.effectivePort))
            is java.net.UnknownHostException -> IOException(App.str(R.string.unknown_server_address, server.host))
            is IOException -> if (e.message.isNullOrBlank()) IOException(App.str(R.string.failed_2, what)) else e
            else -> IOException(App.str(R.string.failed_3, what, e.message ?: e.javaClass.simpleName))
        }
    }
}
