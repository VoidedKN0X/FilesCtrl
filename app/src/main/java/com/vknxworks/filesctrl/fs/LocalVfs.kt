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

/** A storage volume on the phone (internal storage, SD card, USB drive). */
data class Volume(val path: String, val label: String, val removable: Boolean, val primary: Boolean)

/** The phone's own storage. Needs "All files access" (MANAGE_EXTERNAL_STORAGE) outside the app's folders. */
class LocalVfs(private val context: Context) : Vfs {
    override val id = ID
    override val title = "This phone"
    override val isRemote = false
    override val canChmod = false

    fun volumes(): List<Volume> {
        val sm = context.getSystemService(StorageManager::class.java)
        val out = sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
            Volume(dir.absolutePath, v.getDescription(context) ?: dir.name, v.isRemovable, v.isPrimary)
        }
        return out.ifEmpty { listOf(Volume(Environment.getExternalStorageDirectory().absolutePath, "Internal storage", false, true)) }
    }

    val primaryRoot: String get() = volumes().firstOrNull { it.primary }?.path ?: Environment.getExternalStorageDirectory().absolutePath

    override fun rootOf(path: String): Pair<String, String> {
        val v = volumes().filter { Paths.isWithin(path, it.path) }.maxByOrNull { it.path.length }
        return if (v != null) v.path to v.label else "/" to "Root"
    }

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
        val d = File(dir)
        if (!d.exists()) throw FileNotFoundException("$dir: not found")
        if (!d.isDirectory) throw IOException("Not a folder")
        val files = d.listFiles() ?: throw IOException("Can't read this folder (permission denied)")
        return files.map { entry(it) }
    }

    override fun stat(path: String): FileEntry {
        val f = File(path)
        if (!f.exists() && !isDanglingLink(path)) throw FileNotFoundException("$path: not found")
        return entry(f)
    }

    private fun isDanglingLink(path: String) = try { OsConstants.S_ISLNK(Os.lstat(path).st_mode) } catch (_: ErrnoException) { false }

    override fun openRead(path: String): InputStream = FileInputStream(path)
    override fun openWrite(path: String): OutputStream = FileOutputStream(path, false)

    override fun mkdir(path: String) {
        val f = File(path)
        if (f.exists()) throw IOException("Already exists")
        if (!f.mkdir()) throw IOException("Couldn't create folder ${f.name}")
    }

    override fun createFile(path: String) {
        if (!File(path).createNewFile()) throw IOException("Already exists")
    }

    override fun delete(path: String, isDir: Boolean) {
        try {
            Os.remove(path) // remove(3) handles both files and empty folders
        } catch (e: ErrnoException) {
            throw IOException("Couldn't delete ${Paths.name(path)}: ${Os.strerror(e.errno)}")
        }
    }

    override fun rename(from: String, to: String) {
        if (File(to).exists() && !from.equals(to, ignoreCase = true)) throw IOException("${Paths.name(to)} already exists")
        try {
            Os.rename(from, to)
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EXDEV) throw CrossDeviceException()
            throw IOException("Couldn't rename: ${Os.strerror(e.errno)}")
        }
    }

    override fun setModified(path: String, time: Long) { File(path).setLastModified(time) }

    override fun space(path: String): Space? = try {
        val s = StatFs(path)
        Space(s.totalBytes, s.availableBytes)
    } catch (_: Exception) { null }

    companion object { const val ID = "local" }
}

/** A move between two volumes; the caller falls back to copy + delete. */
class CrossDeviceException : IOException("Different volume")
