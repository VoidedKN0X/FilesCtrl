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
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlCard
import com.vknxworks.filesctrl.ui.components.CtrlIconButton
import com.vknxworks.filesctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.components.CtrlSwitch
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlFonts
import com.vknxworks.filesctrl.ui.theme.CtrlShapes
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlTextField

private sealed interface TestResult {
    data object Running : TestResult
    data class Ok(val text: String) : TestResult
    data class Failed(val text: String) : TestResult
}

/** Add or edit a network location. [serverId] null = new. */
@Composable
fun ServerEditScreen(serverId: String?, onDone: (openId: String?) -> Unit) {
    // Passwords and keys are typed here: never in screenshots, recordings or casting.
    com.vknxworks.filesctrl.ui.lock.SecureWindow()
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
            // Key files are a few KB; don't read a huge file someone picked by mistake into memory.
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytes(MAX_KEY_BYTES + 1) }
            val text = bytes.toString(Charsets.UTF_8)
            if (bytes.size > MAX_KEY_BYTES || !text.contains("PRIVATE KEY")) message = App.str(R.string.that_doesn_t_look_like_a_private_key)
            else privateKey = text
        } catch (e: Exception) { message = App.str(R.string.couldn_t_read_the_key, e.message) }
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
                        else -> if (s.secure && s.type == Proto.FTP) "${s.type.label}S" else if (s.secure) App.str(R.string.over_https, s.type.label) else s.type.label
                    }
                    val found = App.plural(
                        if (s.type == Proto.SMB && s.path.isBlank()) R.plurals.test_found_shares
                        else R.plurals.test_found_items,
                        items.size, items.size, what,
                    )
                    TestResult.Ok(
                        space?.takeIf { it.total > 0 }?.let { App.str(R.string.test_found_space, found, Fmt.size(ctx, it.free), Fmt.size(ctx, it.total)) } ?: found,
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
    val hintColor = CtrlColors.Muted

    val hostTrailing: (@Composable () -> Unit)? = if (type != Proto.NFS) null else { {
                            if (scanBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = {
                                scanBusy = true
                                scope.launch {
                                    found = withContext(Dispatchers.IO) { try { NfsNative.findServers()?.toList() } catch (_: Throwable) { null } }
                                    scanBusy = false
                                    if (found.isNullOrEmpty()) message = App.str(R.string.no_nfs_servers_answered_on_this)
                                    else scanMenu = true
                                }
                            }) { Icon(Icons.Outlined.Radar, stringResource(R.string.find_nfs_servers)) }
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
                                if (exports.isNullOrEmpty()) message = App.str(R.string.couldn_t_list_exports_the_server_may)
                                else exportsMenu = true
                            }
                        }) { Icon(Icons.Outlined.FolderOpen, stringResource(R.string.list_the_server_s_exports)) }
    } }

    Scaffold(
        topBar = {
            CtrlBackBar(
                title = if (existing == null) stringResource(R.string.add_network) else stringResource(R.string.edit, existing.type.label),
                onBack = { onDone(null) },
                backDescription = stringResource(R.string.back),
                actions = {
                    if (existing != null) CtrlIconButton(Icons.Outlined.Delete, stringResource(R.string.remove), onClick = { confirmDelete = true }, tint = CtrlColors.Error)
                    Spacer(Modifier.width(6.dp))
                    CtrlButton(enabled = valid, height = 44.dp, onClick = {
                        val s = build()
                        try {
                            App.servers.save(s)
                            onDone(if (existing == null) s.id else null)
                        } catch (e: Exception) {
                            message = e.message ?: App.str(R.string.couldn_t_save_the_server)
                        }
                    }) { Text(stringResource(R.string.save), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CtrlSectionLabel(stringResource(R.string.type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Proto.entries.forEach { p ->
                    FilterChip(
                        selected = type == p,
                        onClick = { type = p; test = null },
                        label = { Text(p.label.uppercase(), fontFamily = CtrlFonts.Mono, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                        leadingIcon = { Icon(protoIcon(p), null, Modifier.size(18.dp)) },
                        shape = CtrlShapes.Badge,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CtrlColors.Surface,
                            labelColor = CtrlColors.Text,
                            iconColor = CtrlColors.Muted,
                            selectedContainerColor = CtrlColors.Accent,
                            selectedLabelColor = CtrlColors.Ink,
                            selectedLeadingIconColor = CtrlColors.Ink,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = type == p,
                            borderColor = CtrlColors.ControlBorder, selectedBorderColor = CtrlColors.Accent,
                        ),
                    )
                }
            }
            Text(
                when (type) {
                    Proto.NFS -> App.str(R.string.linux_servers_and_nas_boxes_nfsv3_v4)
                    Proto.SMB -> App.str(R.string.windows_shares_samba_and_most_nas)
                    Proto.SFTP -> App.str(R.string.files_over_ssh_on_any_machine)
                    Proto.FTP -> App.str(R.string.ftp_servers_optionally_encrypted)
                    Proto.WEBDAV -> App.str(R.string.nextcloud_owncloud_and_other_webdav)
                },
                style = hint, color = hintColor,
            )

            // ----- Address -----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    CtrlTextField(
                        value = host, onValueChange = { host = it; exports = null },
                        label = { Text(stringResource(R.string.server_address)) },
                        placeholder = { Text(stringResource(R.string.n_192_168_1_10_or_nas_local)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = hostTrailing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                        found.orEmpty().forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { host = a; scanMenu = false }) }
                    }
                }
                CtrlTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.port)) },
                    placeholder = { Text(if (type == Proto.NFS) "auto" else type.port(secure).toString()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }

            // ----- Path -----
            Box(Modifier.fillMaxWidth()) {
                CtrlTextField(
                    value = path, onValueChange = { path = it },
                    label = {
                        Text(when (type) {
                            Proto.NFS -> App.str(R.string.export_path)
                            Proto.SMB -> App.str(R.string.share_optional)
                            Proto.WEBDAV -> App.str(R.string.path)
                            else -> App.str(R.string.folder_optional)
                        })
                    },
                    placeholder = {
                        Text(when (type) {
                            Proto.NFS -> "/srv/share"
                            Proto.SMB -> App.str(R.string.media_or_media_movies)
                            Proto.SFTP -> "/home/me"
                            Proto.FTP -> "/"
                            Proto.WEBDAV -> "/remote.php/dav/files/me"
                        })
                    },
                    supportingText = {
                        Text(when (type) {
                            Proto.NFS -> App.str(R.string.for_nfsv4_this_is_the_path_in_the)
                            Proto.SMB -> App.str(R.string.leave_empty_to_see_every_share_on)
                            Proto.WEBDAV -> App.str(R.string.the_webdav_address_on_the_server)
                            else -> App.str(R.string.where_to_start_browsing_empty_starts)
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
                    if (type == Proto.FTP) stringResource(R.string.encrypt_ftps) else stringResource(R.string.use_https),
                    when {
                        !secure -> App.str(R.string.off_your_password_and_files_travel)
                        type == Proto.FTP -> App.str(R.string.explicit_tls_on_the_normal_ftp_port)
                        else -> null
                    },
                    secure,
                ) { secure = it }
                if (secure) SwitchRow(
                    stringResource(R.string.accept_self_signed_certificates),
                    stringResource(R.string.for_your_own_server_with_a),
                    insecureTls,
                ) { insecureTls = it }
                if (secure && insecureTls && certPin.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.trusted_certificate_sha_256))
                            Text(certPin, style = hint, color = hintColor, fontFamily = FontFamily.Monospace)
                        }
                        TextButton(onClick = { certPin = "" }) { Text(stringResource(R.string.forget)) }
                    }
                }
            }
            if (type == Proto.SMB && !guest) {
                SwitchRow(
                    stringResource(R.string.require_signing),
                    stringResource(R.string.on_by_default_refuses_servers_that),
                    smbSigning,
                ) { smbSigning = it }
                SwitchRow(stringResource(R.string.encrypt_smb_3), stringResource(R.string.encrypt_all_traffic_the_server_must), smbEncrypt) { smbEncrypt = it }
            }

            // ----- Sign-in -----
            if (type == Proto.NFS) {
                CtrlSectionLabel(stringResource(R.string.nfs_version))
                val versions = listOf(0 to App.str(R.string.auto), 3 to "v3", 4 to "v4.0", 42 to "v4.2")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    versions.forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = version == v,
                            onClick = { version = v },
                            shape = SegmentedButtonDefaults.itemShape(i, versions.size, baseShape = CtrlShapes.Control),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = CtrlColors.Accent, activeContentColor = CtrlColors.Ink, activeBorderColor = CtrlColors.Accent,
                                inactiveContainerColor = CtrlColors.Surface, inactiveContentColor = CtrlColors.Text, inactiveBorderColor = CtrlColors.ControlBorder,
                            ),
                            icon = {},
                        ) { Text(label, fontFamily = CtrlFonts.Mono) }
                    }
                }
                Text(stringResource(R.string.auto_tries_nfsv3_first_then_nfsv4), style = hint, color = hintColor)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CtrlTextField(
                        value = uid, onValueChange = { uid = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.user_id_uid)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    CtrlTextField(
                        value = gid, onValueChange = { gid = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.group_id_gid)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.nfs_ids_hint),
                    style = hint, color = hintColor,
                )
            } else {
                if (type == Proto.SMB) {
                    SwitchRow(
                        stringResource(R.string.guest),
                        if (guest) stringResource(R.string.connecting_as_guest_without_an) else stringResource(R.string.connect_without_an_account),
                        guest,
                    ) { guest = it }
                }
                if (type != Proto.SMB || !guest) CtrlTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    placeholder = { Text(if (type == Proto.FTP) stringResource(R.string.empty_for_anonymous) else "") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != Proto.SMB || !guest) CtrlTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (type == Proto.SFTP && privateKey.isNotBlank()) stringResource(R.string.key_passphrase) else stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) stringResource(R.string.hide) else stringResource(R.string.show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.saved_encrypted_on_this_phone), style = hint, color = hintColor)
                if (type == Proto.SFTP) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, null, tint = hintColor)
                        Spacer(Modifier.width(12.dp))
                        Text(if (privateKey.isBlank()) stringResource(R.string.no_private_key) else stringResource(R.string.private_key_loaded), Modifier.weight(1f))
                        if (privateKey.isNotBlank()) TextButton(onClick = { privateKey = "" }) { Text(stringResource(R.string.remove)) }
                        CtrlSecondaryButton(onClick = { com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); keyPicker.launch(arrayOf("*/*")) }) { Text(if (privateKey.isBlank()) stringResource(R.string.choose_key) else stringResource(R.string.replace)) }
                    }
                    if (hostKey.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.trusted_host_key))
                                Text(hostKey, style = hint, color = hintColor, fontFamily = FontFamily.Monospace)
                            }
                            TextButton(onClick = { hostKey = "" }) { Text(stringResource(R.string.forget)) }
                        }
                    }
                }
            }

            SwitchRow(stringResource(R.string.read_only), stringResource(R.string.never_change_anything_on_this_server), readOnly) { readOnly = it }

            CtrlTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.name_optional)) },
                placeholder = { Text(host.ifBlank { App.str(R.string.my_nas) }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.advanced))
            }
            if (advanced) {
                if (type == Proto.SMB) {
                    CtrlTextField(
                        value = domain, onValueChange = { domain = it },
                        label = { Text(stringResource(R.string.domain_workgroup)) }, placeholder = { Text(stringResource(R.string.usually_empty)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == Proto.NFS) {
                    CtrlTextField(
                        value = mountPort, onValueChange = { mountPort = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.mount_port_v3)) }, placeholder = { Text(stringResource(R.string.auto_lower)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    Text(stringResource(R.string.set_the_port_above_and_this_one_for), style = hint, color = hintColor)
                }
                CtrlTextField(
                    value = timeout, onValueChange = { timeout = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.timeout_seconds)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            CtrlButton(
                height = 56.dp,
                enabled = valid && test != TestResult.Running,
                onClick = {
                    test = TestResult.Running
                    val s = build()
                    runTest(s)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (test == TestResult.Running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CtrlColors.Ink)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connecting_2))
                } else Text(stringResource(R.string.test_connection))
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
        CtrlAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_2, existing.title)) },
            text = { Text(stringResource(R.string.this_only_forgets_the_server_in)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    App.servers.remove(existing.id)
                    onDone(null)
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
    message?.let { m -> InfoDialog(stringResource(R.string.network), m) { message = null } }
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CtrlShapes.Control)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        CtrlSwitch(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun ResultCard(ok: Boolean, text: String) {
    CtrlCard(Modifier.fillMaxWidth(), borderColor = if (ok) CtrlColors.Accent else CtrlColors.Error) {
        Row {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                tint = if (ok) CtrlColors.Accent else CtrlColors.Error,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text)
        }
    }
}

private const val MAX_KEY_BYTES = 64 * 1024
