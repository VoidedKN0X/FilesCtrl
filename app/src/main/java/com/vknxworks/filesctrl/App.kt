package com.vknxworks.filesctrl

import android.app.Application
import android.content.Context
import android.provider.DocumentsContract
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.vknxworks.filesctrl.data.Bookmarks
import com.vknxworks.filesctrl.data.ServerStore
import com.vknxworks.filesctrl.data.Settings
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.VfsRegistry
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.provider.NetDocumentsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny service locator. Safe to init from the Application or the documents provider, whichever runs first. */
object App {
    lateinit var context: Context
        private set
    lateinit var settings: Settings
        private set
    lateinit var servers: ServerStore
        private set
    lateinit var bookmarks: Bookmarks
        private set
    lateinit var vfs: VfsRegistry
        private set
    lateinit var lock: com.vknxworks.filesctrl.data.LockSettings
        private set

    /** For work that outlives a screen (file operations). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        // SFTP needs the full BouncyCastle provider; Android ships a cut-down one under the same name.
        try {
            java.security.Security.removeProvider("BC")
            java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
        } catch (_: Throwable) {}
        settings = Settings(context)
        servers = ServerStore(context)
        bookmarks = Bookmarks(context)
        vfs = VfsRegistry(context, servers)
        lock = com.vknxworks.filesctrl.data.LockSettings(context)
        servers.onChange { id ->
            if (servers.get(id) == null) bookmarks.removeVfs(RemoteVfs.idFor(id))
            // Let the system file picker see the new list of shares.
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(NetDocumentsProvider.AUTHORITY), null)
        }
        Ops.init(context)
    }
}

class FilesApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
