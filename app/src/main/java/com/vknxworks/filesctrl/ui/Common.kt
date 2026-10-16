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
import com.vknxworks.filesctrl.data.NfsServer
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.NfsVfs
import com.vknxworks.filesctrl.provider.NfsDocumentsProvider
import java.io.File

val LocalNav = staticCompositionLocalOf<NavHostController> { error("No nav controller") }

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
    val k = Mime.kind(e)
    if (k != Mime.Kind.Image && k != Mime.Kind.Video) return false
    if (e.vfsId == LocalVfs.ID) return true
    // Remote: images only (video frames would need the whole file streamed), and not huge ones.
    return App.settings.nfsThumbnails && k == Mime.Kind.Image && e.size in 1..25L * 1024 * 1024
}

fun thumbnailModel(e: FileEntry): Any = if (e.vfsId == LocalVfs.ID) File(e.path) else NfsDocumentsProvider.uriFor(e)

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

/** A content:// URI other apps can read (and write) the file through. */
fun contentUri(ctx: Context, e: FileEntry): Uri =
    if (e.vfsId == LocalVfs.ID) FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(e.path))
    else NfsDocumentsProvider.uriFor(e)

fun openFile(ctx: Context, e: FileEntry, chooser: Boolean = false, mimeOverride: String? = null) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri(ctx, e), mimeOverride ?: e.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(if (chooser) Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
    } catch (_: ActivityNotFoundException) {
        if (!chooser) openFile(ctx, e, chooser = true, mimeOverride = "*/*")
        else Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, items: List<FileEntry>) {
    val files = items.filter { !it.isDir }
    if (files.isEmpty()) { Toast.makeText(ctx, "Folders can't be shared", Toast.LENGTH_SHORT).show(); return }
    val uris = ArrayList(files.map { contentUri(ctx, it) })
    val mimes = files.map { it.mime }.distinct()
    val type = if (mimes.size == 1) mimes[0] else mimes.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    intent.type = type
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Local path, or nfs://host/export/path for shares. */
fun displayPath(e: FileEntry): String {
    if (e.vfsId == LocalVfs.ID) return e.path
    val s: NfsServer = App.servers.get(e.vfsId.removePrefix(NfsVfs.PREFIX)) ?: return e.path
    val export = s.export.trimEnd('/')
    return "nfs://${s.host}$export${e.path}"
}

fun copyText(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
