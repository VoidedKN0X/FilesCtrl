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
import com.vknxworks.filesctrl.fs.NfsVfs
import com.vknxworks.filesctrl.fs.VfsRegistry
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.provider.NfsDocumentsProvider
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

    /** For work that outlives a screen (file operations). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        settings = Settings(context)
        servers = ServerStore(context)
        bookmarks = Bookmarks(context)
        vfs = VfsRegistry(context, servers)
        servers.onChange { id ->
            if (servers.get(id) == null) bookmarks.removeVfs(NfsVfs.idFor(id))
            // Let the system file picker see the new list of shares.
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(NfsDocumentsProvider.AUTHORITY), null)
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
