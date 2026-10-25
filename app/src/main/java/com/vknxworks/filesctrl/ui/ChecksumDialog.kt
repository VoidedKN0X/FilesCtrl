package com.vknxworks.filesctrl.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.ops.Checksums
import com.vknxworks.filesctrl.ops.HashAlgo
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlProgress
import com.vknxworks.filesctrl.ui.components.CtrlTextField
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checksums of one file: MD5, SHA-1, SHA-256, SHA-512 and CRC32, worked out in a single read (streamed from
 * servers, with progress), each with a copy button, and a field to paste a hash to compare with.
 */
@Composable
fun ChecksumDialog(e: FileEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var done by remember { mutableLongStateOf(0L) }
    var results by remember { mutableStateOf<Map<HashAlgo, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var compare by remember { mutableStateOf("") }

    // Closing the dialog cancels this, which stops reading the file.
    LaunchedEffect(e.vfsId, e.path) {
        try {
            results = withContext(Dispatchers.IO) { Checksums.compute(e) { n -> done = n } }
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: Exception) {
            error = ex.message ?: ex.javaClass.simpleName
        }
    }

    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checksums), maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.name, style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val r = results
                when {
                    error != null -> Text(stringResource(R.string.checksum_failed, error.orEmpty()), color = CtrlColors.Error)
                    r == null -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (e.size > 0) stringResource(R.string.checksum_reading, Fmt.size(ctx, done), Fmt.size(ctx, e.size))
                            else stringResource(R.string.checksum_reading_unknown, Fmt.size(ctx, done)),
                            style = CtrlType.Meta, color = CtrlColors.Text,
                        )
                        CtrlProgress(if (e.size > 0) done.toFloat() / e.size else 0f, color = CtrlColors.AccentText)
                    }
                    else -> {
                        val matched = if (compare.isBlank()) null else Checksums.match(r, compare)
                        HashAlgo.entries.forEach { a ->
                            HashRow(a.label, r.getValue(a), highlighted = matched == a) {
                                copyText(ctx, a.label, r.getValue(a))
                                Toast.makeText(ctx, App.str(R.string.checksum_copied, a.label), Toast.LENGTH_SHORT).show()
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        CtrlTextField(
                            value = compare,
                            onValueChange = { compare = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.checksum_compare)) },
                            placeholder = { Text(stringResource(R.string.checksum_compare_hint)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                                    if (!clip.isNullOrBlank()) compare = clip.trim()
                                }) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (compare.isNotBlank()) {
                            val guess = Checksums.guess(compare)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (matched != null) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                                    Modifier.size(18.dp), tint = if (matched != null) CtrlColors.AccentText else CtrlColors.Error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        matched != null -> stringResource(R.string.checksum_match, matched.label)
                                        guess != null -> stringResource(R.string.checksum_no_match_algo, guess.label)
                                        else -> stringResource(R.string.checksum_no_match)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (matched != null) CtrlColors.AccentText else CtrlColors.Error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun HashRow(label: String, value: String, highlighted: Boolean, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = CtrlType.LabelStrong, color = if (highlighted) CtrlColors.AccentText else CtrlColors.Muted)
            SelectionContainer {
                Text(value, style = CtrlType.Meta.copy(fontSize = 11.sp), color = if (highlighted) CtrlColors.AccentText else CtrlColors.Text)
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.checksum_copy, label), Modifier.size(18.dp), tint = CtrlColors.Muted)
        }
    }
}
