package com.vknxworks.filesctrl.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vknxworks.filesctrl.App

/** What's shared, in the words used by the first-run question and the Settings footer. */
const val USAGE_STATS_DESCRIPTION =
    "Which screens you open, which kinds of network storage you add and browse (NFS, SMB, SFTP, FTP or WebDAV), " +
        "whether you copy, move, delete or share files, the app version, Android version, phone model and language. " +
        "Never your server addresses, usernames, share names, or any file or folder names. " +
        "Sent to VKNXWorks' own server, not to any analytics company."

/**
 * Asked once, on the main screen (after the app lock, if there is one). Nothing is sent until the user says yes;
 * they can change it in Settings. The settings are read from storage when the app starts, so people who already
 * answered never see it flash.
 */
@Composable
fun UsageStatsPrompt() {
    val t = App.telemetry
    if (!t.available || App.settings.usageStatsAsked) return
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Outlined.Insights, null) },
        title = { Text("Help improve FilesCtrl?") },
        text = {
            Text(
                "Share anonymous usage stats so I can see which features people use and which Android " +
                    "versions to support.\n\n$USAGE_STATS_DESCRIPTION\n\nYou can change this any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { t.setOptIn(true) }) { Text("Share stats") } },
        dismissButton = { TextButton(onClick = { t.setOptIn(false) }) { Text("No thanks") } },
    )
}
