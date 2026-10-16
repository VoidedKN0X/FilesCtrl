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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteDialog(items: List<FileEntry>, remote: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = if (items.size == 1) "\"${items[0].name}\"" else "${items.size} items"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = {
            Text(
                if (remote) "This deletes from the server. There is no recycle bin." else "This can't be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown before pasting when names are already taken in the destination. */
@Composable
fun ConflictDialog(names: List<String>, onDismiss: () -> Unit, onChoose: (Conflict) -> Unit) {
    var choice by remember { mutableStateOf(Conflict.KeepBoth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "\"${names[0]}\" already exists" else "${names.size} items already exist") },
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
                    Conflict.KeepBoth to "Keep both (rename the new ones)",
                    Conflict.Replace to "Replace (folders are merged)",
                    Conflict.Skip to "Skip these",
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
        confirmButton = { TextButton(onClick = { onChoose(choice) }) { Text("Paste") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
    val vfs = App.vfs.find(e.vfsId)

    LaunchedEffect(entry.path) {
        withContext(Dispatchers.IO) {
            try { vfs?.stat(entry.path)?.let { st -> e = st.copy(name = entry.name) } } catch (_: Exception) {}
            if (e.isDir) vfs?.space(e.path)?.let { s ->
                space = "${Fmt.size(ctx, s.free)} free of ${Fmt.size(ctx, s.total)}"
            }
            if (e.isLink && vfs is com.vknxworks.filesctrl.fs.RemoteVfs) linkTarget = try { vfs.readlink(e.path) } catch (_: Exception) { null }
            if (e.isLink && vfs is com.vknxworks.filesctrl.fs.LocalVfs) linkTarget = try { android.system.Os.readlink(e.path) } catch (_: Exception) { null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PropRow("Location", if (e.path == "/") displayPath(e) else Paths.parent(displayPath(e)), mono = true)
                PropRow("Type", if (e.isDir) "Folder" else "${Mime.kind(e).name} · ${e.mime}")
                if (e.isDir) {
                    val s = folderSize
                    if (s == null) {
                        TextButton(onClick = {
                            folderSize = "Calculating…"
                            scope.launch {
                                folderSize = try {
                                    val (b, n) = withContext(Dispatchers.IO) { Ops.folderSize(e) }
                                    "${Fmt.size(ctx, b)} · ${n - 1} items"
                                } catch (ex: Exception) { ex.message ?: "Couldn't calculate" }
                            }
                        }) { Text("Calculate size") }
                    } else PropRow("Size", s)
                    space?.let { PropRow("Space", it) }
                } else {
                    PropRow("Size", "${Fmt.size(ctx, e.size)} (${"%,d".format(e.size)} bytes)")
                }
                PropRow("Modified", Fmt.dateTime(ctx, e.modified))
                linkTarget?.let { PropRow("Link to", it, mono = true) }
                if (e.mode != 0) PropRow("Permissions", "${Fmt.perms(e.mode)}  (${Fmt.octal(e.mode)})", mono = true)
                if (e.uid >= 0) PropRow("Owner", "uid ${e.uid} · gid ${e.gid}", mono = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (vfs?.canChmod == true && e.mode != 0 && !e.isLink) TextButton(onClick = { chmod = true }) { Text("Permissions") }
        },
    )

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(72.dp))
                    listOf("Read", "Write", "Exec").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                listOf("Owner" to 6, "Group" to 3, "Others" to 0).forEach { (who, shift) ->
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
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Running and finished file operations. */
@Composable
fun TasksSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Operations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (Ops.tasks.any { it.state != TaskState.Running }) TextButton(onClick = { Ops.clearFinished() }) { Text("Clear finished") }
        }
        if (Ops.tasks.isEmpty()) {
            Text(
                "Nothing running.", Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            items(Ops.tasks, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            val sub = when (t.state) {
                                TaskState.Running -> buildString {
                                    append(t.detail)
                                    if (t.bytesTotal > 0) append(" · ${Fmt.size(ctx, t.bytesDone)} of ${Fmt.size(ctx, t.bytesTotal)}")
                                    else if (t.filesTotal > 0) append(" · ${t.filesDone} of ${t.filesTotal}")
                                }
                                TaskState.Failed -> t.error ?: "Failed"
                                else -> t.detail
                            }
                            Text(
                                sub, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = if (t.state == TaskState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (t.state == TaskState.Running) {
                            IconButton(onClick = { t.cancel() }) { Icon(Icons.Outlined.Close, "Cancel") }
                        }
                    }
                    if (t.state == TaskState.Running) {
                        Spacer(Modifier.height(6.dp))
                        val p = t.progress
                        if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                    t.warnings.take(5).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(text) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
