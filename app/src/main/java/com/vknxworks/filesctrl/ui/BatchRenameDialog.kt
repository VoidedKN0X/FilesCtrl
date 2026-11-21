package com.vknxworks.filesctrl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.Paths
import com.vknxworks.filesctrl.ops.BatchRename
import com.vknxworks.filesctrl.ops.RenameCase
import com.vknxworks.filesctrl.ops.RenameRules
import com.vknxworks.filesctrl.ui.components.CtrlAlertDialog
import com.vknxworks.filesctrl.ui.components.CtrlTextField
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType

/**
 * Renames the selected items in one go: find and replace, a name template with a counter, and upper or lower case.
 * The preview shows every new name; names that can't be used are marked and stop the rename until they're fixed.
 * [taken] are the names of the other items in the folder.
 */
@Composable
fun BatchRenameDialog(items: List<FileEntry>, taken: Set<String>, onDismiss: () -> Unit, onConfirm: (List<Pair<FileEntry, String>>) -> Unit) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(RenameRules.NAME) }
    var start by remember { mutableStateOf("1") }
    var case by remember { mutableStateOf(RenameCase.Keep) }
    val rules = RenameRules(find, replace, matchCase, template, start.toIntOrNull()?.coerceIn(0, 999_999) ?: 1, case)
    val plan = remember(items, taken, rules) {
        BatchRename.plan(items.map { it.name }, items.map { it.isDir }, rules, taken) { Paths.validName(it) == null && Paths.isSafeName(it) }
    }
    val changes = plan.indices.count { plan[it].first != items[it].name }
    val problems = plan.count { it.second != null }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_rename_title, items.size)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CtrlTextField(find, { find = it }, label = { Text(stringResource(R.string.batch_find)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                CtrlTextField(replace, { replace = it }, label = { Text(stringResource(R.string.batch_replace)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { matchCase = !matchCase }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(matchCase, { matchCase = it })
                    Text(stringResource(R.string.batch_match_case), color = CtrlColors.Text)
                }
                CtrlTextField(
                    template, { template = it }, label = { Text(stringResource(R.string.batch_template)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.batch_template_hint)) },
                )
                Spacer(Modifier.height(6.dp))
                CtrlTextField(
                    start, { v -> start = v.filter { it.isDigit() }.take(6) }, label = { Text(stringResource(R.string.batch_start)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(RenameCase.Keep to R.string.batch_case_keep, RenameCase.Lower to R.string.batch_case_lower, RenameCase.Upper to R.string.batch_case_upper).forEach { (c, label) ->
                        Row(Modifier.clickable { case = c }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(case == c, { case = c })
                            Text(stringResource(label), color = CtrlColors.Text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.batch_preview).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    itemsIndexed(plan) { i, (name, problem) ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(items[i].name, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("→", color = CtrlColors.Muted)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (problem != null) CtrlColors.Error else if (name != items[i].name) CtrlColors.Text else CtrlColors.Muted,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (problem != null) Text(
                                stringResource(
                                    when (problem) {
                                        BatchRename.Problem.Invalid -> R.string.batch_problem_invalid
                                        BatchRename.Problem.Duplicate -> R.string.batch_problem_duplicate
                                        BatchRename.Problem.Exists -> R.string.batch_problem_exists
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(items.mapIndexed { i, e -> e to plan[i].first }) }, enabled = changes > 0 && problems == 0) {
                Text(stringResource(R.string.batch_rename_confirm, changes))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}
