package com.vknxworks.filesctrl.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.vknxworks.filesctrl.fs.Location
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { System, Light, Dark }
enum class SortBy { Name, Size, Modified, Type }

/** App preferences, backed by SharedPreferences and observable from Compose. */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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
        get() = ThemeMode.entries[_theme.value.coerceIn(0, 2)]
        set(v) { _theme.value = v.ordinal }

    private val _dynamic = bool("dynamic_color", true)
    var dynamicColor by _dynamic

    private val _black = bool("pure_black", false)
    var pureBlack by _black

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

    private val _last = str("last_location", "")
    var lastLocation: Location?
        get() = Location.fromKey(_last.value)
        set(v) { _last.value = v?.key ?: "" }

    var askedNotifications by bool("asked_notifications", false)

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
    }
}
