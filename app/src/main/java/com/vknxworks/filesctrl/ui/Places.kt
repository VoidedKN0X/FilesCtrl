package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.Proto
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.R

/** Icon for a kind of network location. */
fun protoIcon(p: Proto): ImageVector = when (p) {
    Proto.NFS -> Icons.Outlined.Dns
    Proto.SMB -> Icons.Outlined.Lan
    Proto.SFTP -> Icons.Outlined.Terminal
    Proto.FTP -> Icons.Outlined.SwapVert
    Proto.WEBDAV -> Icons.Outlined.Cloud
}

@Composable
private fun UsageBar(sp: Space) {
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { (1f - sp.free.toFloat() / sp.total).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Where files live: this phone's storage, saved network locations and bookmarks. */
@Composable
fun PlacesTab(current: Location, onOpen: (Location) -> Unit, onAddServer: () -> Unit, onEditServer: (String) -> Unit) {
    val ctx = LocalContext.current
    val volumes = remember { App.vfs.local.volumes() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottom) {
        item { TabTitle(stringResource(R.string.places)) }

        item {
            SettingsSection(stringResource(R.string.you_this_phone)) {
                volumes.forEachIndexed { i, v ->
                    if (i > 0) ItemDivider()
                    var space by remember(v.path) { mutableStateOf<Space?>(null) }
                    LaunchedEffect(v.path) { space = withContext(Dispatchers.IO) { App.vfs.local.space(v.path) } }
                    val sp = space?.takeIf { it.total > 0 }
                    val isOpen = current.vfsId == LocalVfs.ID && Paths.isWithin(current.path, v.path)
                    val mark: (@Composable () -> Unit)? = if (isOpen) { { OpenDot() } } else null
                    NavItem(
                        icon = when { v.primary -> Icons.Outlined.PhoneAndroid; v.label.contains("USB", true) -> Icons.Outlined.Usb; else -> Icons.Outlined.SdCard },
                        title = v.label,
                        subtitle = sp?.let { App.str(R.string.free_of, Fmt.size(ctx, it.free), Fmt.size(ctx, it.total)) },
                        below = sp?.let { { UsageBar(it) } },
                        trailing = mark,
                    ) { onOpen(Location(LocalVfs.ID, v.path)) }
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.network)) {
                App.servers.all.forEach { s ->
                    val id = RemoteVfs.idFor(s.id)
                    // Storage use for servers that are connected; just the address otherwise (the list never connects).
                    var space by remember(s) { mutableStateOf<Space?>(null) }
                    LaunchedEffect(s, current) {
                        val v = App.vfs.connected(s.id)
                        if (v != null) space = withContext(Dispatchers.IO) { v.space("/") }
                    }
                    val sp = space?.takeIf { it.total > 0 }
                    NavItem(
                        icon = protoIcon(s.type),
                        title = s.title,
                        subtitle = sp?.let { App.str(R.string.of_used, Fmt.size(ctx, it.total - it.free), Fmt.size(ctx, it.total)) }
                            ?: "${s.type.label} · ${s.host}",
                        below = sp?.let { { UsageBar(it) } },
                        trailing = {
                            IconButton(onClick = { onEditServer(s.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit, s.title), Modifier.size(20.dp)) }
                        },
                    ) { onOpen(Location(id, "/")) }
                    ItemDivider()
                }
                NavItem(Icons.Outlined.Add, stringResource(R.string.add_network), stringResource(R.string.nfs_smb_sftp_ftp_or_webdav)) { onAddServer() }
            }
        }

        if (App.bookmarks.all.isNotEmpty()) {
            item {
                SettingsSection(stringResource(R.string.bookmarks)) {
                    App.bookmarks.all.forEachIndexed { i, b ->
                        if (i > 0) ItemDivider()
                        NavItem(
                            icon = Icons.Outlined.Bookmark,
                            title = b.name,
                            subtitle = App.vfs.find(b.location.vfsId)?.title,
                            trailing = {
                                IconButton(onClick = { App.bookmarks.remove(b.location) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.remove_bookmark), Modifier.size(18.dp))
                                }
                            },
                        ) { onOpen(b.location) }
                    }
                }
            }
        }
    }
}

/** Marks the place that's open in the Files tab. */
@Composable
private fun OpenDot() {
    Text(stringResource(R.string.open), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
}
