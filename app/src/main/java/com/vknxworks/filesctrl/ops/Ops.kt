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
import kotlinx.coroutines.flow.first
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
import com.vknxworks.filesctrl.R

/** A file another app shared with FilesCtrl: its name, size (-1 if unknown) and how to read it. */
class IncomingItem(val name: String, val size: Long, val open: () -> java.io.InputStream, val discard: () -> Unit = {})

/** What to do when a pasted item's name is already taken in the destination. */
enum class Conflict { Replace, KeepBoth, Skip }

enum class TaskState { Running, Done, Failed, Cancelled }

/**
 * One running or finished operation, observable from Compose. [uses] are the file systems (vfs ids) it reads or
 * writes, so locking the app can close every other server connection straight away.
 */
class OpTask(val id: Int, val title: String, internal val uses: Set<String> = emptySet(), val pausable: Boolean = true) {
    var state by mutableStateOf(TaskState.Running)
    /** Paused by the user: the work waits at its next step (a file being copied waits mid-file). */
    var paused by mutableStateOf(false)
        private set
    /** Mirrors [paused] for the worker thread, which waits on it. */
    private val pauseFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Starts the same operation again (after it failed or was cancelled); null when it can't be repeated. */
    internal var retry: (() -> OpTask)? = null
    val canRetry: Boolean get() = retry != null && (state == TaskState.Failed || state == TaskState.Cancelled)
    var detail by mutableStateOf(App.str(R.string.preparing))
    var bytesTotal by mutableLongStateOf(0L)
    var bytesDone by mutableLongStateOf(0L)
    var filesTotal by mutableStateOf(0)
    var filesDone by mutableStateOf(0)
    var error by mutableStateOf<String?>(null)
    /** Skipped items and other non-fatal problems. */
    val warnings = mutableStateListOf<String>()
    internal var job: Job? = null

    /**
     * True when the operation reads from or writes to a server (also through an archive or vault that's on one),
     * so [OpService] keeps Wi-Fi awake for it. Worked out once from [uses]; when in doubt it's true.
     */
    internal val onNetwork: Boolean = try { App.vfs.onNetwork(uses) } catch (_: Exception) { true }

    val progress: Float?
        get() = when {
            bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            filesTotal > 0 -> (filesDone.toFloat() / filesTotal).coerceIn(0f, 1f)
            else -> null
        }

    fun cancel() { job?.cancel() }

    fun pause() {
        if (!pausable || state != TaskState.Running) return
        paused = true
        pauseFlow.value = true
    }

    fun resume() {
        paused = false
        pauseFlow.value = false
    }

    /** Waits here while paused (and stops if cancelled meanwhile). Called by the work between steps. */
    internal suspend fun checkPause() {
        if (pauseFlow.value) {
            val before = detail
            detail = App.str(R.string.transfer_paused)
            pauseFlow.first { !it }
            detail = before
        }
    }
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

    /** The file systems (vfs ids) that running operations still use. */
    fun inUse(): Set<String> = active.flatMapTo(HashSet()) { it.uses }

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    /** Lets open screens know a folder changed outside an operation (e.g. a file saved in the editor). */
    fun notifyChanged(loc: Location) { _changed.tryEmit(loc) }

    fun clearFinished() { tasks.removeAll { it.state != TaskState.Running } }

    /** Runs a failed or cancelled operation again; the old entry makes way for the new one. */
    fun retry(t: OpTask): OpTask? {
        if (!t.canRetry) return null
        val again = t.retry ?: return null
        tasks.remove(t)
        return again()
    }

    private fun launch(
        title: String,
        touched: List<Location>,
        uses: Set<String>,
        pausable: Boolean = true,
        retry: (() -> OpTask)? = null,
        work: suspend (OpTask) -> Unit,
    ): OpTask {
        val t = OpTask(nextId++, title, uses, pausable)
        t.retry = retry
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
                t.detail = if (t.warnings.isEmpty()) App.str(R.string.lock_done) else App.plural(R.plurals.done_with_problems, t.warnings.size, t.warnings.size)
            } catch (e: CancellationException) {
                t.state = TaskState.Cancelled
                t.detail = App.str(R.string.cancelled)
                t.resume()
            } catch (e: Exception) {
                t.state = TaskState.Failed
                t.error = e.message ?: e.javaClass.simpleName
                t.detail = App.str(R.string.failed_5)
            } finally {
                touched.distinct().forEach { _changed.tryEmit(it) }
                // With the app open there's no notification for the result, so say when something went wrong.
                if (t.state == TaskState.Failed || (t.state == TaskState.Done && t.warnings.isNotEmpty())) {
                    val msg = if (t.state == TaskState.Failed) App.str(R.string.failed_3, t.title, t.error)
                    else App.str(R.string.skipped, t.title, t.warnings.size, t.warnings.first())
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

    /**
     * Copies or moves [items] into [dest]. With [resume] (a retry) the work carries on where it stopped: a file that is
     * already in the destination with the same size counts as done, folders are merged, and an item a move already
     * took away is left out. Everything else follows [conflict] as the first time.
     */
    fun paste(items: List<FileEntry>, dest: Location, move: Boolean, conflict: Conflict, resume: Boolean = false): OpTask {
        val title = App.plural(if (move) R.plurals.moving_items else R.plurals.copying_items, items.size, items.size)
        val touched = listOf(dest) + if (move) items.map { it.parentLocation } else emptyList()
        return launch(title, touched, items.mapTo(HashSet()) { it.vfsId } + dest.vfsId, retry = { paste(items, dest, move, conflict, resume = true) }) { t ->
            val dst = App.vfs.get(dest.vfsId)
            for (src in items) {
                if (src.isDir && src.vfsId == dest.vfsId && Paths.isWithin(dest.path, src.path))
                    throw IOException(App.str(if (move) R.string.cant_move_into_itself else R.string.cant_copy_into_itself, src.name))
            }
            // Count what needs copying (moves within one file system are just renames).
            t.detail = App.str(R.string.counting_files)
            // A retried move: what was already moved isn't at its old place any more.
            val todo = if (resume && move) items.filter { runCatching { App.vfs.get(it.vfsId).exists(it.path) }.getOrDefault(true) } else items
            for (src in todo) {
                coroutineContext.ensureActive()
                t.checkPause()
                if (move && src.vfsId == dest.vfsId) { t.filesTotal++; continue }
                val (bytes, files) = measure(App.vfs.get(src.vfsId), src)
                t.bytesTotal += bytes
                t.filesTotal += files
            }
            for (src in todo) {
                coroutineContext.ensureActive()
                t.checkPause()
                val srcVfs = App.vfs.get(src.vfsId)
                var name = src.name
                if (!Paths.isSafeName(name)) { t.warnings += App.str(R.string.skipped_unsafe_name, src.name); t.filesDone++; continue }
                val sameFolder = src.vfsId == dest.vfsId && src.parentLocation.path == dest.path
                if (sameFolder && move) { t.filesDone++; continue }
                var target = Paths.join(dest.path, name)
                // Replacing a folder that contains the item itself would delete the item; never do that.
                if (src.vfsId == dest.vfsId && target != src.path && Paths.isWithin(src.path, target)) {
                    t.warnings += App.str(R.string.skipped_it_s_inside_the_item_it, src.name)
                    t.filesDone++
                    continue
                }
                if (resume && !sameFolder && dst.exists(target) && alreadyThere(srcVfs, src, dst, target)) {
                    // Retrying: a folder is merged (its files are checked one by one below), a finished file is done.
                    if (!src.isDir) { t.bytesDone += src.size; t.filesDone++; if (move) deleteTree(srcVfs, src, null); continue }
                    copyTree(srcVfs, src, dst, target, t, resume = true)
                    if (move) deleteTree(srcVfs, src, null)
                    continue
                }
                if (dst.exists(target)) {
                    val policy = if (sameFolder) Conflict.KeepBoth else conflict
                    when (policy) {
                        Conflict.Skip -> { t.warnings += App.str(R.string.skipped_2, src.name); t.filesDone++; continue }
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

    /**
     * For a retry: [target] already holds [e] (a folder onto a folder, or a file of the same size). Only then is it
     * left as it is; anything else goes through the usual conflict choice.
     */
    private fun alreadyThere(src: Vfs, e: FileEntry, dst: Vfs, target: String): Boolean {
        val there = try { dst.stat(target) } catch (_: Exception) { return false }
        return if (e.isDir && !e.isLink) there.isDir else !there.isDir && there.size == e.size
    }

    private suspend fun copyTree(src: Vfs, e: FileEntry, dst: Vfs, target: String, t: OpTask, resume: Boolean = false) {
        coroutineContext.ensureActive()
        t.checkPause()
        if (e.isLink && e.isDir) {
            // Following folder links could loop or copy a whole other tree; leave them out.
            t.warnings += App.str(R.string.skipped_folder_link, e.name)
            t.filesDone++
            return
        }
        if (e.isDir && !e.isLink) {
            if (!dst.exists(target)) dst.mkdir(target)
            t.filesDone++
            for (c in src.list(e.path)) {
                // Names come from the source (maybe a remote server): never let one climb out of the target folder.
                if (!Paths.isSafeName(c.name)) { t.warnings += App.str(R.string.skipped_unsafe_name, c.name); continue }
                val childTarget = Paths.join(target, c.name)
                checkInside(dst, childTarget, target)
                copyTree(src, c, dst, childTarget, t, resume)
            }
            dst.setModified(target, e.modified)
        } else {
            if (resume && dst.exists(target) && alreadyThere(src, e, dst, target)) {
                t.bytesDone += e.size
                t.filesDone++
                return
            }
            try {
                copyFile(src, e, dst, target, t)
            } catch (ex: java.io.FileNotFoundException) {
                if (!e.isLink) throw ex
                t.warnings += App.str(R.string.skipped_broken_link, e.name)
            }
            t.filesDone++
        }
    }

    private suspend fun copyFile(src: Vfs, e: FileEntry, dst: Vfs, target: String, t: OpTask) {
        t.detail = e.name
        copyStream({ src.openRead(e.path) }, dst, target, t)
        dst.setModified(target, e.modified)
    }

    /** Streams [open] into [target] with progress; a half-written target is removed on failure or cancel. */
    private suspend fun copyStream(open: () -> java.io.InputStream, dst: Vfs, target: String, t: OpTask) {
        val buf = ByteArray(1024 * 1024)
        var ok = false
        var created = false
        try {
            open().use { input ->
                dst.openWrite(target).also { created = true }.use { out ->
                    var lastUi = 0L
                    var pending = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        t.checkPause()
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
            ok = true
        } finally {
            // Don't leave half-copied files behind on failure or cancel (only ones this copy created).
            if (!ok && created) try { dst.delete(target, false) } catch (_: Exception) {}
        }
    }

    // ---------- Save shared files ----------

    /**
     * Saves files another app shared with FilesCtrl into [dest]. Names that are taken get " (1)", " (2)"...
     * Nothing already in the folder is ever replaced.
     */
    fun saveIncoming(items: List<IncomingItem>, dest: Location): OpTask =
        launch(App.plural(R.plurals.saving_items, items.size, items.size), listOf(dest), setOf(dest.vfsId)) { t ->
            val dst = App.vfs.get(dest.vfsId)
            t.filesTotal = items.size
            // Only count bytes when every size is known, so the bar doesn't jump.
            if (items.all { it.size >= 0 }) t.bytesTotal = items.sumOf { it.size }
            try {
                for (item in items) {
                    coroutineContext.ensureActive()
                    t.detail = item.name
                    if (!Paths.isSafeName(item.name)) { t.warnings += App.str(R.string.skipped_unsafe_name, item.name); t.filesDone++; continue }
                    val name = Paths.unique(dest.path, item.name) { dst.exists(it) }
                    val target = Paths.join(dest.path, name)
                    checkInside(dst, target, dest.path)
                    try {
                        copyStream(item.open, dst, target, t)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One unreadable file doesn't stop the others.
                        if (items.size == 1) throw e
                        t.warnings += App.str(R.string.failed_3, item.name, e.message ?: e.javaClass.simpleName)
                    }
                    t.filesDone++
                }
            } finally {
                // Streams that were opened in advance and never read (cancelled, failed) are closed.
                items.forEach { runCatching { it.discard() } }
            }
        }

    // ---------- Delete ----------

    fun delete(items: List<FileEntry>): OpTask {
        return deleteTask(items)
    }

    /** With [resume] (a retry) items that are already gone are left out instead of failing. */
    private fun deleteTask(items: List<FileEntry>, resume: Boolean = false): OpTask =
        launch(
            App.plural(R.plurals.deleting_items, items.size, items.size), items.map { it.parentLocation }.distinct(), items.mapTo(HashSet()) { it.vfsId },
            retry = { deleteTask(items, resume = true) },
        ) { t ->
            t.filesTotal = items.size
            for (e in items) {
                coroutineContext.ensureActive()
                t.checkPause()
                val vfs = App.vfs.get(e.vfsId)
                if (resume && !vfs.exists(e.path)) { t.filesDone++; continue }
                deleteTree(vfs, e, t)
                t.filesDone++
            }
        }

    private suspend fun deleteTree(vfs: Vfs, e: FileEntry, t: OpTask?) {
        coroutineContext.ensureActive()
        t?.checkPause()
        t?.detail = e.name
        if (e.isDir && !e.isLink) {
            for (c in vfs.list(e.path)) deleteTree(vfs, c, t)
        }
        vfs.delete(e.path, e.isDir && !e.isLink)
    }

    // ---------- One-way folder sync ----------

    /** Every file and folder under [root] by relative path. Folder links and unsafe names are left out. */
    private suspend fun listTree(vfs: Vfs, root: String, out: HashMap<String, SyncMeta> = HashMap(), rel: String = "", onFolder: () -> Unit = {}): HashMap<String, SyncMeta> {
        coroutineContext.ensureActive()
        onFolder()
        for (e in vfs.list(Paths.join(root, rel).let { if (rel.isEmpty()) root else it })) {
            if (!Paths.isSafeName(e.name)) continue
            if (e.isDir && e.isLink) continue
            val r = if (rel.isEmpty()) e.name else "$rel/${e.name}"
            out[r] = SyncMeta(e.isDir, e.size, e.modified)
            if (e.isDir) listTree(vfs, root, out, r, onFolder)
        }
        return out
    }

    /** Refuses a sync where one folder is inside the other (it would copy into itself or delete its own source). */
    fun syncProblem(src: Location, dst: Location): String? = when {
        src.vfsId == dst.vfsId && (Paths.isWithin(dst.path, src.path) || Paths.isWithin(src.path, dst.path)) -> App.str(R.string.sync_inside_itself)
        else -> null
    }

    /** The dry run: what [sync] would do right now. [onFolder] is called for every folder read (for progress). */
    suspend fun planSync(src: Location, dst: Location, mirror: Boolean, onFolder: () -> Unit = {}): SyncPlan {
        syncProblem(src, dst)?.let { throw IOException(it) }
        val from = listTree(App.vfs.get(src.vfsId), src.path, onFolder = onFolder)
        val dstVfs = App.vfs.get(dst.vfsId)
        val to = if (dstVfs.exists(dst.path)) listTree(dstVfs, dst.path, onFolder = onFolder) else HashMap()
        return SyncPlan.of(from, to, mirror)
    }

    /**
     * Makes [dst] match [src], one way: missing and changed files are copied over, and with [mirror] what's only in
     * the destination is deleted. The plan is worked out again when it starts (the dry run is a preview). A changed
     * file is written next to the old one first and only then put in its place, so a failed copy never loses the
     * old file. Nothing in [src] is changed.
     */
    fun sync(src: Location, dst: Location, mirror: Boolean): OpTask =
        launch(App.str(R.string.sync_task_title, Paths.name(src.path), Paths.name(dst.path)), listOf(dst), setOf(src.vfsId, dst.vfsId), retry = { sync(src, dst, mirror) }) { t ->
            t.detail = App.str(R.string.counting_files)
            val plan = planSync(src, dst, mirror)
            val from = App.vfs.get(src.vfsId)
            val to = App.vfs.get(dst.vfsId)
            t.bytesTotal = plan.bytes
            t.filesTotal = plan.delete.size + plan.create.size + plan.copy.size + plan.update.size
            if (plan.conflicts.isNotEmpty() && !mirror) plan.conflicts.forEach { t.warnings += App.str(R.string.sync_conflict_skipped, it) }
            if (!to.exists(dst.path)) to.mkdir(dst.path)
            for (rel in plan.delete) {
                coroutineContext.ensureActive()
                t.checkPause()
                val target = Paths.join(dst.path, rel)
                checkInside(to, target, dst.path)
                t.detail = rel
                deleteTree(to, to.stat(target), null)
                t.filesDone++
            }
            for (rel in plan.create) {
                coroutineContext.ensureActive()
                val target = Paths.join(dst.path, rel)
                checkInside(to, target, dst.path)
                if (!to.exists(target)) to.mkdir(target)
                t.filesDone++
            }
            for (rel in plan.copy + plan.update) {
                coroutineContext.ensureActive()
                t.checkPause()
                val source = from.stat(Paths.join(src.path, rel))
                val target = Paths.join(dst.path, rel)
                checkInside(to, target, dst.path)
                t.detail = rel
                if (!to.exists(target)) {
                    copyFile(from, source, to, target, t)
                } else {
                    // Changed file: the new copy goes next to it first, so a failure leaves the old one as it was.
                    val dir = Paths.parent(target)
                    val tmp = Paths.join(dir, Paths.unique(dir, ".filesctrl-sync-${Paths.name(target)}") { to.exists(it) })
                    copyFile(from, source, to, tmp, t)
                    try {
                        to.delete(target, false)
                    } catch (e: Exception) {
                        // The old file is still there: the new copy goes away again.
                        try { to.delete(tmp, false) } catch (_: Exception) {}
                        throw e
                    }
                    try {
                        to.rename(tmp, target)
                    } catch (e: Exception) {
                        // The old file is gone, so the new copy stays, under its temporary name.
                        throw IOException(App.str(R.string.sync_kept_temporary, rel, Paths.name(tmp)), e)
                    }
                    to.setModified(target, source.modified)
                }
                t.filesDone++
            }
        }

    // ---------- Rename several ----------

    /**
     * Renames each item to its new name, in its own folder. When a new name is the current name of another item in
     * the list, everything is first renamed to a temporary name, then to its new name, so no file is ever replaced.
     * A name that's taken by a file outside the list is skipped (the preview already said so).
     */
    fun renameAll(renames: List<Pair<FileEntry, String>>): OpTask {
        val list = renames.filter { (e, n) -> n != e.name }
        return launch(App.plural(R.plurals.renaming_items, list.size, list.size), list.map { it.first.parentLocation }.distinct(), list.mapTo(HashSet()) { it.first.vfsId }) { t ->
            t.filesTotal = list.size
            val oldPaths = list.mapTo(HashSet()) { it.first.path }
            val twoSteps = list.any { (e, n) -> Paths.join(Paths.parent(e.path), n) in oldPaths }
            val stage = ArrayList<Triple<Vfs, String, String>>() // (vfs, temporary path, final path)
            try {
                for ((e, n) in list) {
                    coroutineContext.ensureActive()
                    t.checkPause()
                    t.detail = e.name
                    if (!Paths.isSafeName(n) || Paths.validName(n) != null) { t.warnings += App.str(R.string.skipped_unsafe_name, n); t.filesDone++; continue }
                    val vfs = App.vfs.get(e.vfsId)
                    val dir = Paths.parent(e.path)
                    val target = Paths.join(dir, n)
                    if (twoSteps) {
                        val tmp = Paths.join(dir, Paths.unique(dir, ".filesctrl-rename-${t.id}-${e.name}") { vfs.exists(it) })
                        vfs.rename(e.path, tmp)
                        stage += Triple(vfs, tmp, target)
                    } else if (target.equals(e.path, ignoreCase = true)) {
                        // Only the case changes: on storage that ignores case (SD cards, many servers) the new name
                        // "exists" already, so go through a temporary name.
                        val tmp = Paths.join(dir, Paths.unique(dir, ".filesctrl-rename-${t.id}-${e.name}") { vfs.exists(it) })
                        vfs.rename(e.path, tmp)
                        stage += Triple(vfs, tmp, target)
                    } else {
                        if (vfs.exists(target)) { t.warnings += App.str(R.string.skipped_2, n); t.filesDone++; continue }
                        vfs.rename(e.path, target)
                        t.filesDone++
                    }
                }
            } finally {
                // Always finishes the second step (also after a failure or cancel), so nothing keeps its temporary name
                // unless its new name was taken in the meantime; then it says which.
                for ((vfs, tmp, target) in stage) {
                    t.detail = Paths.name(target)
                    try {
                        if (vfs.exists(target)) t.warnings += App.str(R.string.rename_kept_temporary, Paths.name(target), Paths.name(tmp))
                        else vfs.rename(tmp, target)
                    } catch (ex: Exception) {
                        t.warnings += App.str(R.string.rename_kept_temporary, Paths.name(target), Paths.name(tmp))
                    }
                    t.filesDone++
                }
            }
        }
    }

    // ---------- Archives ----------

    fun compress(items: List<FileEntry>, dest: Location, name: String, format: ArchiveFormat = ArchiveFormat.ZIP, password: CharArray? = null): OpTask {
        return compressTask(items, dest, name, format, password?.takeIf { it.isNotEmpty() && format.passwords })
    }

    /** Walks [e] (recursively for folders) and hands each folder and file to [sink] with its path in the archive. */
    private suspend fun walk(vfs: Vfs, e: FileEntry, name: String, t: OpTask, sink: ArchiveSink) {
        coroutineContext.ensureActive()
        t.checkPause()
        t.detail = e.name
        if (e.isLink && e.isDir) { t.warnings += App.str(R.string.skipped_folder_link, e.name); t.filesDone++; return }
        if (e.isDir && !e.isLink) {
            sink.dir("$name/", e.modified)
            t.filesDone++
            for (c in vfs.list(e.path)) walk(vfs, c, "$name/${c.name}", t, sink)
            return
        }
        val input = try { vfs.openRead(e.path) } catch (ex: java.io.FileNotFoundException) {
            if (!e.isLink) throw ex
            t.warnings += App.str(R.string.skipped_broken_link, e.name); t.filesDone++; return
        }
        input.use { sink.file(name, e.size, e.modified, it) { n -> t.bytesDone += n } }
        t.filesDone++
    }

    /** No retry for an archive with a password: the password isn't kept after the first try. */
    private fun compressTask(items: List<FileEntry>, dest: Location, name: String, format: ArchiveFormat, password: CharArray?): OpTask =
        launch(App.str(R.string.compressing_to, name), listOf(dest), items.mapTo(HashSet()) { it.vfsId } + dest.vfsId, retry = if (password == null) { { compressTask(items, dest, name, format, null) } } else null) { t ->
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
                    val out = if (dst is LocalVfs && dst.direct(target)) File(target) else File.createTempFile("pack", ".7z", appContext.cacheDir).also { tmp = it }
                    SevenZOutputFile(out, password).use { z ->
                        z.setContentCompression(SevenZMethod.LZMA2)
                        val sink = SevenZSink(z)
                        for (e in items) walk(App.vfs.get(e.vfsId), e, e.name, t, sink)
                        z.finish()
                    }
                    tmp?.let { f ->
                        t.detail = App.str(R.string.uploading, name)
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
        launch(App.str(R.string.extracting, archive.name), listOf(dest), setOf(archive.vfsId, ArchiveVfs.idFor(archive.location), dest.vfsId), pausable = false) { t ->
            val arc = App.vfs.archive(ArchiveVfs.idFor(archive.location)) ?: throw IOException(App.str(R.string.couldn_t_open, archive.name))
            if (password != null) arc.password = password
            val dst = App.vfs.get(dest.vfsId)
            val job = coroutineContext.job
            t.detail = App.str(R.string.reading_2, archive.name)
            t.bytesTotal = arc.totalSize()
            val single = arc.isSingleFile
            val root = if (single) dest.path else Paths.join(dest.path, Paths.unique(dest.path, arc.baseName) { dst.exists(it) }).also { dst.mkdir(it) }
            val made = HashSet<String>().apply { add(root) }
            val buf = ByteArray(256 * 1024)
            var created: String? = null
            // An archive made to unpack without end must not fill the phone. Only bytes really written count
            // (see ExtractGuard). Not for a destination on a server: that would mean asking the server for its
            // free space over and over, which extracting never did.
            val guard = if (App.vfs.onNetwork(setOf(dest.vfsId))) null
            else ExtractGuard({ try { dst.space(root)?.free } catch (_: Exception) { null } })
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
                            if (guard != null && guard.wrote(n)) throw IOException(App.str(R.string.failed_no_space_left, e.name))
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
            if (arc.skippedUnsafe > 0) t.warnings += App.plural(R.plurals.left_out_unsafe, arc.skippedUnsafe, arc.skippedUnsafe)
            if (arc.skippedLinks > 0) t.warnings += App.plural(R.plurals.left_out_links, arc.skippedLinks, arc.skippedLinks)
        }

    /**
     * Refuses to write [target] unless it's inside [dir]. Remote names are already filtered, but on the phone a
     * folder link inside the destination could still point elsewhere, so local targets are checked by their real
     * (canonical) path too.
     */
    private fun checkInside(dst: Vfs, target: String, dir: String, allowRoot: Boolean = false) {
        if (!Paths.isWithin(target, dir) || (!allowRoot && target.trimEnd('/') == dir.trimEnd('/'))) throw IOException(App.str(R.string.refused_to_write_outside_the, Paths.name(target)))
        if (dst.id != LocalVfs.ID || !App.vfs.local.direct(target)) return
        val base = File(dir).canonicalPath
        val real = File(target).canonicalPath
        if (!real.startsWith(base + File.separator)) throw IOException(App.str(R.string.refused_to_write_outside_the, Paths.name(target)))
    }

    // ---------- Recursive size (for Properties) ----------

    suspend fun folderSize(e: FileEntry): Pair<Long, Int> = measure(App.vfs.get(e.vfsId), e)

}
