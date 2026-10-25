package com.vknxworks.filesctrl.ui

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.ui.lock.PIN_MAX
import com.vknxworks.filesctrl.ui.lock.PIN_MIN
import com.vknxworks.filesctrl.ui.lock.biometricAvailable
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.ui.lock.disableLock
import com.vknxworks.filesctrl.ui.lock.migrate
import com.vknxworks.filesctrl.ui.lock.PinRequest
import com.vknxworks.filesctrl.ui.lock.PinRequests
import com.vknxworks.filesctrl.ui.lock.lockActivity
import com.vknxworks.filesctrl.data.lock.LockVault
import com.vknxworks.filesctrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings, opened from the You page (tap your profile picture). About is on the You page itself. */
@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val s = App.settings
    val lock = App.lock
    val ctx = LocalContext.current
    val bioAvailable = remember { biometricAvailable(ctx) }
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    var allFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    // Checked again whenever the app comes back, so allowing (or blocking) them in Android's settings shows here.
    val notifications = rememberNotificationAccess()

    // Pick up permission changes made in Android's settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
                allFiles = Environment.isExternalStorageManager()
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    Scaffold(topBar = { BackBar(stringResource(R.string.settings), onBack = { nav.back() }) }) { pad ->
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {

        item {
            SettingsSection(stringResource(R.string.group_appearance)) {
                ToggleItem(Icons.Outlined.GridView, stringResource(R.string.grid_view), stringResource(R.string.show_files_as_tiles_instead_of_a), s.gridView) { s.gridView = it }
                ItemDivider()
                ToggleItem(Icons.Outlined.VerticalSplit, stringResource(R.string.dual_pane), stringResource(R.string.dual_pane_subtitle), s.dualPane) { s.dualPane = it }
            }
        }

        item {
            SettingsSection(
                stringResource(R.string.group_privacy),
                footer = stringResource(R.string.privacy_footer),
            ) {
                val vault = App.vault
                ToggleItem(
                    Icons.Outlined.Lock, stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    vault.enabled,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (vault.enabled) {
                    NavItem(
                        Icons.Outlined.Password, stringResource(R.string.lock_change_pin), stringResource(R.string.lock_change_pin_subtitle, PIN_MIN),
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    ) { PinRequests.request = PinRequest.CHANGE }
                    ToggleItem(
                        Icons.Outlined.Fingerprint, stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        vault.biometricEnabled, enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        val act = ctx.lockActivity() as? FragmentActivity
                        if (on && act != null) {
                            val cipher = vault.biometricEnrolCipher()
                            if (cipher == null) Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                            else try {
                                showBiometricPrompt(act, ctx.getString(R.string.lock_fingerprint_enable_title), cipher, subtitle = ctx.getString(R.string.lock_fingerprint_enable_subtitle)) { unlocked ->
                                    if (!vault.finishBiometricEnrol(unlocked)) {
                                        Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (_: Exception) {
                                // The system refused to show the prompt (e.g. while leaving the app).
                                Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                            }
                        } else if (!on) vault.disableBiometric()
                    }
                    val afters = listOf(0, 60, 300, 900, 3600, -1)
                    ChoiceItem(
                        Icons.Outlined.Timer, stringResource(R.string.lock_again),
                        listOf(
                            R.string.lock_again_immediately, R.string.lock_again_1m, R.string.lock_again_5m,
                            R.string.lock_again_15m, R.string.lock_again_1h, R.string.lock_again_restart,
                        ).map { stringResource(it) },
                        afters.indexOf(lock.lockTimeoutSeconds).coerceAtLeast(0),
                    ) { lock.lockTimeoutSeconds = afters[it] }
                }
                ToggleItem(
                    Icons.Outlined.VisibilityOff, stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle), lock.hideScreenshots,
                ) { lock.hideScreenshots = it }
                ToggleItem(
                    Icons.Outlined.Layers, stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle), lock.hideRecents,
                ) { lock.hideRecents = it }
            }
        }

        item {
            SettingsSection(stringResource(R.string.files)) {
                ToggleItem(Icons.Outlined.Visibility, stringResource(R.string.show_hidden_files), stringResource(R.string.names_starting_with_a_dot), s.showHidden) { s.showHidden = it }
                ItemDivider()
                ToggleItem(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.folders_first), stringResource(R.string.keep_folders_above_files_when), s.foldersFirst) { s.foldersFirst = it }
                ItemDivider()
                ToggleItem(Icons.Outlined.Delete, stringResource(R.string.confirm_before_deleting), null, s.confirmDelete) { s.confirmDelete = it }
                ItemDivider()
                ToggleItem(
                    Icons.Outlined.Image, stringResource(R.string.thumbnails_on_network_locations),
                    stringResource(R.string.load_image_previews_over_the_network), s.nfsThumbnails,
                ) { s.nfsThumbnails = it }
                ItemDivider()
                ToggleItem(
                    Icons.Outlined.Photo, stringResource(R.string.open_pictures_in_filesctrl),
                    stringResource(R.string.open_pictures_in_filesctrl_subtitle), s.builtInViewer,
                ) { s.builtInViewer = it }
                ItemDivider()
                ToggleItem(
                    Icons.Outlined.Bookmarks, stringResource(R.string.launcher_bookmarks),
                    stringResource(R.string.launcher_bookmarks_subtitle), s.launcherBookmarks,
                ) {
                    s.launcherBookmarks = it
                    val app = ctx.applicationContext
                    Thread { com.vknxworks.filesctrl.shortcuts.Shortcuts.updateDynamic(app) }.start()
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.permissions)) {
                if (notifications.granted) {
                    NavItem(
                        Icons.Outlined.Notifications, stringResource(R.string.notifications),
                        stringResource(R.string.notifications_allowed_summary),
                    ) { openNotificationSettings(ctx) }
                } else {
                    NavItem(
                        Icons.Outlined.NotificationsOff, stringResource(R.string.notifications_are_blocked),
                        stringResource(R.string.notifications_are_blocked_summary),
                        trailing = {
                            com.vknxworks.filesctrl.ui.components.CtrlButton(onClick = { notifications.request() }, height = 40.dp) {
                                Text(stringResource(R.string.allow), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        },
                    ) { notifications.request() }
                }
                ItemDivider()
                NavItem(
                    Icons.Outlined.FolderSpecial, stringResource(R.string.all_files_access),
                    if (allFiles) stringResource(R.string.allowed_this_phone_s_storage_can_be) else stringResource(R.string.needed_to_browse_this_phone_s),
                ) {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); try { ctx.startActivity(i) } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
                ItemDivider()
                NavItem(
                    Icons.Outlined.BatteryChargingFull, stringResource(R.string.background_activity),
                    if (unrestricted) stringResource(R.string.unrestricted_so_long_copies_keep)
                    else stringResource(R.string.restricted_coloros_and_other_phones),
                ) {
                    if (!unrestricted) {
                        run { com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
                    } else {
                        run { com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                }
            }
        }

        item {
            SettingsSection(stringResource(R.string.other_apps)) {
                NavItem(
                    Icons.Outlined.Share, stringResource(R.string.network_locations_in_other_apps),
                    stringResource(R.string.saved_servers_appear_in_android_s),
                ) {}
            }
        }
    }
    }
}

