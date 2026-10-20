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
import com.vknxworks.filesctrl.fs.LocalVfs
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
import java.io.File
import java.io.IOException
import com.vknxworks.filesctrl.fs.ArchiveVfs
import kotlinx.coroutines.job
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
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
        // Keep only a few finished ones around (the notification is where results are shown now).
        tasks.filter { it.state != TaskState.Running }.drop(10).forEach { tasks.remove(it) }
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
                // With the app open there's no notification for the result, so say when something went wrong.
                if (t.state == TaskState.Failed || (t.state == TaskState.Done && t.warnings.isNotEmpty())) {
                    val msg = if (t.state == TaskState.Failed) "${t.title} failed: ${t.error}"
                    else "${t.title}: ${t.warnings.size} skipped (${t.warnings.first()})"
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (com.vknxworks.filesctrl.MainActivity.inForeground) {
                            android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
                if (!Paths.isSafeName(name)) { t.warnings += "Skipped unsafe name ${src.name}"; t.filesDone++; continue }
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
                checkInside(dst, target, dest.path)
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
            for (c in src.list(e.path)) {
                // Names come from the source (maybe a remote server): never let one climb out of the target folder.
                if (!Paths.isSafeName(c.name)) { t.warnings += "Skipped unsafe name ${c.name}"; continue }
                val childTarget = Paths.join(target, c.name)
                checkInside(dst, childTarget, target)
                copyTree(src, c, dst, childTarget, t)
            }
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

    fun delete(items: List<FileEntry>): OpTask {
        return deleteTask(items)
    }

    private fun deleteTask(items: List<FileEntry>): OpTask =
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

    // ---------- Archives ----------

    fun compress(items: List<FileEntry>, dest: Location, name: String, format: ArchiveFormat = ArchiveFormat.ZIP, password: CharArray? = null): OpTask {
        return compressTask(items, dest, name, format, password?.takeIf { it.isNotEmpty() && format.passwords })
    }

    /** Walks [e] (recursively for folders) and hands each folder and file to [sink] with its path in the archive. */
    private suspend fun walk(vfs: Vfs, e: FileEntry, name: String, t: OpTask, sink: ArchiveSink) {
        coroutineContext.ensureActive()
        t.detail = e.name
        if (e.isLink && e.isDir) { t.warnings += "Skipped folder link ${e.name}"; t.filesDone++; return }
        if (e.isDir && !e.isLink) {
            sink.dir("$name/", e.modified)
            t.filesDone++
            for (c in vfs.list(e.path)) walk(vfs, c, "$name/${c.name}", t, sink)
            return
        }
        val input = try { vfs.openRead(e.path) } catch (ex: java.io.FileNotFoundException) {
            if (!e.isLink) throw ex
            t.warnings += "Skipped broken link ${e.name}"; t.filesDone++; return
        }
        input.use { sink.file(name, e.size, e.modified, it) { n -> t.bytesDone += n } }
        t.filesDone++
    }

    private fun compressTask(items: List<FileEntry>, dest: Location, name: String, format: ArchiveFormat, password: CharArray?): OpTask =
        launch("Compressing to $name", listOf(dest)) { t ->
            val dst = App.vfs.get(dest.vfsId)
            for (e in items) {
                val (b, f) = measure(App.vfs.get(e.vfsId), e)
                t.bytesTotal += b; t.filesTotal += f
            }
            val target = Paths.join(dest.path, Paths.unique(dest.path, name) { dst.exists(it) })
            var ok = false
            var tmp: File? = null
            try {
                if (format == ArchiveFormat.SEVENZ) {
                    // 7z is written with random access: straight into the target on the phone, else via a temporary file.
                    val out = if (dst is LocalVfs) File(target) else File.createTempFile("pack", ".7z", appContext.cacheDir).also { tmp = it }
                    SevenZOutputFile(out, password).use { z ->
                        z.setContentCompression(SevenZMethod.LZMA2)
                        val sink = SevenZSink(z)
                        for (e in items) walk(App.vfs.get(e.vfsId), e, e.name, t, sink)
                        z.finish()
                    }
                    tmp?.let { f ->
                        t.detail = "Uploading $name"
                        f.inputStream().use { input -> dst.openWrite(target).use { input.copyTo(it, 1 shl 20) } }
                    }
                } else {
                    BufferedOutputStream(dst.openWrite(target), 256 * 1024).use { raw ->
                        val sink: ArchiveSink = when (format) {
                            ArchiveFormat.ZIP -> if (password != null) Zip4jSink(raw, password) else ZipSink(raw)
                            else -> TarSink(raw, format)
                        }
                        sink.use { for (e in items) walk(App.vfs.get(e.vfsId), e, e.name, t, it) }
                    }
                }
                ok = true
            } finally {
                tmp?.delete()
                if (!ok) try { dst.delete(target, false) } catch (_: Exception) {}
            }
        }

    /**
     * Extracts any supported archive ([com.vknxworks.filesctrl.fs.ArchiveVfs]) into a new folder in [dest] named
     * after it, or, for a single compressed file like "notes.txt.gz", puts the one file straight into [dest].
     */
    fun extract(archive: FileEntry, dest: Location, password: CharArray? = null): OpTask {
        return extractTask(archive, dest, password)
    }

    private fun extractTask(archive: FileEntry, dest: Location, password: CharArray?): OpTask =
        launch("Extracting ${archive.name}", listOf(dest)) { t ->
            val arc = App.vfs.archive(ArchiveVfs.idFor(archive.location)) ?: throw IOException("Couldn't open ${archive.name}")
            if (password != null) arc.password = password
            val dst = App.vfs.get(dest.vfsId)
            val job = coroutineContext.job
            t.detail = "Reading ${archive.name}"
            t.bytesTotal = arc.totalSize()
            val single = arc.isSingleFile
            val root = if (single) dest.path else Paths.join(dest.path, Paths.unique(dest.path, arc.baseName) { dst.exists(it) }).also { dst.mkdir(it) }
            val made = HashSet<String>().apply { add(root) }
            val buf = ByteArray(256 * 1024)
            var created: String? = null
            try {
                arc.forEachEntry { e, input ->
                    job.ensureActive()
                    t.detail = e.name
                    // Entry paths are already cleaned up by the archive reader; check again against the real target.
                    val rel = e.path.trimStart('/')
                    val target = if (single) Paths.join(root, Paths.unique(root, e.name) { dst.exists(it) }) else Paths.join(root, rel)
                    val dirs = if (input == null) target else Paths.parent(target)
                    if (!single) {
                        var d = root
                        for (part in dirs.removePrefix(root).trim('/').split('/').filter { it.isNotEmpty() }) {
                            d = Paths.join(d, part)
                            checkInside(dst, d, root)
                            if (made.add(d) && !dst.exists(d)) dst.mkdir(d)
                        }
                    }
                    if (input == null) return@forEachEntry
                    checkInside(dst, target, root, allowRoot = single)
                    created = target
                    dst.openWrite(target).use { out ->
                        while (true) {
                            job.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            t.bytesDone += n
                        }
                    }
                    created = null
                    if (e.modified > 0) dst.setModified(target, e.modified)
                    t.filesDone++
                }
            } catch (e: Exception) {
                // Leave no half-written file behind.
                created?.let { try { dst.delete(it, false) } catch (_: Exception) {} }
                throw e
            }
            if (arc.skippedUnsafe > 0) t.warnings += "Left out ${plural(arc.skippedUnsafe, "entry")} with unsafe names"
            if (arc.skippedLinks > 0) t.warnings += "Left out ${plural(arc.skippedLinks, "link")}"
        }

    /**
     * Refuses to write [target] unless it's inside [dir]. Remote names are already filtered, but on the phone a
     * folder link inside the destination could still point elsewhere, so local targets are checked by their real
     * (canonical) path too.
     */
    private fun checkInside(dst: Vfs, target: String, dir: String, allowRoot: Boolean = false) {
        if (!Paths.isWithin(target, dir) || (!allowRoot && target.trimEnd('/') == dir.trimEnd('/'))) throw IOException("Refused to write outside the destination: ${Paths.name(target)}")
        if (dst.id != LocalVfs.ID) return
        val base = File(dir).canonicalPath
        val real = File(target).canonicalPath
        if (!real.startsWith(base + File.separator)) throw IOException("Refused to write outside the destination: ${Paths.name(target)}")
    }

    // ---------- Recursive size (for Properties) ----------

    suspend fun folderSize(e: FileEntry): Pair<Long, Int> = measure(App.vfs.get(e.vfsId), e)

    fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
