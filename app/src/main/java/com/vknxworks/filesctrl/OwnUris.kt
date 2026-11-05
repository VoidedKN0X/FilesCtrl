package com.vknxworks.filesctrl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import com.vknxworks.filesctrl.provider.NetDocumentsProvider

/**
 * FilesCtrl's own content:// links (its FileProvider and its network documents provider). FilesCtrl can always
 * read and write these itself, whether or not the sending app could, so another app must never be able to hand
 * one to the editor or "Save to FilesCtrl" and have FilesCtrl open it on its behalf.
 */
object OwnUris {
    fun isOwn(activity: Activity, uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        // content://10@authority/... names another user's provider; the provider part is after the last '@'.
        val authority = uri.encodedAuthority?.substringAfterLast('@')?.takeIf { it.isNotEmpty() } ?: return false
        if (authority.equals("${activity.packageName}.fileprovider", ignoreCase = true) ||
            authority.equals(NetDocumentsProvider.AUTHORITY, ignoreCase = true)
        ) return true
        // Any other provider that belongs to FilesCtrl counts too.
        return runCatching {
            activity.packageManager.resolveContentProvider(authority, 0)?.packageName == activity.packageName
        }.getOrDefault(false)
    }

    /**
     * The activity was started by FilesCtrl itself (directly or through Android's "Open with" / share sheet, which
     * starts the chosen app as FilesCtrl). Only uses what Android sets, never what the sender can put in the intent.
     */
    fun launchedByUs(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val uid = runCatching { activity.launchedFromUid }.getOrDefault(Process.INVALID_UID)
            if (uid == Process.myUid()) return true
        }
        // Without referrer extras, the referrer is the package Android saw starting the activity.
        val intent = activity.intent ?: return false
        val spoofable = runCatching {
            intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)
        }.getOrDefault(true)
        if (spoofable) return false
        val referrer = runCatching { activity.referrer }.getOrNull() ?: return false
        return referrer.scheme == "android-app" && referrer.host == activity.packageName
    }
}
