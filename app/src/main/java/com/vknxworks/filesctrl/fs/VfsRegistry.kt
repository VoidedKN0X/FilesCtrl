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
        servers.onChange { id -> nfs.remove(id)?.close() }
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

    /** Drops idle connections (e.g. when the app goes to the background for a while). */
    fun disconnectAll() = nfs.values.forEach { it.session.disconnect() }
}
