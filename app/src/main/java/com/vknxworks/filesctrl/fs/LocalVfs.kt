package com.vknxworks.filesctrl.fs

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.shizuku.ShellFiles

/** A storage volume on the phone (internal storage, SD card, USB drive). */
data class Volume(val path: String, val label: String, val removable: Boolean, val primary: Boolean)

/**
 * The phone's own storage. Needs "All files access" (MANAGE_EXTERNAL_STORAGE) outside the app's folders.
 * Android/data and Android/obb are hidden from file managers even then; with Shizuku turned on in Settings they're
 * reached through [ShellFiles] instead.
 */
class LocalVfs(private val context: Context) : Vfs {
    override val id = ID
    override val title = App.str(R.string.you_this_phone)
    override val isRemote = false
    override val canChmod = false

    fun volumes(): List<Volume> {
        val sm = context.getSystemService(StorageManager::class.java)
        val out = sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
            Volume(dir.absolutePath, v.getDescription(context) ?: dir.name, v.isRemovable, v.isPrimary)
        }
        return out.ifEmpty { listOf(Volume(Environment.getExternalStorageDirectory().absolutePath, App.str(R.string.internal_storage), false, true)) }
    }

    val primaryRoot: String get() = volumes().firstOrNull { it.primary }?.path ?: Environment.getExternalStorageDirectory().absolutePath

    override fun rootOf(path: String): Pair<String, String> {
        val v = volumes().filter { Paths.isWithin(path, it.path) }.maxByOrNull { it.path.length }
        return if (v != null) v.path to v.label else "/" to App.str(R.string.root)
    }

    /** True when [path] is (or is inside) Android/data or Android/obb on any storage volume. */
    fun isProtected(path: String): Boolean = PROTECTED.matches(path)

    /** True when [path] is exactly Android/data or Android/obb. */
    fun isProtectedRoot(path: String): Boolean = PROTECTED_ROOT.matches(path.trimEnd('/'))

    /** True when [path] goes through Shizuku instead of being opened directly. */
    fun viaShell(path: String): Boolean = isProtected(path) && ShellFiles.enabled

    /** True when [path] can be opened as a plain [File] by FilesCtrl (and handed to other apps that way). */
    fun direct(path: String): Boolean = !viaShell(path)

    fun readlink(path: String): String? =
        if (viaShell(path)) ShellFiles.readlink(path) else try { Os.readlink(path) } catch (_: ErrnoException) { null }

    private fun entry(f: File): FileEntry {
        var mode = 0
        var uid = -1
        var gid = -1
        var link = false
        var isDir = f.isDirectory
        try {
            val st = Os.lstat(f.path)
            mode = st.st_mode
            uid = st.st_uid
            gid = st.st_gid
            link = OsConstants.S_ISLNK(st.st_mode)
            if (link) isDir = try { OsConstants.S_ISDIR(Os.stat(f.path).st_mode) } catch (_: ErrnoException) { false }
        } catch (_: ErrnoException) {}
        return FileEntry(ID, f.path, f.name, isDir, if (isDir) 0 else f.length(), f.lastModified(), mode, uid, gid, link)
    }

    override fun list(dir: String): List<FileEntry> {
        if (viaShell(dir)) return ShellFiles.list(dir)
        // Android shows file managers an (almost) empty folder here; say what's needed instead.
        if (isProtectedRoot(dir)) throw IOException(App.str(R.string.shizuku_needed_here))
        val d = File(dir)
        if (!d.exists()) throw FileNotFoundException(App.str(R.string.not_found, dir))
        if (!d.isDirectory) throw IOException(App.str(R.string.not_a_folder))
        val files = d.listFiles() ?: throw IOException(
            App.str(if (isProtected(dir)) R.string.shizuku_needed_here else R.string.can_t_read_this_folder_permission),
        )
        return files.map { entry(it) }
    }

    override fun stat(path: String): FileEntry {
        if (viaShell(path)) return ShellFiles.stat(path)
        val f = File(path)
        if (!f.exists() && !isDanglingLink(path)) throw FileNotFoundException(App.str(R.string.not_found, path))
        return entry(f)
    }

    private fun isDanglingLink(path: String) = try { OsConstants.S_ISLNK(Os.lstat(path).st_mode) } catch (_: ErrnoException) { false }

    override fun openRead(path: String): InputStream = if (viaShell(path)) ShellFiles.openRead(path) else FileInputStream(path)
    override fun openWrite(path: String): OutputStream = if (viaShell(path)) ShellFiles.openWrite(path) else FileOutputStream(path, false)

    override fun mkdir(path: String) {
        if (viaShell(path)) return ShellFiles.mkdir(path)
        val f = File(path)
        if (f.exists()) throw IOException(App.str(R.string.already_exists_2))
        if (!f.mkdir()) throw IOException(App.str(R.string.couldn_t_create_folder, f.name))
    }

    override fun createFile(path: String) {
        if (viaShell(path)) return ShellFiles.createFile(path)
        if (!File(path).createNewFile()) throw IOException(App.str(R.string.already_exists_2))
    }

    override fun delete(path: String, isDir: Boolean) {
        if (viaShell(path)) return ShellFiles.delete(path)
        try {
            Os.remove(path) // remove(3) handles both files and empty folders
        } catch (e: ErrnoException) {
            throw IOException(App.str(R.string.couldn_t_delete, Paths.name(path), Os.strerror(e.errno)))
        }
    }

    override fun rename(from: String, to: String) {
        if (viaShell(from) || viaShell(to)) {
            if (!from.equals(to, ignoreCase = true) && exists(to)) throw IOException(App.str(R.string.already_exists, Paths.name(to)))
            return ShellFiles.rename(from, to)
        }
        if (File(to).exists() && !from.equals(to, ignoreCase = true)) throw IOException(App.str(R.string.already_exists, Paths.name(to)))
        try {
            Os.rename(from, to)
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EXDEV) throw CrossDeviceException()
            throw IOException(App.str(R.string.couldn_t_rename, Os.strerror(e.errno)))
        }
    }

    override fun setModified(path: String, time: Long) {
        if (viaShell(path)) ShellFiles.setModified(path, time) else File(path).setLastModified(time)
    }

    override fun space(path: String): Space? = if (viaShell(path)) ShellFiles.space(path) else try {
        val s = StatFs(path)
        Space(s.totalBytes, s.availableBytes)
    } catch (_: Exception) { null }

    companion object {
        const val ID = "local"

        // Internal storage is /storage/emulated/<user>, SD cards and USB drives /storage/<id>.
        private val PROTECTED = Regex("^/storage/(emulated/\\d+|[^/]+)/Android/(data|obb)(/.*)?$", RegexOption.IGNORE_CASE)
        private val PROTECTED_ROOT = Regex("^/storage/(emulated/\\d+|[^/]+)/Android/(data|obb)$", RegexOption.IGNORE_CASE)
    }
}

/** A move between two volumes; the caller falls back to copy + delete. */
class CrossDeviceException : IOException(App.str(R.string.different_volume))
