package com.vknxworks.filesctrl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.MainTab
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.Space
import com.vknxworks.filesctrl.fs.Vfs
import com.vknxworks.filesctrl.ops.ScanNode
import com.vknxworks.filesctrl.ops.ScanTotals
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlChip
import com.vknxworks.filesctrl.ui.components.CtrlProgress
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

fun analyserRoute(loc: Location) = "analyse?loc=" + android.net.Uri.encode(loc.key)

private enum class AnalyserView { Folders, Largest, Types }

/**
 * Storage analyser: adds up everything under a folder (on the phone or a server) and shows where the space goes,
 * biggest first: by sub-folder (tap one to look inside), the largest files, and by file type. Only reads folder
 * listings, never the files themselves. Folder links aren't followed. Nothing is changed or deleted here.
 */
@Composable
fun AnalyserScreen(loc: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf<ScanNode?>(null) }
    var totals by remember { mutableStateOf<ScanTotals?>(null) }
    var space by remember { mutableStateOf<Space?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scannedFolders by remember { mutableIntStateOf(0) }
    var scannedBytes by remember { mutableLongStateOf(0L) }
    val stack = remember { mutableStateListOf<ScanNode>() }
    var view by remember { mutableStateOf(AnalyserView.Folders) }

    LaunchedEffect(loc) {
        val result = withContext(Dispatchers.IO) {
            try {
                val vfs = App.vfs.get(loc.vfsId)
                space = try { vfs.space(loc.path) } catch (_: Exception) { null }
                val t = ScanTotals()
                val node = ScanNode(Paths.name(loc.path), loc.path, true, loc.vfsId)
                // Counted here and handed to the screen at most every 100 ms: writing Compose state from this
                // thread for every file meant hundreds of thousands of snapshot writes on big folders.
                var bytes = 0L
                var folders = 0
                var publishedAt = 0L
                fun publish(force: Boolean) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (!force && now - publishedAt < 100) return
                    publishedAt = now
                    scannedBytes = bytes
                    scannedFolders = folders
                }
                suspend fun walk(n: ScanNode) {
                    coroutineContext.ensureActive()
                    val list = try { vfs.list(n.path) } catch (e: java.io.IOException) {
                        if (n === node) throw e
                        n.unreadable++
                        return
                    }
                    val files = ArrayList<ScanNode>()
                    for (e in list) {
                        if (e.isDir) {
                            if (e.isLink || !Paths.isSafeName(e.name)) continue
                            val child = ScanNode(e.name, e.path, true, e.vfsId)
                            walk(child)
                            n.children += child
                            n.size += child.size
                            n.files += child.files
                            n.unreadable += child.unreadable
                        } else {
                            val f = ScanNode(e.name, e.path, false, e.vfsId).also { it.size = e.size.coerceAtLeast(0); it.files = 1 }
                            files += f
                            t.addFile(f, e.ext)
                            n.size += f.size
                            n.files++
                            bytes += f.size
                        }
                    }
                    ScanTotals.keepBiggest(n, files)
                    folders++
                    publish(force = false)
                }
                walk(node)
                publish(force = true)
                ScanTotals.sortTree(node)
                node to t
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                null
            }
        }
        if (result != null) {
            root = result.first
            totals = result.second
            stack.clear()
            stack += result.first
        }
    }

    val current = stack.lastOrNull()
    BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex) }

    fun showInFolder(n: ScanNode) {
        onBack()
        BrowserTabs.focused.navigate(Location(n.vfsId, if (n.isDir) n.path else Paths.parent(n.path)))
        MainTab.selected = MainTab.FILES
    }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlBackBar(
            stringResource(R.string.analyser_title),
            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() },
            backDescription = stringResource(R.string.back),
            subtitle = displayPath(FileEntry(loc.vfsId, current?.path ?: loc.path, Paths.name(current?.path ?: loc.path), true, 0, 0)),
        )
        when {
            error != null -> Text(stringResource(R.string.analyser_failed, error ?: ""), color = CtrlColors.Error, modifier = Modifier.padding(20.dp))
            current == null -> Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.analyser_scanning), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.analyser_progress, scannedFolders, scannedFolders, Fmt.size(ctx, scannedBytes)),
                    style = CtrlType.Label, color = CtrlColors.Muted,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = CtrlColors.Accent, trackColor = CtrlColors.ProgressTrack)
            }
            else -> {
                val top = root!!
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(Fmt.size(ctx, current.size), style = CtrlType.PageTitle, color = CtrlColors.Text)
                    Text(
                        pluralStringResource(R.plurals.analyser_files, current.files, current.files) +
                            (space?.takeIf { it.total > 0 && stack.size == 1 }?.let { " · " + App.str(R.string.analyser_free_of, Fmt.size(ctx, it.free), Fmt.size(ctx, it.total)) } ?: ""),
                        style = CtrlType.Label, color = CtrlColors.Muted,
                    )
                    if (current.unreadable > 0) Text(
                        pluralStringResource(R.plurals.analyser_unreadable, current.unreadable, current.unreadable),
                        style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AnalyserView.Folders to R.string.analyser_view_folders,
                            AnalyserView.Largest to R.string.analyser_view_largest,
                            AnalyserView.Types to R.string.analyser_view_types,
                        ).forEach { (v, label) ->
                            CtrlChip(stringResource(label), Modifier.clickable { view = v }, filled = view == v)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    when (view) {
                        AnalyserView.Folders -> {
                            items(current.children, key = { it.path }) { n ->
                                BarRow(
                                    if (n.isDir) Icons.Outlined.Folder else Icons.Outlined.Description, n.name, Fmt.size(ctx, n.size),
                                    if (n.isDir) pluralStringResource(R.plurals.analyser_files, n.files, n.files) else null,
                                    n.size, current.size,
                                    onClick = { if (n.isDir && (n.children.isNotEmpty() || n.otherFiles > 0)) stack += n else showInFolder(n) },
                                    onLong = { showInFolder(n) },
                                )
                            }
                            if (current.otherFiles > 0) item(key = "other") {
                                BarRow(
                                    Icons.Outlined.Description, pluralStringResource(R.plurals.analyser_other_files, current.otherFiles, current.otherFiles),
                                    Fmt.size(ctx, current.otherBytes), null, current.otherBytes, current.size, onClick = {}, onLong = {},
                                )
                            }
                            if (current.children.isEmpty() && current.otherFiles == 0) item(key = "empty") {
                                Text(stringResource(R.string.analyser_empty), color = CtrlColors.Muted, modifier = Modifier.padding(20.dp))
                            }
                        }
                        AnalyserView.Largest -> items(totals?.largestFiles().orEmpty().filter { Paths.isWithin(it.path, current.path) }, key = { "l" + it.path }) { n ->
                            BarRow(Icons.Outlined.Description, n.name, Fmt.size(ctx, n.size), Paths.parent(n.path), n.size, top.size, onClick = { showInFolder(n) }, onLong = { showInFolder(n) })
                        }
                        AnalyserView.Types -> items(totals?.types().orEmpty(), key = { "t" + it.first }) { (ext, bytes, count) ->
                            BarRow(Icons.Outlined.Description, if (ext == "—") stringResource(R.string.analyser_no_extension) else ".$ext", Fmt.size(ctx, bytes), pluralStringResource(R.plurals.analyser_files, count, count), bytes, top.size, onClick = {}, onLong = {})
                        }
                    }
                    item(key = "note") {
                        Text(
                            stringResource(if (view == AnalyserView.Folders) R.string.analyser_note else R.string.analyser_note_whole),
                            style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, size: String, sub: String?, bytes: Long, total: Long, onClick: () -> Unit, onLong: () -> Unit) {
    val fraction = if (total > 0) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().topDivider(inset = 20.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = CtrlType.Label, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(size, style = CtrlType.Label, color = CtrlColors.Text)
                Text("%.1f%%".format(fraction * 100), style = CtrlType.Label, color = CtrlColors.Muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        CtrlProgress(fraction, Modifier.fillMaxWidth(), color = CtrlColors.Accent)
    }
}
