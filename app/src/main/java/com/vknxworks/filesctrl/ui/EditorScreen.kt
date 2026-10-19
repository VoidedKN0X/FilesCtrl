package com.vknxworks.filesctrl.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.ops.Ops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

const val MAX_EDIT_BYTES = 4L * 1024 * 1024

/** Something the editor can load and save: a file in FilesCtrl, or a file handed over by another app. */
interface TextSource {
    val name: String
    val subtitle: String
    val readOnly: Boolean
    fun read(): ByteArray
    fun write(bytes: ByteArray)
    /** Called when the editor closes. */
    fun closed() {}
}

class VfsTextSource(private val loc: Location) : TextSource {
    override val name = Paths.name(loc.path)
    override val subtitle: String get() = App.vfs.find(loc.vfsId)?.title ?: ""
    override val readOnly: Boolean = loc.vfsId.startsWith(com.vknxworks.filesctrl.fs.ArchiveVfs.PREFIX) ||
        App.servers.get(loc.vfsId.removePrefix(RemoteVfs.PREFIX))?.readOnly == true

    override fun read(): ByteArray {
        val vfs = App.vfs.get(loc.vfsId)
        if (vfs.stat(loc.path).size > MAX_EDIT_BYTES) throw IOException("This file is too large to edit here (over 4 MB).")
        return vfs.openRead(loc.path).use { it.readBytes() }
    }

    override fun write(bytes: ByteArray) = App.vfs.get(loc.vfsId).openWrite(loc.path).use { it.write(bytes) }

    // The browser refreshes the folder when the editor closes.
    override fun closed() = Ops.notifyChanged(Location(loc.vfsId, Paths.parent(loc.path)))
}

/** A file another app asked us to open ("Open with" / "Edit with"). */
class UriTextSource(private val ctx: Context, private val uri: Uri, writable: Boolean, from: String?) : TextSource {
    override val name: String = displayName() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Text file"
    override val subtitle: String = listOfNotNull(from?.let { "From $it" }, if (writable) null else "read-only").joinToString(" · ")
    override val readOnly: Boolean = !writable

    private fun displayName(): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    override fun read(): ByteArray {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("The other app didn't hand over the file")
        input.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_EDIT_BYTES) throw IOException("This file is too large to edit here (over 4 MB).")
            }
            return out.toByteArray()
        }
    }

    override fun write(bytes: ByteArray) {
        // "wt" truncates; a few providers only know "w".
        val out = try { ctx.contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) { null }
            ?: ctx.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("The other app doesn't allow saving this file")
        out.use { it.write(bytes) }
    }
}

/** How a file's bytes were decoded, so saving writes them back the same way. */
private data class Encoding(val charset: Charset, val bom: Boolean) {
    val label: String get() = if (charset == Charsets.UTF_8) (if (bom) "UTF-8 BOM" else "UTF-8") else charset.displayName()

    fun encode(text: String): ByteArray {
        val body = text.toByteArray(charset)
        return if (bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body else body
    }

    companion object {
        /** UTF-8 (with or without BOM) when the bytes are valid UTF-8, otherwise Windows-1252 (Latin-1 superset). */
        fun decode(bytes: ByteArray): Pair<String, Encoding> {
            val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            val body = if (bom) bytes.copyOfRange(3, bytes.size) else bytes
            return try {
                val dec = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                dec.decode(ByteBuffer.wrap(body)).toString() to Encoding(Charsets.UTF_8, bom)
            } catch (_: CharacterCodingException) {
                val cs = try { Charset.forName("windows-1252") } catch (_: Exception) { Charsets.ISO_8859_1 }
                String(body, cs) to Encoding(cs, false)
            }
        }
    }
}

/** A plain text editor for small files from FilesCtrl or from other apps. */
@Composable
fun EditorScreen(source: TextSource, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf("") }
    var encoding by remember { mutableStateOf(Encoding(Charsets.UTF_8, false)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    var askDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var afterCopy by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(source) {
        try {
            val bytes = withContext(Dispatchers.IO) { source.read() }
            // NUL bytes near the start mean it isn't text (images, archives, executables).
            if (bytes.take(8000).any { it == 0.toByte() }) {
                error = "This looks like a binary file, not text, so it can't be edited here."
            } else {
                val (s, enc) = Encoding.decode(bytes)
                text = s
                original = s
                encoding = enc
            }
        } catch (e: SecurityException) {
            error = "The other app didn't give permission to read this file."
        } catch (e: Exception) {
            error = e.message ?: "Couldn't open the file"
        }
    }

    val dirty = text != null && text != original

    fun saveTo(write: (ByteArray) -> Unit, then: (() -> Unit)? = null, markClean: Boolean = true) {
        val t = text ?: return
        saving = true
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try { write(encoding.encode(t)); null }
                catch (e: SecurityException) { "Not allowed to save here. Use \"Save a copy\" instead." }
                catch (e: Exception) { e.message ?: "Save failed" }
            }
            saving = false
            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            else {
                if (markClean) original = t
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                then?.invoke()
            }
        }
    }

    fun save(then: (() -> Unit)? = null) = saveTo({ source.write(it) }, then)

    // "Save a copy": pick a new file anywhere (including FilesCtrl's network locations) and write there.
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val next = afterCopy
        afterCopy = null
        if (uri != null) {
            saveTo({ bytes ->
                (ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't create the copy")).use { it.write(bytes) }
            }, next)
        }
    }

    BackHandler(enabled = dirty) { askDiscard = true }
    DisposableEffect(source) { onDispose { source.closed() } }

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
                        Text((if (dirty) "• " else "") + source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(source.subtitle, if (text != null) encoding.label else "").filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
                        Icon(Icons.AutoMirrored.Outlined.WrapText, "Wrap lines")
                    }
                    if (saving) CircularProgressIndicator(Modifier.padding(12.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    else if (!source.readOnly) IconButton(onClick = { save() }, enabled = dirty) { Icon(Icons.Outlined.Save, "Save") }
                    else IconButton(onClick = { saveCopy.launch(source.name) }, enabled = text != null) { Icon(Icons.Outlined.SaveAs, "Save a copy") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save a copy") },
                                leadingIcon = { Icon(Icons.Outlined.SaveAs, null) },
                                enabled = text != null,
                                onClick = { menu = false; saveCopy.launch(source.name) },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            val t = text
            when {
                error != null -> Text(
                    error!!, Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                t == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    var mod = Modifier.fillMaxSize().verticalScroll(vScroll)
                    if (!wrap) mod = mod.horizontalScroll(hScroll)
                    BasicTextField(
                        value = t,
                        onValueChange = { text = it },
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
            text = {
                Text(
                    if (source.readOnly) "${source.name} can't be changed in place. Save your edits as a copy?"
                    else "Save your changes to ${source.name}?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    if (source.readOnly) { afterCopy = onBack; saveCopy.launch(source.name) } else save(onBack)
                }) { Text(if (source.readOnly) "Save a copy" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false; onBack() }) { Text("Discard") }
            },
        )
    }
}
