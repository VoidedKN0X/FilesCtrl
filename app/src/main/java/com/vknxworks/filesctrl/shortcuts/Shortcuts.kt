package com.vknxworks.filesctrl.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.MainActivity
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs

/**
 * Home screen shortcuts to folders: pinned ones ("Add to home screen") and the first bookmarks as launcher
 * shortcuts (long-press FilesCtrl's icon). Each opens FilesCtrl at that folder, behind the app lock like any
 * other start. Nothing but the folder's place is in the shortcut: no passwords, no server details.
 */
object Shortcuts {
    const val ACTION_OPEN = "com.vknxworks.filesctrl.action.OPEN_LOCATION"
    const val EXTRA_LOCATION = "location"
    private const val MAX_DYNAMIC = 4

    fun intentFor(ctx: Context, loc: Location): Intent =
        Intent(ACTION_OPEN).setClass(ctx, MainActivity::class.java)
            .putExtra(EXTRA_LOCATION, loc.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /**
     * The folder an intent asks to open, or null. MainActivity can be started by any app, so only places that
     * exist in FilesCtrl are accepted: a folder on this phone's storage volumes, or a path on a saved server.
     */
    fun locationFrom(intent: Intent?): Location? {
        if (intent?.action != ACTION_OPEN) return null
        val loc = intent.getStringExtra(EXTRA_LOCATION)?.let { Location.fromKey(it) } ?: return null
        val path = Paths.normalize(loc.path) ?: return null
        return when {
            loc.vfsId == LocalVfs.ID ->
                if (App.vfs.local.volumes().any { Paths.isWithin(path, it.path) }) Location(LocalVfs.ID, path) else null
            loc.vfsId.startsWith(RemoteVfs.PREFIX) ->
                if (App.servers.get(loc.vfsId.removePrefix(RemoteVfs.PREFIX)) != null) Location(loc.vfsId, path) else null
            else -> null
        }
    }

    private fun idFor(loc: Location): String =
        "loc_" + java.security.MessageDigest.getInstance("SHA-256").digest(loc.key.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)

    /** Archives and other places that can't be opened directly from outside don't get shortcuts. */
    fun canPin(loc: Location): Boolean = loc.vfsId == LocalVfs.ID || loc.vfsId.startsWith(RemoteVfs.PREFIX)

    private fun info(ctx: Context, name: String, loc: Location, rank: Int = 0): ShortcutInfoCompat {
        val icon = if (loc.vfsId.startsWith(RemoteVfs.PREFIX)) R.mipmap.ic_shortcut_network else R.mipmap.ic_shortcut_folder
        val label = name.ifBlank { Paths.name(loc.path) }
        return ShortcutInfoCompat.Builder(ctx, idFor(loc))
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(ctx, icon))
            .setIntent(intentFor(ctx, loc))
            .setRank(rank)
            .build()
    }

    /** Asks the launcher to put a shortcut to [loc] on the home screen. False if the launcher can't. */
    fun pin(ctx: Context, name: String, loc: Location): Boolean {
        if (!canPin(loc) || !ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(ctx, info(ctx, name, loc), null) }.getOrDefault(false)
    }

    /** The first bookmarks as launcher shortcuts (or none, when switched off in Settings). */
    fun updateDynamic(ctx: Context) {
        runCatching {
            if (!App.settings.launcherBookmarks) { ShortcutManagerCompat.removeAllDynamicShortcuts(ctx); return }
            val max = minOf(MAX_DYNAMIC, ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtLeast(1))
            val list = App.bookmarks.all.filter { canPin(it.location) }.take(max)
                .mapIndexed { i, b -> info(ctx, b.name, b.location, i) }
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        }
    }

    /** A removed server's home screen shortcuts are greyed out, since they can't open anything any more. */
    fun disableForVfs(ctx: Context, vfsId: String) {
        runCatching {
            val ids = ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter { s -> s.intent.getStringExtra(EXTRA_LOCATION)?.let { Location.fromKey(it)?.vfsId } == vfsId }
                .map { it.id }
            if (ids.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(ctx, ids, App.str(R.string.shortcut_server_removed))
        }
    }
}
