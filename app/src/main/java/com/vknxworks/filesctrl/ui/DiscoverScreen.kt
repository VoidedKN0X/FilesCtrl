package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.nfs.Discovery
import com.vknxworks.filesctrl.nfs.FoundServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Route for the share editor, pre-filled with a discovered server and export. */
fun newServerRoute(host: String, export: String?) =
    "server/new?host=" + android.net.Uri.encode(host) + (export?.let { "&export=" + android.net.Uri.encode(it) } ?: "")

/** Every NFS server and export found on the local network, with one-tap add. */
@Composable
fun DiscoverScreen(onBack: () -> Unit, onAdd: (host: String, export: String?) -> Unit, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateListOf<FoundServer>() }
    var probed by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val subnet = remember { Discovery.subnetLabel(ctx) }

    fun start() {
        job?.cancel()
        found.clear()
        probed = 0; total = 0
        running = true
        job = scope.launch {
            try {
                Discovery.scan(
                    ctx,
                    onProgress = { p, t -> scope.launch(Dispatchers.Main) { probed = p; total = t } },
                    onFound = { s ->
                        scope.launch(Dispatchers.Main) {
                            found.removeAll { it.address == s.address }
                            found.add(s)
                            found.sortBy { it.address.split('.').map { p -> p.toIntOrNull() ?: 0 }.fold(0L) { a, b -> a * 256 + b } }
                        }
                    },
                )
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(Unit) { start() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Network shares")
                        Text(
                            subnet?.let { "Scanning $it" } ?: "Not on a network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { start() }, enabled = !running) { Icon(Icons.Outlined.Refresh, "Scan again") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (running) {
                if (total > 0) LinearProgressIndicator(progress = { probed.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (found.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Radar, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (running) "Looking for NFS servers…" else "No NFS servers found",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!running) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Check you're on the same Wi-Fi as the server and that NFS is enabled on it. " +
                                        "You can still add a share by its address.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = { onAdd("", null) }) { Text("Add by address") }
                            }
                        }
                    }
                }
                items(found, key = { it.address }) { s -> ServerCard(s, onAdd, onEdit) }
            }
        }
    }
}

@Composable
private fun ServerCard(s: FoundServer, onAdd: (String, String?) -> Unit, onEdit: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.hostname ?: s.address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            s.address.takeIf { s.hostname != null },
                            when {
                                s.portmap && s.nfs -> "NFSv3 / v4"
                                s.nfs -> "NFS (no portmapper, likely v4 only)"
                                else -> "Portmapper only"
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val exports = s.exports
            when {
                exports == null -> {
                    Text(
                        "This server doesn't list its exports (common for NFSv4-only servers). Add it and type the path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(onClick = { onAdd(s.address, null) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add server")
                    }
                }
                exports.isEmpty() -> Text(
                    "No exports offered to this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                else -> exports.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp))
                    val saved = App.servers.all.firstOrNull { it.host == s.address && it.export.trimEnd('/') == e.trimEnd('/') }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(e, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (saved != null) {
                            AssistChip(onClick = { onEdit(saved.id) }, label = { Text("Added") }, leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) })
                        } else {
                            FilledTonalButton(onClick = { onAdd(s.address, e) }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}
