package com.vknxworks.filesctrl.fs

import android.content.Context
import com.vknxworks.filesctrl.data.ServerStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Hands out the file system for an id: "local" or "net:<server id>". */
class VfsRegistry(context: Context, private val servers: ServerStore) {
    val local = LocalVfs(context.applicationContext)
    private val remotes = ConcurrentHashMap<String, RemoteVfs>()

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
        else -> null
    }

    fun get(id: String): Vfs = find(id) ?: throw IOException("That server no longer exists")

    fun remote(serverId: String): RemoteVfs? {
        remotes[serverId]?.let { return it }
        val s = servers.get(serverId) ?: return null
        // Facts learned while connecting (an SFTP host key) are saved without dropping the connection.
        return remotes.getOrPut(serverId) { RemoteVfs.create(s) { learned -> servers.remember(learned) } }
    }

    /** The server's file system only if it's connected now (doesn't start a connection). */
    fun connected(serverId: String): RemoteVfs? = remotes[serverId]?.takeIf { it.connected }

    /** Drops every connection (e.g. after the app was in the background for a while); they reconnect on use. */
    fun dropAll() = remotes.values.forEach { try { it.drop() } catch (_: Exception) {} }
}
