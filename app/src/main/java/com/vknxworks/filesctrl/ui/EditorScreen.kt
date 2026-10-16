package com.vknxworks.filesctrl.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_EDIT_BYTES = 4L * 1024 * 1024

/** A plain text editor for small files on any file system. */
@Composable
fun EditorScreen(loc: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    var askDiscard by remember { mutableStateOf(false) }
    val name = Paths.name(loc.path)
    val readOnly = App.servers.get(loc.vfsId.removePrefix("nfs:"))?.readOnly == true

    LaunchedEffect(loc) {
        try {
            val content = withContext(Dispatchers.IO) {
                val vfs = App.vfs.get(loc.vfsId)
                val st = vfs.stat(loc.path)
                if (st.size > MAX_EDIT_BYTES) throw java.io.IOException("This file is too large to edit here (over 4 MB).")
                vfs.openRead(loc.path).use { it.readBytes() }
            }
            if (content.take(8000).any { it == 0.toByte() }) error = "This looks like a binary file."
            else {
                val s = String(content, Charsets.UTF_8)
                text = s
                original = s
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't open the file"
        }
    }

    val dirty = text != null && text != original

    fun save(then: (() -> Unit)? = null) {
        val t = text ?: return
        saving = true
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    App.vfs.get(loc.vfsId).openWrite(loc.path).use { it.write(t.toByteArray(Charsets.UTF_8)) }
                    null
                } catch (e: Exception) { e.message ?: "Save failed" }
            }
            saving = false
            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            else {
                original = t
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                then?.invoke()
            }
        }
    }

    BackHandler(enabled = dirty) { askDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (dirty) askDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Text((if (dirty) "• " else "") + name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            App.vfs.find(loc.vfsId)?.title ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
                        Icon(Icons.AutoMirrored.Outlined.WrapText, "Wrap lines")
                    }
                    if (saving) CircularProgressIndicator(Modifier.padding(12.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    else if (!readOnly) IconButton(onClick = { save() }, enabled = dirty) { Icon(Icons.Outlined.Save, "Save") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            val t = text
            when {
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                t == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    var mod = Modifier.fillMaxSize().verticalScroll(vScroll)
                    if (!wrap) mod = mod.horizontalScroll(hScroll)
                    BasicTextField(
                        value = t,
                        onValueChange = { if (!readOnly) text = it },
                        readOnly = readOnly,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = mod.padding(horizontal = 12.dp, vertical = 8.dp).let { if (wrap) it.fillMaxWidth() else it },
                    )
                }
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes to $name?") },
            confirmButton = {
                TextButton(enabled = !readOnly, onClick = { askDiscard = false; save(onBack) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false; onBack() }) { Text("Discard") }
            },
        )
    }
    // The browser refreshes the folder when we leave.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (original.isNotEmpty() || text != null) Ops.notifyChanged(Location(loc.vfsId, Paths.parent(loc.path))) }
    }
}
