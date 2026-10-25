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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.VerticalSplit
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlIconButton
import com.vknxworks.filesctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.filesctrl.ui.components.CtrlSquareButton
import com.vknxworks.filesctrl.ui.components.CtrlTextField
import com.vknxworks.filesctrl.ui.components.CtrlTopBar
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlShapes
import com.vknxworks.filesctrl.ui.theme.CtrlType

/** Bottom padding for this screen's lists, so they scroll clear of the island and paste card. */
private val LocalListPadding = androidx.compose.runtime.compositionLocalOf { PaddingValues(bottom = 96.dp) }

private sealed interface Dlg {
    data class New(val folder: Boolean) : Dlg
    data class Rename(val e: FileEntry) : Dlg
    data class Delete(val items: List<FileEntry>) : Dlg
    data class Props(val e: FileEntry) : Dlg
    data class Checksum(val e: FileEntry) : Dlg
    data class Compress(val items: List<FileEntry>) : Dlg
    data class Extract(val archive: FileEntry, val dest: Location) : Dlg
    data class Conflicts(val names: List<String>, val items: List<FileEntry>, val dest: Location, val move: Boolean, val fromClipboard: Boolean = true) : Dlg
    data class Error(val title: String, val message: String) : Dlg
}

// Navigation decodes the argument itself, so encode with Uri.encode (not URLEncoder, whose "+" for space it would keep).
fun editorRoute(e: FileEntry) = "edit?loc=" + Uri.encode(e.location.key)

@Composable
/** [islandSpace]: room the floating tab island takes at the bottom (0 when tabs are in a side rail). */
fun BrowserScreen(vm: BrowserViewModel, islandSpace: Dp, pane: Pane? = null, dual: Boolean = false, focused: Boolean = true) {
    val ctx = LocalContext.current
    LaunchedEffect(vm) { vm.ensureLoaded() }
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
    // Switching tabs and coming back keeps the scroll position.
    val latestIndex = androidx.compose.runtime.rememberUpdatedState(currentIndex)
    DisposableEffect(vm) { onDispose { vm.keepScroll(latestIndex.value()) } }

    // Hide the pull indicator explicitly when a refresh ends, so it can't get stuck half-way.
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(vm.refreshing) { if (!vm.refreshing) pullState.animateToHidden() }

    val items = vm.visible()
    val readOnly = vm.readOnly

    // In dual pane, back acts on the pane last touched.
    BackHandler(enabled = focused) {
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
        else if (s.builtInViewer && com.vknxworks.filesctrl.viewer.Viewable.isImage(e)) {
            com.vknxworks.filesctrl.viewer.ViewerSession.set(items)
            nav.go(com.vknxworks.filesctrl.viewer.viewerRoute(e))
        }
        else if (Mime.opensInEditor(e) && e.size < MAX_EDIT_BYTES) nav.go(editorRoute(e))
        else openFile(ctx, e)
    }

    fun pasteInto(clip: List<FileEntry>, dest: Location, move: Boolean, fromClipboard: Boolean) {
        if (clip.isEmpty()) return
        scope.launch {
            val clashes = withContext(Dispatchers.IO) {
                try {
                    val dst = App.vfs.get(dest.vfsId)
                    clip.filter { it.parentLocation != dest && dst.exists(Paths.join(dest.path, it.name)) }.map { it.name }
                } catch (e: Exception) { emptyList() }
            }
            if (clashes.isEmpty()) {
                Ops.paste(clip, dest, move, Conflict.KeepBoth)
                if (fromClipboard) FileClipboard.clear()
            } else dlg = Dlg.Conflicts(clashes, clip, dest, move, fromClipboard)
        }
    }

    fun startPaste() = pasteInto(FileClipboard.items, vm.location, FileClipboard.cut, fromClipboard = true)

    /** Dual pane: copy or move the selection straight into the folder open in the other pane. */
    fun toOtherPane(move: Boolean) {
        val dest = BrowserTabs.otherLocation(vm) ?: return
        val sel = vm.selected()
        vm.selection.clear()
        pasteInto(sel, dest, move, fromClipboard = false)
    }

    val pasting = FileClipboard.items.isNotEmpty()
    // Everything at the bottom floats above the tab island (and the paste card, when it's showing).
    val floatSpace = islandSpace + if (pasting) 76.dp else 0.dp

    androidx.compose.runtime.CompositionLocalProvider(LocalListPadding provides PaddingValues(bottom = floatSpace + 96.dp)) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            Column {
                if (vm.selecting) {
                    SelectionBar(vm, readOnly, onDialog = { dlg = it }, onOtherPane = if (dual) { move: Boolean -> toOtherPane(move) } else null)
                } else {
                    MainBar(vm, pane = pane, compact = dual && pane?.right == true, onDialog = { dlg = it })
                }
                if (pane != null && !vm.searchOpen && (dual || pane.tabs.size > 1)) TabStrip(pane, focused, dual)
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
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 20.dp)) {
                if ((vm.loading && !vm.refreshing) || vm.deepSearching) LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                    color = CtrlColors.Accent, trackColor = CtrlColors.ProgressTrack,
                )
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
        is Dlg.Checksum -> ChecksumDialog(d.e) { dlg = null }
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
            if (d.fromClipboard) FileClipboard.clear()
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
private fun MainBar(vm: BrowserViewModel, pane: Pane?, compact: Boolean, onDialog: (Dlg) -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val s = App.settings
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (vm.searchOpen) {
        Row(
            Modifier.fillMaxWidth().background(CtrlColors.Background).statusBarsPadding()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.close_search), onClick = { vm.searchOpen = false; vm.filter = ""; vm.closeDeepSearch() })
            Spacer(Modifier.width(10.dp))
            LaunchedEffect(Unit) { focus.requestFocus() }
            CtrlTextField(
                value = vm.filter,
                onValueChange = { vm.filter = it },
                placeholder = { Text(stringResource(R.string.filter_or_search_subfolders), maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.deepSearch(vm.filter) }),
                trailingIcon = if (vm.filter.isNotBlank()) { {
                    IconButton(onClick = { vm.deepSearch(vm.filter) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search_subfolders)) }
                } } else null,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth().background(CtrlColors.Background)) {
    CtrlTopBar(
        stringResource(R.string.wordmark_name),
        // In dual pane the right pane leaves the wordmark and profile button to the left one, so both bars fit.
        profile = if (compact) null else { { ProfileButton(connected = vm.location.vfsId.startsWith(RemoteVfs.PREFIX) && vm.error == null) } },
        showWordmark = !compact,
        actions = {
            CtrlIconButton(Icons.Outlined.Search, stringResource(R.string.search), onClick = { vm.searchOpen = true })
            Box {
                CtrlIconButton(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort), onClick = { sortMenu = true })
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
                CtrlIconButton(Icons.Outlined.MoreVert, stringResource(R.string.more), onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (pane != null && pane.tabs.size < BrowserTabs.MAX_TABS) DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_tab)) },
                        leadingIcon = { Icon(Icons.Outlined.Tab, null) },
                        onClick = { menu = false; BrowserTabs.openTab(vm, vm.location) },
                    )
                    if (pane != null && pane.tabs.size > 1) DropdownMenuItem(
                        text = { Text(stringResource(R.string.close_this_tab)) },
                        leadingIcon = { Icon(Icons.Outlined.Close, null) },
                        onClick = { menu = false; pane.close(pane.active) },
                    )
                    if (BrowserTabs.wideWindow) DropdownMenuItem(
                        text = { Text(if (s.dualPane) stringResource(R.string.single_pane) else stringResource(R.string.dual_pane)) },
                        leadingIcon = { Icon(Icons.Outlined.VerticalSplit, null) },
                        onClick = { menu = false; s.dualPane = !s.dualPane },
                    )
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
                    if (com.vknxworks.filesctrl.shortcuts.Shortcuts.canPin(vm.location)) DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_home_screen)) },
                        leadingIcon = { Icon(Icons.Outlined.Home, null) },
                        onClick = {
                            menu = false
                            pinShortcut(ctx, App.bookmarks.all.firstOrNull { it.location == vm.location }?.name ?: vm.title, vm.location)
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
        },
    )
    // The folder's name, big, with where it is (this phone, a share's storage use or the address) in mono above.
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
        Text(
            (if (vm.readOnly && !vm.isArchive) stringResource(R.string.subtitle_read_only, subtitle(vm)) else subtitle(vm)).uppercase(),
            style = CtrlType.Label, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(vm.title, style = CtrlType.PageTitle, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
}

/** Under the folder name: this phone, or a share's storage use (just its address if the server won't say). */
@Composable
private fun subtitle(vm: BrowserViewModel): String {
    val ctx = LocalContext.current
    if (vm.isLocal) return vm.vfsTitle
    if (vm.isArchive) {
        val label = (runCatching { App.vfs.find(vm.location.vfsId) }.getOrNull() as? ArchiveVfs)?.loadedFormatLabel
        return App.str(R.string.subtitle_read_only, label?.let { App.str(R.string.archive_2, it) } ?: App.str(R.string.archive))
    }
    val sp = vm.space
    if (sp != null && sp.total > 0) return App.str(R.string.of_used, Fmt.size(ctx, sp.total - sp.free), Fmt.size(ctx, sp.total))
    return App.servers.get(vm.location.vfsId.removePrefix(RemoteVfs.PREFIX))?.host ?: vm.vfsTitle
}

@Composable
private fun SelectionBar(vm: BrowserViewModel, readOnly: Boolean, onDialog: (Dlg) -> Unit, onOtherPane: ((Boolean) -> Unit)? = null) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val sel = vm.selected()
    val single = sel.singleOrNull()
    // Selection mode: an accent bar in place of the top bar, with the count in mono.
    Box(Modifier.fillMaxWidth().background(CtrlColors.Background).statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(CtrlShapes.Card).background(CtrlColors.Accent).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            CtrlIconButton(Icons.Outlined.Close, stringResource(R.string.clear_selection), onClick = { vm.selection.clear() }, tint = CtrlColors.Ink)
            Text(
                stringResource(R.string.selected, vm.selection.size).uppercase(),
                style = CtrlType.LabelStrong.copy(fontSize = 13.sp), color = CtrlColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            CtrlIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.copy), onClick = { vm.copySelection(cut = false) }, tint = CtrlColors.Ink)
            if (!readOnly) CtrlIconButton(Icons.Outlined.ContentCut, stringResource(R.string.cut), onClick = { vm.copySelection(cut = true) }, tint = CtrlColors.Ink)
            if (!readOnly) CtrlIconButton(Icons.Outlined.Delete, stringResource(R.string.lock_delete), tint = CtrlColors.Ink, onClick = {
                if (App.settings.confirmDelete) onDialog(Dlg.Delete(sel)) else { vm.selection.clear(); Ops.delete(sel) }
            })
            Box {
                CtrlIconButton(Icons.Outlined.MoreVert, stringResource(R.string.more), onClick = { menu = true }, tint = CtrlColors.Ink)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                        onClick = { vm.selectAll(); menu = false })
                    if (onOtherPane != null) {
                        val other = BrowserTabs.otherLocation(vm)
                        val otherWritable = other != null && canWriteTo(other)
                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_to_other_pane)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            enabled = otherWritable, onClick = { menu = false; onOtherPane(false) })
                        if (!readOnly) DropdownMenuItem(text = { Text(stringResource(R.string.move_to_other_pane)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                            enabled = otherWritable, onClick = { menu = false; onOtherPane(true) })
                    }
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
                    if (single != null && !single.isDir) DropdownMenuItem(text = { Text(stringResource(R.string.checksums)) }, leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                        onClick = { menu = false; onDialog(Dlg.Checksum(single)) })
                    if (single != null) DropdownMenuItem(text = { Text(stringResource(R.string.properties)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = { menu = false; onDialog(Dlg.Props(single)) })
                }
            }
    }
    }
}

// ---------- Item actions ----------

enum class ItemAction { Open, OpenWith, EditWith, EditText, Share, Copy, Cut, Rename, Delete, Compress, Extract, Browse, Bookmark, CopyPath, Properties, OpenLocation, Checksum, HomeScreen, NewTab, OtherPane }

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
        ItemAction.Checksum -> onDialog(Dlg.Checksum(e))
        ItemAction.HomeScreen -> pinShortcut(ctx, e.name, e.location)
        ItemAction.NewTab -> BrowserTabs.openTab(vm, e.location)
        ItemAction.OtherPane -> BrowserTabs.openInOtherPane(vm, e.location)
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
        if (e.isDir && !e.vfsId.startsWith(ArchiveVfs.PREFIX)) {
            entry(stringResource(R.string.open_in_new_tab), Icons.Outlined.Tab, ItemAction.NewTab)
            if (BrowserTabs.dualPane) entry(stringResource(R.string.open_in_other_pane), Icons.Outlined.VerticalSplit, ItemAction.OtherPane)
        }
        if (e.isDir) entry(
            if (App.bookmarks.contains(e.location)) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark),
            if (App.bookmarks.contains(e.location)) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
            ItemAction.Bookmark,
        )
        if (e.isDir && com.vknxworks.filesctrl.shortcuts.Shortcuts.canPin(e.location)) entry(stringResource(R.string.add_to_home_screen), Icons.Outlined.Home, ItemAction.HomeScreen)
        if (!e.isDir) entry(stringResource(R.string.checksums), Icons.Outlined.Tag, ItemAction.Checksum)
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
    // The path in mono, "/" between the parts in the accent; the current folder is bright, the rest muted.
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) {
        itemsIndexed(crumbs) { i, (label, loc) ->
            val last = i == crumbs.size - 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Text("/", style = CtrlType.Meta.copy(fontWeight = FontWeight.SemiBold), color = CtrlColors.AccentText)
                Text(
                    label,
                    style = CtrlType.Meta.copy(fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (last) CtrlColors.Text else CtrlColors.Muted,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { if (!last) onClick(loc) })
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun subtitleFor(e: FileEntry): String {
    val ctx = LocalContext.current
    val date = Fmt.date(ctx, e.modified)
    return if (e.isDir) date else listOf(Fmt.size(ctx, e.size), date).filter { it.isNotEmpty() }.joinToString(App.str(R.string.list_separator))
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
    // Plain row with a 1dp top divider; size and date in mono. Selected rows get an accent edge and tint.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(if (selected) CtrlColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .topDivider()
            .drawBehind { if (selected) drawRect(CtrlColors.Accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(e, 40.dp, Modifier.combinedClickable(onClick = onLongClick), selected = selected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                e.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (e.hidden) CtrlColors.Muted else CtrlColors.Text,
            )
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = CtrlType.Meta.copy(fontSize = 11.sp),
                color = CtrlColors.Muted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.options), Modifier.size(20.dp), tint = CtrlColors.Muted)
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = LocalListPadding.current.calculateBottomPadding()),
    ) {
        items(items, key = { it.path }) { e ->
            val selected = e.path in vm.selection
            Column(
                Modifier.padding(5.dp).fillMaxWidth()
                    .clip(CtrlShapes.Card)
                    .background(if (selected) CtrlColors.Accent.copy(alpha = 0.14f) else CtrlColors.Surface)
                    .border(1.dp, if (selected) CtrlColors.AccentText else CtrlColors.CardBorder, CtrlShapes.Card)
                    .combinedClickable(
                        onClick = { if (vm.selecting) vm.toggle(e) else onOpen(e) },
                        onLongClick = { vm.toggle(e) },
                    )
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FileIcon(e, 80.dp, selected = selected)
                Spacer(Modifier.height(8.dp))
                Text(
                    e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = CtrlColors.Text,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!e.isDir) Text(
                    Fmt.size(LocalContext.current, e.size), maxLines = 1, style = CtrlType.Meta.copy(fontSize = 10.sp), color = CtrlColors.Muted,
                )
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
                    if (vm.deepSearching) stringResource(R.string.searching_for, vm.deepQuery.orEmpty(), vm.title)
                    else pluralStringResource(R.plurals.results_for, vm.deepResults.size, vm.deepResults.size, vm.deepQuery.orEmpty(), vm.title),
                )
            },
            style = CtrlType.Meta,
            color = CtrlColors.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
        FloatingActionButton(
            onClick = { open = !open },
            shape = RoundedCornerShape(18.dp),
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.Ink,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Icon(if (open) Icons.Outlined.Close else Icons.Outlined.Add, if (open) stringResource(R.string.close) else stringResource(R.string.create))
        }
    }
}

@Composable
private fun DialItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CtrlShapes.Badge,
            color = CtrlColors.Surface,
            border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        ) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = CtrlColors.Text)
        }
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.padding(end = 4.dp).border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control),
            shape = CtrlShapes.Control,
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) { Icon(icon, label) }
    }
}

@Composable
private fun PasteBar(readOnly: Boolean, modifier: Modifier = Modifier, onPaste: () -> Unit) {
    val n = FileClipboard.items.size
    Surface(
        color = CtrlColors.Surface,
        shape = CtrlShapes.Card,
        border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        modifier = modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (FileClipboard.cut) Icons.AutoMirrored.Outlined.DriveFileMove else Icons.Outlined.ContentCopy, null, tint = CtrlColors.AccentText)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(if (FileClipboard.cut) R.plurals.items_to_move else R.plurals.items_to_copy, n, n),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = CtrlColors.Text,
                )
                Text(
                    FileClipboard.items.joinToString(", ") { it.name },
                    style = CtrlType.Meta.copy(fontSize = 11.sp),
                    color = CtrlColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { FileClipboard.clear() }, colors = ButtonDefaults.textButtonColors(contentColor = CtrlColors.Muted)) { Text(stringResource(R.string.lock_cancel)) }
            CtrlButton(onClick = onPaste, enabled = !readOnly, height = 44.dp) {
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
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(CtrlColors.Surface).border(1.dp, CtrlColors.CardBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(28.dp), tint = CtrlColors.Inactive) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = CtrlType.Title, color = CtrlColors.Text, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = CtrlColors.Muted, modifier = Modifier.widthIn(max = 420.dp),
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
            if (vm.lockedArchive != null) CtrlButton(onClick = { vm.passwordPromptHidden = false }) { Text(stringResource(R.string.enter_password)) }
            else CtrlButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
            if (vm.location.vfsId.startsWith(RemoteVfs.PREFIX)) {
                CtrlSecondaryButton(onClick = { nav.go("server/" + vm.location.vfsId.removePrefix(RemoteVfs.PREFIX)) }) { Text(stringResource(R.string.edit_server)) }
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
        CtrlButton(height = 56.dp, onClick = {
            val i = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); try { ctx.startActivity(i) } catch (_: Exception) {
                ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }) { Text(stringResource(R.string.grant_access)) }
    }
}

/** Asks the launcher for a home screen shortcut to [loc], and says what happened. */
fun pinShortcut(ctx: android.content.Context, name: String, loc: Location) {
    val ok = com.vknxworks.filesctrl.shortcuts.Shortcuts.pin(ctx, name, loc)
    Toast.makeText(ctx, if (ok) App.str(R.string.shortcut_requested, name) else App.str(R.string.shortcut_not_supported), Toast.LENGTH_SHORT).show()
}
