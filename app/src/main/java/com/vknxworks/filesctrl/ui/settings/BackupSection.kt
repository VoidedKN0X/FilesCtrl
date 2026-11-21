package com.vknxworks.filesctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.data.BackupFormatException
import com.vknxworks.filesctrl.data.SettingsBackup
import com.vknxworks.filesctrl.ui.NavItem
import com.vknxworks.filesctrl.ui.SettingsSection
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Settings > Back up and restore: FilesCtrl's settings in a JSON file you pick, optionally with your servers and
 * bookmarks (never passwords, keys or trusted host keys and certificates). Restoring shows what's in the file first.
 */
@Composable
fun BackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askExport by remember { mutableStateOf(false) }
    var includePlaces by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SettingsBackup.Contents?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val places = includePlaces
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = SettingsBackup.export(places, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("no stream")
                }
            }.isSuccess
            Toast.makeText(ctx, if (ok) R.string.backup_saved else R.string.backup_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is small; anything far bigger isn't one.
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > SettingsBackup.MAX_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw IOException("no stream")
                }
                SettingsBackup.parse(text)
            }.onSuccess { pending = it }.onFailure { e ->
                val msg = when {
                    e is BackupFormatException && e.newer -> R.string.backup_too_new
                    e is BackupFormatException -> R.string.backup_not_valid
                    else -> R.string.backup_read_failed
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsSection(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        NavItem(Icons.Outlined.FileDownload, stringResource(R.string.backup_save), stringResource(R.string.backup_save_sub)) { askExport = true }
        NavItem(Icons.Outlined.FileUpload, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_sub)) {
            AppLockState.expectReturn()
            open.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }
    }

    if (askExport) {
        CtrlAlertDialog(
            onDismissRequest = { askExport = false },
            title = { Text(stringResource(R.string.backup_save)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_save_body))
                    Row(Modifier.fillMaxWidth().clickable { includePlaces = !includePlaces }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includePlaces, { includePlaces = it })
                        Text(stringResource(R.string.backup_include_places), color = CtrlColors.Text)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askExport = false
                    AppLockState.expectReturn()
                    save.launch("filesctrl-settings-${LocalDate.now()}.json")
                }) { Text(stringResource(R.string.backup_choose_file)) }
            },
            dismissButton = { TextButton(onClick = { askExport = false }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }

    pending?.let { c ->
        val none = stringResource(R.string.backup_none)
        val servers = c.servers.joinToString(", ") { it.title }.ifBlank { none }
        CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_body, c.createdAt.ifBlank { "?" }, servers, c.bookmarks.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { SettingsBackup.restore(c) } }
                        r.onSuccess { res ->
                            Toast.makeText(ctx, App.str(R.string.backup_restored, res.serversAdded, res.serversKept, res.bookmarksAdded), Toast.LENGTH_LONG).show()
                        }.onFailure { e ->
                            Toast.makeText(ctx, App.str(R.string.backup_restore_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}
