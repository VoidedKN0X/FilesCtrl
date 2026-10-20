package com.vknxworks.filesctrl.fs

import android.content.Context
import com.vknxworks.filesctrl.data.ServerStore
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Hands out the file system for an id: "local", "net:<server id>" or "arc:<archive file>". */
class VfsRegistry(context: Context, private val servers: ServerStore) {
    val local = LocalVfs(context.applicationContext)
    private val remotes = ConcurrentHashMap<String, RemoteVfs>()
    private val cacheDir = File(context.applicationContext.cacheDir, "archives")
    /** Open archives, most recently used last; a few are kept so going back into one is instant. */
    private val archives = object : LinkedHashMap<String, ArchiveVfs>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArchiveVfs>?) = size > 6
    }

    init {
        // A changed or removed server drops its connection; the next use reconnects with the new settings.
        servers.onChange { id ->
            val old = remotes[id] ?: return@onChange
            if (old.server == servers.get(id)) return@onChange // Saved without changes: keep the connection.
            remotes.remove(id)
            // Closing waits for any running call on that server; never block the main thread on it.
            Thread { old.close() }.start()
        }
    }

    fun find(id: String): Vfs? = when {
        id == LocalVfs.ID -> local
        id.startsWith(RemoteVfs.PREFIX) -> remote(id.removePrefix(RemoteVfs.PREFIX))
        id.startsWith(ArchiveVfs.PREFIX) -> archive(id)
        else -> null
    }

    /** The archive file [id] points at, as a folder; null if the place it's in no longer exists. */
    fun archive(id: String): ArchiveVfs? {
        synchronized(archives) { archives[id]?.let { return it } }
        val loc = ArchiveVfs.archiveOf(id) ?: return null
        val inner = find(loc.vfsId) ?: return null
        val v = ArchiveVfs(id, VfsArchiveSource(inner, loc.path, cacheDir))
        synchronized(archives) { return archives.getOrPut(id) { v } }
    }

    /** Reads the archive again next time (e.g. when opened afresh, in case the file changed). Keeps no password. */
    fun forgetArchive(id: String) { synchronized(archives) { archives.remove(id) } }

    fun get(id: String): Vfs = find(id) ?: throw IOException("That server no longer exists")

    fun remote(serverId: String): RemoteVfs? {
        remotes[serverId]?.let { return it }
        // Locked: saved passwords and keys aren't available, so don't start a connection without them.
        if (servers.locked) throw IOException("FilesCtrl is locked")
        val s = servers.get(serverId) ?: return null
        // Facts learned while connecting (an SFTP host key) are saved without dropping the connection.
        return remotes.getOrPut(serverId) { RemoteVfs.create(s) { learned -> servers.remember(learned) } }
    }

    /** The server's file system only if it's connected now (doesn't start a connection). */
    fun connected(serverId: String): RemoteVfs? = remotes[serverId]?.takeIf { it.connected }

    /** Closes and forgets every connection (the app locked). They connect again after unlocking. */
    fun closeAll() {
        val all = remotes.values.toList()
        remotes.clear()
        all.forEach { try { it.close() } catch (_: Exception) {} }
    }

    /** Drops every connection (e.g. after the app was in the background for a while); they reconnect on use. */
    fun dropAll() = remotes.values.forEach { try { it.drop() } catch (_: Exception) {} }
}

/**
 * An archive file in any file system. Stream formats are read straight from it; zip, 7z and rar need random
 * access, so an archive on a server (or inside another archive) is copied into FilesCtrl's private cache once.
 */
class VfsArchiveSource(private val vfs: Vfs, private val path: String, private val cacheDir: File) : ArchiveSource {
    override val name: String = Paths.name(path)
    private val st by lazy { vfs.stat(path) }
    override val size: Long get() = st.size
    private var local: File? = null

    override fun openStream() = vfs.openRead(path)

    @Synchronized
    override fun localFile(): File {
        local?.let { return it }
        if (vfs is LocalVfs) return File(path).also { local = it }
        cacheDir.mkdirs()
        // Old copies go after a day, so the cache doesn't grow without end.
        cacheDir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete() }
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${vfs.id}|$path|${st.size}|${st.modified}".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val f = File(cacheDir, key)
        if (!f.exists() || f.length() != st.size) {
            val free = cacheDir.usableSpace
            if (st.size > free - 64L * 1024 * 1024) throw IOException("Not enough free space on the phone to read this ${Paths.name(path)} (it has to be copied from the server first)")
            val tmp = File(cacheDir, "$key.part")
            try {
                vfs.openRead(path).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                if (!tmp.renameTo(f)) throw IOException("Couldn't store a copy of the archive")
            } finally { tmp.delete() }
        }
        f.setLastModified(System.currentTimeMillis())
        return f.also { local = it }
    }
}
