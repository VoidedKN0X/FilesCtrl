package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlCard
import com.vknxworks.filesctrl.ui.components.CtrlSwitch
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType

/** Above the files of a folder that holds a Cryptomator vault: what it is, and Unlock. */
@Composable
fun VaultBanner(onUnlock: () -> Unit) {
    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), onClick = onUnlock) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = CtrlColors.AccentText, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vault_label).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, maxLines = 1)
                Text(stringResource(R.string.vault_banner_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            CtrlButton(onClick = onUnlock, height = 40.dp) { Text(stringResource(R.string.vault_unlock)) }
        }
    }
}

/**
 * Asks for a vault's password, with an opt-in "remember" switch (off every time). [wrong]: the last password
 * didn't work; [message]: another reason it didn't open; [busy]: scrypt is running.
 */
@Composable
fun VaultUnlockDialog(
    title: String,
    wrong: Boolean,
    message: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray, remember: Boolean) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (pw.isNotEmpty() && !busy) { onConfirm(pw.toCharArray(), save); pw = "" } }
    CtrlAlertDialog(
        sensitive = true,
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(stringResource(R.string.vault_unlock_title, title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                val (text, isError) = when {
                    wrong -> stringResource(R.string.that_password_didn_t_work_try_again) to true
                    message != null -> message to true
                    else -> stringResource(R.string.vault_unlock_body) to false
                }
                Text(text, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PasswordField(pw, stringResource(R.string.password), { pw = it }, onDone = submit, modifier = Modifier.focusRequester(focus))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.vault_remember_password), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.vault_remember_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    CtrlSwitch(save, { save = it })
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = CtrlColors.AccentText, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.vault_unlocking), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty() && !busy, onClick = submit) { Text(stringResource(R.string.vault_unlock)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}
