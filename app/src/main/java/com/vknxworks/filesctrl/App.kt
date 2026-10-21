package com.vknxworks.filesctrl

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vknxworks.filesctrl.data.lock.LockVault
import com.vknxworks.filesctrl.ui.lock.AppLockState
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
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
    /** The app lock's PIN, fingerprint key and the key that encrypts saved passwords while locked. */
    lateinit var vault: LockVault
        private set

    /** For work that outlives a screen (file operations). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Text from strings.xml for code that has no Context of its own (file systems, operations, errors). */
    fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** A plural from strings.xml, for code that has no Context of its own. */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)

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
        vault = LockVault(context, "filesctrl")
        servers = ServerStore(context, vault)
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

    /** Lets Android's file picker see the shares again, or not while FilesCtrl is locked. */
    fun notifyRoots() {
        runCatching { context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(NetDocumentsProvider.AUTHORITY), null) }
    }

    /**
     * Locked: the vault key and every saved password leave memory, and connections close. File operations that
     * are already running keep their connection until they finish; nothing new can connect until you unlock.
     */
    fun onLocked() {
        servers.lockSecrets()
        vault.close()
        notifyRoots()
        Thread {
            while (Ops.active.isNotEmpty() && !AppLockState.unlocked) Thread.sleep(2_000)
            if (!AppLockState.unlocked) vfs.closeAll()
        }.start()
    }

    fun onUnlocked() {
        servers.unlockSecrets()
        notifyRoots()
    }
}

class FilesApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
        // The lock follows the whole app (the file browser and the text editor), not one screen.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) AppLockState.onForeground()
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0 && !activity.isChangingConfigurations) AppLockState.onBackground()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
