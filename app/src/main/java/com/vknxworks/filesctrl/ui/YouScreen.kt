package com.vknxworks.filesctrl.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.components.CtrlAvatarButton
import com.vknxworks.filesctrl.ui.components.CtrlBackBar
import com.vknxworks.filesctrl.ui.components.CtrlButton
import com.vknxworks.filesctrl.ui.components.CtrlSectionLabel
import com.vknxworks.filesctrl.ui.settings.Group
import com.vknxworks.filesctrl.ui.settings.GroupRow
import com.vknxworks.filesctrl.ui.theme.CtrlColors
import com.vknxworks.filesctrl.ui.theme.CtrlType

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FilesCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/FilesCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val OFL_URL = "https://openfontlicense.org"

/** Initials for this phone's name ("OPPO Find X9 Pro" → "OF"), the stand-in for a profile picture. */
@Composable
fun rememberDeviceName(): String {
    val ctx = LocalContext.current
    return remember {
        runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ").trim()
    }
}

fun initialsOf(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "FC" }

/**
 * The profile button at the top right of the Files and Places tabs. FilesCtrl has no accounts, so it shows
 * this phone's initials; tapping it opens the You page, the only way into Settings (like every Ctrl app).
 * [connected]: a network location is open, shown as the small accent square.
 */
@Composable
fun ProfileButton(connected: Boolean = false) {
    val nav = LocalNav.current
    val initials = initialsOf(rememberDeviceName())
    CtrlAvatarButton(onClick = { nav.go("you") }, contentDescription = stringResource(R.string.you_and_settings), connected = connected) {
        Text(initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = CtrlColors.Text)
    }
}

/**
 * The You page: this phone (FilesCtrl has no accounts), the way into Settings, the About section every
 * Ctrl app ends with, and Support at the bottom.
 */
@Composable
fun YouScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val deviceName = rememberDeviceName()
    val servers = App.servers.all.size
    fun open(url: String) {
        // Opening the browser from here doesn't count as leaving FilesCtrl for the app lock.
        com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlBackBar(
            title = "",
            onBack = { nav.back() },
            backDescription = stringResource(R.string.close),
            backIcon = Icons.Outlined.Close,
            actions = { },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            CtrlSectionLabel(stringResource(R.string.profile_label), Modifier.padding(horizontal = 20.dp))
            // This phone, in place of the account card the server apps have.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initialsOf(deviceName), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CtrlColors.Ink)
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        deviceName.ifBlank { stringResource(R.string.you_this_phone) },
                        style = MaterialTheme.typography.headlineSmall,
                        color = CtrlColors.Text,
                    )
                    Text(
                        listOf(
                            stringResource(R.string.you_android_version, Build.VERSION.RELEASE),
                            if (servers == 0) stringResource(R.string.you_no_servers)
                            else pluralStringResource(R.plurals.you_server_count, servers, servers),
                        ).joinToString(App.str(R.string.list_separator)),
                        style = CtrlType.Meta,
                        color = CtrlColors.Muted,
                    )
                }
            }

            Group(stringResource(R.string.app_group)) {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = { nav.go("settings") },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), Icons.Outlined.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), Icons.Outlined.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), Icons.Outlined.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
                GroupRow(stringResource(R.string.fonts_title), stringResource(R.string.fonts_subtitle), Icons.Outlined.TextFields, showChevron = true, onClick = { open(OFL_URL) })
            }

            Spacer(Modifier.height(16.dp))
            CtrlButton(
                onClick = { open(SPONSOR_URL) },
                height = 56.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.support_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                stringResource(R.string.support_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.signal_footer),
                style = CtrlType.Label.copy(letterSpacing = 0.12.em),
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = CtrlType.Meta,
                color = CtrlColors.Inactive,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
