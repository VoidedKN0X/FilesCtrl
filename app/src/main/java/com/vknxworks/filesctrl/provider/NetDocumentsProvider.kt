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
        return true
    }

    private fun split(docId: String): Pair<RemoteVfs, String> {
        val i = docId.indexOf(':')
        if (i <= 0) throw FileNotFoundException("Bad document id")
        val vfs = App.vfs.remote(docId.substring(0, i)) ?: throw FileNotFoundException("Server was removed")
        return vfs to docId.substring(i + 1).ifEmpty { "/" }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: ROOT_COLUMNS)
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

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
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
        val (pv, pp) = split(parentDocumentId)
        val (cv, cp) = split(documentId)
        return pv.server.id == cv.server.id && Paths.isWithin(cp, pp)
    }

    override fun getDocumentType(documentId: String): String {
        val (vfs, path) = split(documentId)
        val e = io { vfs.stat(path) }
        return if (e.isDir) Document.MIME_TYPE_DIR else Mime.ofName(e.name)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val (vfs, dir) = split(parentDocumentId)
        val name = Paths.unique(dir, displayName.replace('/', '_')) { vfs.exists(it) }
        val path = Paths.join(dir, name)
        io { if (mimeType == Document.MIME_TYPE_DIR) vfs.mkdir(path) else vfs.createFile(path) }
        notifyChildren(vfs.server.id, dir)
        return docId(vfs.server.id, path)
    }

    override fun deleteDocument(documentId: String) {
        val (vfs, path) = split(documentId)
        io { deleteTree(vfs, vfs.stat(path)) }
        notifyChildren(vfs.server.id, Paths.parent(path))
    }

    private fun deleteTree(vfs: RemoteVfs, e: FileEntry) {
        if (e.isDir && !e.isLink) vfs.list(e.path).forEach { deleteTree(vfs, it) }
        vfs.delete(e.path, e.isDir && !e.isLink)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val (vfs, path) = split(documentId)
        val target = Paths.join(Paths.parent(path), displayName.replace('/', '_'))
        io { vfs.rename(path, target) }
        notifyChildren(vfs.server.id, Paths.parent(path))
        return docId(vfs.server.id, target)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val (vfs, path) = split(documentId)
        val write = mode.contains('w')
        if (write && vfs.server.readOnly) throw FileNotFoundException("The share is read-only")
        val truncate = write && (mode.contains('t') || mode == "w")

        // Native random access where the protocol has it (NFS, SMB, SFTP); otherwise stream with re-seeking for
        // reads, and edit a local copy that's uploaded on close for writes (FTP, WebDAV).
        val rf: RandomFile = io {
            vfs.openRandom(path, write, truncate) ?: when {
                write -> TempCopyFile(vfs, path, context!!.cacheDir, truncate)
                else -> SeekingReader(vfs, path, vfs.stat(path).size)
            }
        }

        val thread = HandlerThread("net-fd").apply { start() }
        val cb = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = errno { rf.size() }
            override fun onRead(offset: Long, chunk: Int, data: ByteArray): Int = errno { rf.read(offset, data, chunk) }
            override fun onWrite(offset: Long, chunk: Int, data: ByteArray): Int = errno { rf.write(offset, data, chunk); chunk }
            override fun onFsync() { errno { rf.sync() } }
            override fun onRelease() {
                try { rf.close() } catch (_: Exception) {}
                thread.quitSafely()
                if (write) notifyChildren(vfs.server.id, Paths.parent(path))
            }
        }
        val sm = context!!.getSystemService(StorageManager::class.java)
        try {
            return sm.openProxyFileDescriptor(ParcelFileDescriptor.parseMode(mode), cb, Handler(thread.looper))
        } catch (e: Exception) {
            try { rf.close() } catch (_: Exception) {}
            thread.quitSafely()
            throw FileNotFoundException(e.message ?: "Couldn't open")
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

        /** Content URI another app can open for a file on an NFS share. */
        fun uriFor(e: FileEntry): Uri {
            val serverId = e.vfsId.removePrefix(RemoteVfs.PREFIX)
            return DocumentsContract.buildDocumentUri(AUTHORITY, docId(serverId, e.path))
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
