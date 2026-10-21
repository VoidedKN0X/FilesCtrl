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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
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
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.data.ThemeMode
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

@Composable
fun SettingsTab() {
    val s = App.settings
    val lock = App.lock
    val ctx = LocalContext.current
    val bioAvailable = remember { biometricAvailable(ctx) }
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    var allFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }

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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListBottom) {
        item { TabTitle("Settings") }

        item {
            SettingsSection("Appearance") {
                ChoiceItem(
                    Icons.Outlined.DarkMode, "Theme",
                    THEMES.map { it.label },
                    THEMES.indexOf(s.theme).coerceAtLeast(0),
                ) { s.theme = THEMES[it] }
                ToggleItem(Icons.Outlined.Palette, "Use wallpaper colours", "Replaces the default colours with ones from your wallpaper", s.dynamicColor) { s.dynamicColor = it }
                ToggleItem(Icons.Outlined.GridView, "Grid view", "Show files as tiles instead of a list", s.gridView) { s.gridView = it }
            }
        }

        item {
            SettingsSection(
                "Privacy and security",
                footer = "FilesCtrl has no analytics or tracking. It only connects to the network locations you add.",
            ) {
                val vault = App.vault
                ToggleItem(
                    Icons.Outlined.Lock, "App lock",
                    "Ask for an app PIN (and optionally your fingerprint) when opening FilesCtrl. While locked, saved passwords " +
                        "and keys stay encrypted with the PIN, and your servers don't show in other apps' file pickers. " +
                        "Copies and moves that are already running finish.",
                    vault.enabled,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (vault.enabled) {
                    NavItem(
                        Icons.Outlined.Password, "Change app PIN", "At least $PIN_MIN digits, separate from your phone's PIN",
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    ) { PinRequests.request = PinRequest.CHANGE }
                    ToggleItem(
                        Icons.Outlined.Fingerprint, "Unlock with fingerprint",
                        if (bioAvailable || vault.biometricEnabled) "Only strong biometrics such as your fingerprint, checked by the phone's " +
                            "secure hardware. The PIN is still needed after a restart and every 72 hours."
                        else "Set up a fingerprint on this phone first",
                        vault.biometricEnabled, enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        val act = ctx.lockActivity() as? FragmentActivity
                        if (on && act != null) {
                            val cipher = vault.biometricEnrolCipher()
                            if (cipher == null) Toast.makeText(ctx, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                            else showBiometricPrompt(act, "Turn on fingerprint unlock", cipher, subtitle = "Confirm your fingerprint") { unlocked ->
                                if (!vault.finishBiometricEnrol(unlocked)) {
                                    Toast.makeText(ctx, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else if (!on) vault.disableBiometric()
                    }
                    val afters = listOf(0, 60, 300, 900, 3600, -1)
                    ChoiceItem(
                        Icons.Outlined.Timer, "Lock again",
                        listOf("As soon as you leave the app", "After 1 minute away", "After 5 minutes away", "After 15 minutes away", "After 1 hour away", "Only when the app restarts"),
                        afters.indexOf(lock.lockTimeoutSeconds).coerceAtLeast(0),
                    ) { lock.lockTimeoutSeconds = afters[it] }
                }
                ToggleItem(
                    Icons.Outlined.VisibilityOff, "Hide in screenshots and recent apps",
                    "Screenshots, screen recordings and the recent apps view show a blank screen", lock.hideContent,
                ) { lock.hideContent = it }
            }
        }

        item {
            SettingsSection("Files") {
                ToggleItem(Icons.Outlined.Visibility, "Show hidden files", "Names starting with a dot", s.showHidden) { s.showHidden = it }
                ItemDivider()
                ToggleItem(Icons.AutoMirrored.Outlined.Sort, "Folders first", "Keep folders above files when sorting", s.foldersFirst) { s.foldersFirst = it }
                ItemDivider()
                ToggleItem(Icons.Outlined.Delete, "Confirm before deleting", null, s.confirmDelete) { s.confirmDelete = it }
                ItemDivider()
                ToggleItem(
                    Icons.Outlined.Image, "Thumbnails on network locations",
                    "Load image previews over the network (images up to 25 MB)", s.nfsThumbnails,
                ) { s.nfsThumbnails = it }
            }
        }

        item {
            SettingsSection("Permissions") {
                NavItem(
                    Icons.Outlined.FolderSpecial, "All files access",
                    if (allFiles) "Allowed: this phone's storage can be browsed" else "Needed to browse this phone's storage; tap to allow",
                ) {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); try { ctx.startActivity(i) } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
                ItemDivider()
                NavItem(
                    Icons.Outlined.BatteryChargingFull, "Background activity",
                    if (unrestricted) "Unrestricted, so long copies keep going with the screen off"
                    else "Restricted. ColorOS and other phones may pause long copies in the background; tap to allow",
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
            SettingsSection("Other apps") {
                NavItem(
                    Icons.Outlined.Share, "Network locations in other apps",
                    "Saved servers appear in Android's file picker (\"Browse\" in any app that opens files), and files opened from FilesCtrl stream straight from the server.",
                ) {}
            }
        }

        item {
            fun open(url: String) {
                com.vknxworks.filesctrl.ui.lock.AppLockState.expectReturn(); runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingsSection(stringResource(R.string.group_about)) {
                NavItem(Icons.Filled.Favorite, stringResource(R.string.support_title), stringResource(R.string.support_subtitle), trailing = chevron) { open(SPONSOR_URL) }
                ItemDivider()
                NavItem(Icons.Filled.BugReport, stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), trailing = chevron) { open(ISSUES_URL) }
                ItemDivider()
                NavItem(Icons.Filled.Code, stringResource(R.string.source_title), stringResource(R.string.source_subtitle), trailing = chevron) { open(REPO_URL) }
                ItemDivider()
                NavItem(Icons.Filled.Gavel, stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), trailing = chevron) { open(LICENCE_URL) }
            }
        }

        item {
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

}

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FilesCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/FilesCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** In the same order as every Ctrl app. */
private val THEMES = listOf(ThemeMode.System, ThemeMode.Dark, ThemeMode.Light, ThemeMode.Black)
