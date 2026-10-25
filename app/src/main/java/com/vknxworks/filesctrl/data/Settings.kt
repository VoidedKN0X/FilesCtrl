package com.vknxworks.filesctrl.data

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.filesctrl.R
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.vknxworks.filesctrl.fs.Location
import org.json.JSONArray
import org.json.JSONObject

/** The theme choices every Ctrl app offers. Stored by position, so new ones go at the end. */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Black(R.string.theme_black),
}
enum class SortBy { Name, Size, Modified, Type }

/** App preferences, backed by SharedPreferences and observable from Compose. */
class Settings(context: Context) {
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
    private fun str(key: String, def: String) = Pref(key, def, { k, d -> sp.getString(k, d) ?: d }, { k, v -> sp.edit().putString(k, v).apply() })

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
    var lastSaveTo by str("last_save_to", "")

    /** Pictures open in FilesCtrl's own viewer (otherwise in another app, as before 1.7.0). */
    private val _viewer = bool("builtin_viewer", true)
    var builtInViewer by _viewer

    /** Open tabs of both panes, restored when FilesCtrl starts again ([com.vknxworks.filesctrl.ui.BrowserTabs]). */
    var tabs by str("tabs", "")

    /** Two browsers side by side on wide windows (tablets, unfolded foldables, landscape). */
    private val _dualPane = bool("dual_pane", true)
    var dualPane by _dualPane

    /** Show the first bookmarks when long-pressing FilesCtrl's icon on the home screen. */
    private val _launcherBookmarks = bool("launcher_bookmarks", true)
    var launcherBookmarks by _launcherBookmarks


    private operator fun <T> Pref<T>.getValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>): T = value
    private operator fun <T> Pref<T>.setValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>, v: T) { value = v }
}

data class Bookmark(val name: String, val location: Location)

class Bookmarks(context: Context) {
    private val sp = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    val all = mutableStateListOf<Bookmark>()

    init {
        try {
            val arr = JSONArray(sp.getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val loc = Location.fromKey(o.optString("loc")) ?: continue
                all += Bookmark(o.optString("name"), loc)
            }
        } catch (_: Exception) {}
    }

    private val listeners = mutableListOf<() -> Unit>()

    /** Called after every change to the list (the launcher shortcuts follow it). */
    fun onChange(l: () -> Unit) { listeners += l }

    fun contains(loc: Location) = all.any { it.location == loc }

    fun add(b: Bookmark) {
        if (contains(b.location)) return
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
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("name", it.name).put("loc", it.location.key)) }
        sp.edit().putString("list", arr.toString()).apply()
        listeners.forEach { runCatching { it() } }
    }
}
