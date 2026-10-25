package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Mime
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.Conflict
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ops.TaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlTextField

/** Asks for a file or folder name. The part before the extension is pre-selected, like Material Files. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    validate: (String) -> String? = { Paths.validName(it) },
    onConfirm: (String) -> Unit,
) {
    val dot = initial.lastIndexOf('.')
    var value by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(0, if (dot > 0) dot else initial.length)))
    }
    val err = if (value.text == initial && initial.isNotEmpty()) null else validate(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canConfirm = value.text.isNotBlank() && err == null
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            CtrlTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = err != null && value.text.isNotEmpty(),
                supportingText = { if (err != null && value.text.isNotEmpty()) Text(err) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = canConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
fun DeleteDialog(items: List<FileEntry>, remote: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = if (items.size == 1) "\"${items[0].name}\"" else App.plural(R.plurals.items_count, items.size, items.size)
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_2, what)) },
        text = {
            Text(
                if (remote) stringResource(R.string.this_deletes_from_the_server_there) else stringResource(R.string.this_can_t_be_undone),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.lock_delete), color = CtrlColors.Error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

/** Shown before pasting when names are already taken in the destination. */
@Composable
fun ConflictDialog(names: List<String>, onDismiss: () -> Unit, onChoose: (Conflict) -> Unit) {
    var choice by remember { mutableStateOf(Conflict.KeepBoth) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) stringResource(R.string.already_exists_3, names[0]) else stringResource(R.string.items_already_exist, names.size)) },
        text = {
            Column {
                if (names.size > 1) {
                    Text(
                        names.take(5).joinToString(", ") + if (names.size > 5) ", …" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                listOf(
                    Conflict.KeepBoth to App.str(R.string.keep_both_rename_the_new_ones),
                    Conflict.Replace to App.str(R.string.replace_folders_are_merged),
                    Conflict.Skip to App.str(R.string.skip_these),
                ).forEach { (c, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == c, onClick = { choice = c })
                        Text(label, Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(choice) }) { Text(stringResource(R.string.paste)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
private fun PropRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
fun PropertiesDialog(entry: FileEntry, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    // Start with what the list knew, then refresh from the file system (folders opened from the menu have no details yet).
    var e by remember { mutableStateOf(entry) }
    val scope = rememberCoroutineScope()
    var folderSize by remember { mutableStateOf<String?>(null) }
    var space by remember { mutableStateOf<String?>(null) }
    var linkTarget by remember { mutableStateOf<String?>(null) }
    var chmod by remember { mutableStateOf(false) }
    var checksums by remember { mutableStateOf(false) }
    val vfs = App.vfs.find(e.vfsId)

    LaunchedEffect(entry.path) {
        withContext(Dispatchers.IO) {
            try { vfs?.stat(entry.path)?.let { st -> e = st.copy(name = entry.name) } } catch (_: Exception) {}
            if (e.isDir) vfs?.space(e.path)?.let { s ->
                space = App.str(R.string.free_of, Fmt.size(ctx, s.free), Fmt.size(ctx, s.total))
            }
            if (e.isLink && vfs is com.vknxworks.filesctrl.fs.RemoteVfs) linkTarget = try { vfs.readlink(e.path) } catch (_: Exception) { null }
            if (e.isLink && vfs is com.vknxworks.filesctrl.fs.LocalVfs) linkTarget = try { android.system.Os.readlink(e.path) } catch (_: Exception) { null }
        }
    }

    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PropRow(stringResource(R.string.location), if (e.path == "/") displayPath(e) else Paths.parent(displayPath(e)), mono = true)
                PropRow(stringResource(R.string.type), if (e.isDir) stringResource(R.string.folder) else "${Mime.kind(e).name} · ${e.mime}")
                if (e.isDir) {
                    val s = folderSize
                    if (s == null) {
                        TextButton(onClick = {
                            folderSize = App.str(R.string.calculating)
                            scope.launch {
                                folderSize = try {
                                    val (b, n) = withContext(Dispatchers.IO) { Ops.folderSize(e) }
                                    App.plural(R.plurals.folder_size_items, n - 1, n - 1, Fmt.size(ctx, b))
                                } catch (ex: Exception) { ex.message ?: App.str(R.string.couldn_t_calculate) }
                            }
                        }) { Text(stringResource(R.string.calculate_size)) }
                    } else PropRow(stringResource(R.string.size), s)
                    space?.let { PropRow(stringResource(R.string.space), it) }
                } else {
                    PropRow(stringResource(R.string.size), stringResource(R.string.bytes, Fmt.size(ctx, e.size), "%,d".format(e.size)))
                }
                PropRow(stringResource(R.string.modified), Fmt.dateTime(ctx, e.modified))
                linkTarget?.let { PropRow(stringResource(R.string.link_to), it, mono = true) }
                if (e.mode != 0) PropRow(stringResource(R.string.permissions), "${Fmt.perms(e.mode)}  (${Fmt.octal(e.mode)})", mono = true)
                if (e.uid >= 0) PropRow(stringResource(R.string.owner), stringResource(R.string.uid_gid, e.uid, e.gid), mono = true)
                if (!e.isDir) TextButton(onClick = { checksums = true }) { Text(stringResource(R.string.checksums)) }
                if (e.isDir && com.vknxworks.filesctrl.shortcuts.Shortcuts.canPin(e.location)) TextButton(onClick = { pinShortcut(ctx, e.name, e.location) }) {
                    Text(stringResource(R.string.add_to_home_screen))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (vfs?.canChmod == true && e.mode != 0 && !e.isLink) TextButton(onClick = { chmod = true }) { Text(stringResource(R.string.permissions)) }
        },
    )

    if (checksums) ChecksumDialog(e) { checksums = false }

    if (chmod) {
        ChmodDialog(e, onDismiss = { chmod = false }) { mode ->
            chmod = false
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    try { vfs?.chmod(e.path, mode); null } catch (ex: Exception) { ex.message }
                }
                if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                else { onChanged(); onDismiss() }
            }
        }
    }
}

@Composable
fun ChmodDialog(e: FileEntry, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf(e.mode and 0xFFF) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(72.dp))
                    listOf(App.str(R.string.read), App.str(R.string.write), App.str(R.string.exec)).forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                listOf(App.str(R.string.owner) to 6, App.str(R.string.group) to 3, App.str(R.string.others) to 0).forEach { (who, shift) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(who, Modifier.width(72.dp))
                        for (bit in 2 downTo 0) {
                            val mask = 1 shl (shift + bit)
                            Row(Modifier.weight(1f)) {
                                Checkbox(checked = mode and mask != 0, onCheckedChange = { on ->
                                    mode = if (on) mode or mask else mode and mask.inv()
                                })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Fmt.perms((e.mode and 0xF000) or mode)}  (${Fmt.octal(mode)})",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(text) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_ok)) } },
    )
}

/** The server with the host key / certificate from [e] marked as trusted. */
fun trusted(s: com.vknxworks.filesctrl.data.NetServer, e: com.vknxworks.filesctrl.fs.UntrustedIdentityException) =
    if (e is com.vknxworks.filesctrl.fs.HostKeyUnknownException) s.copy(hostKey = e.fingerprint) else s.copy(certPin = e.fingerprint)

/** Asks before trusting a server's SSH host key or self-signed certificate for the first time. */
@Composable
fun TrustDialog(e: com.vknxworks.filesctrl.fs.UntrustedIdentityException, host: String, onDismiss: () -> Unit, onTrust: () -> Unit) {
    val ssh = e is com.vknxworks.filesctrl.fs.HostKeyUnknownException
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ssh) stringResource(R.string.trust_this_server) else stringResource(R.string.trust_this_certificate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (ssh) stringResource(R.string.trust_ssh_body, host)
                    else stringResource(R.string.trust_cert_body, host),
                )
                SelectionContainer { Text(e.fingerprint, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.no_password_is_sent_until_you_trust), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.trust)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}
