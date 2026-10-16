package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.ops.OpTask
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ops.TaskState

/** Copies, moves, deletes and zips: running ones with progress, then the finished ones. */
@Composable
fun TransfersTab() {
    val running = Ops.tasks.filter { it.state == TaskState.Running }
    val finished = Ops.tasks.filter { it.state != TaskState.Running }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottom) {
        item { TabTitle("Transfers", if (running.isEmpty()) "Nothing running" else Ops.plural(running.size, "operation") + " running") }

        if (Ops.tasks.isEmpty()) {
            item { EmptyBox(Icons.Outlined.Sync, "Copies, moves and deletes show up here while they run, and stay until you clear them.") }
        }
        if (running.isNotEmpty()) {
            item {
                SettingsSection("Running") {
                    running.forEachIndexed { i, t -> if (i > 0) ItemDivider(); TaskRow(t) }
                }
            }
        }
        if (finished.isNotEmpty()) {
            item {
                SectionHeader("Finished", Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = { Ops.clearFinished() }) { Text("Clear") }
                }
            }
            item {
                SettingsSection(null) {
                    finished.forEachIndexed { i, t -> if (i > 0) ItemDivider(); TaskRow(t) }
                }
            }
        }
    }
}

private fun taskIcon(t: OpTask): ImageVector = when {
    t.state == TaskState.Done -> Icons.Outlined.CheckCircle
    t.state == TaskState.Failed -> Icons.Outlined.ErrorOutline
    t.state == TaskState.Cancelled -> Icons.Outlined.DoNotDisturbOn
    t.title.startsWith("Deleting") -> Icons.Outlined.Delete
    t.title.startsWith("Compressing") || t.title.startsWith("Extracting") -> Icons.Outlined.FolderZip
    else -> Icons.Outlined.ContentCopy
}

@Composable
private fun TaskRow(t: OpTask) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            taskIcon(t), null,
            tint = when (t.state) { TaskState.Failed -> cs.error; TaskState.Done -> cs.primary; else -> cs.onSurfaceVariant },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                color = if (t.state == TaskState.Failed) cs.error else cs.onSurfaceVariant,
            )
            if (t.state == TaskState.Running) {
                Spacer(Modifier.height(8.dp))
                val p = t.progress
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
            t.warnings.take(5).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (t.state == TaskState.Running) {
            IconButton(onClick = { t.cancel() }) { Icon(Icons.Outlined.Close, "Cancel") }
        } else Spacer(Modifier.width(12.dp))
    }
}
