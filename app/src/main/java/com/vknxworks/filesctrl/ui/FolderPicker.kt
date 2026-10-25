package com.vknxworks.filesctrl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlChip
import com.vknxworks.filesctrl.ui.components.CtrlIconButton
import com.vknxworks.filesctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** True if files can be written to [loc] (not inside an archive, not on a server saved as read-only). */
fun canWriteTo(loc: Location): Boolean = when {
    loc.vfsId == LocalVfs.ID -> true
    loc.vfsId.startsWith(RemoteVfs.PREFIX) -> App.servers.get(loc.vfsId.removePrefix(RemoteVfs.PREFIX))?.let { !it.readOnly } == true
    else -> false
}

/** A short name for a place: "Internal storage / Download", "NAS / media". */
fun placeLabel(loc: Location): String {
    val vfs = App.vfs.find(loc.vfsId) ?: return loc.path
    val (root, label) = try { vfs.rootOf(loc.path) } catch (_: Exception) { "/" to vfs.title }
    val server = if (loc.vfsId.startsWith(RemoteVfs.PREFIX)) App.servers.get(loc.vfsId.removePrefix(RemoteVfs.PREFIX))?.title else null
    val rest = loc.path.removePrefix(root).trim('/')
    val head = listOfNotNull(server, label.takeIf { server == null || it != server && it != "/" }).joinToString(" / ")
    return if (rest.isEmpty()) head else "$head / $rest"
}

/**
 * Full-screen folder chooser over every place FilesCtrl knows: this phone's storage, SD cards and USB drives,
 * saved network locations and bookmarks. Shows folders only; can make a new folder; [onPick] gets the folder
 * that's open when the main button is tapped. [start] opens straight in that folder, when it still exists.
 */
@Composable
fun FolderPicker(
    title: String,
    confirmLabel: String,
    start: Location?,
    onPick: (Location) -> Unit,
    onCancel: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(start?.takeIf { canWriteTo(it) }) }
    var folders by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }

    LaunchedEffect(current, reload) {
        val loc = current ?: run { folders = emptyList(); error = null; return@LaunchedEffect }
        loading = true
        error = null
        try {
            val list = withContext(Dispatchers.IO) { App.vfs.get(loc.vfsId).list(loc.path) }
            val showHidden = App.settings.showHidden
            folders = list.filter { it.isDir && (showHidden || !it.hidden) }
                .sortedWith { a, b -> BrowserViewModel.naturalCompare(a.name, b.name) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            folders = emptyList()
            error = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    fun up() {
        val loc = current ?: return
        val vfs = App.vfs.find(loc.vfsId)
        val root = try { vfs?.rootOf(loc.path)?.first } catch (_: Exception) { null }
        current = if (vfs == null || loc.path == root || loc.path == "/") null else Location(loc.vfsId, Paths.parent(loc.path))
    }

    BackHandler { if (current != null) up() else onCancel() }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlBackBar(
            title = title,
            onBack = { if (current != null) up() else onCancel() },
            backDescription = stringResource(if (current != null) R.string.back else R.string.lock_cancel),
            subtitle = current?.let { placeLabel(it) } ?: stringResource(R.string.picker_choose_place),
            backIcon = if (current != null) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
            actions = {
                if (current != null && error == null) {
                    CtrlIconButton(Icons.Outlined.CreateNewFolder, stringResource(R.string.new_folder), onClick = { newFolder = true })
                }
            },
        )
        header?.invoke()
        Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 20.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CtrlColors.Accent, trackColor = CtrlColors.ProgressTrack)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val loc = current
            when {
                loc == null -> PlacesList(start) { current = it }
                error != null -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyBox(Icons.Outlined.CloudOff, stringResource(R.string.couldn_t_open_this_folder) + "\n" + error)
                    CtrlButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
                folders.isEmpty() && !loading -> EmptyBox(Icons.Outlined.FolderOff, stringResource(R.string.picker_no_folders))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(folders, key = { it.path }) { f ->
                        PickerRow(leading = { FileIcon(f, 36.dp) }, title = f.name, meta = null) { current = f.location }
                    }
                }
            }
        }
        // The main button: save (or move/copy) into the folder that's open.
        Row(
            Modifier.fillMaxWidth().topDivider().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlSecondaryButton(onClick = onCancel, height = 52.dp) { Text(stringResource(R.string.lock_cancel)) }
            val loc = current
            val writable = loc != null && canWriteTo(loc) && error == null
            CtrlButton(onClick = { if (loc != null) onPick(loc) }, enabled = writable, height = 52.dp, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        loc == null -> stringResource(R.string.picker_open_a_folder)
                        !canWriteTo(loc) -> stringResource(R.string.picker_read_only)
                        else -> confirmLabel
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (newFolder) {
        val loc = current
        NameDialog(stringResource(R.string.new_folder), "", stringResource(R.string.create), onDismiss = { newFolder = false }) { name ->
            newFolder = false
            if (loc == null) return@NameDialog
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    try { App.vfs.get(loc.vfsId).mkdir(Paths.join(loc.path, name)); null } catch (e: Exception) { e.message ?: App.str(R.string.failed_5) }
                }
                if (err != null) error = err else current = Location(loc.vfsId, Paths.join(loc.path, name))
            }
        }
    }
}

/** The top level of the picker: last used folder, phone storage, servers and bookmarks. */
@Composable
private fun PlacesList(last: Location?, onOpen: (Location) -> Unit) {
    val volumes = remember { App.vfs.local.volumes() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (last != null && canWriteTo(last)) {
            item { SectionHeader(stringResource(R.string.picker_last_used)) }
            item { PickerRow({ PickerIcon(Icons.Outlined.History) }, placeLabel(last), null) { onOpen(last) } }
        }
        item { SectionHeader(stringResource(R.string.you_this_phone)) }
        items(volumes, key = { it.path }) { v ->
            PickerRow({ PickerIcon(Icons.Outlined.PhoneAndroid) }, v.label, v.path) { onOpen(Location(LocalVfs.ID, v.path)) }
        }
        val servers = App.servers.all.toList()
        if (servers.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.network)) }
            items(servers, key = { it.id }) { s ->
                PickerRow(
                    { CtrlChip(s.type.label.uppercase().take(6)) },
                    s.title,
                    if (s.readOnly) stringResource(R.string.subtitle_read_only, s.host) else s.host,
                ) { onOpen(Location(RemoteVfs.idFor(s.id), "/")) }
            }
        }
        val marks = App.bookmarks.all.filter { canWriteTo(it.location) }
        if (marks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.bookmarks)) }
            items(marks, key = { it.location.key }) { b ->
                PickerRow({ PickerIcon(Icons.Outlined.BookmarkBorder) }, b.name, placeLabel(b.location)) { onOpen(b.location) }
            }
        }
    }
}

@Composable
private fun PickerIcon(icon: ImageVector) {
    Icon(icon, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
}

@Composable
private fun PickerRow(leading: @Composable () -> Unit, title: String, meta: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp).topDivider()
            .clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!meta.isNullOrBlank()) Text(meta, style = CtrlType.Meta.copy(fontSize = 11.sp), color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
