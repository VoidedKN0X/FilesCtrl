package com.vknxworks.filesctrl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject

/** One side of the browser: its own tabs, one of them showing. */
class Pane(val right: Boolean) {
    val tabs = mutableStateListOf<BrowserViewModel>()
    var active by mutableIntStateOf(0)

    val vm: BrowserViewModel get() { ensureOne(); return tabs[active.coerceIn(0, tabs.size - 1)] }

    fun ensureOne() { if (tabs.isEmpty()) { tabs += BrowserViewModel(); active = 0 } }

    fun open(loc: Location?, show: Boolean = true) {
        val at = minOf(active + 1, tabs.size)
        tabs.add(at, BrowserViewModel(loc))
        if (show) active = at
    }

    fun close(index: Int) {
        if (tabs.size <= 1 || index !in tabs.indices) return
        val gone = tabs.removeAt(index)
        gone.dispose()
        if (active > index || active >= tabs.size) active = (active - 1).coerceAtLeast(0)
    }
}

/**
 * The browser's tabs. On phones in portrait there's one pane with tabs; on wide windows (tablets, unfolded
 * foldables, landscape) a second pane with its own tabs sits next to it. Restored when FilesCtrl starts again.
 */
object BrowserTabs {
    val left = Pane(right = false)
    val right = Pane(right = true)

    /** True while the window is wide enough and dual pane is switched on (set by the layout). */
    var dualPane by mutableStateOf(false)
    /** True while the window is wide enough for two panes (tablets, unfolded foldables, landscape). */
    var wideWindow by mutableStateOf(false)
    /** In dual pane: the pane last touched, which Places, shortcuts and back act on. */
    var focusedRight by mutableStateOf(false)

    private var restored = false

    fun init() {
        if (restored) return
        restored = true
        restore()
        // Both panes always have a tab, so nothing is created while the screen is being drawn. A tab only
        // reads its folder once it's on screen.
        left.ensureOne()
        right.ensureOne()
    }

    val focusedPane: Pane get() = if (dualPane && focusedRight) right else left
    val focused: BrowserViewModel get() { init(); return focusedPane.vm }

    fun all(): List<BrowserViewModel> = left.tabs + right.tabs

    /** The tabs on screen right now: the open tab of each pane that's showing. */
    fun visible(): List<BrowserViewModel> {
        init()
        return if (dualPane) { right.ensureOne(); listOf(left.vm, right.vm) } else listOf(left.vm)
    }

    fun paneOf(vm: BrowserViewModel): Pane = if (right.tabs.contains(vm)) right else left
    fun otherPane(vm: BrowserViewModel): Pane = if (paneOf(vm).right) left else right

    /** The folder showing in the other pane, in dual pane mode. */
    fun otherLocation(vm: BrowserViewModel): Location? = if (dualPane) otherPane(vm).also { it.ensureOne() }.vm.location else null

    fun openTab(from: BrowserViewModel, loc: Location) {
        val p = paneOf(from)
        if (p.tabs.size >= MAX_TABS) return
        p.open(loc)
    }

    const val MAX_TABS = 12

    fun openInOtherPane(from: BrowserViewModel, loc: Location) {
        val p = otherPane(from)
        p.ensureOne()
        p.vm.navigate(loc)
    }

    fun onDualPaneShown() { right.ensureOne() }

    // ---------- Saved between starts ----------

    fun snapshot(): String {
        fun pane(p: Pane) = JSONObject().put("active", p.active)
            .put("tabs", JSONArray().apply { p.tabs.forEach { put(savable(it.location).key) } })
        return JSONObject().put("left", pane(left)).put("right", pane(right)).put("focusRight", focusedRight).toString()
    }

    fun save() { App.settings.tabs = snapshot() }

    private fun restore() {
        val raw = App.settings.tabs
        if (raw.isBlank()) return
        try {
            val o = JSONObject(raw)
            fun pane(p: Pane, j: JSONObject?) {
                j ?: return
                val arr = j.optJSONArray("tabs") ?: return
                for (i in 0 until minOf(arr.length(), 12)) {
                    val loc = Location.fromKey(arr.optString(i))?.let { valid(it) } ?: continue
                    p.tabs += BrowserViewModel(loc)
                }
                p.active = j.optInt("active", 0).coerceIn(0, (p.tabs.size - 1).coerceAtLeast(0))
            }
            pane(left, o.optJSONObject("left"))
            pane(right, o.optJSONObject("right"))
            focusedRight = o.optBoolean("focusRight", false)
        } catch (_: Exception) {}
    }

    /** Inside an archive, the tab is saved as the folder the archive is in (archives are opened afresh). */
    private fun savable(loc: Location): Location =
        if (loc.vfsId.startsWith(ArchiveVfs.PREFIX)) ArchiveVfs.archiveOf(loc.vfsId)?.let { Location(it.vfsId, Paths.parent(it.path)) }?.let { savable(it) } ?: loc
        else loc

    /** Only places that still exist: this phone's volumes, or a server that's still saved. */
    private fun valid(loc: Location): Location? {
        val path = Paths.normalize(loc.path) ?: return null
        return when {
            loc.vfsId == LocalVfs.ID -> if (App.vfs.local.volumes().any { Paths.isWithin(path, it.path) }) Location(LocalVfs.ID, path) else null
            loc.vfsId.startsWith(RemoteVfs.PREFIX) -> if (App.servers.get(loc.vfsId.removePrefix(RemoteVfs.PREFIX)) != null) Location(loc.vfsId, path) else null
            else -> null
        }
    }
}

/** Stops a closed tab's work (loads, searches, refresh on changes). */
fun BrowserViewModel.dispose() { viewModelScope.cancel() }
