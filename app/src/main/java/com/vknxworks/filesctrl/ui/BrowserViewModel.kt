package com.vknxworks.filesctrl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.SortBy
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.coroutines.coroutineContext

/** Copied or cut items waiting to be pasted. Shared by every screen. */
object FileClipboard {
    var items by mutableStateOf<List<FileEntry>>(emptyList())
    var cut by mutableStateOf(false)
    fun clear() { items = emptyList(); cut = false }
}

class BrowserViewModel : ViewModel() {
    var location by mutableStateOf(startLocation())
        private set
    private val history = ArrayDeque<Pair<Location, Int>>()
    /** Scroll position to restore when the list for [location] appears. */
    var restoreIndex by mutableStateOf(0)
        private set

    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** True only while a pull-to-refresh is running, so its spinner can finish. */
    var refreshing by mutableStateOf(false)
        private set
    /** Capacity of the share or volume being shown, when the server reports it. */
    var space by mutableStateOf<com.vknxworks.filesctrl.fs.Space?>(null)
        private set
    private var spaceFor: String? = null
    var error by mutableStateOf<String?>(null)
        private set
    /** Set when the server's host key / certificate needs the user's OK before connecting. */
    var untrusted by mutableStateOf<com.vknxworks.filesctrl.fs.UntrustedIdentityException?>(null)

    /** Saves the identity the user accepted on the current server and loads again. */
    fun trustAndRetry() {
        val u = untrusted ?: return
        untrusted = null
        val id = location.vfsId.removePrefix(RemoteVfs.PREFIX)
        val s = App.servers.get(id) ?: return
        try {
            App.servers.save(trusted(s, u))
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            return
        }
        load()
    }
    /** Bumped on every finished load, so the UI can react (e.g. scroll the breadcrumbs). */
    var loadCount by mutableStateOf(0)
        private set

    val selection = mutableStateListOf<String>()
    val selecting: Boolean get() = selection.isNotEmpty()

    var filter by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    val deepResults = mutableStateListOf<FileEntry>()
    var deepSearching by mutableStateOf(false)
        private set
    var deepQuery by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            Ops.changed.collect { loc -> if (loc == location) load(quiet = true) }
        }
    }

    private fun startLocation(): Location {
        val last = App.settings.lastLocation
        if (last != null && App.vfs.find(last.vfsId) != null) return last
        return Location(LocalVfs.ID, App.vfs.local.primaryRoot)
    }

    val vfsTitle: String get() = App.vfs.find(location.vfsId)?.title ?: ""
    val isLocal: Boolean get() = location.vfsId == LocalVfs.ID
    val readOnly: Boolean
        get() = location.vfsId.startsWith(RemoteVfs.PREFIX) &&
            App.servers.get(location.vfsId.removePrefix(RemoteVfs.PREFIX))?.readOnly == true

    /** Breadcrumb trail: (label, location) from the volume/share root to here. */
    fun crumbs(): List<Pair<String, Location>> {
        val vfs = App.vfs.find(location.vfsId) ?: return emptyList()
        val (root, label) = vfs.rootOf(location.path)
        val out = mutableListOf(label to Location(location.vfsId, root))
        val rest = location.path.removePrefix(root).trim('/')
        if (rest.isNotEmpty()) {
            var p = root
            for (part in rest.split('/')) {
                p = Paths.join(p, part)
                out += part to Location(location.vfsId, p)
            }
        }
        return out
    }

    val title: String get() = crumbs().lastOrNull()?.first ?: ""

    fun navigate(to: Location, currentIndex: Int = 0, addToHistory: Boolean = true) {
        if (to == location) { load(); return }
        if (addToHistory) history.addLast(location to currentIndex)
        if (history.size > 200) history.removeFirst()
        location = to
        restoreIndex = 0
        selection.clear()
        filter = ""
        closeDeepSearch()
        App.settings.lastLocation = to
        load()
    }

    /** Opens a location from the drawer: starts a fresh trail. */
    fun jump(to: Location, currentIndex: Int) {
        navigate(to, currentIndex)
    }

    fun up(currentIndex: Int) {
        val vfs = App.vfs.find(location.vfsId) ?: return
        val (root, _) = vfs.rootOf(location.path)
        if (location.path == root) return
        navigate(Location(location.vfsId, Paths.parent(location.path)), currentIndex)
    }

    val canGoUp: Boolean
        get() {
            val vfs = App.vfs.find(location.vfsId) ?: return false
            return location.path != vfs.rootOf(location.path).first
        }

    /** Back: undo the last navigation. False if there's nowhere to go back to. */
    fun back(): Boolean {
        val (prev, index) = history.removeLastOrNull() ?: return false
        location = prev
        restoreIndex = index
        selection.clear()
        filter = ""
        closeDeepSearch()
        App.settings.lastLocation = prev
        load()
        return true
    }

    private var refreshStarted = 0L

    fun refresh() {
        refreshStarted = System.currentTimeMillis()
        refreshing = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        loadJob?.cancel()
        val loc = location
        if (spaceFor != loc.vfsId) { space = null; spaceFor = null }
        loadJob = viewModelScope.launch {
            if (!quiet) loading = true
            error = null
            try {
                val list = withContext(Dispatchers.IO) { App.vfs.get(loc.vfsId).list(loc.path) }
                if (loc == location) {
                    entries = list
                    // Forget selected items that are gone.
                    val names = list.mapTo(HashSet()) { it.path }
                    selection.retainAll { it in names }
                }
                if (loc.vfsId.startsWith(RemoteVfs.PREFIX)) {
                    val sp = withContext(Dispatchers.IO) { try { App.vfs.get(loc.vfsId).space(loc.path) } catch (_: Exception) { null } }
                    if (loc.vfsId == location.vfsId) { space = sp; spaceFor = loc.vfsId }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loc == location) {
                    entries = emptyList()
                    error = e.message ?: e.javaClass.simpleName
                    if (e is com.vknxworks.filesctrl.fs.UntrustedIdentityException) untrusted = e
                }
            } finally {
                if (loc == location) { loading = false; loadCount++ }
                if (refreshing) {
                    // Keep the spinner up briefly: a reload that finishes within a frame would otherwise leave
                    // the pull indicator stuck, because it never sees refreshing change.
                    val left = 450 - (System.currentTimeMillis() - refreshStarted)
                    viewModelScope.launch {
                        if (left > 0) kotlinx.coroutines.delay(left)
                        refreshing = false
                    }
                }
            }
        }
    }

    /** Entries as shown: hidden files, quick filter and sort applied. */
    fun visible(): List<FileEntry> {
        val s = App.settings
        var list = entries.asSequence()
        if (!s.showHidden) list = list.filter { !it.hidden }
        if (filter.isNotBlank()) list = list.filter { it.name.contains(filter.trim(), ignoreCase = true) }
        return list.sortedWith(comparator(s.sortBy, s.sortDescending, s.foldersFirst)).toList()
    }

    fun toggle(e: FileEntry) {
        if (!selection.remove(e.path)) selection.add(e.path)
    }

    fun selectAll() {
        val all = visible().map { it.path }
        if (selection.size == all.size) selection.clear() else { selection.clear(); selection.addAll(all) }
    }

    fun selected(): List<FileEntry> {
        val set = selection.toHashSet()
        return (entries + deepResults).filter { it.path in set }.distinctBy { it.path }
    }

    fun copySelection(cut: Boolean) {
        FileClipboard.items = selected()
        FileClipboard.cut = cut
        selection.clear()
    }

    // ---------- Recursive search ----------

    fun deepSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        deepResults.clear()
        deepQuery = q
        deepSearching = true
        val loc = location
        searchJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val vfs = App.vfs.get(loc.vfsId)
                    val showHidden = App.settings.showHidden
                    val queue = ArrayDeque<String>().apply { add(loc.path) }
                    var batch = ArrayList<FileEntry>()
                    var last = System.nanoTime()
                    while (queue.isNotEmpty() && deepResults.size + batch.size < 2000) {
                        coroutineContext.ensureActive()
                        val dir = queue.removeFirst()
                        val children = try { vfs.list(dir) } catch (_: Exception) { continue }
                        for (c in children) {
                            if (!showHidden && c.hidden) continue
                            if (c.name.contains(q, ignoreCase = true)) batch += c
                            if (c.isDir && !c.isLink) queue.addLast(c.path)
                        }
                        if (batch.isNotEmpty() && System.nanoTime() - last > 250_000_000) {
                            val b = batch; batch = ArrayList()
                            withContext(Dispatchers.Main) { deepResults.addAll(b) }
                            last = System.nanoTime()
                        }
                    }
                    if (batch.isNotEmpty()) withContext(Dispatchers.Main) { deepResults.addAll(batch) }
                }
            } finally {
                deepSearching = false
            }
        }
    }

    fun closeDeepSearch() {
        searchJob?.cancel()
        deepQuery = null
        deepSearching = false
        deepResults.clear()
    }

    override fun onCleared() {
        searchJob?.cancel()
    }

    companion object {
        private val collator: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

        /** Natural order: "file2" before "file10", case-insensitive. */
        fun naturalCompare(a: String, b: String): Int {
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                val ca = a[i]
                val cb = b[j]
                if (ca.isDigit() && cb.isDigit()) {
                    var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                    var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                    val na = a.substring(i, ei).trimStart('0')
                    val nb = b.substring(j, ej).trimStart('0')
                    if (na.length != nb.length) return na.length - nb.length
                    val c = na.compareTo(nb)
                    if (c != 0) return c
                    i = ei; j = ej
                } else {
                    var ei = i; while (ei < a.length && !a[ei].isDigit()) ei++
                    var ej = j; while (ej < b.length && !b[ej].isDigit()) ej++
                    val c = collator.compare(a.substring(i, ei), b.substring(j, ej))
                    if (c != 0) return c
                    i = ei; j = ej
                }
            }
            return (a.length - i) - (b.length - j)
        }

        fun comparator(by: SortBy, desc: Boolean, foldersFirst: Boolean): Comparator<FileEntry> {
            val byName = Comparator<FileEntry> { x, y -> naturalCompare(x.name, y.name) }
            var c: Comparator<FileEntry> = when (by) {
                SortBy.Name -> byName
                SortBy.Size -> compareBy<FileEntry> { it.size }.then(byName)
                SortBy.Modified -> compareBy<FileEntry> { it.modified }.then(byName)
                SortBy.Type -> compareBy<FileEntry> { Mime.kind(it).ordinal }.thenBy { it.ext }.then(byName)
            }
            if (desc) c = c.reversed()
            return if (foldersFirst) compareBy<FileEntry> { !it.isDir }.then(c) else c
        }
    }
}
