package com.vknxworks.filesctrl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.SortBy
import com.vknxworks.filesctrl.fs.ArchivePasswordException
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.CryptoVfs
import com.vknxworks.filesctrl.fs.VaultLockedException
import com.vknxworks.filesctrl.fs.VaultPasswordException
import com.vknxworks.filesctrl.R
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

/** One browser tab. [start]: the folder it opens in (phone storage when null). Loads when first shown. */
class BrowserViewModel(start: Location? = null) : ViewModel() {
    /** Tells tabs apart in the tab strip. */
    val tabId: Int = nextTabId++

    var location by mutableStateOf(start ?: startLocation())
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

    /** Remembers where the list was scrolled when the tab goes off screen, to come back to it. */
    fun keepScroll(index: Int) { restoreIndex = index }

    /** Loads the first time the tab is on screen, and again if it couldn't load before (locked, offline). */
    fun ensureLoaded() {
        if (loadCount == 0 && !loading) load() else if (error != null && !loading) load(quiet = true)
    }

    init {
        viewModelScope.launch {
            Ops.changed.collect { loc -> if (loc == location) load(quiet = true) }
        }
    }

    /** FilesCtrl always opens in phone storage, wherever it was closed. */
    private fun startLocation(): Location = home

    private val home: Location get() = Location(LocalVfs.ID, App.vfs.local.primaryRoot)

    /**
     * The file system for [id], or null. Never throws: a tab restored on a server is shown (and asks to unlock)
     * while the app is still locked and its passwords aren't available yet.
     */
    private fun findVfs(id: String) = try { App.vfs.find(id) } catch (_: Exception) { null }

    val vfsTitle: String get() = findVfs(location.vfsId)?.title ?: ""
    val isLocal: Boolean get() = location.vfsId == LocalVfs.ID
    val readOnly: Boolean get() = isReadOnlyVfs(location.vfsId)

    /** Archives, read-only servers, and vaults inside either. */
    private fun isReadOnlyVfs(id: String, depth: Int = 0): Boolean = when {
        id.startsWith(ArchiveVfs.PREFIX) -> true
        id.startsWith(RemoteVfs.PREFIX) -> App.servers.get(id.removePrefix(RemoteVfs.PREFIX))?.readOnly == true
        id.startsWith(CryptoVfs.PREFIX) && depth < 8 -> CryptoVfs.folderOf(id)?.let { isReadOnlyVfs(it.vfsId, depth + 1) } ?: true
        else -> false
    }

    // ---------- Cryptomator vaults ----------

    /** Browsing inside an unlocked Cryptomator vault. */
    val isVault: Boolean get() = location.vfsId.startsWith(CryptoVfs.PREFIX)

    /** The folder the vault being browsed is stored in (its encrypted files). */
    val vaultFolder: Location? get() = if (isVault) CryptoVfs.folderOf(location.vfsId) else null

    /** The folder on screen holds a Cryptomator vault's files (vault.cryptomator and masterkey.cryptomator). */
    val vaultHere: Boolean get() = error == null && CryptoVfs.looksLikeVault(entries)

    /** Asking for a vault's password: which vault, its name, and why the last try failed (if it did). */
    data class VaultPrompt(val vaultId: String, val title: String, val wrong: Boolean = false, val message: String? = null)

    var vaultPrompt by mutableStateOf<VaultPrompt?>(null)
    /** scrypt is running (it takes a moment on purpose). */
    var unlocking by mutableStateOf(false)
        private set
    /** Set by a forward navigation, so arriving at a vault's folder asks for its password right away. */
    private var openVaultOnLoad = false

    private fun vaultTitle(id: String) = CryptoVfs.folderOf(id)?.let { Paths.name(it.path) } ?: ""

    /** Opens the vault stored in [folder]: straight in if it's unlocked, with the saved password if there is one, else asks. */
    fun openVault(folder: Location) {
        val id = CryptoVfs.idFor(folder)
        val v = try { App.vfs.vault(id) } catch (_: Exception) { null } ?: return
        if (v.unlocked) { navigate(Location(id, "/")); return }
        val saved = App.vaultPasswords.get(id)
        if (saved != null) unlockVault(id, saved, remember = false, fromSaved = true)
        else vaultPrompt = VaultPrompt(id, vaultTitle(id))
    }

    /** Unlocks off the main thread; on success opens the vault (or reloads, when already in it). */
    fun unlockVault(id: String, password: CharArray, remember: Boolean, fromSaved: Boolean = false) {
        if (unlocking) return
        unlocking = true
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    val v = App.vfs.vault(id) ?: throw java.io.IOException(App.str(R.string.vault_locked))
                    v.unlock(password)
                    if (remember) try { App.vaultPasswords.save(id, password) } catch (_: java.io.IOException) {}
                    null
                } catch (e: Exception) {
                    e
                } finally {
                    password.fill('\u0000')
                }
            }
            unlocking = false
            when {
                err == null -> {
                    vaultPrompt = null
                    if (location.vfsId == id) load() else navigate(Location(id, "/"))
                }
                err is VaultPasswordException && fromSaved -> {
                    // The vault's password changed: forget the saved one and ask.
                    App.vaultPasswords.forget(id)
                    vaultPrompt = VaultPrompt(id, vaultTitle(id), message = App.str(R.string.vault_saved_password_failed))
                }
                err is VaultPasswordException -> vaultPrompt = VaultPrompt(id, vaultTitle(id), wrong = true)
                else -> vaultPrompt = VaultPrompt(id, vaultTitle(id), message = err.message ?: err.javaClass.simpleName)
            }
        }
    }

    /** Asks again for the password of the vault being browsed (after the prompt was cancelled). */
    fun promptVault() {
        if (isVault) vaultPrompt = VaultPrompt(location.vfsId, vaultTitle(location.vfsId))
    }

    /** Locks the vault being browsed (its keys are wiped) and goes back to the folder it's in. */
    fun lockVault(currentIndex: Int = 0) {
        val folder = vaultFolder ?: return
        App.vfs.vault(location.vfsId)?.lock()
        App.clearImageMemory()
        navigate(Location(folder.vfsId, Paths.parent(folder.path)), currentIndex)
    }

    /** Browsing inside an archive (read-only). */
    val isArchive: Boolean get() = location.vfsId.startsWith(ArchiveVfs.PREFIX)

    /** The archive file being browsed, when inside one. */
    val archiveFile: Location? get() = if (isArchive) ArchiveVfs.archiveOf(location.vfsId) else null

    /** Set when the archive being opened needs a password; the screen asks for it. */
    var lockedArchive by mutableStateOf<ArchivePasswordException?>(null)
    /** The password prompt was cancelled; the error screen offers to show it again. */
    var passwordPromptHidden by mutableStateOf(false)

    fun unlockArchive(password: CharArray) {
        (findVfs(location.vfsId) as? ArchiveVfs)?.password = password
        lockedArchive = null
        load()
    }

    /** Opens an archive file as a folder, read afresh in case it changed. */
    fun openArchive(e: FileEntry, currentIndex: Int) {
        val id = ArchiveVfs.idFor(e.location)
        App.vfs.forgetArchive(id)
        navigate(Location(id, "/"), currentIndex)
    }

    /** Breadcrumb trail: (label, location) from the volume/share root to here. */
    fun crumbs(): List<Pair<String, Location>> = crumbsFor(location)

    /** Inside an archive, the trail starts with the folders the archive file is in, then the archive itself. */
    private fun crumbsFor(loc: Location, depth: Int = 0): List<Pair<String, Location>> {
        val vfs = findVfs(loc.vfsId) ?: return emptyList()
        val (root, label) = vfs.rootOf(loc.path)
        val out = mutableListOf<Pair<String, Location>>()
        if (vfs is ArchiveVfs && depth < 8) ArchiveVfs.archiveOf(loc.vfsId)?.let { arc ->
            out += crumbsFor(Location(arc.vfsId, Paths.parent(arc.path)), depth + 1)
        }
        // A vault: the folders it's in, then the vault by its folder's name.
        if (vfs is CryptoVfs && depth < 8) CryptoVfs.folderOf(loc.vfsId)?.let { f ->
            out += crumbsFor(Location(f.vfsId, Paths.parent(f.path)), depth + 1)
        }
        out += label to Location(loc.vfsId, root)
        val rest = loc.path.removePrefix(root).trim('/')
        if (rest.isNotEmpty()) {
            var p = root
            for (part in rest.split('/')) {
                p = Paths.join(p, part)
                out += part to Location(loc.vfsId, p)
            }
        }
        return out
    }

    val title: String get() = crumbs().lastOrNull()?.first ?: ""

    fun navigate(to: Location, currentIndex: Int = 0, addToHistory: Boolean = true) {
        if (to == location) { load(); return }
        if (addToHistory) history.addLast(location to currentIndex)
        if (history.size > 200) history.removeFirst()
        openVaultOnLoad = addToHistory && !to.vfsId.startsWith(ArchiveVfs.PREFIX)
        location = to
        restoreIndex = 0
        selection.clear()
        filter = ""
        closeDeepSearch()
        load()
    }

    /** Opens a location from the drawer: starts a fresh trail. */
    fun jump(to: Location, currentIndex: Int) {
        navigate(to, currentIndex)
    }

    fun up(currentIndex: Int, addToHistory: Boolean = true) {
        val vfs = findVfs(location.vfsId) ?: return
        val (root, _) = vfs.rootOf(location.path)
        if (location.path == root) {
            // Up from the top of an archive goes to the folder the archive is in; from a vault, to the folder
            // its encrypted folder is in.
            (archiveFile ?: vaultFolder)?.let { navigate(Location(it.vfsId, Paths.parent(it.path)), currentIndex, addToHistory) }
            return
        }
        navigate(Location(location.vfsId, Paths.parent(location.path)), currentIndex, addToHistory)
    }

    val canGoUp: Boolean
        get() {
            val vfs = findVfs(location.vfsId) ?: return false
            return isArchive || isVault || location.path != vfs.rootOf(location.path).first
        }

    /**
     * Back: returns to the folder you were in before. With nothing left to go back to, goes to the parent folder,
     * then to phone storage. False only in phone storage's top folder, where back leaves the app.
     */
    fun back(currentIndex: Int = 0): Boolean {
        history.removeLastOrNull()?.let { (prev, index) ->
            location = prev
            restoreIndex = index
            selection.clear()
            filter = ""
            closeDeepSearch()
            load()
            return true
        }
        // Not added to the history, so the next back keeps going up instead of returning here.
        if (canGoUp) { up(currentIndex, addToHistory = false); return true }
        if (location != home) { navigate(home, currentIndex, addToHistory = false); return true }
        return false
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
        val openVault = openVaultOnLoad
        openVaultOnLoad = false
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
                    // Arrived at a vault's folder: open the vault (asks for the password unless it's unlocked).
                    if (openVault && CryptoVfs.looksLikeVault(list)) openVault(loc)
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
                    if (e is ArchivePasswordException) { lockedArchive = e; passwordPromptHidden = false }
                    if (e is VaultLockedException && !unlocking) vaultPrompt = VaultPrompt(e.vaultId, vaultTitle(e.vaultId))
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
        private var nextTabId = 1
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
