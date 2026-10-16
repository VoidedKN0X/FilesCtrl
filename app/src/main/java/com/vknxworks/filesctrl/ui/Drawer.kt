package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.NfsVfs
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.Space
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DrawerContent(
    current: Location,
    onOpen: (Location) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onSettings: () -> Unit,
    onTasks: () -> Unit,
) {
    val ctx = LocalContext.current
    val volumes = remember { App.vfs.local.volumes() }
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            "FilesCtrl",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Section("This phone")
        for (v in volumes) {
            var space by remember(v.path) { mutableStateOf<Space?>(null) }
            LaunchedEffect(v.path) { space = withContext(Dispatchers.IO) { App.vfs.local.space(v.path) } }
            val sp = space
            DrawerEntry(
                icon = when { v.primary -> Icons.Outlined.PhoneAndroid; v.label.contains("USB", true) -> Icons.Outlined.Usb; else -> Icons.Outlined.SdCard },
                label = v.label,
                sub = sp?.let { "${Fmt.size(ctx, it.free)} free of ${Fmt.size(ctx, it.total)}" },
                progress = sp?.let { if (it.total > 0) 1f - it.free.toFloat() / it.total else null },
                selected = current.vfsId == LocalVfs.ID && Paths.isWithin(current.path, v.path),
                onClick = { onOpen(Location(LocalVfs.ID, v.path)) },
            )
        }

        Section("NFS shares")
        for (s in App.servers.all) {
            val id = NfsVfs.idFor(s.id)
            DrawerEntry(
                icon = Icons.Outlined.Dns,
                label = s.title,
                sub = s.summary,
                selected = current.vfsId == id,
                onClick = { onOpen(Location(id, "/")) },
                trailing = {
                    IconButton(onClick = { onEditServer(s.id) }) { Icon(Icons.Outlined.Edit, "Edit ${s.title}", Modifier.size(20.dp)) }
                },
            )
        }
        DrawerEntry(Icons.Outlined.Add, "Add NFS share", selected = false, onClick = onAddServer)

        if (App.bookmarks.all.isNotEmpty()) {
            Section("Bookmarks")
            for (b in App.bookmarks.all) {
                val where = App.vfs.find(b.location.vfsId)?.title ?: ""
                DrawerEntry(
                    icon = Icons.Outlined.Bookmark,
                    label = b.name,
                    sub = where,
                    selected = current == b.location,
                    onClick = { onOpen(b.location) },
                    trailing = {
                        IconButton(onClick = { App.bookmarks.remove(b.location) }) {
                            Icon(Icons.Outlined.Close, "Remove bookmark", Modifier.size(18.dp))
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        val running = Ops.active.size
        val runningBadge: (@Composable () -> Unit)? = if (running > 0) { { Badge { Text("$running") } } } else null
        DrawerEntry(Icons.Outlined.Sync, "Operations", selected = false, onClick = onTasks, trailing = runningBadge)
        DrawerEntry(Icons.Outlined.Settings, "Settings", selected = false, onClick = onSettings)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    progress: Float? = null,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = {
            Column {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(
                    sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.9f))
                }
            }
        },
        badge = trailing,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).height(if (sub != null || progress != null) 64.dp else 52.dp),
    )
}
