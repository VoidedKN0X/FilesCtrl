package com.vknxworks.filesctrl.ops

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.CrossDeviceException
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.Vfs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** What to do when a pasted item's name is already taken in the destination. */
enum class Conflict { Replace, KeepBoth, Skip }

enum class TaskState { Running, Done, Failed, Cancelled }

/** One running or finished operation, observable from Compose. */
class OpTask(val id: Int, val title: String) {
    var state by mutableStateOf(TaskState.Running)
    var detail by mutableStateOf("Preparing…")
    var bytesTotal by mutableLongStateOf(0L)
    var bytesDone by mutableLongStateOf(0L)
    var filesTotal by mutableStateOf(0)
    var filesDone by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    /** Skipped items and other non-fatal problems. */
    val warnings = mutableStateListOf<String>()
    internal var job: Job? = null

    val progress: Float?
        get() = when {
            bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            filesTotal > 0 -> (filesDone.toFloat() / filesTotal).coerceIn(0f, 1f)
            else -> null
        }

    fun cancel() { job?.cancel() }
}

/** Runs file operations in the background (kept alive by [OpService]) and reports progress. */
object Ops {
    private lateinit var appContext: Context
    val tasks = mutableStateListOf<OpTask>()
    private var nextId = 1

    /** Emits the folders an operation changed, so open screens can refresh. */
    private val _changed = MutableSharedFlow<Location>(extraBufferCapacity = 32)
    val changed = _changed.asSharedFlow()

    val active: List<OpTask> get() = tasks.filter { it.state == TaskState.Running }

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    /** Lets open screens know a folder changed outside an operation (e.g. a file saved in the editor). */
    fun notifyChanged(loc: Location) { _changed.tryEmit(loc) }

    fun clearFinished() { tasks.removeAll { it.state != TaskState.Running } }

    private fun launch(title: String, touched: List<Location>, work: suspend (OpTask) -> Unit): OpTask {
        val t = OpTask(nextId++, title)
        tasks.add(0, t)
        try {
            appContext.startForegroundService(Intent(appContext, OpService::class.java))
        } catch (_: Exception) {
            // Started from the background on a strict device: the task still runs while the app process lives.
        }
        t.job = App.scope.launch {
            try {
                work(t)
                t.state = TaskState.Done
                t.detail = if (t.warnings.isEmpty()) "Done" else "Done with ${t.warnings.size} problem(s)"
            } catch (e: CancellationException) {
                t.state = TaskState.Cancelled
                t.detail = "Cancelled"
            } catch (e: Exception) {
                t.state = TaskState.Failed
                t.error = e.message ?: e.javaClass.simpleName
                t.detail = "Failed"
            } finally {
                touched.distinct().forEach { _changed.tryEmit(it) }
            }
        }
        return t
    }

    // ---------- Copy / move ----------

    fun paste(items: List<FileEntry>, dest: Location, move: Boolean, conflict: Conflict): OpTask {
        val verb = if (move) "Moving" else "Copying"
        val title = "$verb ${plural(items.size, "item")}"
        val touched = listOf(dest) + if (move) items.map { it.parentLocation } else emptyList()
        return launch(title, touched) { t ->
            val dst = App.vfs.get(dest.vfsId)
            for (src in items) {
                if (src.isDir && src.vfsId == dest.vfsId && Paths.isWithin(dest.path, src.path))
                    throw IOException("Can't ${if (move) "move" else "copy"} ${src.name} into itself")
            }
            // Count what needs copying (moves within one file system are just renames).
            t.detail = "Counting files…"
            for (src in items) {
                coroutineContext.ensureActive()
                if (move && src.vfsId == dest.vfsId) { t.filesTotal++; continue }
                val (bytes, files) = measure(App.vfs.get(src.vfsId), src)
                t.bytesTotal += bytes
                t.filesTotal += files
            }
            for (src in items) {
                coroutineContext.ensureActive()
                val srcVfs = App.vfs.get(src.vfsId)
                var name = src.name
                val sameFolder = src.vfsId == dest.vfsId && src.parentLocation.path == dest.path
                if (sameFolder && move) { t.filesDone++; continue }
                var target = Paths.join(dest.path, name)
                // Replacing a folder that contains the item itself would delete the item; never do that.
                if (src.vfsId == dest.vfsId && target != src.path && Paths.isWithin(src.path, target)) {
                    t.warnings += "Skipped ${src.name}: it's inside the item it would replace"
                    t.filesDone++
                    continue
                }
                if (dst.exists(target)) {
                    val policy = if (sameFolder) Conflict.KeepBoth else conflict
                    when (policy) {
                        Conflict.Skip -> { t.warnings += "Skipped ${src.name}"; t.filesDone++; continue }
                        Conflict.KeepBoth -> { name = Paths.unique(dest.path, name) { dst.exists(it) }; target = Paths.join(dest.path, name) }
                        Conflict.Replace -> {
                            val existing = dst.stat(target)
                            // Folders onto folders merge; anything else replaces what's there.
                            if (!(existing.isDir && src.isDir)) deleteTree(dst, existing, null)
                        }
                    }
                }
                if (move && src.vfsId == dest.vfsId && !dst.exists(target)) {
                    try {
                        t.detail = src.name
                        dst.rename(src.path, target)
                        t.filesDone++
                        continue
                    } catch (_: CrossDeviceException) {
                        // Different volume: fall through to copy + delete.
                        val (bytes, files) = measure(srcVfs, src)
                        t.bytesTotal += bytes; t.filesTotal += files - 1
                    }
                }
                copyTree(srcVfs, src, dst, target, t)
                if (move) deleteTree(srcVfs, src, null)
            }
        }
    }

    private suspend fun measure(vfs: Vfs, e: FileEntry): Pair<Long, Int> {
        if (!e.isDir || e.isLink) return e.size to 1
        var bytes = 0L
        var files = 1
        for (c in vfs.list(e.path)) {
            coroutineContext.ensureActive()
            val (b, f) = measure(vfs, c)
            bytes += b; files += f
        }
        return bytes to files
    }

    private suspend fun copyTree(src: Vfs, e: FileEntry, dst: Vfs, target: String, t: OpTask) {
        coroutineContext.ensureActive()
        if (e.isLink && e.isDir) {
            // Following folder links could loop or copy a whole other tree; leave them out.
            t.warnings += "Skipped folder link ${e.name}"
            t.filesDone++
            return
        }
        if (e.isDir && !e.isLink) {
            if (!dst.exists(target)) dst.mkdir(target)
            t.filesDone++
            for (c in src.list(e.path)) copyTree(src, c, dst, Paths.join(target, c.name), t)
            dst.setModified(target, e.modified)
        } else {
            try {
                copyFile(src, e, dst, target, t)
            } catch (ex: java.io.FileNotFoundException) {
                if (!e.isLink) throw ex
                t.warnings += "Skipped broken link ${e.name}"
            }
            t.filesDone++
        }
    }

    private suspend fun copyFile(src: Vfs, e: FileEntry, dst: Vfs, target: String, t: OpTask) {
        t.detail = e.name
        val buf = ByteArray(1024 * 1024)
        var ok = false
        var created = false
        try {
            src.openRead(e.path).use { input ->
                dst.openWrite(target).also { created = true }.use { out ->
                    var lastUi = 0L
                    var pending = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        pending += n
                        val now = System.nanoTime()
                        if (now - lastUi > 150_000_000) { t.bytesDone += pending; pending = 0; lastUi = now }
                    }
                    t.bytesDone += pending
                }
            }
            dst.setModified(target, e.modified)
            ok = true
        } finally {
            // Don't leave half-copied files behind on failure or cancel (only ones this copy created).
            if (!ok && created) try { dst.delete(target, false) } catch (_: Exception) {}
        }
    }

    // ---------- Delete ----------

    fun delete(items: List<FileEntry>): OpTask =
        launch("Deleting ${plural(items.size, "item")}", items.map { it.parentLocation }.distinct()) { t ->
            t.filesTotal = items.size
            for (e in items) {
                coroutineContext.ensureActive()
                deleteTree(App.vfs.get(e.vfsId), e, t)
                t.filesDone++
            }
        }

    private suspend fun deleteTree(vfs: Vfs, e: FileEntry, t: OpTask?) {
        coroutineContext.ensureActive()
        t?.detail = e.name
        if (e.isDir && !e.isLink) {
            for (c in vfs.list(e.path)) deleteTree(vfs, c, t)
        }
        vfs.delete(e.path, e.isDir && !e.isLink)
    }

    // ---------- Zip ----------

    fun compress(items: List<FileEntry>, dest: Location, zipName: String): OpTask =
        launch("Compressing to $zipName", listOf(dest)) { t ->
            val dst = App.vfs.get(dest.vfsId)
            for (e in items) {
                val (b, f) = measure(App.vfs.get(e.vfsId), e)
                t.bytesTotal += b; t.filesTotal += f
            }
            val target = Paths.join(dest.path, Paths.unique(dest.path, zipName) { dst.exists(it) })
            var ok = false
            try {
                ZipOutputStream(BufferedOutputStream(dst.openWrite(target), 256 * 1024)).use { zip ->
                    for (e in items) addToZip(zip, App.vfs.get(e.vfsId), e, e.name, t)
                }
                ok = true
            } finally {
                if (!ok) try { dst.delete(target, false) } catch (_: Exception) {}
            }
        }

    private suspend fun addToZip(zip: ZipOutputStream, vfs: Vfs, e: FileEntry, name: String, t: OpTask) {
        coroutineContext.ensureActive()
        t.detail = e.name
        if (e.isLink && e.isDir) { t.warnings += "Skipped folder link ${e.name}"; t.filesDone++; return }
        if (e.isDir && !e.isLink) {
            zip.putNextEntry(ZipEntry("$name/").apply { time = e.modified })
            zip.closeEntry()
            t.filesDone++
            for (c in vfs.list(e.path)) addToZip(zip, vfs, c, "$name/${c.name}", t)
            return
        }
        val input0 = try { vfs.openRead(e.path) } catch (ex: java.io.FileNotFoundException) {
            if (!e.isLink) throw ex
            t.warnings += "Skipped broken link ${e.name}"; t.filesDone++; return
        }
        zip.putNextEntry(ZipEntry(name).apply { time = e.modified })
        val buf = ByteArray(256 * 1024)
        input0.use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                zip.write(buf, 0, n)
                t.bytesDone += n
            }
        }
        zip.closeEntry()
        t.filesDone++
    }

    fun extract(zipFile: FileEntry, dest: Location): OpTask =
        launch("Extracting ${zipFile.name}", listOf(dest)) { t ->
            val src = App.vfs.get(zipFile.vfsId)
            val dst = App.vfs.get(dest.vfsId)
            val folderName = Paths.unique(dest.path, zipFile.name.substringBeforeLast('.').ifBlank { "extracted" }) { dst.exists(it) }
            val root = Paths.join(dest.path, folderName)
            dst.mkdir(root)
            t.bytesTotal = zipFile.size
            val counting = CountingInput(BufferedInputStream(src.openRead(zipFile.path), 256 * 1024)) { t.bytesDone = it }
            ZipInputStream(counting).use { zip ->
                val buf = ByteArray(256 * 1024)
                val made = HashSet<String>().apply { add(root) }
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    // Refuse paths that would escape the target folder ("zip slip").
                    val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
                    if (parts.isEmpty() || parts.any { it == ".." }) { t.warnings += "Skipped unsafe entry ${entry.name}"; continue }
                    t.detail = parts.last()
                    var dir = root
                    val dirParts = if (entry.isDirectory) parts else parts.dropLast(1)
                    for (p in dirParts) {
                        dir = Paths.join(dir, p)
                        if (made.add(dir) && !dst.exists(dir)) dst.mkdir(dir)
                    }
                    if (entry.isDirectory) continue
                    val target = Paths.join(dir, parts.last())
                    dst.openWrite(target).use { out ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = zip.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    if (entry.time > 0) dst.setModified(target, entry.time)
                    t.filesDone++
                }
            }
        }

    // ---------- Recursive size (for Properties) ----------

    suspend fun folderSize(e: FileEntry): Pair<Long, Int> = measure(App.vfs.get(e.vfsId), e)

    fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}

private class CountingInput(private val inner: java.io.InputStream, private val report: (Long) -> Unit) : java.io.InputStream() {
    private var count = 0L
    override fun read(): Int = inner.read().also { if (it >= 0) bump(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
    override fun close() = inner.close()
    private fun bump(n: Long) { count += n; report(count) }
}
