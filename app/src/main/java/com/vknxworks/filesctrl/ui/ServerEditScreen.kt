package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.NfsServer
import com.vknxworks.filesctrl.fs.NfsVfs
import com.vknxworks.filesctrl.nfs.NfsNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface TestResult {
    data object Running : TestResult
    data class Ok(val text: String) : TestResult
    data class Failed(val text: String) : TestResult
}

/** Add or edit an NFS share. [serverId] null = new. */
@Composable
fun ServerEditScreen(serverId: String?, onDone: (openId: String?) -> Unit) {
    val existing = serverId?.let { App.servers.get(it) }
    val base = existing ?: NfsServer()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(base.name) }
    var host by remember { mutableStateOf(base.host) }
    var export by remember { mutableStateOf(base.export) }
    var version by remember { mutableStateOf(base.version) }
    var uid by remember { mutableStateOf(base.uid.toString()) }
    var gid by remember { mutableStateOf(base.gid.toString()) }
    var nfsPort by remember { mutableStateOf(if (base.nfsPort > 0) base.nfsPort.toString() else "") }
    var mountPort by remember { mutableStateOf(if (base.mountPort > 0) base.mountPort.toString() else "") }
    var timeout by remember { mutableStateOf(base.timeoutSec.toString()) }
    var readOnly by remember { mutableStateOf(base.readOnly) }
    var advanced by remember { mutableStateOf(base.nfsPort > 0 || base.mountPort > 0 || base.timeoutSec != 10) }

    var test by remember { mutableStateOf<TestResult?>(null) }
    var exports by remember { mutableStateOf<List<String>?>(null) }
    var exportsBusy by remember { mutableStateOf(false) }
    var exportsMenu by remember { mutableStateOf(false) }
    var scanBusy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<String>?>(null) }
    var scanMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun build() = base.copy(
        name = name.trim(),
        host = host.trim(),
        export = export.trim().ifEmpty { "/" },
        version = version,
        uid = uid.toIntOrNull() ?: 1000,
        gid = gid.toIntOrNull() ?: 1000,
        nfsPort = nfsPort.toIntOrNull()?.coerceIn(0, 65535) ?: 0,
        mountPort = mountPort.toIntOrNull()?.coerceIn(0, 65535) ?: 0,
        timeoutSec = timeout.toIntOrNull()?.coerceIn(2, 120) ?: 10,
        readOnly = readOnly,
    )

    val valid = host.isNotBlank() && uid.toIntOrNull() != null && gid.toIntOrNull() != null

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { onDone(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text(if (existing == null) "Add NFS share" else "Edit share") },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete share") }
                    TextButton(enabled = valid, onClick = {
                        val s = build()
                        App.servers.save(s)
                        onDone(if (existing == null) s.id else null)
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it; exports = null },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.10 or nas.local") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        if (scanBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = {
                            scanBusy = true
                            scope.launch {
                                found = withContext(Dispatchers.IO) { try { NfsNative.findServers()?.toList() } catch (_: Throwable) { null } }
                                scanBusy = false
                                if (found.isNullOrEmpty()) message = "No NFS servers answered on this network. Some servers and routers don't reply to broadcasts; type the address instead."
                                else scanMenu = true
                            }
                        }) { Icon(Icons.Outlined.Radar, "Find servers on this network") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                    found.orEmpty().forEach { a ->
                        DropdownMenuItem(text = { Text(a) }, onClick = { host = a; scanMenu = false; exports = null })
                    }
                }
            }

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = export, onValueChange = { export = it },
                    label = { Text("Export path") },
                    placeholder = { Text("/srv/share") },
                    singleLine = true,
                    supportingText = { Text("For NFSv4 this is the path in the server's NFSv4 tree, often the same as for v3.") },
                    trailingIcon = {
                        if (exportsBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton(enabled = host.isNotBlank(), onClick = {
                            exportsBusy = true
                            scope.launch {
                                val mp = mountPort.toIntOrNull() ?: 0
                                exports = withContext(Dispatchers.IO) {
                                    try { NfsNative.exports(host.trim(), mp, 5)?.toList() } catch (_: Throwable) { null }
                                }
                                exportsBusy = false
                                if (exports.isNullOrEmpty()) message = "Couldn't list exports. The server may be NFSv4-only or block the mount service; type the path instead."
                                else exportsMenu = true
                            }
                        }) { Icon(Icons.Outlined.FolderOpen, "List the server's exports") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = exportsMenu, onDismissRequest = { exportsMenu = false }) {
                    exports.orEmpty().forEach { e ->
                        DropdownMenuItem(text = { Text(e, fontFamily = FontFamily.Monospace) }, onClick = {
                            export = e; exportsMenu = false
                            if (name.isBlank()) name = e.trimEnd('/').substringAfterLast('/')
                        })
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text(host.ifBlank { "My NAS" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("NFS version", style = MaterialTheme.typography.labelLarge)
            val versions = listOf(0 to "Auto", 3 to "v3", 4 to "v4.0", 42 to "v4.2")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                versions.forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = version == v,
                        onClick = { version = v },
                        shape = SegmentedButtonDefaults.itemShape(i, versions.size),
                    ) { Text(label) }
                }
            }
            Text(
                "Auto tries NFSv3 first, then NFSv4.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uid, onValueChange = { uid = it.filter(Char::isDigit) },
                    label = { Text("User ID (uid)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = gid, onValueChange = { gid = it.filter(Char::isDigit) },
                    label = { Text("Group ID (gid)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "NFS trusts the IDs the client sends. Use the uid/gid that owns your files on the server " +
                    "(often 1000 on Linux, 1026+ on Synology). 0 only works if the export has no_root_squash.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Read-only")
                    Text("Never change anything on this share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = readOnly, onCheckedChange = { readOnly = it })
            }

            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text("Advanced")
            }
            if (advanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nfsPort, onValueChange = { nfsPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("NFS port") }, placeholder = { Text("auto") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mountPort, onValueChange = { mountPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Mount port (v3)") }, placeholder = { Text("auto") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Set both for servers without rpcbind/portmap, or behind port forwarding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = timeout, onValueChange = { timeout = it.filter(Char::isDigit).take(3) },
                    label = { Text("Timeout (seconds)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                enabled = valid && test != TestResult.Running,
                onClick = {
                    test = TestResult.Running
                    val s = build()
                    scope.launch {
                        test = withContext(Dispatchers.IO) {
                            val v = NfsVfs(s)
                            try {
                                val items = v.list("/")
                                val ver = v.session.activeVersion.let { if (it == 42) "NFSv4.2" else "NFSv$it" }
                                val space = v.space("/")
                                TestResult.Ok(
                                    "Connected with $ver. ${items.size} item${if (items.size == 1) "" else "s"} at the top of the share." +
                                        (space?.let { "\n${it.free / (1L shl 30)} GB free of ${it.total / (1L shl 30)} GB." } ?: ""),
                                )
                            } catch (e: Exception) {
                                TestResult.Failed(e.message ?: e.javaClass.simpleName)
                            } finally {
                                v.close()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (test == TestResult.Running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…")
                } else Text("Test connection")
            }

            when (val t = test) {
                is TestResult.Ok -> ResultCard(true, t.text)
                is TestResult.Failed -> ResultCard(false, t.text)
                else -> {}
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${existing.title}?") },
            text = { Text("This only forgets the share in FilesCtrl. Nothing on the server is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    App.servers.remove(existing.id)
                    onDone(null)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    message?.let { m -> InfoDialog("NFS", m) { message = null } }
}

@Composable
private fun ResultCard(ok: Boolean, text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
