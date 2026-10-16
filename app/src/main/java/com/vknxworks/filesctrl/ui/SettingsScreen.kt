package com.vknxworks.filesctrl.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.data.ThemeMode

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = App.settings
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text("Settings") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header("Appearance")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.theme == m,
                            onClick = { s.theme = m },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.name) }
                    }
                }
            }
            Toggle("Material You colours", "Use colours from your wallpaper", s.dynamicColor) { s.dynamicColor = it }
            Toggle("Pure black", "Black background in dark theme", s.pureBlack) { s.pureBlack = it }

            Header("Files")
            Toggle("Show hidden files", "Names starting with a dot", s.showHidden) { s.showHidden = it }
            Toggle("Folders first", null, s.foldersFirst) { s.foldersFirst = it }
            Toggle("Grid view", null, s.gridView) { s.gridView = it }
            Toggle("Confirm before deleting", null, s.confirmDelete) { s.confirmDelete = it }
            Toggle(
                "Thumbnails on NFS shares",
                "Load image previews over the network (images up to 25 MB)",
                s.nfsThumbnails,
            ) { s.nfsThumbnails = it }

            Header("About")
            ListItem(
                headlineContent = { Text("FilesCtrl ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Build ${BuildConfig.VERSION_CODE} · NFS by libnfs 8.0") },
            )
            ListItem(
                headlineContent = { Text("Source code") },
                supportingContent = { Text("github.com/VoidedKN0X/FilesCtrl") },
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VoidedKN0X/FilesCtrl")))
                },
            )
            ListItem(
                headlineContent = { Text("NFS shares in other apps") },
                supportingContent = {
                    Text(
                        "Your saved shares also appear in Android's file picker (\"Browse\" in any app that opens files), " +
                            "and files opened from FilesCtrl stream straight from the server.",
                    )
                },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val supporting: (@Composable () -> Unit)? = if (sub != null) { { Text(sub) } } else null
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
