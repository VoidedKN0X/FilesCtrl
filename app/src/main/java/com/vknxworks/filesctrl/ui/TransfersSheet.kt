package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ops.OpTask
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ops.TaskState
import com.vknxworks.filesctrl.ui.components.CtrlIconButton
import com.vknxworks.filesctrl.ui.components.CtrlProgress
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.components.topDivider
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType

/**
 * The transfers list: every running operation (copy, move, delete, compress, extract, rename, sync, saving shared
 * files) and the last finished ones, with pause and resume, cancel, and retry for one that failed or was cancelled.
 */
@Composable
fun TransfersSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CtrlColors.Surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(CtrlColors.ControlBorder)) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlSectionLabel(stringResource(R.string.transfers_title), Modifier.weight(1f))
            if (Ops.tasks.any { it.state != TaskState.Running }) TextButton(onClick = { Ops.clearFinished() }) {
                Text(stringResource(R.string.transfers_clear_finished), color = CtrlColors.AccentText)
            }
        }
        if (Ops.tasks.isEmpty()) {
            Text(
                stringResource(R.string.transfers_empty), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            )
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(Ops.tasks.toList(), key = { it.id }) { t -> TaskRow(t) }
        }
    }
}

@Composable
private fun TaskRow(t: OpTask) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().topDivider(inset = 24.dp).padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when {
                    t.state == TaskState.Running && t.paused -> stringResource(R.string.transfer_paused)
                    t.state == TaskState.Failed -> t.error ?: t.detail
                    else -> t.detail
                }
                Text(
                    status, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (t.state == TaskState.Failed) CtrlColors.Error else CtrlColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (t.state == TaskState.Running) {
                    if (t.pausable) {
                        if (t.paused) CtrlIconButton(Icons.Outlined.PlayArrow, stringResource(R.string.transfer_resume), onClick = { t.resume() })
                        else CtrlIconButton(Icons.Outlined.Pause, stringResource(R.string.transfer_pause), onClick = { t.pause() })
                    }
                    CtrlIconButton(Icons.Outlined.Close, stringResource(R.string.lock_cancel), onClick = { t.cancel() })
                } else if (t.canRetry) {
                    CtrlIconButton(Icons.Outlined.Refresh, stringResource(R.string.transfer_retry), onClick = { Ops.retry(t) })
                }
            }
        }
        if (t.state == TaskState.Running) {
            Spacer(Modifier.height(8.dp))
            CtrlProgress(t.progress ?: 0f, Modifier.fillMaxWidth(), color = if (t.paused) CtrlColors.Muted else CtrlColors.Accent)
            val p = t.progress
            if (t.bytesTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Fmt.size(ctx, t.bytesDone)} / ${Fmt.size(ctx, t.bytesTotal)}" + (p?.let { " · ${(it * 100).toInt()}%" } ?: ""),
                    style = CtrlType.Label, color = CtrlColors.Muted,
                )
            } else if (t.filesTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${t.filesDone} / ${t.filesTotal}", style = CtrlType.Label, color = CtrlColors.Muted)
            }
        }
        if (t.warnings.isNotEmpty() && t.state != TaskState.Running) {
            Text(
                t.warnings.take(3).joinToString("\n") + if (t.warnings.size > 3) "\n…" else "",
                style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
