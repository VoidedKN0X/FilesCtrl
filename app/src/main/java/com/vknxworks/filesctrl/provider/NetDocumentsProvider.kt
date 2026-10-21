package com.vknxworks.filesctrl.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.RandomFile
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.SeekingReader
import com.vknxworks.filesctrl.fs.TempCopyFile
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.nfs.NfsException
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Exposes saved network locations (NFS, SMB, SFTP, FTP, WebDAV) to the rest of Android: the system file picker,
 * and any app FilesCtrl opens a file with. Files are streamed on demand through a proxy file descriptor, so
 * players can seek in large videos without downloading them first.
 *
 * Document ids are "<server id>:<path inside the server>".
 */
class NetDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        App.init(context!!)
        // Temporary copies of edited network files left by a crash: nothing can be using them in a new process.
        try { context!!.cacheDir.listFiles { f -> f.name.startsWith("edit") && f.name.endsWith(".tmp") }?.forEach { it.delete() } } catch (_: Exception) {}
        return true
    }

    /** While FilesCtrl is locked, other apps can't browse or open anything on your servers. */
    private fun locked(): Boolean = AppLockState.enabled && !AppLockState.unlocked

    private fun split(docId: String): Pair<RemoteVfs, String> {
        if (locked()) throw FileNotFoundException(App.str(R.string.filesctrl_is_locked_open_it_and))
        val i = docId.indexOf(':')
        if (i <= 0) throw FileNotFoundException(App.str(R.string.bad_document_id))
        // Normalise the path and refuse "." / ".." segments, so an id can never climb out of a granted folder.
        val path = Paths.normalize(docId.substring(i + 1).ifEmpty { "/" }) ?: throw FileNotFoundException(App.str(R.string.bad_document_id))
        val vfs = App.vfs.remote(docId.substring(0, i)) ?: throw FileNotFoundException(App.str(R.string.server_was_removed))
        return vfs to path
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: ROOT_COLUMNS)
        if (locked()) return c
        for (s in App.servers.all) {
            c.newRow().apply {
                add(Root.COLUMN_ROOT_ID, s.id)
                add(Root.COLUMN_DOCUMENT_ID, docId(s.id, "/"))
                add(Root.COLUMN_TITLE, s.title)
                add(Root.COLUMN_SUMMARY, "${s.type.label} · ${s.host}")
                add(Root.COLUMN_ICON, R.drawable.ic_stat_files)
                add(Root.COLUMN_FLAGS, (if (s.readOnly) 0 else Root.FLAG_SUPPORTS_CREATE) or Root.FLAG_SUPPORTS_IS_CHILD)
            }
        }
        return c
    }

    private fun addRow(c: MatrixCursor, serverId: String, e: FileEntry, readOnly: Boolean) {
        var flags = 0
        if (!readOnly) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
            flags = flags or if (e.isDir) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        }
        c.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docId(serverId, e.path))
            add(Document.COLUMN_DISPLAY_NAME, if (e.path == "/") App.servers.get(serverId)?.title ?: e.name else e.name)
            add(Document.COLUMN_MIME_TYPE, if (e.isDir) Document.MIME_TYPE_DIR else Mime.ofName(e.name))
            add(Document.COLUMN_SIZE, if (e.isDir) null else e.size)
            add(Document.COLUMN_LAST_MODIFIED, e.modified)
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    /**
     * A file inside an archive ("arc.<archive id>:<path>"), opened from FilesCtrl in another app. Read-only, and
     * never listed in the file picker: only the files you open are handed out.
     */
    private fun splitArchive(docId: String): Pair<ArchiveVfs, String>? {
        if (!docId.startsWith(ARC)) return null
        if (locked()) throw FileNotFoundException(App.str(R.string.filesctrl_is_locked_open_it_and))
        val i = docId.indexOf(':')
        if (i <= ARC.length) throw FileNotFoundException(App.str(R.string.bad_document_id))
        val path = Paths.normalize(docId.substring(i + 1).ifEmpty { "/" }) ?: throw FileNotFoundException(App.str(R.string.bad_document_id))
        val vfs = App.vfs.archive(ArchiveVfs.PREFIX + docId.substring(ARC.length, i)) ?: throw FileNotFoundException(App.str(R.string.the_archive_is_gone))
        return vfs to path
    }

    /** Uncompressed size; for single compressed files whose size isn't stored, counted by reading through once. */
    private fun archiveSize(vfs: ArchiveVfs, path: String, listed: Long): Long {
        if (listed > 0) return listed
        return vfs.openRead(path).use { s ->
            val buf = ByteArray(256 * 1024)
            var n = 0L
            while (true) { val r = s.read(buf); if (r < 0) break; n += r }
            n
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        splitArchive(documentId)?.let { (vfs, path) ->
            val c = MatrixCursor(projection ?: DOC_COLUMNS)
            val e = io { vfs.stat(path) }
            c.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, documentId)
                add(Document.COLUMN_DISPLAY_NAME, e.name)
                add(Document.COLUMN_MIME_TYPE, if (e.isDir) Document.MIME_TYPE_DIR else Mime.ofName(e.name))
                add(Document.COLUMN_SIZE, if (e.isDir) null else io { archiveSize(vfs, path, e.size) })
                add(Document.COLUMN_LAST_MODIFIED, e.modified)
                add(Document.COLUMN_FLAGS, 0)
            }
            return c
        }
        val (vfs, path) = split(documentId)
        val c = MatrixCursor(projection ?: DOC_COLUMNS)
        addRow(c, vfs.server.id, io { vfs.stat(path) }, vfs.server.readOnly)
        c.setNotificationUri(context!!.contentResolver, DocumentsContract.buildDocumentUri(AUTHORITY, documentId))
        return c
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val (vfs, path) = split(parentDocumentId)
        val c = MatrixCursor(projection ?: DOC_COLUMNS)
        val showHidden = App.settings.showHidden
        for (e in io { vfs.list(path) }) {
            if (!showHidden && e.hidden) continue
            addRow(c, vfs.server.id, e, vfs.server.readOnly)
        }
        // Lets the picker refresh when notifyChildren() reports a change here.
        c.setNotificationUri(context!!.contentResolver, DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId))
        return c
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val (pv, pp) = try { split(parentDocumentId) } catch (_: FileNotFoundException) { return false }
        val (cv, cp) = try { split(documentId) } catch (_: FileNotFoundException) { return false }
        return pv.server.id == cv.server.id && Paths.isWithin(cp, pp)
    }

    override fun getDocumentType(documentId: String): String {
        splitArchive(documentId)?.let { (vfs, path) ->
            val e = io { vfs.stat(path) }
            return if (e.isDir) Document.MIME_TYPE_DIR else Mime.ofName(e.name)
        }
        val (vfs, path) = split(documentId)
        val e = io { vfs.stat(path) }
        return if (e.isDir) Document.MIME_TYPE_DIR else Mime.ofName(e.name)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val (vfs, dir) = split(parentDocumentId)
        if (vfs.server.readOnly) throw FileNotFoundException(App.str(R.string.the_share_is_read_only))
        val clean = displayName.replace('/', '_')
        Paths.validName(clean)?.let { throw IllegalArgumentException(it) }
        val name = Paths.unique(dir, clean) { vfs.exists(it) }
        val path = Paths.join(dir, name)
        io { if (mimeType == Document.MIME_TYPE_DIR) vfs.mkdir(path) else vfs.createFile(path) }
        notifyChildren(vfs.server.id, dir)
        return docId(vfs.server.id, path)
    }

    override fun deleteDocument(documentId: String) {
        val (vfs, path) = split(documentId)
        if (vfs.server.readOnly) throw FileNotFoundException(App.str(R.string.the_share_is_read_only))
        if (path == "/") throw FileNotFoundException(App.str(R.string.the_top_folder_can_t_be_deleted))
        io { deleteTree(vfs, vfs.stat(path)) }
        notifyChildren(vfs.server.id, Paths.parent(path))
    }

    private fun deleteTree(vfs: RemoteVfs, e: FileEntry) {
        if (e.isDir && !e.isLink) vfs.list(e.path).forEach { deleteTree(vfs, it) }
        vfs.delete(e.path, e.isDir && !e.isLink)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val (vfs, path) = split(documentId)
        if (vfs.server.readOnly) throw FileNotFoundException(App.str(R.string.the_share_is_read_only))
        if (path == "/") throw FileNotFoundException(App.str(R.string.the_top_folder_can_t_be_renamed))
        Paths.validName(displayName)?.let { throw IllegalArgumentException(it) }
        val target = Paths.join(Paths.parent(path), displayName)
        io { vfs.rename(path, target) }
        notifyChildren(vfs.server.id, Paths.parent(path))
        return docId(vfs.server.id, target)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        splitArchive(documentId)?.let { (vfs, path) ->
            if (mode.contains('w')) throw FileNotFoundException(App.str(R.string.files_inside_archives_are_read_only))
            val rf = io {
                val e = vfs.stat(path)
                if (e.isDir) throw FileNotFoundException(App.str(R.string.that_s_a_folder))
                ArchiveEntryReader(vfs, path, archiveSize(vfs, path, e.size))
            }
            return proxy(rf, mode, null)
        }
        val (vfs, path) = split(documentId)
        val write = mode.contains('w')
        if (write && vfs.server.readOnly) throw FileNotFoundException(App.str(R.string.the_share_is_read_only))
        val truncate = write && (mode.contains('t') || mode == "w")

        // Native random access where the protocol has it (NFS, SMB, SFTP); otherwise stream with re-seeking for
        // reads, and edit a local copy that's uploaded on close for writes (FTP, WebDAV).
        val rf: RandomFile = io {
            vfs.openRandom(path, write, truncate) ?: when {
                write -> TempCopyFile(vfs, path, context!!.cacheDir, truncate)
                else -> SeekingReader(vfs, path, vfs.stat(path).size)
            }
        }

        return proxy(rf, mode) { if (write) notifyChildren(vfs.server.id, Paths.parent(path)) }
    }

    private fun proxy(rf: RandomFile, mode: String, onClosed: (() -> Unit)?): ParcelFileDescriptor {
        val thread = HandlerThread("net-fd").apply { start() }
        val cb = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = errno { rf.size() }
            override fun onRead(offset: Long, chunk: Int, data: ByteArray): Int = errno { rf.read(offset, data, chunk) }
            override fun onWrite(offset: Long, chunk: Int, data: ByteArray): Int = errno { rf.write(offset, data, chunk); chunk }
            override fun onFsync() { errno { rf.sync() } }
            override fun onRelease() {
                try { rf.close() } catch (_: Exception) {}
                thread.quitSafely()
                onClosed?.invoke()
            }
        }
        val sm = context!!.getSystemService(StorageManager::class.java)
        try {
            return sm.openProxyFileDescriptor(ParcelFileDescriptor.parseMode(mode), cb, Handler(thread.looper))
        } catch (e: Exception) {
            try { rf.close() } catch (_: Exception) {}
            thread.quitSafely()
            throw FileNotFoundException(e.message ?: App.str(R.string.couldn_t_open_2))
        }
    }

    private fun notifyChildren(serverId: String, dir: String) {
        val uri: Uri = DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId(serverId, dir))
        context?.contentResolver?.notifyChange(uri, null)
    }

    /** Maps our IOExceptions to what the provider API expects. */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: FileNotFoundException) {
        throw e
    } catch (e: IOException) {
        throw FileNotFoundException(e.message)
    }

    /** Proxy FD callbacks report failures as ErrnoException. */
    private inline fun <T> errno(block: () -> T): T = try {
        block()
    } catch (e: ErrnoException) {
        throw e
    } catch (e: NfsException) {
        throw ErrnoException("nfs", e.errno)
    } catch (e: FileNotFoundException) {
        throw ErrnoException("nfs", OsConstants.ENOENT)
    } catch (e: IOException) {
        throw ErrnoException("nfs", OsConstants.EIO)
    }

    companion object {
        const val AUTHORITY = "com.vknxworks.filesctrl.documents"

        fun docId(serverId: String, path: String) = "$serverId:$path"

        private const val ARC = "arc."

        /** Content URI another app can open for a file on a server, or inside an archive. */
        fun uriFor(e: FileEntry): Uri {
            val owner = if (e.vfsId.startsWith(ArchiveVfs.PREFIX)) ARC + e.vfsId.removePrefix(ArchiveVfs.PREFIX)
            else e.vfsId.removePrefix(RemoteVfs.PREFIX)
            return DocumentsContract.buildDocumentUri(AUTHORITY, docId(owner, e.path))
        }

        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON, Root.COLUMN_FLAGS,
        )
        private val DOC_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )
    }
}

/** Reads a file inside an archive at any offset: keeps one stream open and reopens it when a reader seeks back. */
private class ArchiveEntryReader(private val vfs: ArchiveVfs, private val path: String, private val length: Long) : RandomFile {
    private var stream: java.io.InputStream? = null
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
