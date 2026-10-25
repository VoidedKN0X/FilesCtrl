package com.vknxworks.filesctrl.fs

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import com.vknxworks.filesctrl.data.NetServer
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/**
 * SMB 2/3 (Windows shares, Samba, most NAS boxes). The first path segment is the share; with no share in the
 * server's path, the top level lists the shares.
 */
class SmbVfs(override val server: NetServer) : RemoteVfs {
    override val id = RemoteVfs.idFor(server.id)
    override val title: String get() = server.title
    override val canChmod = false

    private val lock = Any()
    private var client: SMBClient? = null
    private var conn: Connection? = null
    private var session: Session? = null
    private val shares = ConcurrentHashMap<String, DiskShare>()

    override val connected: Boolean get() = conn?.isConnected == true

    private fun session(): Session = synchronized(lock) {
        session?.takeIf { conn?.isConnected == true }?.let { return it }
        dropLocked()
        val t = server.timeoutSec.coerceIn(2, 120).toLong()
        val cfg = SmbConfig.builder()
            .withTimeout(t, TimeUnit.SECONDS)
            .withSoTimeout(t, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            // Guest sessions have no key to sign or encrypt with, so these only apply to signed-in users.
            .withSigningRequired(server.smbSigning && !server.guest)
            .withEncryptData(server.smbEncrypt && !server.guest)
            .build()
        val c = SMBClient(cfg)
        try {
            val cn = c.connect(server.host.trim(), server.effectivePort)
            val auth = when {
                server.guest -> AuthenticationContext.guest()
                server.username.isBlank() -> throw IOException(App.str(R.string.enter_a_username_or_turn_on_guest_in))
                else -> AuthenticationContext(server.username, server.password.toCharArray(), server.domain.ifBlank { null })
            }
            val s = cn.authenticate(auth)
            client = c; conn = cn; session = s
            return s
        } catch (e: Exception) {
            try { c.close() } catch (_: Exception) {}
            throw friendly(e, App.str(R.string.connecting))
        }
    }

    private fun dropLocked() {
        shares.clear()
        try { session?.close() } catch (_: Exception) {}
        try { conn?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        session = null; conn = null; client = null
    }

    override fun close() = synchronized(lock) { dropLocked() }

    private fun share(name: String): DiskShare {
        shares[name]?.takeIf { it.isConnected }?.let { return it }
        val s = session().connectShare(name) as? DiskShare ?: throw IOException(App.str(R.string.isn_t_a_file_share, name))
        shares[name] = s
        return s
    }

    /** (share, path inside the share with backslashes) for a path in this file system; share empty at the top. */
    private fun split(path: String): Pair<String, String> {
        val parts = RemoteVfs.join(server.path, path).trim('/').split('/').filter { it.isNotEmpty() }
        // SMB separates folders with backslashes: a name with one (possible on phones and other servers) could
        // point into another folder, or climb out with "..\", so it's refused.
        if (path.contains('\\')) throw IOException(App.str(R.string.invalid_character))
        if (parts.isEmpty()) return "" to ""
        return parts[0] to parts.drop(1).joinToString("\\")
    }

    /** Runs [block], reconnecting once if the connection dropped. */
    private fun <T> call(what: String, path: String, block: () -> T): T {
        try {
            return block()
        } catch (e: SMBApiException) {
            throw friendly(e, what, path)
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: Exception) {
            // The connection may look open but be dead (e.g. after time in the background): start fresh once.
            if (connected && !RemoteVfs.looksLikeConnectionError(e)) throw friendly(e, what, path)
            close()
            return try { block() } catch (e2: Exception) { throw friendly(e2, what, path) }
        }
    }

    private fun entry(path: String, name: String, attrs: Long, size: Long, modified: Long): FileEntry {
        val dir = attrs and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
        val link = attrs and FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.value != 0L
        val ro = attrs and FileAttributes.FILE_ATTRIBUTE_READONLY.value != 0L
        // SMB has no Unix modes; show a plausible one so the properties dialog has something to say.
        val mode = (if (dir) 0x4000 else 0x8000) or (if (ro) 0b101_101_101 else 0b111_101_101)
        return FileEntry(id, path, name, dir, if (dir) 0 else size, modified, mode, isLink = link)
    }

    override fun list(dir: String): List<FileEntry> {
        val (shareName, inner) = split(dir)
        if (shareName.isEmpty()) return listShares()
        return call(App.str(R.string.listing), dir) {
            share(shareName).list(inner).filter { Paths.isSafeName(it.fileName) && !it.fileName.contains('\\') }.map {
                entry(Paths.join(dir, it.fileName), it.fileName, it.fileAttributes, it.endOfFile, it.lastWriteTime.toEpochMillis())
            }
        }
    }

    private fun listShares(): List<FileEntry> = call(App.str(R.string.listing_shares), "/") {
        val transport = SMBTransportFactories.SRVSVC.getTransport(session())
        ServerService(transport).shares1
            // Disk shares only (type 0), and not the hidden admin ones like C$ and IPC$.
            .filter { (it.type and 0x0FFFFFFF) == 0 && !it.netName.endsWith("$") && Paths.isSafeName(it.netName) }
            .map { FileEntry(id, "/" + it.netName, it.netName, true, 0, 0, 0x4000 or 0b111_101_101) }
    }

    override fun stat(path: String): FileEntry {
        val (shareName, inner) = split(path)
        if (shareName.isEmpty() || (inner.isEmpty() && RemoteVfs.join(server.path, path).trim('/') == shareName)) {
            if (shareName.isNotEmpty()) call(App.str(R.string.opening), path) { share(shareName) }
            return FileEntry(id, path, Paths.name(path), true, 0, 0, 0x4000 or 0b111_101_101)
        }
        return call(App.str(R.string.reading), path) {
            val info = share(shareName).getFileInformation(inner)
            entry(path, Paths.name(path), info.basicInformation.fileAttributes, info.standardInformation.endOfFile,
                info.basicInformation.lastWriteTime.toEpochMillis())
        }
    }

    private fun open(path: String, write: Boolean, disposition: SMB2CreateDisposition): com.hierynomus.smbj.share.File {
        val (shareName, inner) = split(path)
        if (inner.isEmpty()) throw IOException(App.str(R.string.that_s_a_share_not_a_file))
        val access = if (write) EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE) else EnumSet.of(AccessMask.GENERIC_READ)
        return share(shareName).openFile(
            inner, access, null, SMB2ShareAccess.ALL, disposition,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        )
    }

    override fun openRead(path: String): InputStream = call(App.str(R.string.opening), path) {
        val f = open(path, false, SMB2CreateDisposition.FILE_OPEN)
        object : FilterInputStream(f.inputStream) {
            override fun close() { try { super.close() } finally { f.close() } }
        }
    }

    override fun openRead(path: String, offset: Long): InputStream {
        if (offset == 0L) return openRead(path)
        val rf = openRandom(path, false, false)
        return object : InputStream() {
            var pos = offset
            val one = ByteArray(1)
            override fun read(): Int = if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val tmp = if (off == 0) b else ByteArray(len)
                val n = rf.read(pos, tmp, len)
                if (n <= 0) return -1
                if (off != 0) System.arraycopy(tmp, 0, b, off, n)
                pos += n
                return n
            }
            override fun close() = rf.close()
        }
    }

    override fun openWrite(path: String): OutputStream = call(App.str(R.string.creating), path) {
        val f = open(path, true, SMB2CreateDisposition.FILE_OVERWRITE_IF)
        object : FilterOutputStream(f.outputStream) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() { try { super.close() } finally { f.close() } }
        }
    }

    override fun openRandom(path: String, write: Boolean, truncate: Boolean): RandomFile = call(App.str(R.string.opening), path) {
        val disp = when {
            !write -> SMB2CreateDisposition.FILE_OPEN
            truncate -> SMB2CreateDisposition.FILE_OVERWRITE_IF
            else -> SMB2CreateDisposition.FILE_OPEN_IF
        }
        val f = open(path, write, disp)
        object : RandomFile {
            override fun size(): Long = f.fileInformation.standardInformation.endOfFile
            override fun read(offset: Long, buf: ByteArray, len: Int): Int {
                var done = 0
                while (done < len) {
                    val n = f.read(buf, offset + done, done, len - done)
                    if (n <= 0) break
                    done += n
                }
                return done
            }
            override fun write(offset: Long, buf: ByteArray, len: Int) { f.write(buf, offset, 0, len) }
            override fun sync() = f.flush()
            override fun close() = f.close()
        }
    }

    override fun mkdir(path: String) {
        val (shareName, inner) = split(path)
        if (inner.isEmpty()) throw IOException(App.str(R.string.new_shares_can_t_be_created_from))
        call(App.str(R.string.creating_folder), path) {
            val s = share(shareName)
            if (s.folderExists(inner) || s.fileExists(inner)) throw IOException(App.str(R.string.already_exists, Paths.name(path)))
            s.mkdir(inner)
        }
    }

    override fun createFile(path: String) {
        call(App.str(R.string.creating_file), path) { open(path, true, SMB2CreateDisposition.FILE_CREATE).close() }
    }

    override fun delete(path: String, isDir: Boolean) {
        val (shareName, inner) = split(path)
        if (inner.isEmpty()) throw IOException(App.str(R.string.shares_can_t_be_deleted_from_here))
        call(App.str(R.string.deleting), path) { if (isDir) share(shareName).rmdir(inner, false) else share(shareName).rm(inner) }
    }

    override fun rename(from: String, to: String) {
        val (fs, fi) = split(from)
        val (ts, ti) = split(to)
        if (fi.isEmpty() || ti.isEmpty()) throw IOException(App.str(R.string.shares_can_t_be_renamed_from_here))
        if (fs != ts) throw CrossDeviceException()
        call(App.str(R.string.renaming), from) {
            val s = share(fs)
            if (!from.equals(to, ignoreCase = true) && (s.fileExists(ti) || s.folderExists(ti)))
                throw IOException(App.str(R.string.already_exists, Paths.name(to)))
            s.open(fi, EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN, null).use { it.rename(ti) }
        }
    }

    override fun setModified(path: String, time: Long) {
        try {
            val (shareName, inner) = split(path)
            if (inner.isEmpty()) return
            share(shareName).open(inner, EnumSet.of(AccessMask.FILE_WRITE_ATTRIBUTES), null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN, null).use { e ->
                val keep = FileBasicInformation.DONT_SET
                e.setFileInformation(FileBasicInformation(keep, keep, FileTime.ofEpochMillis(time), keep, 0))
            }
        } catch (_: Exception) {}
    }

    override fun space(path: String): Space? = try {
        val (shareName, _) = split(path)
        if (shareName.isEmpty()) null
        else share(shareName).shareInformation.let { Space(it.totalSpace, it.callerFreeSpace) }
    } catch (_: Exception) { null }

    private fun friendly(e: Exception, what: String, path: String? = null): IOException {
        if (e is SMBApiException) {
            val name = path?.let { " (${Paths.name(it)})" } ?: ""
            return when (e.status) {
                NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NO_SUCH_FILE ->
                    FileNotFoundException(App.str(R.string.not_found, path ?: "That"))
                NtStatus.STATUS_BAD_NETWORK_NAME -> IOException(App.str(R.string.the_share_doesn_t_exist_on_this))
                NtStatus.STATUS_ACCESS_DENIED -> IOException(App.str(R.string.failed_permission_denied, what, name))
                NtStatus.STATUS_LOGON_FAILURE -> IOException(App.str(R.string.wrong_username_or_password))
                NtStatus.STATUS_ACCOUNT_DISABLED -> IOException(App.str(R.string.this_account_is_disabled_or_locked))
                NtStatus.STATUS_OBJECT_NAME_COLLISION -> IOException(App.str(R.string.failed_already_exists, what, name))
                NtStatus.STATUS_DIRECTORY_NOT_EMPTY -> IOException(App.str(R.string.failed_folder_isn_t_empty, what, name))
                NtStatus.STATUS_DISK_FULL -> IOException(App.str(R.string.failed_the_share_is_full, what))
                NtStatus.STATUS_SHARING_VIOLATION -> IOException(App.str(R.string.failed_the_file_is_open_in_another, what, name))
                else -> IOException(App.str(R.string.failed_4, what, e.status, name))
            }
        }
        if (e is IOException && e.message?.isNotBlank() == true && e !is java.net.SocketTimeoutException) return e
        val msg = when (e) {
            is java.net.SocketTimeoutException -> App.str(R.string.the_server_stopped_responding)
            is java.net.ConnectException -> App.str(R.string.couldn_t_reach_on_port_is_file, server.host, server.effectivePort)
            is java.net.UnknownHostException -> App.str(R.string.unknown_server_address, server.host)
            else -> e.message ?: e.javaClass.simpleName
        }
        return IOException(App.str(R.string.failed_3, what, msg))
    }
}
