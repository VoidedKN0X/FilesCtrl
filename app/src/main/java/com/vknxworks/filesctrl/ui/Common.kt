package com.vknxworks.filesctrl.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.provider.NetDocumentsProvider
import java.io.File

val LocalNav = staticCompositionLocalOf<NavHostController> { error("No nav controller") }

/**
 * Back from a screen the user is looking at. Repeat taps while a screen change is still animating are ignored,
 * and the main screen at the bottom is never popped (that emptied the screen stack and left a black screen
 * until the app was restarted). Returns true if a screen was closed.
 */
fun NavHostController.back(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (previousBackStackEntry == null) return false
    if (!current.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return false
    return popBackStack()
}

/** Closes [route] if it's on top, for screens that finish on their own (not from a tap). Returns true if closed. */
fun NavHostController.close(route: String): Boolean =
    previousBackStackEntry != null && currentBackStackEntry?.destination?.route == route && popBackStack()

/** Opens a screen from a tap, ignoring taps on a screen that's already on its way out or not shown yet. */
fun NavHostController.go(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == false) return
    navigate(route)
}

object Fmt {
    fun size(ctx: Context, bytes: Long): String = Formatter.formatFileSize(ctx, bytes)

    fun date(ctx: Context, ms: Long): String {
        if (ms <= 0) return ""
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
            (if (System.currentTimeMillis() - ms < DateUtils.DAY_IN_MILLIS) DateUtils.FORMAT_SHOW_TIME else 0) or
            (if (System.currentTimeMillis() - ms > DateUtils.YEAR_IN_MILLIS / 2) DateUtils.FORMAT_SHOW_YEAR else 0)
        return DateUtils.formatDateTime(ctx, ms, flags)
    }

    fun dateTime(ctx: Context, ms: Long): String = if (ms <= 0) "—" else DateUtils.formatDateTime(
        ctx, ms, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )

    /** "drwxr-xr-x" */
    fun perms(mode: Int): String {
        val type = when (mode and 0xF000) {
            0x4000 -> 'd'; 0xA000 -> 'l'; 0x8000 -> '-'; 0x2000 -> 'c'; 0x6000 -> 'b'; 0x1000 -> 'p'; 0xC000 -> 's'; else -> '-'
        }
        val sb = StringBuilder().append(type)
        val bits = "rwxrwxrwx"
        for (i in 0 until 9) sb.append(if (mode and (1 shl (8 - i)) != 0) bits[i] else '-')
        return sb.toString()
    }

    fun octal(mode: Int): String = Integer.toOctalString(mode and 0xFFF).padStart(4, '0')
}

@Suppress("DEPRECATION")
fun kindIcon(kind: Mime.Kind): ImageVector = when (kind) {
    Mime.Kind.Folder -> Icons.Filled.Folder
    Mime.Kind.Image -> Icons.Outlined.Image
    Mime.Kind.Video -> Icons.Outlined.VideoFile
    Mime.Kind.Audio -> Icons.Outlined.AudioFile
    Mime.Kind.Text -> Icons.Outlined.TextSnippet
    Mime.Kind.Code -> Icons.Outlined.Code
    Mime.Kind.Pdf -> Icons.Outlined.PictureAsPdf
    Mime.Kind.Archive -> Icons.Outlined.FolderZip
    Mime.Kind.Apk -> Icons.Outlined.Android
    Mime.Kind.Document -> Icons.Outlined.Description
    Mime.Kind.Sheet -> Icons.Outlined.TableChart
    Mime.Kind.Slides -> Icons.Outlined.Slideshow
    Mime.Kind.Other -> Icons.Outlined.InsertDriveFile
}

@Composable
fun kindColor(kind: Mime.Kind): Color {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        Mime.Kind.Folder -> cs.primary
        Mime.Kind.Image -> Color(0xFF2E9E62)
        Mime.Kind.Video -> Color(0xFFD94F45)
        Mime.Kind.Audio -> Color(0xFFE08A1E)
        Mime.Kind.Pdf -> Color(0xFFC62828)
        Mime.Kind.Archive -> Color(0xFF8D6E63)
        Mime.Kind.Apk -> Color(0xFF3DDC84)
        Mime.Kind.Document -> Color(0xFF3B78E7)
        Mime.Kind.Sheet -> Color(0xFF1E8E3E)
        Mime.Kind.Slides -> Color(0xFFF29900)
        Mime.Kind.Code -> cs.tertiary
        else -> cs.onSurfaceVariant
    }
}

/** True if the file can get a picture thumbnail. */
fun hasThumbnail(e: FileEntry): Boolean {
    // Inside an archive each thumbnail would mean reading through the archive again.
    if (e.vfsId.startsWith(ArchiveVfs.PREFIX)) return false
    val k = Mime.kind(e)
    if (k != Mime.Kind.Image && k != Mime.Kind.Video) return false
    if (e.vfsId == LocalVfs.ID) return true
    // Remote: images only (video frames would need the whole file streamed), and not huge ones.
    return App.settings.nfsThumbnails && k == Mime.Kind.Image && e.size in 1..25L * 1024 * 1024
}

fun thumbnailModel(e: FileEntry): Any = if (e.vfsId == LocalVfs.ID) File(e.path) else NetDocumentsProvider.uriFor(e)

/** A file's type icon on a tinted tile, or its thumbnail. */
@Composable
fun FileIcon(e: FileEntry, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val kind = Mime.kind(e)
    val tint = kindColor(kind)
    val shape = RoundedCornerShape(size * 0.28f)
    var failed by remember(e.path, e.modified) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(shape).background(if (selected) MaterialTheme.colorScheme.primary else tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.55f))
        } else {
            Icon(kindIcon(kind), null, tint = tint, modifier = Modifier.size(size * 0.55f))
            if (hasThumbnail(e) && !failed) {
                AsyncImage(
                    model = thumbnailModel(e),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (e.isLink) {
                Icon(
                    Icons.Outlined.Link, null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.32f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                )
            }
        }
    }
}

// ---------- Opening and sharing ----------

/**
 * A content:// URI other apps can read (and write) the file through. Local files are shared only from
 * phone storage, SD cards and USB drives (under /storage); anything else throws IllegalArgumentException.
 */
fun contentUri(ctx: Context, e: FileEntry): Uri =
    if (e.vfsId == LocalVfs.ID) FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(e.path))
    else NetDocumentsProvider.uriFor(e)

private fun contentUriOrToast(ctx: Context, e: FileEntry): Uri? = try {
    contentUri(ctx, e)
} catch (_: IllegalArgumentException) {
    Toast.makeText(ctx, "Only files in phone storage, SD cards and USB drives can be opened in other apps", Toast.LENGTH_LONG).show()
    null
}

/** True if [e] is on a server saved as read-only. */
fun isReadOnly(e: FileEntry): Boolean =
    e.vfsId.startsWith(RemoteVfs.PREFIX) && App.servers.get(e.vfsId.removePrefix(RemoteVfs.PREFIX))?.readOnly == true

/**
 * Opens [e] in another app. Viewing grants read access only; [edit] (an explicit "Edit with") sends ACTION_EDIT
 * with write access so the other app can save back, and is refused for read-only servers.
 */
fun openFile(ctx: Context, e: FileEntry, chooser: Boolean = false, mimeOverride: String? = null, edit: Boolean = false) {
    val canWrite = edit && !isReadOnly(e)
    if (edit && !canWrite) { Toast.makeText(ctx, "This server is read-only", Toast.LENGTH_SHORT).show(); return }
    var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    if (canWrite) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val uri = contentUriOrToast(ctx, e) ?: return
    val intent = Intent(if (canWrite) Intent.ACTION_EDIT else Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeOverride ?: e.mime)
        .addFlags(flags)
    try {
        ctx.startActivity(
            if (chooser) Intent.createChooser(intent, if (canWrite) "Edit with" else "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else intent,
        )
    } catch (_: ActivityNotFoundException) {
        if (!chooser) openFile(ctx, e, chooser = true, mimeOverride = "*/*", edit = edit)
        else Toast.makeText(ctx, "No app can ${if (canWrite) "edit" else "open"} this file", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, items: List<FileEntry>) {
    val files = items.filter { !it.isDir }
    if (files.isEmpty()) { Toast.makeText(ctx, "Folders can't be shared", Toast.LENGTH_SHORT).show(); return }
    val uris = ArrayList(files.map { contentUriOrToast(ctx, it) ?: return })
    val mimes = files.map { it.mime }.distinct()
    val type = if (mimes.size == 1) mimes[0] else mimes.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    intent.type = type
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Local path, or a URL like smb://nas/media/file for network locations. */
fun displayPath(e: FileEntry): String {
    if (e.vfsId == LocalVfs.ID) return e.path
    if (e.vfsId.startsWith(ArchiveVfs.PREFIX)) {
        // A file inside an archive: the archive's own path, then the path inside it.
        val arc = ArchiveVfs.archiveOf(e.vfsId) ?: return e.path
        val outer = displayPath(FileEntry(arc.vfsId, arc.path, Paths.name(arc.path), false, 0, 0))
        return if (e.path == "/") outer else outer + e.path
    }
    val s = App.servers.get(e.vfsId.removePrefix(RemoteVfs.PREFIX)) ?: return e.path
    return s.url(e.path)
}

fun copyText(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
