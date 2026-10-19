package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.ArchivePasswordException
import com.vknxworks.filesctrl.fs.ArchiveVfs
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.ArchiveFormat
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit, onDone: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide password" else "Show password")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Asks for an archive's password. [wrong]: the last one didn't work. */
@Composable
fun PasswordDialog(title: String, wrong: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    if (wrong) "That password didn't work. Try again." else "This archive is password-protected.",
                    color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(pw, "Password", { pw = it }, onDone = { if (pw.isNotEmpty()) onConfirm(pw.toCharArray()) }, Modifier.focusRequester(focus))
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { onConfirm(pw.toCharArray()) }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Name, format and optional password for a new archive. */
@Composable
fun CompressDialog(baseName: String, onDismiss: () -> Unit, onConfirm: (name: String, format: ArchiveFormat, password: CharArray?) -> Unit) {
    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var name by remember { mutableStateOf(baseName) }
    var pw by remember { mutableStateOf("") }
    val err = Paths.validName(name + format.ext)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    suffix = { Text(format.ext) },
                    isError = err != null && name.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ArchiveFormat.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { format = f }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(f.label)
                            Text(f.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (format.passwords) {
                    Spacer(Modifier.height(8.dp))
                    PasswordField(pw, "Password (optional)", { pw = it })
                    Text(
                        if (pw.isEmpty()) "Leave empty for no password."
                        else "AES-256. There's no way to open the archive without this password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && err == null, onClick = {
                val n = name.trim().let { if (it.endsWith(format.ext, true)) it else it + format.ext }
                onConfirm(n, format, pw.takeIf { format.passwords && it.isNotEmpty() }?.toCharArray())
            }) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Extract here": reads the archive first so a password can be asked for before anything is written, then
 * starts the extraction in the background.
 */
@Composable
fun ExtractFlow(archive: FileEntry, dest: Location, onDone: () -> Unit, onError: (String, String) -> Unit) {
    var needs by remember { mutableStateOf<ArchivePasswordException?>(null) }
    val id = remember { ArchiveVfs.idFor(archive.location) }
    LaunchedEffect(archive) {
        val result = withContext(Dispatchers.IO) {
            try {
                App.vfs.forgetArchive(id)
                val arc = App.vfs.archive(id) ?: throw java.io.IOException("Couldn't open ${archive.name}")
                arc.list("/")
                null
            } catch (e: Exception) { e }
        }
        when (result) {
            null -> { Ops.extract(archive, dest); onDone() }
            is ArchivePasswordException -> needs = result
            else -> onError("Couldn't extract ${archive.name}", result.message ?: result.javaClass.simpleName)
        }
    }
    needs?.let { ex ->
        PasswordDialog("Password for ${archive.name}", ex.wrong, onDismiss = onDone) { pw ->
            Ops.extract(archive, dest, pw)
            onDone()
        }
    }
}
