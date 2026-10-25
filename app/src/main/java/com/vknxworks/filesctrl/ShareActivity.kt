package com.vknxworks.filesctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.ops.IncomingItem
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ui.FilesTheme
import com.vknxworks.filesctrl.ui.FolderPicker
import com.vknxworks.filesctrl.ui.isAppInDarkTheme
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.ui.lock.AppLockViewModel
import com.vknxworks.filesctrl.ui.lock.ChooseNewPinScreen
import com.vknxworks.filesctrl.ui.lock.LockScreen
import com.vknxworks.filesctrl.ui.lock.LockWindow
import com.vknxworks.filesctrl.ui.placeLabel
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Save to FilesCtrl" in Android's share sheet: files (any type) and text shared from another app are saved into
 * a folder you pick, on this phone or on any saved server, by the same background copy as Paste (with its
 * progress notification). The folder you saved to last time is offered first. Text is saved as a .txt file.
 */
class ShareActivity : FragmentActivity() {
    private sealed interface Shared {
        val name: String
        data class Stream(val uri: Uri, override val name: String, val size: Long) : Shared
        data class Text(val text: String, override val name: String) : Shared
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        // Any app can send to this screen: a malformed intent (bad parcel, wrong extra types) is treated as
        // "nothing shared" instead of crashing.
        val shared = try { readShared(intent) } catch (_: Exception) { emptyList() }
        if (shared.isEmpty()) {
            Toast.makeText(this, App.str(R.string.share_nothing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val nightNow = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(signalBarStyle(nightNow), signalBarStyle(nightNow))
        setContent {
            FilesTheme {
                val dark = isAppInDarkTheme()
                LaunchedEffect(dark) { enableEdgeToEdge(statusBarStyle = signalBarStyle(dark), navigationBarStyle = signalBarStyle(dark)) }
                val secure = App.lock.hideScreenshots || com.vknxworks.filesctrl.ui.lock.SecureScreens.active > 0
                // "Hide in recent apps", and always with the app lock on: a blank card instead of a snapshot.
                val blankRecents = App.lock.hideRecents || AppLockState.enabled
                LaunchedEffect(blankRecents) { setRecentsScreenshotEnabled(!blankRecents) }
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val lockVm: AppLockViewModel = viewModel()
                val locked = lockVm.locked
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(locked) { if (locked) { focus.clearFocus(force = true); keyboard?.hide() } }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        // Nothing is listed until unlocked: the server passwords aren't available while locked.
                        Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
                            if (!locked && !AppLockState.mustChoosePin) SaveTo(shared)
                        }
                        if (locked) {
                            LockWindow {
                                LockScreen(vm = lockVm, onForgot = {
                                    Toast.makeText(this@ShareActivity, App.str(R.string.open_filesctrl_to_reset_the_pin), Toast.LENGTH_LONG).show()
                                })
                            }
                        } else if (AppLockState.mustChoosePin) {
                            ChooseNewPinScreen()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SaveTo(shared: List<Shared>) {
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        val last = remember { Location.fromKey(App.settings.lastSaveTo) }
        FolderPicker(
            title = getString(R.string.share_save_to),
            confirmLabel = getString(R.string.share_save_here),
            start = last,
            onCancel = { finish() },
            onPick = { dest ->
                if (busy) return@FolderPicker
                busy = true
                scope.launch {
                    // Shared files may only be readable while this screen is open, so they're opened now and
                    // read by the background copy after it closes.
                    val items = withContext(Dispatchers.IO) { shared.mapNotNull { open(it) } }
                    if (items.isEmpty()) {
                        Toast.makeText(this@ShareActivity, App.str(R.string.share_unreadable), Toast.LENGTH_LONG).show()
                        busy = false
                        return@launch
                    }
                    App.settings.lastSaveTo = dest.key
                    Ops.saveIncoming(items, dest)
                    Toast.makeText(
                        this@ShareActivity,
                        App.plural(R.plurals.share_saving_to, items.size, items.size, placeLabel(dest)),
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
            },
            header = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        pluralStringResource(R.plurals.share_items, shared.size, shared.size).uppercase(),
                        style = CtrlType.Label, color = CtrlColors.AccentText,
                    )
                    Text(
                        shared.joinToString(", ") { it.name },
                        style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }

    private fun open(s: Shared): IncomingItem? = when (s) {
        is Shared.Text -> {
            val bytes = s.text.toByteArray(Charsets.UTF_8)
            IncomingItem(s.name, bytes.size.toLong(), { bytes.inputStream() })
        }
        is Shared.Stream -> try {
            val input = contentResolver.openInputStream(s.uri)
            if (input == null) null else {
                var used = false
                IncomingItem(s.name, s.size, { used = true; input }, { if (!used) input.close() })
            }
        } catch (_: Exception) { null }
    }

    private fun readShared(intent: Intent?): List<Shared> {
        if (intent == null) return emptyList()
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> runCatching { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) }.getOrNull()?.let { uris += it }
            Intent.ACTION_SEND_MULTIPLE -> runCatching { intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) }.getOrNull()?.let { uris += it.filterNotNull() }
            else -> return emptyList()
        }
        if (uris.isEmpty()) intent.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { uris += it } }
        // Only content:// links: a file:// path could point into FilesCtrl's own private files.
        val streams = uris.filter { it.scheme.equals("content", ignoreCase = true) }.map { describe(it) }
        if (streams.isNotEmpty()) return streams
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!text.isNullOrEmpty()) {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { cleanName(it) }?.takeIf { it.isNotBlank() }?.take(80)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date())
            val base = subject ?: getString(R.string.share_text_name, stamp)
            return listOf(Shared.Text(text, if (base.endsWith(".txt", ignoreCase = true)) base else "$base.txt"))
        }
        return emptyList()
    }

    private fun describe(uri: Uri): Shared.Stream {
        var name: String? = null
        var size = -1L
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {}
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: getString(R.string.share_file_name)
        var n = cleanName(name ?: fallback).ifBlank { getString(R.string.share_file_name) }
        // No extension: add one from the type the sender gave, so the file opens in the right app later.
        if (!n.contains('.')) {
            val ext = runCatching { contentResolver.getType(uri) }.getOrNull()?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            if (!ext.isNullOrBlank()) n = "$n.$ext"
        }
        return Shared.Stream(uri, n, size)
    }

    /** A name another app sent, made safe to use as one file name. */
    private fun cleanName(raw: String): String {
        var n = raw.replace(Regex("[/\\\\\\u0000-\\u001f]"), "_").trim()
        if (n == "." || n == "..") n = "_"
        while (n.toByteArray().size > 255) n = n.dropLast(1)
        return n
    }
}
