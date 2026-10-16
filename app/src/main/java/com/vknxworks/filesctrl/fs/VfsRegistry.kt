package com.vknxworks.filesctrl.fs

import android.content.Context
import com.vknxworks.filesctrl.data.ServerStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Hands out the file system for an id: "local" or "nfs:<server id>". */
class VfsRegistry(context: Context, private val servers: ServerStore) {
    val local = LocalVfs(context.applicationContext)
    private val nfs = ConcurrentHashMap<String, NfsVfs>()

    init {
        // A changed or removed server drops its connection; the next use reconnects with the new settings.
        servers.onChange { id ->
            val old = nfs[id] ?: return@onChange
            if (old.server == servers.get(id)) return@onChange // Saved without changes: keep the connection.
            nfs.remove(id)
            // Closing waits for any running call on that share; never block the main thread on it.
            Thread { old.close() }.start()
        }
    }

    fun find(id: String): Vfs? = when {
        id == LocalVfs.ID -> local
        id.startsWith(NfsVfs.PREFIX) -> nfsFor(id.removePrefix(NfsVfs.PREFIX))
        else -> null
    }

    fun get(id: String): Vfs = find(id) ?: throw IOException("That server no longer exists")

    fun nfsFor(serverId: String): NfsVfs? {
        nfs[serverId]?.let { return it }
        val s = servers.get(serverId) ?: return null
        return nfs.getOrPut(serverId) { NfsVfs(s) }
    }

    /** The share's file system only if it's already been used (doesn't start a connection). */
    fun openNfs(serverId: String): NfsVfs? = nfs[serverId]?.takeIf { it.session.isConnected }

    /** Drops idle connections (e.g. when the app goes to the background for a while). */
    fun disconnectAll() = nfs.values.forEach { it.session.disconnect() }
}
