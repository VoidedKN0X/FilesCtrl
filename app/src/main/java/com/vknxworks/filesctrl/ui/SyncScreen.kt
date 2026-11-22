package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.TransfersUi
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ops.SyncPlan
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlCard
import com.vknxworks.filesctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.components.CtrlSwitch
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun syncRoute(loc: Location) = "sync?loc=" + android.net.Uri.encode(loc.key)

/**
 * One-way folder sync: makes a destination folder match [source]. Pick the destination, look at the dry run (what
 * would be copied, updated and, with "mirror", deleted), then sync. The sync runs in the background as a transfer.
 */
@Composable
fun SyncScreen(source: Location, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dest by remember { mutableStateOf<Location?>(null) }
    var picking by remember { mutableStateOf(true) }
    var mirror by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<SyncPlan?>(null) }
    /** The settings [plan] was worked out for; Sync only runs a plan that matches what's on screen. */
    var planFor by remember { mutableStateOf<Pair<Location, Boolean>?>(null) }
    var running by remember { mutableStateOf<Job?>(null) }
    var folders by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (picking) {
        FolderPicker(
            title = stringResource(R.string.sync_pick_destination),
            confirmLabel = stringResource(R.string.sync_use_folder),
            start = dest,
            onPick = { loc ->
                val problem = Ops.syncProblem(source, loc)
                if (problem != null) {
                    android.widget.Toast.makeText(ctx, problem, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    dest = loc
                    plan = null
                    picking = false
                }
            },
            onCancel = { if (dest == null) onBack() else picking = false },
        )
        return
    }
    val to = dest ?: return

    fun dryRun() {
        running?.cancel()
        plan = null
        error = null
        folders = 0
        running = scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { Ops.planSync(source, to, mirror) { folders++ } }
                plan = p
                planFor = to to mirror
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                running = null
            }
        }
    }

    fun start() {
        // Only what this dry run showed may be deleted; the sync checks again when it runs.
        val confirmed = plan?.takeIf { planFor == (to to mirror) } ?: return
        Ops.sync(source, to, mirror, confirmed)
        TransfersUi.open = true
        onBack()
    }

    val current = plan?.takeIf { planFor == (to to mirror) }
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlBackBar(stringResource(R.string.sync_title), onBack = onBack, backDescription = stringResource(R.string.back))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            CtrlCard(Modifier.fillMaxWidth()) {
                CtrlSectionLabel(stringResource(R.string.sync_from))
                Text(placeLabel(source), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                Spacer(Modifier.height(10.dp))
                CtrlSectionLabel(stringResource(R.string.sync_to))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(placeLabel(to), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.sync_change), color = CtrlColors.AccentText) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clickable { mirror = !mirror }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sync_mirror), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
                    Text(stringResource(R.string.sync_mirror_sub), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                }
                Spacer(Modifier.width(12.dp))
                CtrlSwitch(mirror, { mirror = it })
            }
            Text(stringResource(R.string.sync_rules), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            Spacer(Modifier.height(16.dp))
            when {
                running != null -> {
                    Text(stringResource(R.string.sync_checking, folders), style = CtrlType.Label, color = CtrlColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = CtrlColors.Accent, trackColor = CtrlColors.ProgressTrack)
                }
                error != null -> Text(stringResource(R.string.sync_failed, error ?: ""), color = CtrlColors.Error)
                current != null -> PlanSummary(current, mirror)
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            CtrlSecondaryButton(onClick = { dryRun() }, modifier = Modifier.weight(1f), enabled = running == null) {
                Text(stringResource(R.string.sync_dry_run))
            }
            Spacer(Modifier.width(12.dp))
            CtrlButton(
                onClick = { if (current != null && current.delete.isNotEmpty()) confirmDelete = true else start() },
                modifier = Modifier.weight(1f),
                enabled = running == null && current != null && !current.isEmpty,
            ) { Text(stringResource(R.string.sync_now)) }
        }
    }
    if (confirmDelete) {
        val n = current?.delete?.size ?: 0
        CtrlAlertDialog(
            sensitive = true,
            onDismissRequest = { confirmDelete = false },
            title = { Text(App.plural(R.plurals.sync_confirm_delete_title, n, n)) },
            text = { Text(stringResource(R.string.sync_confirm_delete_body, placeLabel(to))) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; start() }) { Text(stringResource(R.string.sync_now), color = CtrlColors.Error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}

@Composable
private fun PlanSummary(p: SyncPlan, mirror: Boolean) {
    val ctx = LocalContext.current
    if (p.isEmpty) {
        Text(stringResource(R.string.sync_up_to_date), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
        return
    }
    Text(stringResource(R.string.sync_plan_bytes, Fmt.size(ctx, p.bytes)), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
    Spacer(Modifier.height(8.dp))
    PlanGroup(stringResource(R.string.sync_plan_new_folders), p.create)
    PlanGroup(stringResource(R.string.sync_plan_copy), p.copy)
    PlanGroup(stringResource(R.string.sync_plan_update), p.update)
    if (mirror) PlanGroup(stringResource(R.string.sync_plan_delete), p.delete, danger = true)
    PlanGroup(stringResource(if (mirror) R.string.sync_plan_conflicts_replaced else R.string.sync_plan_conflicts_skipped), p.conflicts, danger = mirror)
}

@Composable
private fun PlanGroup(title: String, items: List<String>, danger: Boolean = false) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) CtrlColors.Error else CtrlColors.Text, modifier = Modifier.weight(1f))
            Text(items.size.toString(), style = CtrlType.Label, color = CtrlColors.Muted)
        }
        if (open) {
            items.take(SHOWN).forEach { Text(it, style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.padding(top = 2.dp)) }
            if (items.size > SHOWN) Text(stringResource(R.string.sync_and_more, items.size - SHOWN), style = CtrlType.Label, color = CtrlColors.Muted)
        } else {
            Text(stringResource(R.string.sync_tap_to_list), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
    }
}

private const val SHOWN = 200

