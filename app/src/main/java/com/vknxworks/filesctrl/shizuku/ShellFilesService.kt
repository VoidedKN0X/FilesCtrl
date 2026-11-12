package com.vknxworks.filesctrl.shizuku

import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.Keep
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Runs in a separate process that Shizuku starts as the shell user (the same access `adb shell` has), so it can
 * reach Android/data and Android/obb. FilesCtrl's own code (App, settings, strings) isn't set up in this process:
 * everything here works on plain paths and reports errors as errno values.
 */
@Keep
class ShellFilesService : IShellFiles.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun list(dir: String): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            try {
                DataOutputStream(BufferedOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]), 64 * 1024)).use { out ->
                    writeList(dir, out)
                }
            } catch (_: IOException) {
                // FilesCtrl stopped reading (the folder was left): nothing to do.
            }
        }.start()
        return pipe[0]
    }

    private fun writeList(dir: String, out: DataOutputStream) {
        val st = try { Os.stat(dir) } catch (e: ErrnoException) { out.writeInt(e.errno); return }
        if (!OsConstants.S_ISDIR(st.st_mode)) { out.writeInt(OsConstants.ENOTDIR); return }
        val names = File(dir).list() ?: run { out.writeInt(OsConstants.EACCES); return }
        out.writeInt(0)
        for (name in names) {
            val s = statOf(if (dir.endsWith("/")) dir + name else "$dir/$name")
            if (s[0] != 0L) continue // Gone since it was listed.
            out.writeBoolean(true)
            out.writeUTF(name)
            for (i in 1 until STAT_SIZE) out.writeLong(s[i])
        }
        out.writeBoolean(false)
    }

    override fun stat(path: String): LongArray = statOf(path)

    private fun statOf(path: String): LongArray = try {
        val l = Os.lstat(path)
        val link = OsConstants.S_ISLNK(l.st_mode)
        var isDir = OsConstants.S_ISDIR(l.st_mode)
        var size = l.st_size
        if (link) try {
            val t = Os.stat(path)
            isDir = OsConstants.S_ISDIR(t.st_mode)
            size = t.st_size
        } catch (_: ErrnoException) {}
        val modified = l.st_mtim.tv_sec * 1000 + l.st_mtim.tv_nsec / 1_000_000
        longArrayOf(
            0, if (isDir) 1 else 0, if (isDir) 0 else size, modified,
            l.st_mode.toLong(), l.st_uid.toLong(), l.st_gid.toLong(), if (link) 1 else 0,
        )
    } catch (e: ErrnoException) {
        longArrayOf(e.errno.toLong())
    }

    override fun open(path: String, flags: Int, err: IntArray): ParcelFileDescriptor? = try {
        val fd = Os.open(path, flags or OsConstants.O_CLOEXEC, FILE_MODE)
        try { ParcelFileDescriptor.dup(fd) } finally { Os.close(fd) }
    } catch (e: ErrnoException) {
        if (err.isNotEmpty()) err[0] = e.errno
        null
    } catch (_: IOException) {
        if (err.isNotEmpty()) err[0] = OsConstants.EIO
        null
    }

    override fun mkdir(path: String): Int = errno { Os.mkdir(path, DIR_MODE) }

    override fun createFile(path: String): Int = errno {
        Os.close(Os.open(path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_CLOEXEC, FILE_MODE))
    }

    override fun remove(path: String): Int = errno { Os.remove(path) }

    override fun rename(from: String, to: String): Int = errno { Os.rename(from, to) }

    override fun setModified(path: String, time: Long): Int =
        if (File(path).setLastModified(time)) 0 else OsConstants.EPERM

    override fun space(path: String): LongArray = try {
        val s = StatFs(path)
        longArrayOf(0, s.totalBytes, s.availableBytes)
    } catch (_: Exception) {
        longArrayOf(OsConstants.EIO.toLong())
    }

    override fun readlink(path: String): String? = try { Os.readlink(path) } catch (_: ErrnoException) { null }

    private inline fun errno(block: () -> Unit): Int = try {
        block()
        0
    } catch (e: ErrnoException) {
        e.errno
    } catch (_: Exception) {
        OsConstants.EIO
    }

    companion object {
        /** Number of values in a successful [stat] result. */
        const val STAT_SIZE = 8
        private const val FILE_MODE = 432 // 0660
        private const val DIR_MODE = 505 // 0771
    }
}
