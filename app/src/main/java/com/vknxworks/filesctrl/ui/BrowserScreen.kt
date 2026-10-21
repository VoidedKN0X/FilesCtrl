package com.vknxworks.filesctrl.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.Bookmark
import com.vknxworks.filesctrl.data.SortBy
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.Conflict
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.filesctrl.R

/** Bottom padding for this screen's lists, so they scroll clear of the island and paste card. */
private val LocalListPadding = androidx.compose.runtime.compositionLocalOf { PaddingValues(bottom = 96.dp) }

private sealed interface Dlg {
    data class New(val folder: Boolean) : Dlg
    data class Rename(val e: FileEntry) : Dlg
    data class Delete(val items: List<FileEntry>) : Dlg
    data class Props(val e: FileEntry) : Dlg
    data class Compress(val items: List<FileEntry>) : Dlg
    data class Extract(val archive: FileEntry, val dest: Location) : Dlg
    data class Conflicts(val names: List<String>, val items: List<FileEntry>, val dest: Location, val move: Boolean) : Dlg
    data class Error(val title: String, val message: String) : Dlg
}

// Navigation decodes the argument itself, so encode with Uri.encode (not URLEncoder, whose "+" for space it would keep).
fun editorRoute(e: FileEntry) = "edit?loc=" + Uri.encode(e.location.key)

@Composable
/** [islandSpace]: room the floating tab island takes at the bottom (0 when tabs are in a side rail). */
fun BrowserScreen(vm: BrowserViewModel, islandSpace: Dp) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val s = App.settings
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var hasAllFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    // Re-check "All files access" when coming back from the settings page, and refresh what's on screen.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                val now = Environment.isExternalStorageManager()
                if (now != hasAllFiles) { hasAllFiles = now; vm.load() }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val listState = remember(vm.location) { LazyListState() }
    val gridState = remember(vm.location) { LazyGridState() }
    var restored by remember(vm.location) { mutableStateOf(false) }
    LaunchedEffect(vm.location, vm.loadCount) {
        if (!restored && vm.loadCount > 0 && !vm.loading) {
            val i = vm.restoreIndex
            if (i > 0) { listState.scrollToItem(i); gridState.scrollToItem(i) }
            restored = true
        }
    }
    val currentIndex = { if (s.gridView) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex }

    // Hide the pull indicator explicitly when a refresh ends, so it can't get stuck half-way.
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(vm.refreshing) { if (!vm.refreshing) pullState.animateToHidden() }

    val items = vm.visible()
    val readOnly = vm.readOnly

    BackHandler {
        when {
            vm.selecting -> vm.selection.clear()
            vm.deepQuery != null -> vm.closeDeepSearch()
            vm.searchOpen -> { vm.searchOpen = false; vm.filter = "" }
            !vm.back(currentIndex()) -> (ctx as? Activity)?.finish()
        }
    }

    fun openEntry(e: FileEntry) {
        if (e.isDir) vm.navigate(e.location, currentIndex())
        else if (Mime.isArchive(e)) vm.openArchive(e, currentIndex())
        else if (Mime.opensInEditor(e) && e.size < MAX_EDIT_BYTES) nav.go(editorRoute(e))
        else openFile(ctx, e)
    }

    fun startPaste() {
        val clip = FileClipboard.items
        if (clip.isEmpty()) return
        val dest = vm.location
        val move = FileClipboard.cut
        scope.launch {
            val clashes = withContext(Dispatchers.IO) {
                try {
                    val dst = App.vfs.get(dest.vfsId)
                    clip.filter { it.parentLocation != dest && dst.exists(Paths.join(dest.path, it.name)) }.map { it.name }
                } catch (e: Exception) { emptyList() }
            }
            if (clashes.isEmpty()) {
                Ops.paste(clip, dest, move, Conflict.KeepBoth)
                FileClipboard.clear()
            } else dlg = Dlg.Conflicts(clashes, clip, dest, move)
        }
    }

    val pasting = FileClipboard.items.isNotEmpty()
    // Everything at the bottom floats above the tab island (and the paste card, when it's showing).
    val floatSpace = islandSpace + if (pasting) 76.dp else 0.dp

    androidx.compose.runtime.CompositionLocalProvider(LocalListPadding provides PaddingValues(bottom = floatSpace + 96.dp)) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (vm.selecting) {
                SelectionBar(vm, readOnly, onDialog = { dlg = it })
            } else {
                MainBar(vm, onDialog = { dlg = it })
            }
        },
        floatingActionButton = {
            if (!vm.selecting && !readOnly && vm.error == null && vm.deepQuery == null && (hasAllFiles || !vm.isLocal)) {
                Box(Modifier.padding(bottom = floatSpace)) {
                    SpeedDial(
                        pasteVisible = pasting,
                        onNewFolder = { dlg = Dlg.New(true) },
                        onNewFile = { dlg = Dlg.New(false) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Breadcrumbs(vm) { vm.navigate(it, currentIndex()) }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if ((vm.loading && !vm.refreshing) || vm.deepSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                vm.isLocal && !hasAllFiles -> PermissionGate()
                vm.deepQuery != null -> SearchResults(vm, onOpen = ::openEntry, onMenu = { e, a -> handleItemAction(ctx, vm, e, a, nav) { dlg = it } })
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refresh() },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        vm.error != null -> ErrorState(vm.error!!, vm, nav)
                        items.isEmpty() && !vm.loading && vm.loadCount > 0 -> EmptyState(vm.filter.isNotBlank())
                        s.gridView -> FileGrid(items, vm, gridState, ::openEntry)
                        else -> FileList(items, vm, listState, ::openEntry) { e, a -> handleItemAction(ctx, vm, e, a, nav) { dlg = it } }
                    }
                }
            }
        }
    }

        if (pasting) {
            PasteBar(
                readOnly,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = islandSpace + 8.dp),
                onPaste = ::startPaste,
            )
        }
    }
    }

    // ---------- Dialogs ----------
    vm.lockedArchive?.let { ex ->
        if (!vm.passwordPromptHidden) {
            PasswordDialog(
                title = stringResource(R.string.password_for, vm.title),
                wrong = ex.wrong,
                onDismiss = { vm.passwordPromptHidden = true },
            ) { pw -> vm.unlockArchive(pw) }
        }
    }
    when (val d = dlg) {
        null -> {}
        is Dlg.New -> NameDialog(
            title = if (d.folder) stringResource(R.string.new_folder) else stringResource(R.string.new_file),
            initial = "",
            confirm = stringResource(R.string.create),
            onDismiss = { dlg = null },
        ) { name ->
            dlg = null
            val loc = vm.location
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    try {
                        val v = App.vfs.get(loc.vfsId)
                        val p = Paths.join(loc.path, name)
                        if (d.folder) v.mkdir(p) else v.createFile(p)
                        null
                    } catch (e: Exception) { e.message ?: App.str(R.string.failed_5) }
                }
                if (err != null) dlg = Dlg.Error(App.str(R.string.couldn_t_create, name), err) else vm.load(quiet = true)
            }
        }
        is Dlg.Rename -> NameDialog(stringResource(R.string.rename), d.e.name, stringResource(R.string.rename), onDismiss = { dlg = null }) { name ->
            dlg = null
            if (name == d.e.name) return@NameDialog
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    try {
                        App.vfs.get(d.e.vfsId).rename(d.e.path, Paths.join(Paths.parent(d.e.path), name)); null
                    } catch (e: Exception) { e.message ?: App.str(R.string.failed_5) }
                }
                if (err != null) dlg = Dlg.Error(App.str(R.string.couldn_t_rename_2), err) else vm.load(quiet = true)
            }
        }
        is Dlg.Delete -> DeleteDialog(d.items, remote = d.items.firstOrNull()?.vfsId?.startsWith(RemoteVfs.PREFIX) == true, onDismiss = { dlg = null }) {
            dlg = null
            vm.selection.clear()
            Ops.delete(d.items)
        }
        is Dlg.Props -> PropertiesDialog(d.e, onDismiss = { dlg = null }, onChanged = { vm.load(quiet = true) })
        is Dlg.Compress -> CompressDialog(
            (if (d.items.size == 1) d.items[0].name.substringBeforeLast('.').ifEmpty { d.items[0].name } else vm.title),
            onDismiss = { dlg = null },
        ) { name, format, password ->
            dlg = null
            vm.selection.clear()
            Ops.compress(d.items, vm.location, name, format, password)
        }
        is Dlg.Extract -> ExtractFlow(d.archive, d.dest, onDone = { dlg = null }, onError = { t, m -> dlg = Dlg.Error(t, m) })
        is Dlg.Conflicts -> ConflictDialog(d.names, onDismiss = { dlg = null }) { choice ->
            dlg = null
            Ops.paste(d.items, d.dest, d.move, choice)
            FileClipboard.clear()
        }
        is Dlg.Error -> InfoDialog(d.title, d.message) { dlg = null }
    }
    vm.untrusted?.let { u ->
        val host = App.servers.get(vm.location.vfsId.removePrefix(RemoteVfs.PREFIX))?.host ?: ""
        TrustDialog(u, host, onDismiss = { vm.untrusted = null }, onTrust = { vm.trustAndRetry() })
    }
}

// ---------- Top bars ----------

@Composable
private fun MainBar(vm: BrowserViewModel, onDialog: (Dlg) -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val s = App.settings
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    TopAppBar(
        navigationIcon = {
            if (vm.searchOpen) {
                IconButton(onClick = { vm.searchOpen = false; vm.filter = ""; vm.closeDeepSearch() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.close_search))
                }
            }
        },
        title = {
            if (vm.searchOpen) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = vm.filter,
                    onValueChange = { vm.filter = it },
                    placeholder = { Text(stringResource(R.string.filter_or_search_subfolders)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.deepSearch(vm.filter) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            } else {
                Column {
                    Text(vm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (vm.readOnly && !vm.isArchive) stringResource(R.string.subtitle_read_only, subtitle(vm)) else subtitle(vm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            if (vm.searchOpen) {
                if (vm.filter.isNotBlank()) {
                    IconButton(onClick = { vm.deepSearch(vm.filter) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search_subfolders)) }
                }
                return@TopAppBar
            }
            IconButton(onClick = { vm.searchOpen = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortBy.entries.forEach { by ->
                        DropdownMenuItem(
                            text = { Text(by.name) },
                            leadingIcon = { if (s.sortBy == by) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) },
                            onClick = { s.sortBy = by; sortMenu = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.descending)) },
                        leadingIcon = { if (s.sortDescending) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { s.sortDescending = !s.sortDescending; sortMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folders_first)) },
                        leadingIcon = { if (s.foldersFirst) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { s.foldersFirst = !s.foldersFirst; sortMenu = false },
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (s.gridView) stringResource(R.string.list_view) else stringResource(R.string.grid_view)) },
                        leadingIcon = { Icon(if (s.gridView) Icons.Outlined.ViewList else Icons.Outlined.GridView, null) },
                        onClick = { s.gridView = !s.gridView; menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (s.showHidden) stringResource(R.string.hide_hidden_files) else stringResource(R.string.show_hidden_files)) },
                        leadingIcon = { Icon(if (s.showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) },
                        onClick = { s.showHidden = !s.showHidden; menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_all)) },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                        onClick = { vm.selectAll(); menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.refresh)) },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        onClick = { vm.load(); menu = false },
                    )
                    val marked = App.bookmarks.contains(vm.location)
                    DropdownMenuItem(
                        text = { Text(if (marked) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark_this_folder)) },
                        leadingIcon = { Icon(if (marked) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd, null) },
                        onClick = {
                            if (marked) App.bookmarks.remove(vm.location)
                            else App.bookmarks.add(Bookmark(vm.title, vm.location))
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_path)) },
                        leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        onClick = {
                            val loc = vm.location
                            copyText(ctx, App.str(R.string.path), displayPath(FileEntry(loc.vfsId, loc.path, Paths.name(loc.path), true, 0, 0)))
                            Toast.makeText(ctx, App.str(R.string.path_copied), Toast.LENGTH_SHORT).show()
                            menu = false
                        },
                    )
                    vm.archiveFile?.let { arc ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.extract_all)) },
                            leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                            onClick = {
                                menu = false
                                onDialog(Dlg.Extract(FileEntry(arc.vfsId, arc.path, Paths.name(arc.path), false, 0, 0), Location(arc.vfsId, Paths.parent(arc.path))))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_properties)) },
                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = {
                            menu = false
                            val loc = vm.location
                            onDialog(Dlg.Props(FileEntry(loc.vfsId, loc.path, vm.title, true, 0, 0)))
                        },
                    )
                }
            }
            ProfileButton()
        },
    )
}

/** Under the folder name: this phone, or a share's storage use (just its address if the server won't say). */
@Composable
private fun subtitle(vm: BrowserViewModel): String {
    val ctx = LocalContext.current
    if (vm.isLocal) return vm.vfsTitle
    if (vm.isArchive) {
        val label = (App.vfs.find(vm.location.vfsId) as? ArchiveVfs)?.loadedFormatLabel
        return App.str(R.string.subtitle_read_only, label?.let { App.str(R.string.archive_2, it) } ?: App.str(R.string.archive))
    }
    val sp = vm.space
    if (sp != null && sp.total > 0) return App.str(R.string.of_used, Fmt.size(ctx, sp.total - sp.free), Fmt.size(ctx, sp.total))
    return App.servers.get(vm.location.vfsId.removePrefix(RemoteVfs.PREFIX))?.host ?: vm.vfsTitle
}

@Composable
private fun SelectionBar(vm: BrowserViewModel, readOnly: Boolean, onDialog: (Dlg) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val sel = vm.selected()
    val single = sel.singleOrNull()
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = { IconButton(onClick = { vm.selection.clear() }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_selection)) } },
        title = { Text(stringResource(R.string.selected, vm.selection.size)) },
        actions = {
            IconButton(onClick = { vm.copySelection(cut = false) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
            if (!readOnly) IconButton(onClick = { vm.copySelection(cut = true) }) { Icon(Icons.Outlined.ContentCut, stringResource(R.string.cut)) }
            if (!readOnly) IconButton(onClick = {
                if (App.settings.confirmDelete) onDialog(Dlg.Delete(sel)) else { vm.selection.clear(); Ops.delete(sel) }
            }) { Icon(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                        onClick = { vm.selectAll(); menu = false })
                    if (sel.any { !it.isDir }) DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; shareFiles(ctx, sel) })
                    if (single != null && !readOnly) DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menu = false; vm.selection.clear(); onDialog(Dlg.Rename(single)) })
                    if (!readOnly) DropdownMenuItem(text = { Text(stringResource(R.string.compress)) }, leadingIcon = { Icon(Icons.Outlined.FolderZip, null) },
                        onClick = { menu = false; onDialog(Dlg.Compress(sel)) })
                    if (single != null && Mime.canBrowseAsArchive(single) && !readOnly) DropdownMenuItem(text = { Text(stringResource(R.string.extract_here)) }, leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                        onClick = { menu = false; vm.selection.clear(); onDialog(Dlg.Extract(single, vm.location)) })
                    if (single != null && !single.isDir) DropdownMenuItem(text = { Text(stringResource(R.string.open_with)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        onClick = { menu = false; openFile(ctx, single, chooser = true) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_path)) }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        onClick = {
                            menu = false
                            copyText(ctx, App.str(R.string.paths), sel.joinToString("\n") { displayPath(it) })
                            Toast.makeText(ctx, if (sel.size == 1) App.str(R.string.path_copied) else App.str(R.string.paths_copied, sel.size), Toast.LENGTH_SHORT).show()
                        })
                    if (single != null) DropdownMenuItem(text = { Text(stringResource(R.string.properties)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = { menu = false; onDialog(Dlg.Props(single)) })
                }
            }
        },
    )
}

// ---------- Item actions ----------

enum class ItemAction { Open, OpenWith, EditWith, EditText, Share, Copy, Cut, Rename, Delete, Compress, Extract, Browse, Bookmark, CopyPath, Properties, OpenLocation }

private fun handleItemAction(
    ctx: android.content.Context,
    vm: BrowserViewModel,
    e: FileEntry,
    a: ItemAction,
    nav: androidx.navigation.NavHostController,
    onDialog: (Dlg) -> Unit,
) {
    when (a) {
        ItemAction.Open -> if (e.isDir) vm.navigate(e.location) else openFile(ctx, e)
        ItemAction.OpenWith -> openFile(ctx, e, chooser = true)
        ItemAction.EditWith -> openFile(ctx, e, chooser = true, edit = true)
        ItemAction.EditText -> nav.go(editorRoute(e))
        ItemAction.Share -> shareFiles(ctx, listOf(e))
        ItemAction.Copy -> { FileClipboard.items = listOf(e); FileClipboard.cut = false }
        ItemAction.Cut -> { FileClipboard.items = listOf(e); FileClipboard.cut = true }
        ItemAction.Rename -> onDialog(Dlg.Rename(e))
        ItemAction.Delete -> if (App.settings.confirmDelete) onDialog(Dlg.Delete(listOf(e))) else Ops.delete(listOf(e))
        ItemAction.Compress -> onDialog(Dlg.Compress(listOf(e)))
        ItemAction.Extract -> onDialog(Dlg.Extract(e, vm.location))
        ItemAction.Browse -> vm.openArchive(e, 0)
        ItemAction.Bookmark -> {
            if (App.bookmarks.contains(e.location)) App.bookmarks.remove(e.location)
            else App.bookmarks.add(Bookmark(e.name, e.location))
        }
        ItemAction.CopyPath -> {
            copyText(ctx, App.str(R.string.path), displayPath(e))
            Toast.makeText(ctx, App.str(R.string.path_copied), Toast.LENGTH_SHORT).show()
        }
        ItemAction.Properties -> onDialog(Dlg.Props(e))
        ItemAction.OpenLocation -> vm.navigate(e.parentLocation)
    }
}

@Composable
private fun ItemMenu(e: FileEntry, readOnly: Boolean, inSearch: Boolean, expanded: Boolean, onDismiss: () -> Unit, onAction: (ItemAction) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun entry(label: String, icon: ImageVector, a: ItemAction) {
            DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { onDismiss(); onAction(a) })
        }
        if (inSearch) entry(stringResource(R.string.show_in_folder), Icons.Outlined.FolderOpen, ItemAction.OpenLocation)
        if (!e.isDir) {
            entry(stringResource(R.string.open_with), Icons.AutoMirrored.Outlined.OpenInNew, ItemAction.OpenWith)
            if (!readOnly) entry(stringResource(R.string.edit_with), Icons.Outlined.Edit, ItemAction.EditWith)
            if (e.size < MAX_EDIT_BYTES) entry(stringResource(R.string.edit_as_text), Icons.Outlined.Edit, ItemAction.EditText)
            entry(stringResource(R.string.share), Icons.Outlined.Share, ItemAction.Share)
        }
        entry(stringResource(R.string.copy), Icons.Outlined.ContentCopy, ItemAction.Copy)
        if (!readOnly) {
            entry(stringResource(R.string.cut), Icons.Outlined.ContentCut, ItemAction.Cut)
            entry(stringResource(R.string.rename), Icons.Outlined.DriveFileRenameOutline, ItemAction.Rename)
            entry(stringResource(R.string.lock_delete), Icons.Outlined.Delete, ItemAction.Delete)
            entry(stringResource(R.string.compress), Icons.Outlined.FolderZip, ItemAction.Compress)
            if (Mime.canBrowseAsArchive(e)) entry(stringResource(R.string.extract_here), Icons.Outlined.Unarchive, ItemAction.Extract)
        }
        // Zip-based files with their own app (APKs, e-books, office files) open normally; look inside from here.
        if (Mime.canBrowseAsArchive(e) && !Mime.isArchive(e)) entry(stringResource(R.string.browse_contents), Icons.Outlined.FolderZip, ItemAction.Browse)
        if (e.isDir) entry(
            if (App.bookmarks.contains(e.location)) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark),
            if (App.bookmarks.contains(e.location)) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
            ItemAction.Bookmark,
        )
        entry(stringResource(R.string.copy_path), Icons.Outlined.Link, ItemAction.CopyPath)
        entry(stringResource(R.string.properties), Icons.Outlined.Info, ItemAction.Properties)
    }
}

// ---------- Content ----------

@Composable
private fun Breadcrumbs(vm: BrowserViewModel, onClick: (Location) -> Unit) {
    val crumbs = vm.crumbs()
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size, vm.location) { if (crumbs.isNotEmpty()) state.animateScrollToItem(crumbs.size - 1) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        itemsIndexed(crumbs) { i, (label, loc) ->
            val last = i == crumbs.size - 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Icon(
                    Icons.Outlined.ChevronRight, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { if (!last) onClick(loc) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun subtitleFor(e: FileEntry): String {
    val ctx = LocalContext.current
    val date = Fmt.date(ctx, e.modified)
    return if (e.isDir) date else listOf(date, Fmt.size(ctx, e.size)).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun FileRow(
    e: FileEntry,
    selected: Boolean,
    subtitle: String,
    readOnly: Boolean,
    inSearch: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (ItemAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(e, 44.dp, Modifier.combinedClickable(onClick = onLongClick), selected = selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (e.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ItemMenu(e, readOnly, inSearch, menu, onDismiss = { menu = false }, onAction = onAction)
        }
    }
}

@Composable
private fun FileList(
    items: List<FileEntry>,
    vm: BrowserViewModel,
    state: LazyListState,
    onOpen: (FileEntry) -> Unit,
    onAction: (FileEntry, ItemAction) -> Unit,
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = LocalListPadding.current) {
        items(items, key = { it.path }) { e ->
            val selected = e.path in vm.selection
            FileRow(
                e, selected, subtitleFor(e), vm.readOnly, inSearch = false,
                onClick = { if (vm.selecting) vm.toggle(e) else onOpen(e) },
                onLongClick = { vm.toggle(e) },
                onAction = { onAction(e, it) },
            )
        }
    }
}

@Composable
private fun FileGrid(items: List<FileEntry>, vm: BrowserViewModel, state: LazyGridState, onOpen: (FileEntry) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = LocalListPadding.current.calculateBottomPadding()),
    ) {
        items(items, key = { it.path }) { e ->
            val selected = e.path in vm.selection
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.padding(4.dp).fillMaxWidth().clip(CardDefaults.shape)
                    .combinedClickable(
                        onClick = { if (vm.selecting) vm.toggle(e) else onOpen(e) },
                        onLongClick = { vm.toggle(e) },
                    ),
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FileIcon(e, 84.dp, selected = selected)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(vm: BrowserViewModel, onOpen: (FileEntry) -> Unit, onMenu: (FileEntry, ItemAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            buildString {
                append(
                    if (vm.deepSearching) stringResource(R.string.searching_for, vm.deepQuery, vm.title)
                    else pluralStringResource(R.plurals.results_for, vm.deepResults.size, vm.deepResults.size, vm.deepQuery, vm.title),
                )
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalListPadding.current) {
            items(vm.deepResults, key = { it.path }) { e ->
                val rel = Paths.parent(e.path).removePrefix(vm.location.path).trimStart('/')
                FileRow(
                    e, e.path in vm.selection, if (rel.isEmpty()) stringResource(R.string.in_this_folder) else stringResource(R.string.in_text, rel),
                    vm.readOnly, inSearch = true,
                    onClick = { if (vm.selecting) vm.toggle(e) else onOpen(e) },
                    onLongClick = { vm.toggle(e) },
                    onAction = { onMenu(e, it) },
                )
            }
        }
    }
}

@Composable
private fun SpeedDial(pasteVisible: Boolean, onNewFolder: () -> Unit, onNewFile: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialItem(stringResource(R.string.new_file), Icons.Outlined.NoteAdd) { open = false; onNewFile() }
                DialItem(stringResource(R.string.new_folder), Icons.Outlined.CreateNewFolder) { open = false; onNewFolder() }
            }
        }
        FloatingActionButton(onClick = { open = !open }) {
            Icon(if (open) Icons.Outlined.Close else Icons.Outlined.Add, if (open) stringResource(R.string.close) else stringResource(R.string.create))
        }
    }
}

@Composable
private fun DialItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(onClick = onClick, modifier = Modifier.padding(end = 4.dp)) { Icon(icon, label) }
    }
}

@Composable
private fun PasteBar(readOnly: Boolean, modifier: Modifier = Modifier, onPaste: () -> Unit) {
    val n = FileClipboard.items.size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (FileClipboard.cut) Icons.AutoMirrored.Outlined.DriveFileMove else Icons.Outlined.ContentCopy, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(if (FileClipboard.cut) R.plurals.items_to_move else R.plurals.items_to_copy, n, n),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    FileClipboard.items.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { FileClipboard.clear() }) { Text(stringResource(R.string.lock_cancel)) }
            Button(onClick = onPaste, enabled = !readOnly) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.paste))
            }
        }
    }
}

@Composable
private fun CenteredMessage(icon: ImageVector, title: String, body: String?, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    CenteredMessage(Icons.Outlined.FolderOff, if (filtered) stringResource(R.string.no_matches) else stringResource(R.string.this_folder_is_empty),
        if (filtered) stringResource(R.string.press_search_on_the_keyboard_to_look) else null)
}

@Composable
private fun ErrorState(message: String, vm: BrowserViewModel, nav: androidx.navigation.NavHostController) {
    CenteredMessage(Icons.Outlined.CloudOff, stringResource(R.string.couldn_t_open_this_folder), message) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.lockedArchive != null) FilledTonalButton(onClick = { vm.passwordPromptHidden = false }) { Text(stringResource(R.string.enter_password)) }
            else FilledTonalButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
            if (vm.location.vfsId.startsWith(RemoteVfs.PREFIX)) {
                OutlinedButton(onClick = { nav.go("server/" + vm.location.vfsId.removePrefix(RemoteVfs.PREFIX)) }) { Text(stringResource(R.string.edit_server)) }
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    val ctx = LocalContext.current
    CenteredMessage(
        Icons.Outlined.Storage,
        stringResource(R.string.allow_access_to_your_files),
        stringResource(R.string.filesctrl_needs_all_files_access_to),
    ) {
        Button(onClick = {
            val i = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); try { ctx.startActivity(i) } catch (_: Exception) {
                ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }) { Text(stringResource(R.string.grant_access)) }
    }
}
