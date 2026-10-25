package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.data.Proto
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.fs.Space
import com.vknxworks.filesctrl.ui.components.CtrlChip
import com.vknxworks.filesctrl.ui.components.CtrlDot
import com.vknxworks.filesctrl.ui.components.CtrlHeroCard
import com.vknxworks.filesctrl.ui.components.CtrlProgress
import com.vknxworks.filesctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.filesctrl.ui.components.CtrlStatus
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icon for a kind of network location. */
fun protoIcon(p: Proto): ImageVector = when (p) {
    Proto.NFS -> Icons.Outlined.Dns
    Proto.SMB -> Icons.Outlined.Lan
    Proto.SFTP -> Icons.Outlined.Terminal
    Proto.FTP -> Icons.Outlined.SwapVert
    Proto.WEBDAV -> Icons.Outlined.Cloud
}

/** Where files live: this phone's storage, saved network locations and bookmarks (Ctrl / Signal layout). */
@Composable
fun PlacesTab(current: Location, onOpen: (Location) -> Unit, onAddServer: () -> Unit, onEditServer: (String) -> Unit) {
    val ctx = LocalContext.current
    val volumes = remember { App.vfs.local.volumes() }
    val primary = volumes.firstOrNull { it.primary } ?: volumes.firstOrNull()
    val others = volumes.filter { it != primary }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottom) {
        item { TabTitle(stringResource(R.string.places), subtitle = stringResource(R.string.you_this_phone)) }

        // The hero: this phone's main storage, free space as the big number.
        if (primary != null) item {
            var space by remember(primary.path) { mutableStateOf<Space?>(null) }
            LaunchedEffect(primary.path) { space = withContext(Dispatchers.IO) { App.vfs.local.space(primary.path) } }
            val sp = space?.takeIf { it.total > 0 }
            val isOpen = current.vfsId == LocalVfs.ID && Paths.isWithin(current.path, primary.path)
            CtrlHeroCard(
                label = stringResource(R.string.label_storage) + " · " + primary.label,
                trailingLabel = if (isOpen) stringResource(R.string.label_open) else sp?.let { stringResource(R.string.label_used_percent, usedPercent(it)) },
                onClick = { onOpen(Location(LocalVfs.ID, primary.path)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                val free = sp?.let { Fmt.size(ctx, it.free) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(free?.substringBeforeLast(' ') ?: "—", style = CtrlType.BigNumber, color = CtrlColors.Ink, maxLines = 1)
                    if (free != null && free.contains(' ')) {
                        Text(" " + free.substringAfterLast(' '), style = CtrlType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = CtrlColors.Ink, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                if (sp != null) {
                    Text(
                        stringResource(R.string.free_of, Fmt.size(ctx, sp.free), Fmt.size(ctx, sp.total)),
                        style = CtrlType.Meta, color = CtrlColors.Ink.copy(alpha = 0.75f),
                    )
                    CtrlProgress(1f - sp.free.toFloat() / sp.total, color = CtrlColors.Ink, trackColor = CtrlColors.Ink.copy(alpha = 0.18f))
                }
            }
        }

        // SD cards and USB drives.
        if (others.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.you_this_phone)) }
            items(others.size) { i ->
                val v = others[i]
                var space by remember(v.path) { mutableStateOf<Space?>(null) }
                LaunchedEffect(v.path) { space = withContext(Dispatchers.IO) { App.vfs.local.space(v.path) } }
                val sp = space?.takeIf { it.total > 0 }
                val isOpen = current.vfsId == LocalVfs.ID && Paths.isWithin(current.path, v.path)
                PlaceRow(
                    lead = { Icon(if (v.label.contains("USB", true)) Icons.Outlined.Usb else Icons.Outlined.SdCard, null, Modifier.size(20.dp), tint = CtrlColors.Muted) },
                    title = v.label,
                    meta = sp?.let { App.str(R.string.free_of, Fmt.size(ctx, it.free), Fmt.size(ctx, it.total)) },
                    usage = sp,
                    status = if (isOpen) { { CtrlStatus(stringResource(R.string.label_open), active = true) } } else null,
                ) { onOpen(Location(LocalVfs.ID, v.path)) }
            }
        }

        item {
            SectionHeader(stringResource(R.string.network), trailing = {
                Text(App.servers.all.size.toString().padStart(2, '0'), style = CtrlType.Meta, color = CtrlColors.Muted)
            })
        }
        items(App.servers.all.size) { i ->
            val s = App.servers.all[i]
            val id = RemoteVfs.idFor(s.id)
            // Storage use for servers that are connected; just the address otherwise (the list never connects).
            var space by remember(s) { mutableStateOf<Space?>(null) }
            val connected = App.vfs.connected(s.id) != null
            LaunchedEffect(s, current) {
                val v = App.vfs.connected(s.id)
                if (v != null) space = withContext(Dispatchers.IO) { v.space("/") }
            }
            val sp = space?.takeIf { it.total > 0 }
            val isOpen = current.vfsId == id
            PlaceRow(
                lead = { CtrlChip(s.type.label.uppercase().take(6)) },
                title = s.title,
                meta = sp?.let { App.str(R.string.of_used, Fmt.size(ctx, it.total - it.free), Fmt.size(ctx, it.total)) } ?: s.host,
                usage = sp,
                status = { CtrlDot(if (isOpen || connected) CtrlColors.AccentText else CtrlColors.Inactive) },
                trailing = {
                    IconButton(onClick = { onEditServer(s.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit, s.title), Modifier.size(20.dp), tint = CtrlColors.Muted) }
                },
            ) { onOpen(Location(id, "/")) }
        }
        item {
            CtrlSecondaryButton(
                onClick = onAddServer,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = CtrlColors.AccentText)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.add_network), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.nfs_smb_sftp_ftp_or_webdav), style = CtrlType.Meta.copy(fontSize = 11.sp), color = CtrlColors.Muted)
                }
                Spacer(Modifier.weight(1f))
            }
        }

        if (App.bookmarks.all.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.bookmarks)) }
            items(App.bookmarks.all.size) { i ->
                val b = App.bookmarks.all[i]
                PlaceRow(
                    lead = { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(20.dp), tint = CtrlColors.Muted) },
                    title = b.name,
                    meta = runCatching { App.vfs.find(b.location.vfsId)?.title }.getOrNull(),
                    trailing = {
                        if (com.vknxworks.filesctrl.shortcuts.Shortcuts.canPin(b.location)) {
                            IconButton(onClick = { pinShortcut(ctx, b.name, b.location) }) {
                                Icon(Icons.Outlined.Home, stringResource(R.string.add_to_home_screen), Modifier.size(18.dp), tint = CtrlColors.Muted)
                            }
                        }
                        IconButton(onClick = { App.bookmarks.remove(b.location) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.remove_bookmark), Modifier.size(18.dp), tint = CtrlColors.Muted)
                        }
                    },
                ) { onOpen(b.location) }
            }
        }
    }
}

private fun usedPercent(sp: Space): Int = ((1f - sp.free.toFloat() / sp.total) * 100).toInt().coerceIn(0, 100)

/** A plain list row: leading mono chip or icon, title, mono meta, status and an optional action. */
@Composable
private fun PlaceRow(
    lead: @Composable () -> Unit,
    title: String,
    meta: String?,
    usage: Space? = null,
    status: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp)
            .topDivider()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) { lead() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!meta.isNullOrBlank()) Text(meta, style = CtrlType.Meta.copy(fontSize = 11.sp), color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (usage != null) {
                Spacer(Modifier.height(3.dp))
                CtrlProgress(1f - usage.free.toFloat() / usage.total)
            }
        }
        if (status != null) {
            Spacer(Modifier.width(12.dp))
            status()
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}
