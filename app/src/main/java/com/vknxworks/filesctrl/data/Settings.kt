package com.vknxworks.filesctrl.data

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.filesctrl.R
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.crypto.SealedPrefs
import com.vknxworks.filesctrl.data.crypto.SealedText
import com.vknxworks.filesctrl.fs.Location
import org.json.JSONArray
import org.json.JSONObject

/** The theme choices every Ctrl app offers. Stored by position, so new ones go at the end. */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Black(R.string.theme_black),
}
enum class SortBy { Name, Size, Modified, Type }

/**
 * App preferences, backed by SharedPreferences and observable from Compose. The two that say where you have
 * been ([tabs] and [lastSaveTo]) are encrypted at rest with the app's data key ([SealedPrefs]); the switches
 * and choices are ordinary values.
 */
class Settings(context: Context, private val sealer: SealedPrefs) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // FilesCtrl 1.1.2 removed the optional anonymous stats. Delete the two settings they used (their keys
        // start with "usage_"), so nothing from them stays on the phone after the update.
        val leftover = sp.all.keys.filter { it.startsWith("usage_") }
        if (leftover.isNotEmpty()) sp.edit().apply { leftover.forEach { remove(it) } }.apply()
        // Before 1.2.1, pure black was a separate switch: Dark with it on is now the Pure black theme.
        if (sp.contains("pure_black")) {
            val edit = sp.edit().remove("pure_black")
            if (sp.getBoolean("pure_black", false) && sp.getInt("theme", 0) == ThemeMode.Dark.ordinal) edit.putInt("theme", ThemeMode.Black.ordinal)
            edit.apply()
        }
    }

    private inner class Pref<T>(private val key: String, def: T, read: (String, T) -> T, private val write: (String, T) -> Unit) {
        val state: MutableState<T> = mutableStateOf(read(key, def))
        var value: T
            get() = state.value
            set(v) { state.value = v; write(key, v) }
    }

    private fun bool(key: String, def: Boolean) = Pref(key, def, { k, d -> sp.getBoolean(k, d) }, { k, v -> sp.edit().putBoolean(k, v).apply() })
    private fun int(key: String, def: Int) = Pref(key, def, { k, d -> sp.getInt(k, d) }, { k, v -> sp.edit().putInt(k, v).apply() })

    /**
     * A text setting that is sealed at rest. Read and written straight from the preferences (nothing in the UI
     * observes these), and only once the app's screens are up, when the data key is in memory. A value from
     * before encryption reads as it is; one that can't be opened reads as "" (it only remembers a place, so it
     * simply starts fresh) and is replaced by the next write.
     */
    private fun sealedText(key: String): String = sealer.open(sp.getString(key, "") ?: "") ?: ""
    private fun putSealedText(key: String, value: String) {
        synchronized(sp) { sp.edit().putString(key, if (value.isEmpty()) "" else sealer.seal(value)).apply() }
    }

    /**
     * Seals [lastSaveTo] if it is still ordinary text from before encryption ([tabs] are written again at every
     * start anyway). Called from a background thread at start, after the data key was asked for.
     */
    fun sealStored() {
        try {
            synchronized(sp) {
                val stored = sp.getString("last_save_to", "") ?: ""
                if (stored.isEmpty() || SealedText.isSealed(stored)) return
                val sealed = sealer.seal(stored)
                if (sealed != stored) sp.edit().putString("last_save_to", sealed).apply()
            }
        } catch (_: Exception) {
        }
    }

    private val _theme = int("theme", 0)
    var theme: ThemeMode
        get() = ThemeMode.entries[_theme.value.coerceIn(0, ThemeMode.entries.size - 1)]
        set(v) { _theme.value = v.ordinal }

    private val _dynamic = bool("dynamic_color", true)
    var dynamicColor by _dynamic

    /** True black backgrounds (the "Pure black" theme). */
    val pureBlack: Boolean get() = theme == ThemeMode.Black

    private val _hidden = bool("show_hidden", false)
    var showHidden by _hidden

    private val _foldersFirst = bool("folders_first", true)
    var foldersFirst by _foldersFirst

    private val _sort = int("sort_by", 0)
    var sortBy: SortBy
        get() = SortBy.entries[_sort.value.coerceIn(0, SortBy.entries.size - 1)]
        set(v) { _sort.value = v.ordinal }

    private val _desc = bool("sort_desc", false)
    var sortDescending by _desc

    private val _grid = bool("grid", false)
    var gridView by _grid

    private val _confirmDelete = bool("confirm_delete", true)
    var confirmDelete by _confirmDelete

    /** Load image thumbnails from NFS shares (reads each image over the network). */
    private val _nfsThumbs = bool("nfs_thumbs", true)
    var nfsThumbnails by _nfsThumbs

    var askedNotifications by bool("asked_notifications", false)

    /** Where "Save to…" (files shared with FilesCtrl) saved last time, as a [Location.key]; empty if never. */
    var lastSaveTo: String
        get() = sealedText("last_save_to")
        set(v) = putSealedText("last_save_to", v)

    /** Pictures open in FilesCtrl's own viewer (otherwise in another app, as before 1.7.0). */
    private val _viewer = bool("builtin_viewer", true)
    var builtInViewer by _viewer

    /** Open tabs of both panes, restored when FilesCtrl starts again ([com.vknxworks.filesctrl.ui.BrowserTabs]). */
    var tabs: String
        get() = sealedText("tabs")
        set(v) = putSealedText("tabs", v)

    /** Two browsers side by side on wide windows (tablets, unfolded foldables, landscape). */
    private val _dualPane = bool("dual_pane", true)
    var dualPane by _dualPane

    /** Show the first bookmarks when long-pressing FilesCtrl's icon on the home screen. */
    private val _launcherBookmarks = bool("launcher_bookmarks", true)
    var launcherBookmarks by _launcherBookmarks

    /** Reach Android/data and Android/obb through Shizuku (optional; off by default). */
    private val _shizuku = bool("shizuku", false)
    var shizuku by _shizuku

    /** Cryptomator vaults lock after FilesCtrl has been in the background this long (0 = as soon as you leave). */
    private val _vaultLock = int("vault_lock_seconds", 300)
    var vaultLockSeconds by _vaultLock

    private operator fun <T> Pref<T>.getValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>): T = value
    private operator fun <T> Pref<T>.setValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>, v: T) { value = v }
}

data class Bookmark(val name: String, val location: Location)

/**
 * The bookmarks. Encrypted at rest with the app's data key ([SealedPrefs]), like the saved servers, and read on
 * a background thread when the app starts (the data key comes from the phone's secure hardware, which is too
 * slow for app start). Everything that reads or changes the list waits for that first load. Bookmarks from
 * before encryption are still read, and are then replaced by their encrypted copy; without a data key they are
 * read and written as before. A sealed list that can't be opened is never overwritten ([unreadable]).
 */
class Bookmarks(context: Context, private val sealer: SealedPrefs) {
    private val sp = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    /** The list itself. Only for code in this class that runs after, or as part of, the first load. */
    private val items = mutableStateListOf<Bookmark>()

    /** Opens when the first load has finished (whether or not it could read anything). */
    private val loaded = java.util.concurrent.CountDownLatch(1)

    private val listeners = mutableListOf<() -> Unit>()

    /** The saved list is sealed and can't be opened: it stays as it is, and nothing is written over it. */
    @Volatile private var unreadableNow = false

    /** The saved bookmarks are encrypted and can't be opened. Observable from Compose. */
    var unreadable by mutableStateOf(false)
        private set

    /** The bookmarks. Waits for the first load. */
    val all: SnapshotStateList<Bookmark>
        get() {
            awaitLoaded()
            return items
        }

    init {
        // Last of the fields above, so the loading thread never sees one that isn't set yet.
        Thread({
            try {
                // The data key first: it asks the secure hardware, and is made at the first start.
                sealer.prepare()
                load()
                sealStored()
            } catch (_: Exception) {
            } finally {
                loaded.countDown()
            }
        }, "bookmarks-load").start()
    }

    /** Blocks until the first load is done. Never call it from [load] or anything it calls. */
    fun awaitLoaded() {
        if (loaded.count == 0L) return
        var interrupted = false
        while (true) {
            try {
                loaded.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    @Synchronized
    private fun load() {
        val raw = sealer.open(sp.getString("list", "[]") ?: "[]")
        if (raw == null) {
            // Sealed and it can't be opened: keep it exactly as it is and list nothing.
            items.clear()
            unreadableNow = true
            unreadable = true
            return
        }
        unreadableNow = false
        unreadable = false
        val list = ArrayList<Bookmark>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val loc = Location.fromKey(o.optString("loc")) ?: continue
                list += Bookmark(o.optString("name"), loc)
            }
        } catch (_: Exception) {}
        items.clear()
        items.addAll(list)
    }

    /**
     * Replaces bookmarks from before encryption at rest by their encrypted copy: the same text, sealed, checked
     * by opening it again and written by SharedPreferences in one step. If anything fails the ordinary text
     * stays in use and the next start tries again. Part of the first load.
     */
    @Synchronized
    private fun sealStored() {
        if (unreadableNow) return
        val stored = sp.getString("list", null) ?: return
        if (SealedText.isSealed(stored)) return
        val sealedText = sealer.seal(stored)
        if (sealedText != stored) sp.edit().putString("list", sealedText).commit()
    }

    /** Tries to open bookmarks that couldn't be opened again ([unreadable]). Off the main thread. */
    fun retryUnreadable() {
        awaitLoaded()
        if (!unreadableNow) return
        load()
        if (!unreadableNow) listeners.forEach { runCatching { it() } }
    }

    /**
     * Gives up on bookmarks that can't be opened and starts with none. With [keep] the sealed text is kept in
     * the same file under another name ("list_unreadable") instead of deleted. Does nothing unless [unreadable].
     */
    fun startEmpty(keep: Boolean) {
        awaitLoaded()
        synchronized(this) {
            if (!unreadableNow) return
            val stored = sp.getString("list", null)
            val edit = sp.edit()
            if (keep && stored != null) edit.putString("list_unreadable", stored)
            edit.remove("list")
            if (!edit.commit()) return
            items.clear()
            unreadableNow = false
            unreadable = false
        }
    }

    /** Called after every change to the list (the launcher shortcuts follow it). */
    fun onChange(l: () -> Unit) { listeners += l }

    fun contains(loc: Location) = all.any { it.location == loc }

    fun add(b: Bookmark) {
        if (contains(b.location)) return
        if (unreadableNow) {
            // Nothing is saved next to (or over) bookmarks that couldn't be opened; say why nothing happened.
            runCatching {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    android.widget.Toast.makeText(App.context, App.str(R.string.unreadable_cant_bookmark), android.widget.Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        all += b
        persist()
    }

    fun remove(loc: Location) {
        all.removeAll { it.location == loc }
        persist()
    }

    fun rename(loc: Location, name: String) {
        val i = all.indexOfFirst { it.location == loc }
        if (i >= 0) { all[i] = all[i].copy(name = name); persist() }
    }

    /** Bookmarks pointing into a deleted server go away with it. */
    fun removeVfs(vfsId: String) {
        if (all.removeAll { it.location.vfsId == vfsId }) persist()
    }

    private fun persist() {
        // Never over bookmarks that couldn't be opened: they stay until they open again or are given up.
        if (unreadableNow) return
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("name", it.name).put("loc", it.location.key)) }
        sp.edit().putString("list", sealer.seal(arr.toString())).apply()
        listeners.forEach { runCatching { it() } }
    }
}
