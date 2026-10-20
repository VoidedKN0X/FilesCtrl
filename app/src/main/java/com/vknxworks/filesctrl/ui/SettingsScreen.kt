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
import com.vknxworks.filesctrl.ui.lock.clearBiometricKey
import com.vknxworks.filesctrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsTab() {
    val s = App.settings
    val lock = App.lock
    val ctx = LocalContext.current
    var pinDialog by remember { mutableStateOf<PinMode?>(null) }
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
                    when (s.theme) { ThemeMode.Light -> Icons.Outlined.LightMode; ThemeMode.Dark -> Icons.Outlined.DarkMode; ThemeMode.System -> Icons.Outlined.Contrast },
                    "Theme", listOf("System", "Light", "Dark"), s.theme.ordinal,
                ) { s.theme = ThemeMode.entries[it] }
                ItemDivider()
                ToggleItem(Icons.Outlined.Palette, "Material You colours", "Match the app to your wallpaper", s.dynamicColor) { s.dynamicColor = it }
                ItemDivider()
                ToggleItem(Icons.Outlined.DarkMode, "Pure black", "True black backgrounds in dark mode", s.pureBlack) { s.pureBlack = it }
                ItemDivider()
                ToggleItem(Icons.Outlined.GridView, "Grid view", "Show files as tiles instead of a list", s.gridView) { s.gridView = it }
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
                    try { ctx.startActivity(i) } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
                ItemDivider()
                NavItem(
                    Icons.Outlined.BatteryChargingFull, "Background activity",
                    if (unrestricted) "Unrestricted, so long copies keep going with the screen off"
                    else "Restricted. ColorOS and other phones may pause long copies in the background; tap to allow",
                ) {
                    if (!unrestricted) {
                        batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    } else {
                        batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        }

        item {
            SettingsSection("Security") {
                ToggleItem(Icons.Outlined.Lock, "App lock", "Ask for a PIN when opening the app", lock.pinSet) { on ->
                    pinDialog = if (on) PinMode.New else PinMode.Disable
                }
                if (lock.pinSet) {
                    ItemDivider()
                    ToggleItem(
                        Icons.Outlined.Fingerprint, "Unlock with fingerprint",
                        if (bioAvailable) "Your PIN still works as a backup" else "No secure fingerprint unlock is set up on this phone",
                        lock.biometricEnabled, enabled = bioAvailable,
                    ) { on ->
                        val act = ctx as? FragmentActivity
                        if (on && act != null) showBiometricPrompt(
                            act, "Confirm to turn on", enrol = true,
                            onUnavailable = { Toast.makeText(ctx, "Fingerprint unlock isn't available on this phone", Toast.LENGTH_SHORT).show() },
                        ) { lock.biometricEnabled = true }
                        else {
                            lock.biometricEnabled = false
                            clearBiometricKey()
                        }
                    }
                    ItemDivider()
                    val afters = listOf(0, 30, 60, 300)
                    ChoiceItem(
                        Icons.Outlined.Timer, "Lock again after leaving the app", listOf("Instantly", "30 s", "1 min", "5 min"),
                        afters.indexOf(lock.lockAfterSeconds).coerceAtLeast(0),
                    ) { lock.lockAfterSeconds = afters[it] }
                    ItemDivider()
                    ToggleItem(
                        Icons.Outlined.VisibilityOff, "Hide from recents & screenshots",
                        "Blank preview in the app switcher; blocks screenshots and screen recording", lock.hideContent,
                    ) { lock.hideContent = it }
                    ItemDivider()
                    NavItem(Icons.Outlined.Password, "Change PIN") { pinDialog = PinMode.Change }
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
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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

    pinDialog?.let { mode -> PinSetupDialog(mode) { pinDialog = null } }
}

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FilesCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/FilesCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

enum class PinMode { New, Change, Disable }

@Composable
private fun PinSetupDialog(mode: PinMode, onDismiss: () -> Unit) {
    val lock = App.lock
    val needsCurrent = mode != PinMode.New
    val needsNew = mode != PinMode.Disable
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun digits(v: String) = v.filter { it.isDigit() }.take(PIN_MAX)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(when (mode) { PinMode.New -> "Set a PIN"; PinMode.Change -> "Change PIN"; PinMode.Disable -> "Turn off app lock" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsCurrent) PinField("Current PIN", current) { current = digits(it) }
                if (needsNew) {
                    PinField("New PIN ($PIN_MIN–$PIN_MAX digits)", first) { first = digits(it) }
                    PinField("Repeat new PIN", second) { second = digits(it) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                scope.launch {
                    working = true
                    val currentOk = !needsCurrent || withContext(Dispatchers.Default) { lock.checkPin(current) }
                    error = when {
                        !currentOk -> "Current PIN is wrong"
                        needsNew && first.length < PIN_MIN -> "Use at least $PIN_MIN digits"
                        needsNew && first != second -> "The PINs don't match"
                        needsNew && first.toSet().size == 1 -> "Pick a PIN that isn't one repeated digit"
                        else -> null
                    }
                    if (error == null) {
                        withContext(Dispatchers.Default) { if (needsNew) lock.setPin(first) else lock.clearPin() }
                        onDismiss()
                    }
                    working = false
                }
            }) { Text(if (mode == PinMode.Disable) "Turn off" else "Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
