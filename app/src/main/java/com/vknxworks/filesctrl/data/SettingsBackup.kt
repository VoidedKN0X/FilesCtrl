package com.vknxworks.filesctrl.data

import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.RemoteVfs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Thrown for a file that isn't a FilesCtrl settings file, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

/**
 * FilesCtrl's settings as a JSON file you keep (Settings > Back up and restore): the switches and choices, and when
 * you ask for it your saved servers and bookmarks. Never in the file: passwords, private keys, the app PIN and lock
 * keys, vault passwords, and also not the host keys and certificates you confirmed (a restored server asks for them
 * again, so a file can never make FilesCtrl trust a server). Every field is optional, so files from older or newer
 * versions restore what they can.
 */
object SettingsBackup {
    const val FORMAT = "filesctrl-settings"
    const val VERSION = 1
    const val MAX_BYTES = 512 * 1024

    /** What a parsed file holds, for the "what will be replaced" question. */
    class Contents(val json: JSONObject) {
        val createdAt: String = json.optString("createdAt").take(10)
        val servers: List<NetServer> = json.optJSONArray("servers")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> runCatching { cleanServer(NetServer.fromJson(arr.getJSONObject(i)) { "" }) }.getOrNull() }
        }.orEmpty()
        val bookmarks: List<Pair<String, String>> = json.optJSONArray("bookmarks")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { it.optString("name") to it.optString("loc") }?.takeIf { it.second.isNotBlank() } }
        }.orEmpty()
        val hasSettings: Boolean = json.optJSONObject("settings") != null
    }

    /** A server as it may come from a file: no secrets, no trusted keys or certificates, no accepted plain HTTP. */
    private fun cleanServer(s: NetServer): NetServer =
        s.copy(password = "", privateKey = "", hostKey = "", certPin = "", httpAcceptedHost = "")

    fun export(includePlaces: Boolean, appVersion: String, now: String): String {
        val s = App.settings
        val lock = App.lock
        val o = JSONObject().put("format", FORMAT).put("version", VERSION).put("appVersion", appVersion).put("createdAt", now)
        o.put(
            "settings",
            JSONObject()
                .put("theme", s.theme.name).put("dynamicColor", s.dynamicColor).put("showHidden", s.showHidden)
                .put("foldersFirst", s.foldersFirst).put("sortBy", s.sortBy.name).put("sortDescending", s.sortDescending)
                .put("grid", s.gridView).put("confirmDelete", s.confirmDelete).put("networkThumbnails", s.nfsThumbnails)
                .put("builtInViewer", s.builtInViewer).put("dualPane", s.dualPane).put("launcherBookmarks", s.launcherBookmarks)
                .put("vaultLockSeconds", s.vaultLockSeconds)
                .put("lockTimeoutSeconds", lock.lockTimeoutSeconds).put("hideScreenshots", lock.hideScreenshots).put("hideRecents", lock.hideRecents),
        )
        if (includePlaces) {
            o.put("servers", JSONArray().apply { App.servers.all.forEach { put(cleanServer(it).toJson { "" }) } })
            o.put("bookmarks", JSONArray().apply { App.bookmarks.all.forEach { put(JSONObject().put("name", it.name).put("loc", it.location.key)) } })
        }
        return o.toString(2)
    }

    fun parse(text: String): Contents {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: throw BackupFormatException(newer = false)
        if (o.optString("format") != FORMAT) throw BackupFormatException(newer = false)
        if (o.optInt("version", 0) > VERSION) throw BackupFormatException(newer = true)
        return Contents(o)
    }

    /** What [restore] did: servers added, servers already here, bookmarks added. */
    data class Result(val serversAdded: Int, val serversKept: Int, val bookmarksAdded: Int)

    /**
     * Applies [c]. Settings are replaced, except that privacy can only get stricter: Hide in screenshots and Hide in
     * recent apps are never turned off, and the app lock and vaults never wait longer than they do now. The app lock
     * itself is never turned on or off. Servers are added when this phone doesn't have the same one yet (same
     * protocol, address, port, folder and user), without passwords, keys or trusted keys: those are entered and
     * confirmed again. Bookmarks are added for places that exist here (on the phone, or on a restored or existing
     * server). Nothing already here is removed. Call off the main thread.
     */
    fun restore(c: Contents): Result {
        c.json.optJSONObject("settings")?.let { applySettings(it) }
        // Old server id in the file -> id here, so bookmarks on those servers point to the right one.
        val ids = HashMap<String, String>()
        var added = 0
        var kept = 0
        for (s in c.servers) {
            val same = App.servers.all.firstOrNull { sameServer(it, s) }
            if (same != null) {
                ids[s.id] = same.id
                kept++
                continue
            }
            val id = if (App.servers.get(s.id) == null) s.id else UUID.randomUUID().toString().take(8)
            // A jump host is kept only when it points at a server that's here (or restored before it).
            val jump = s.jumpId.takeIf { it.isNotBlank() }?.let { ids[it] ?: it.takeIf { j -> App.servers.get(j) != null } }.orEmpty()
            App.servers.save(s.copy(id = id, jumpId = jump))
            ids[s.id] = id
            added++
        }
        var marks = 0
        for ((name, key) in c.bookmarks) {
            val loc = Location.fromKey(key) ?: continue
            val mapped = if (loc.vfsId.startsWith(RemoteVfs.PREFIX)) {
                val old = loc.vfsId.removePrefix(RemoteVfs.PREFIX)
                val here = ids[old] ?: continue
                Location(RemoteVfs.idFor(here), loc.path)
            } else if (loc.vfsId == LocalVfs.ID) loc else continue
            if (App.bookmarks.contains(mapped)) continue
            App.bookmarks.add(Bookmark(name.ifBlank { com.vknxworks.filesctrl.fs.Paths.name(mapped.path) }, mapped))
            marks++
        }
        return Result(added, kept, marks)
    }

    private fun sameServer(a: NetServer, b: NetServer): Boolean =
        a.type == b.type && a.host.trim().equals(b.host.trim(), ignoreCase = true) && a.effectivePort == b.effectivePort &&
            a.path.trim('/') == b.path.trim('/') && a.username == b.username

    private fun applySettings(o: JSONObject) {
        val s = App.settings
        val lock = App.lock
        o.optString("theme").takeIf { it.isNotBlank() }?.let { n -> ThemeMode.entries.firstOrNull { it.name == n }?.let { s.theme = it } }
        if (o.has("dynamicColor")) s.dynamicColor = o.optBoolean("dynamicColor")
        if (o.has("showHidden")) s.showHidden = o.optBoolean("showHidden")
        if (o.has("foldersFirst")) s.foldersFirst = o.optBoolean("foldersFirst")
        o.optString("sortBy").takeIf { it.isNotBlank() }?.let { n -> SortBy.entries.firstOrNull { it.name == n }?.let { s.sortBy = it } }
        if (o.has("sortDescending")) s.sortDescending = o.optBoolean("sortDescending")
        if (o.has("grid")) s.gridView = o.optBoolean("grid")
        if (o.has("confirmDelete")) s.confirmDelete = o.optBoolean("confirmDelete")
        if (o.has("networkThumbnails")) s.nfsThumbnails = o.optBoolean("networkThumbnails")
        if (o.has("builtInViewer")) s.builtInViewer = o.optBoolean("builtInViewer")
        if (o.has("dualPane")) s.dualPane = o.optBoolean("dualPane")
        if (o.has("launcherBookmarks")) s.launcherBookmarks = o.optBoolean("launcherBookmarks")
        if (o.has("vaultLockSeconds")) {
            val v = o.optInt("vaultLockSeconds", s.vaultLockSeconds)
            if (v >= 0 && v < s.vaultLockSeconds) s.vaultLockSeconds = v
        }
        if (o.has("lockTimeoutSeconds")) {
            val v = o.optInt("lockTimeoutSeconds", lock.lockTimeoutSeconds)
            if (stricterTimeout(v, lock.lockTimeoutSeconds)) lock.lockTimeoutSeconds = v
        }
        if (o.optBoolean("hideScreenshots", false)) lock.hideScreenshots = true
        if (o.optBoolean("hideRecents", false)) lock.hideRecents = true
    }

    /** True when [new] locks again at least as soon as [cur] (-1 = only when the app restarts, the longest). */
    fun stricterTimeout(new: Int, cur: Int): Boolean {
        if (new < -1) return false
        if (cur == -1) return new != -1
        if (new == -1) return false
        return new < cur
    }
}
