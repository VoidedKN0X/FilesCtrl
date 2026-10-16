package com.vknxworks.filesctrl.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.data.NetServer
import com.vknxworks.filesctrl.data.Proto
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.nfs.NfsNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface TestResult {
    data object Running : TestResult
    data class Ok(val text: String) : TestResult
    data class Failed(val text: String) : TestResult
}

/** Add or edit a network location. [serverId] null = new. */
@Composable
fun ServerEditScreen(serverId: String?, onDone: (openId: String?) -> Unit) {
    val ctx = LocalContext.current
    val existing = serverId?.let { App.servers.get(it) }
    val base = existing ?: NetServer(type = Proto.SMB, path = "")
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(base.type) }
    var name by remember { mutableStateOf(base.name) }
    var host by remember { mutableStateOf(base.host) }
    var port by remember { mutableStateOf(if (base.port > 0) base.port.toString() else "") }
    var path by remember { mutableStateOf(base.path) }
    var username by remember { mutableStateOf(base.username) }
    var password by remember { mutableStateOf(base.password) }
    var showPassword by remember { mutableStateOf(false) }
    var domain by remember { mutableStateOf(base.domain) }
    var privateKey by remember { mutableStateOf(base.privateKey) }
    var hostKey by remember { mutableStateOf(base.hostKey) }
    var certPin by remember { mutableStateOf(base.certPin) }
    var smbSigning by remember { mutableStateOf(base.smbSigning) }
    var smbEncrypt by remember { mutableStateOf(base.smbEncrypt) }
    var guest by remember { mutableStateOf(base.guest) }
    var untrusted by remember { mutableStateOf<com.vknxworks.filesctrl.fs.UntrustedIdentityException?>(null) }
    var secure by remember { mutableStateOf(base.secure) }
    var insecureTls by remember { mutableStateOf(base.insecureTls) }
    var readOnly by remember { mutableStateOf(base.readOnly) }
    var timeout by remember { mutableStateOf(base.timeoutSec.toString()) }
    var version by remember { mutableStateOf(base.version) }
    var uid by remember { mutableStateOf(base.uid.toString()) }
    var gid by remember { mutableStateOf(base.gid.toString()) }
    var mountPort by remember { mutableStateOf(if (base.mountPort > 0) base.mountPort.toString() else "") }
    var advanced by remember { mutableStateOf(base.mountPort > 0 || base.timeoutSec != 10 || base.domain.isNotBlank()) }

    var test by remember { mutableStateOf<TestResult?>(null) }
    var exports by remember { mutableStateOf<List<String>?>(null) }
    var exportsBusy by remember { mutableStateOf(false) }
    var exportsMenu by remember { mutableStateOf(false) }
    var scanBusy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<String>?>(null) }
    var scanMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            if (!text.contains("PRIVATE KEY")) message = "That doesn't look like a private key file."
            else privateKey = text
        } catch (e: Exception) { message = "Couldn't read the key: ${e.message}" }
    }

    fun build() = base.copy(
        type = type,
        name = name.trim(),
        host = host.trim(),
        port = port.toIntOrNull()?.coerceIn(0, 65535) ?: 0,
        path = path.trim().let { if (type == Proto.NFS) it.ifEmpty { "/" } else it },
        username = username.trim(),
        password = password,
        domain = domain.trim(),
        privateKey = privateKey,
        hostKey = hostKey,
        certPin = if (secure && insecureTls) certPin else "",
        smbSigning = smbSigning,
        smbEncrypt = smbEncrypt,
        guest = type == Proto.SMB && guest,
        secure = secure,
        insecureTls = insecureTls,
        readOnly = readOnly,
        timeoutSec = timeout.toIntOrNull()?.coerceIn(2, 120) ?: 10,
        version = version,
        uid = uid.toIntOrNull() ?: 1000,
        gid = gid.toIntOrNull() ?: 1000,
        mountPort = mountPort.toIntOrNull()?.coerceIn(0, 65535) ?: 0,
    )

    fun runTest(s: NetServer) {
        test = TestResult.Running
        scope.launch {
            test = withContext(Dispatchers.IO) {
                var learned: NetServer? = null
                val v = RemoteVfs.create(s) { learned = it }
                try {
                    val items = v.list("/")
                    val space = v.space("/")
                    val what = when (v) {
                        is com.vknxworks.filesctrl.fs.NfsVfs -> v.session.activeVersion.let { if (it == 42) "NFSv4.2" else "NFSv$it" }
                        else -> s.type.label + if (s.secure && s.type == Proto.FTP) "S" else if (s.secure) " over HTTPS" else ""
                    }
                    val list = if (s.type == Proto.SMB && s.path.isBlank()) "share" else "item"
                    TestResult.Ok(
                        "Connected with $what. ${items.size} $list${if (items.size == 1) "" else "s"} found." +
                            (space?.takeIf { it.total > 0 }?.let { "\n${Fmt.size(ctx, it.free)} free of ${Fmt.size(ctx, it.total)}." } ?: ""),
                    )
                } catch (e: com.vknxworks.filesctrl.fs.UntrustedIdentityException) {
                    withContext(Dispatchers.Main) { untrusted = e }
                    null
                } catch (e: Exception) {
                    TestResult.Failed(e.message ?: e.javaClass.simpleName)
                } finally {
                    v.close()
                    // A remembered MD5 host key upgraded to SHA-256.
                    learned?.let { l -> withContext(Dispatchers.Main) { hostKey = l.hostKey } }
                }
            }
        }
    }

    val valid = host.isNotBlank() && (type != Proto.NFS || (uid.toIntOrNull() != null && gid.toIntOrNull() != null)) &&
        (type != Proto.SMB || guest || username.isNotBlank())
    val hint = MaterialTheme.typography.bodySmall
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    val hostTrailing: (@Composable () -> Unit)? = if (type != Proto.NFS) null else { {
                            if (scanBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = {
                                scanBusy = true
                                scope.launch {
                                    found = withContext(Dispatchers.IO) { try { NfsNative.findServers()?.toList() } catch (_: Throwable) { null } }
                                    scanBusy = false
                                    if (found.isNullOrEmpty()) message = "No NFS servers answered on this network. Type the address instead."
                                    else scanMenu = true
                                }
                            }) { Icon(Icons.Outlined.Radar, "Find NFS servers") }
    } }
    val exportTrailing: (@Composable () -> Unit)? = if (type != Proto.NFS) null else { {
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
    } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { onDone(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text(if (existing == null) "Add network" else "Edit ${existing.type.label}") },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Remove") }
                    TextButton(enabled = valid, onClick = {
                        val s = build()
                        try {
                            App.servers.save(s)
                            onDone(if (existing == null) s.id else null)
                        } catch (e: Exception) {
                            message = e.message ?: "Couldn't save the server"
                        }
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Proto.entries.forEach { p ->
                    FilterChip(
                        selected = type == p,
                        onClick = { type = p; test = null },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(protoIcon(p), null, Modifier.size(18.dp)) },
                    )
                }
            }
            Text(
                when (type) {
                    Proto.NFS -> "Linux servers and NAS boxes (NFSv3 / v4)."
                    Proto.SMB -> "Windows shares, Samba and most NAS boxes (SMB 2/3)."
                    Proto.SFTP -> "Files over SSH, on any machine running an SSH server."
                    Proto.FTP -> "FTP servers, optionally encrypted with TLS (FTPS)."
                    Proto.WEBDAV -> "Nextcloud, ownCloud and other WebDAV servers."
                },
                style = hint, color = hintColor,
            )

            // ----- Address -----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it; exports = null },
                        label = { Text("Server address") },
                        placeholder = { Text("192.168.1.10 or nas.local") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = hostTrailing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                        found.orEmpty().forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { host = a; scanMenu = false }) }
                    }
                }
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    placeholder = { Text(if (type == Proto.NFS) "auto" else type.port(secure).toString()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }

            // ----- Path -----
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = path, onValueChange = { path = it },
                    label = {
                        Text(when (type) {
                            Proto.NFS -> "Export path"
                            Proto.SMB -> "Share (optional)"
                            Proto.WEBDAV -> "Path"
                            else -> "Folder (optional)"
                        })
                    },
                    placeholder = {
                        Text(when (type) {
                            Proto.NFS -> "/srv/share"
                            Proto.SMB -> "media or media/movies"
                            Proto.SFTP -> "/home/me"
                            Proto.FTP -> "/"
                            Proto.WEBDAV -> "/remote.php/dav/files/me"
                        })
                    },
                    supportingText = {
                        Text(when (type) {
                            Proto.NFS -> "For NFSv4 this is the path in the server's NFSv4 tree, often the same as for v3."
                            Proto.SMB -> "Leave empty to see every share on the server."
                            Proto.WEBDAV -> "The WebDAV address on the server. Nextcloud: /remote.php/dav/files/USERNAME"
                            else -> "Where to start browsing. Empty starts in your home folder."
                        })
                    },
                    singleLine = true,
                    trailingIcon = exportTrailing,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = exportsMenu, onDismissRequest = { exportsMenu = false }) {
                    exports.orEmpty().forEach { e ->
                        DropdownMenuItem(text = { Text(e, fontFamily = FontFamily.Monospace) }, onClick = {
                            path = e; exportsMenu = false
                            if (name.isBlank()) name = e.trimEnd('/').substringAfterLast('/')
                        })
                    }
                }
            }

            // ----- Security -----
            if (type == Proto.FTP || type == Proto.WEBDAV) {
                SwitchRow(
                    if (type == Proto.FTP) "Encrypt (FTPS)" else "Use HTTPS",
                    if (type == Proto.FTP) "Explicit TLS on the normal FTP port" else null,
                    secure,
                ) { secure = it }
                if (secure) SwitchRow(
                    "Accept self-signed certificates",
                    "For your own server with a certificate it made itself. You'll be asked to check its fingerprint once.",
                    insecureTls,
                ) { insecureTls = it }
                if (secure && insecureTls && certPin.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Trusted certificate (SHA-256)")
                            Text(certPin, style = hint, color = hintColor, fontFamily = FontFamily.Monospace)
                        }
                        TextButton(onClick = { certPin = "" }) { Text("Forget") }
                    }
                }
            }
            if (type == Proto.SMB && !guest) {
                SwitchRow(
                    "Require signing",
                    "On by default. Refuses servers that don't sign their messages; turn off only for very old NAS boxes that fail to connect.",
                    smbSigning,
                ) { smbSigning = it }
                SwitchRow("Encrypt (SMB 3)", "Encrypt all traffic. The server must support SMB 3 encryption.", smbEncrypt) { smbEncrypt = it }
            }

            // ----- Sign-in -----
            if (type == Proto.NFS) {
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
                Text("Auto tries NFSv3 first, then NFSv4.", style = hint, color = hintColor)
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
                    style = hint, color = hintColor,
                )
            } else {
                if (type == Proto.SMB) {
                    SwitchRow(
                        "Guest",
                        if (guest) "Connecting as guest, without an account. Messages can't be signed or encrypted." else "Connect without an account",
                        guest,
                    ) { guest = it }
                }
                if (type != Proto.SMB || !guest) OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text(if (type == Proto.FTP) "empty for anonymous" else "") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != Proto.SMB || !guest) OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (type == Proto.SFTP && privateKey.isNotBlank()) "Key passphrase" else "Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saved encrypted on this phone.", style = hint, color = hintColor)
                if (type == Proto.SFTP) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, null, tint = hintColor)
                        Spacer(Modifier.width(12.dp))
                        Text(if (privateKey.isBlank()) "No private key" else "Private key loaded", Modifier.weight(1f))
                        if (privateKey.isNotBlank()) TextButton(onClick = { privateKey = "" }) { Text("Remove") }
                        OutlinedButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) { Text(if (privateKey.isBlank()) "Choose key" else "Replace") }
                    }
                    if (hostKey.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Trusted host key")
                                Text(hostKey, style = hint, color = hintColor, fontFamily = FontFamily.Monospace)
                            }
                            TextButton(onClick = { hostKey = "" }) { Text("Forget") }
                        }
                    }
                }
            }

            SwitchRow("Read-only", "Never change anything on this server", readOnly) { readOnly = it }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text(host.ifBlank { "My NAS" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text("Advanced")
            }
            if (advanced) {
                if (type == Proto.SMB) {
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it },
                        label = { Text("Domain / workgroup") }, placeholder = { Text("usually empty") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == Proto.NFS) {
                    OutlinedTextField(
                        value = mountPort, onValueChange = { mountPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Mount port (v3)") }, placeholder = { Text("auto") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    Text("Set the port above and this one for servers without rpcbind/portmap.", style = hint, color = hintColor)
                }
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
                    runTest(s)
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
            text = { Text("This only forgets the server in FilesCtrl. Nothing on the server is deleted.") },
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
    message?.let { m -> InfoDialog("Network", m) { message = null } }
    untrusted?.let { u ->
        TrustDialog(u, host.trim(), onDismiss = { untrusted = null }) {
            untrusted = null
            if (u is com.vknxworks.filesctrl.fs.HostKeyUnknownException) hostKey = u.fingerprint else certPin = u.fingerprint
            runTest(build())
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
