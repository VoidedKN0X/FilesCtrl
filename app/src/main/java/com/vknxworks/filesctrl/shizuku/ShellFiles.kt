package com.vknxworks.filesctrl.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import androidx.compose.runtime.mutableIntStateOf
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.CrossDeviceException
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.Space
import rikka.shizuku.Shizuku
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Optional Shizuku support: reaches Android/data and Android/obb (which Android hides from file managers) through
 * [ShellFilesService], running as the shell user. Off unless turned on in Settings; nothing here talks to Shizuku
 * before that.
 */
object ShellFiles {
    /** Shizuku's own app. Sui (Shizuku through root) works too, without it. */
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST = 4711

    enum class Status { NotInstalled, NotRunning, TooOld, NeedsPermission, Ready }

    /** Bumped whenever Shizuku starts, stops or answers a permission request, so Compose reads [status] again. */
    val changes = mutableIntStateOf(0)

    private val lock = Any()
    @Volatile private var service: IShellFiles? = null
    private var binding: CountDownLatch? = null
    @Volatile private var listening = false

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellFilesService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("files")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) service = IShellFiles.Stub.asInterface(binder)
            release()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            release()
        }
    }

    private fun release() = synchronized(lock) { binding?.countDown(); binding = null }

    /** True when the setting is on: Android/data and Android/obb then go through Shizuku. */
    val enabled: Boolean get() = App.settings.shizuku

    /** Starts following Shizuku's state (for the Settings screen). Safe to call more than once. */
    fun listen() {
        if (listening) return
        listening = true
        try {
            Shizuku.addBinderReceivedListenerSticky { changes.intValue++ }
            Shizuku.addBinderDeadListener { service = null; release(); changes.intValue++ }
            Shizuku.addRequestPermissionResultListener { code, _ -> if (code == PERMISSION_REQUEST) changes.intValue++ }
        } catch (_: Throwable) {}
    }

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun status(ctx: Context): Status {
        if (!running()) return if (installed(ctx)) Status.NotRunning else Status.NotInstalled
        return try {
            when {
                Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> Status.TooOld
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.Ready
                else -> Status.NeedsPermission
            }
        } catch (_: Throwable) {
            Status.NotRunning
        }
    }

    /** Shows Shizuku's "Allow FilesCtrl?" prompt. */
    fun requestPermission() {
        try { Shizuku.requestPermission(PERMISSION_REQUEST) } catch (_: Throwable) {}
    }

    /** Opens Shizuku's app (to start it), if it's installed. */
    fun openShizuku(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn()
        return try { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    /** Stops the helper process (the setting was turned off). */
    fun stop() {
        val had = service != null
        service = null
        release()
        if (had) try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) {}
    }

    /** The helper, started the first time it's needed. Blocks, so never call it on the main thread. */
    private fun files(): IShellFiles {
        service?.let { if (it.asBinder().isBinderAlive) return it }
        when (status(App.context)) {
            Status.Ready -> {}
            Status.NotInstalled -> throw IOException(App.str(R.string.shizuku_error_not_installed))
            Status.NotRunning -> throw IOException(App.str(R.string.shizuku_error_not_running))
            Status.TooOld -> throw IOException(App.str(R.string.shizuku_error_too_old))
            Status.NeedsPermission -> throw IOException(App.str(R.string.shizuku_error_no_permission))
        }
        if (Looper.myLooper() == Looper.getMainLooper()) throw IOException(App.str(R.string.shizuku_error_not_running))
        val latch: CountDownLatch
        var start = false
        synchronized(lock) {
            service?.let { if (it.asBinder().isBinderAlive) return it }
            latch = binding ?: CountDownLatch(1).also { binding = it; start = true }
        }
        if (start) {
            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: Throwable) {
                release()
                throw IOException(App.str(R.string.shizuku_error_not_running))
            }
        }
        if (!latch.await(20, TimeUnit.SECONDS)) {
            release()
            throw IOException(App.str(R.string.shizuku_error_timeout))
        }
        return service ?: throw IOException(App.str(R.string.shizuku_error_not_running))
    }

    /** Runs [block] against the helper, turning a dead connection into a readable error. */
    private inline fun <T> call(block: (IShellFiles) -> T): T {
        val f = files()
        return try {
            block(f)
        } catch (e: RemoteException) {
            service = null
            throw IOException(App.str(R.string.shizuku_error_not_running))
        }
    }

    private fun fail(errno: Int, path: String): Nothing {
        if (errno == OsConstants.ENOENT) throw FileNotFoundException(App.str(R.string.not_found, path))
        throw IOException(Os.strerror(errno))
    }

    private fun entry(path: String, name: String, s: LongArray, from: Int): FileEntry = FileEntry(
        LocalVfs.ID, path, name,
        isDir = s[from] == 1L, size = s[from + 1], modified = s[from + 2],
        mode = s[from + 3].toInt(), uid = s[from + 4].toInt(), gid = s[from + 5].toInt(), isLink = s[from + 6] == 1L,
    )

    // ---------- File operations, used by LocalVfs for Android/data and Android/obb ----------

    fun list(dir: String): List<FileEntry> {
        val pfd = call { it.list(dir) } ?: throw IOException(App.str(R.string.shizuku_error_not_running))
        return DataInputStream(BufferedInputStream(ParcelFileDescriptor.AutoCloseInputStream(pfd), 64 * 1024)).use { input ->
            try {
                val err = input.readInt()
                if (err != 0) {
                    if (err == OsConstants.ENOTDIR) throw IOException(App.str(R.string.not_a_folder))
                    fail(err, dir)
                }
                val out = ArrayList<FileEntry>()
                val s = LongArray(ShellFilesService.STAT_SIZE - 1)
                while (input.readBoolean()) {
                    val name = input.readUTF()
                    for (i in s.indices) s[i] = input.readLong()
                    if (!Paths.isSafeName(name)) continue
                    out += entry(Paths.join(dir, name), name, s, 0)
                }
                out
            } catch (_: EOFException) {
                // The helper went away mid-listing.
                service = null
                throw IOException(App.str(R.string.shizuku_error_not_running))
            }
        }
    }

    fun stat(path: String): FileEntry {
        val s = call { it.stat(path) }
        if (s.isEmpty() || s[0] != 0L || s.size < ShellFilesService.STAT_SIZE) fail(s.firstOrNull()?.toInt() ?: OsConstants.EIO, path)
        return entry(path, Paths.name(path), s, 1)
    }

    /** Opens [path] with open(2) [flags] (O_RDONLY, O_RDWR, O_CREAT ...). */
    fun open(path: String, flags: Int): ParcelFileDescriptor {
        val err = IntArray(1)
        return call { it.open(path, flags, err) } ?: fail(err[0].takeIf { it != 0 } ?: OsConstants.EIO, path)
    }

    fun openRead(path: String): InputStream = ParcelFileDescriptor.AutoCloseInputStream(open(path, OsConstants.O_RDONLY))

    fun openWrite(path: String): OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(
        open(path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_TRUNC),
    )

    fun mkdir(path: String) {
        val r = call { it.mkdir(path) }
        if (r == OsConstants.EEXIST) throw IOException(App.str(R.string.already_exists_2))
        if (r != 0) throw IOException(App.str(R.string.couldn_t_create_folder, Paths.name(path)) + " (" + Os.strerror(r) + ")")
    }

    fun createFile(path: String) {
        val r = call { it.createFile(path) }
        if (r == OsConstants.EEXIST) throw IOException(App.str(R.string.already_exists_2))
        if (r != 0) fail(r, path)
    }

    fun delete(path: String) {
        val r = call { it.remove(path) }
        if (r != 0) throw IOException(App.str(R.string.couldn_t_delete, Paths.name(path), Os.strerror(r)))
    }

    fun rename(from: String, to: String) {
        val r = call { it.rename(from, to) }
        if (r == OsConstants.EXDEV) throw CrossDeviceException()
        if (r != 0) throw IOException(App.str(R.string.couldn_t_rename, Os.strerror(r)))
    }

    fun setModified(path: String, time: Long) {
        call { it.setModified(path, time) }
    }

    fun space(path: String): Space? = try {
        val s = call { it.space(path) }
        if (s.size >= 3 && s[0] == 0L) Space(s[1], s[2]) else null
    } catch (_: IOException) {
        null
    }

    fun readlink(path: String): String? = try { call { it.readlink(path) } } catch (_: IOException) { null }
}
