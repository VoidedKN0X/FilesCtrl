package com.vknxworks.filesctrl.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.ui.lock.AppLockState

/** Android 13+ asks for permission before an app may show notifications (copy and move progress here). */
object NotificationPermission {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Allowed and not switched off in Android's settings for the whole app. */
    fun canPost(ctx: Context): Boolean = hasPermission(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
}

/** Whether Android lets the app show notifications, kept current while the screen is showing. */
@Stable
class NotificationAccess(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    internal var ask: () -> Unit = {}

    /** Asks Android for permission, or opens the app's notification settings when the dialog can't show any more. */
    fun request() = ask()
}

/**
 * [NotificationAccess] for a screen. It is checked again every time the app comes back on screen, so turning
 * notifications on or off in Android's settings shows up straight away.
 */
@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val ctx = LocalContext.current
    val access = remember { NotificationAccess(NotificationPermission.canPost(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access.granted = NotificationPermission.canPost(ctx)
    }
    access.ask = {
        val activity = ctx.findActivity()
        // Android only shows its dialog the first time and once more after a "Don't allow"; after that (or when
        // notifications were switched off in Android's settings) only the settings page can turn them on.
        val dialogCanShow = !NotificationPermission.hasPermission(ctx) && (
            !App.settings.askedNotifications ||
                (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
            )
        if (dialogCanShow) {
            App.settings.askedNotifications = true
            AppLockState.expectReturn()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(ctx)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access.granted = NotificationPermission.canPost(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return access
}

/** Android's notification settings for this app (the general app info page if a phone doesn't have that one). */
fun openNotificationSettings(ctx: Context) {
    AppLockState.expectReturn()
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(direct)
    } catch (_: Exception) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
