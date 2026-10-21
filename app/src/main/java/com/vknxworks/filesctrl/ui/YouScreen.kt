package com.vknxworks.filesctrl.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.settings.Group
import com.vknxworks.filesctrl.ui.settings.GroupRow

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FilesCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/FilesCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * The round profile picture at the top right of the Files and Places tabs. FilesCtrl has no accounts, so it's
 * the series' placeholder avatar; tapping it opens the You page, the only way into Settings (like every Ctrl app).
 */
@Composable
fun ProfileButton() {
    val nav = LocalNav.current
    IconButton(onClick = { nav.go("you") }) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AccountCircle, stringResource(R.string.you_and_settings), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The You page: a card for this phone (FilesCtrl has no accounts), the way into Settings, and the About
 * section every Ctrl app ends with.
 */
@Composable
fun YouScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val cs = MaterialTheme.colorScheme
    val deviceName = remember {
        runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ").trim()
    }
    val servers = App.servers.all.size
    fun open(url: String) {
        // Opening the browser from here doesn't count as leaving FilesCtrl for the app lock.
        com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(topBar = { BackBar(stringResource(R.string.you_title), onBack = { nav.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            // This phone, in place of the account card the server apps have.
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(56.dp), tint = cs.primary)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    deviceName.ifBlank { stringResource(R.string.you_this_phone) },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    listOf(
                        stringResource(R.string.you_android_version, Build.VERSION.RELEASE),
                        if (servers == 0) stringResource(R.string.you_no_servers)
                        else pluralStringResource(R.plurals.you_server_count, servers, servers),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Filled.Settings,
                    showChevron = true,
                    onClick = { nav.go("settings") },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), Icons.Filled.Favorite, showChevron = true, onClick = { open(SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), Icons.Filled.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), Icons.Filled.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), Icons.Filled.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }

            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
