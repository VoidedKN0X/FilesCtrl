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
import com.vknxworks.filesctrl.ui.lock.obviousPin
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
            SettingsSection("Security") {
                val vault = App.vault
                ToggleItem(
                    Icons.Outlined.Lock, "App lock",
                    "Ask for a PIN of at least $PIN_MIN digits when opening the app. While it's locked, saved passwords and " +
                        "keys stay encrypted with your PIN, and your servers don't show in other apps' file pickers. " +
                        "Copies and moves that are already running finish.",
                    vault.enabled,
                ) { on ->
                    pinDialog = if (on) PinMode.New else PinMode.Disable
                }
                if (vault.enabled) {
                    ItemDivider()
                    ToggleItem(
                        Icons.Outlined.Fingerprint, "Unlock with fingerprint",
                        if (bioAvailable || vault.biometricEnabled) "Strong biometrics only. Your PIN is still needed after a restart and every 72 hours"
                        else "No secure fingerprint unlock is set up on this phone",
                        vault.biometricEnabled, enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        val act = ctx as? FragmentActivity
                        if (on && act != null) {
                            val cipher = vault.biometricEnrolCipher()
                            if (cipher == null) Toast.makeText(ctx, "Fingerprint unlock isn't available on this phone", Toast.LENGTH_SHORT).show()
                            else showBiometricPrompt(act, "Confirm to turn on", cipher) { unlocked ->
                                if (!vault.finishBiometricEnrol(unlocked)) {
                                    Toast.makeText(ctx, "Couldn't turn on fingerprint unlock", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else vault.disableBiometric()
                    }
                    ItemDivider()
                    val afters = listOf(0, 60, 300, 900, 3600, -1)
                    MenuChoiceItem(
                        Icons.Outlined.Timer, "Lock again",
                        listOf("As soon as you leave the app", "After 1 minute away", "After 5 minutes away", "After 15 minutes away", "After 1 hour away", "Only when the app restarts"),
                        afters.indexOf(lock.lockTimeoutSeconds).coerceAtLeast(0),
                    ) { lock.lockTimeoutSeconds = afters[it] }
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

    pinDialog?.let { mode -> PinSetupDialog(mode) { pinDialog = null } }
}

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FilesCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/FilesCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** A setting with more choices than fit side by side: shows the current one, tap for a menu. */
@Composable
private fun MenuChoiceItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        NavItem(icon, title, options[selected]) { open = true }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, label ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, fontWeight = if (i == selected) androidx.compose.ui.text.font.FontWeight.Bold else null) },
                    onClick = { open = false; onSelect(i) },
                )
            }
        }
    }
}

/** [Upgrade]: an old, too short PIN was just used; a new one must be chosen before the app opens. */
enum class PinMode { New, Change, Disable, Upgrade }

@Composable
fun PinSetupDialog(mode: PinMode, onDismiss: () -> Unit) {
    val vault = App.vault
    val needsCurrent = mode == PinMode.Change || mode == PinMode.Disable
    val needsNew = mode != PinMode.Disable
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun digits(v: String) = v.filter { it.isDigit() }.take(PIN_MAX)

    AlertDialog(
        onDismissRequest = { if (mode != PinMode.Upgrade) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = mode != PinMode.Upgrade,
            dismissOnClickOutside = mode != PinMode.Upgrade,
        ),
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = {
            Text(
                when (mode) {
                    PinMode.New -> "Set a PIN"
                    PinMode.Change -> "Change PIN"
                    PinMode.Disable -> "Turn off app lock"
                    PinMode.Upgrade -> "Choose a new PIN"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == PinMode.Upgrade) Text(
                    "The app lock now needs at least $PIN_MIN digits, and encrypts your saved passwords with your PIN. " +
                        "Choose a new PIN to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (mode == PinMode.New) Text(
                    "Use a PIN that's different from your phone's. If you forget it, the saved passwords and keys of your " +
                        "network locations are lost (the locations stay).",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                    // Wrong current PINs count towards the same lockout as the lock screen.
                    val check = if (needsCurrent) withContext(Dispatchers.Default) { vault.unlockWithPin(current) } else LockVault.PinResult.Ok
                    error = when {
                        check == LockVault.PinResult.LockedOut -> "Too many tries. Try again in ${(vault.lockoutRemaining() + 999) / 1000} s"
                        check != LockVault.PinResult.Ok -> "Current PIN is wrong"
                        needsNew && first.length < PIN_MIN -> "Use at least $PIN_MIN digits"
                        needsNew && first != second -> "The PINs don't match"
                        needsNew && obviousPin(first) -> "Too easy to guess. Avoid repeated digits and runs like 123456"
                        else -> null
                    }
                    if (error == null) {
                        // Unlocked first, so the lock screen doesn't flash up while the new lock is saved.
                        if (mode == PinMode.New) AppLockState.markUnlocked()
                        val saved = runCatching {
                            withContext(Dispatchers.Default) {
                                when (mode) {
                                    PinMode.New, PinMode.Upgrade -> migrate(first)
                                    PinMode.Change -> vault.changePin(first)
                                    PinMode.Disable -> disableLock()
                                }
                            }
                        }
                        if (saved.isSuccess) {
                            if (mode == PinMode.Upgrade) AppLockState.mustChoosePin = false
                            onDismiss()
                        } else error = "Couldn't save the PIN: ${saved.exceptionOrNull()?.message ?: "unknown error"}"
                    }
                    working = false
                }
            }) { Text(if (mode == PinMode.Disable) "Turn off" else "Save") }
        },
        dismissButton = { if (mode != PinMode.Upgrade) TextButton(onDismiss) { Text("Cancel") } },
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
