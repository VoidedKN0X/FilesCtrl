package com.vknxworks.filesctrl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.ops.Ops
import com.vknxworks.filesctrl.ui.BrowserScreen
import com.vknxworks.filesctrl.ui.BrowserViewModel
import com.vknxworks.filesctrl.ui.EditorScreen
import com.vknxworks.filesctrl.ui.FilesTheme
import com.vknxworks.filesctrl.ui.IslandTab
import com.vknxworks.filesctrl.ui.LocalNav
import com.vknxworks.filesctrl.ui.back
import com.vknxworks.filesctrl.ui.close
import com.vknxworks.filesctrl.ui.go
import com.vknxworks.filesctrl.ui.PlacesTab
import com.vknxworks.filesctrl.ui.ServerEditScreen
import com.vknxworks.filesctrl.ui.SettingsScreen
import com.vknxworks.filesctrl.ui.YouScreen
import com.vknxworks.filesctrl.ui.TabIsland
import com.vknxworks.filesctrl.ui.VfsTextSource
import com.vknxworks.filesctrl.ui.isAppInDarkTheme
import com.vknxworks.filesctrl.ui.lock.AppLockViewModel
import com.vknxworks.filesctrl.ui.lock.LockScreen
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.ui.lock.resetLock
import com.vknxworks.filesctrl.ui.lock.ChooseNewPinScreen
import com.vknxworks.filesctrl.ui.lock.ForgotPinDialog
import com.vknxworks.filesctrl.ui.lock.LockWindow
import androidx.compose.ui.res.stringResource
import com.vknxworks.filesctrl.ui.lock.PinRequestScreen
import com.vknxworks.filesctrl.ui.lock.PinRequests
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

// FragmentActivity (a ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        enableEdgeToEdge()
        setContent {
            FilesTheme {
                val dark = isAppInDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                val secure = App.lock.hideScreenshots || com.vknxworks.filesctrl.ui.lock.SecureScreens.active > 0
                // "Hide in recent apps", and always with the app lock on: a blank card instead of a snapshot.
                val blankRecents = App.lock.hideRecents || AppLockState.enabled
                LaunchedEffect(blankRecents) { setRecentsScreenshotEnabled(!blankRecents) }
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(this) }
            }
        }
    }

    override fun onStart() { super.onStart(); inForeground = true }
    override fun onStop() { super.onStop(); inForeground = false }

    companion object {
        @Volatile var inForeground = false
    }
}

/** Which tab is showing; other screens can switch it. */
object MainTab {
    const val FILES = 0
    const val PLACES = 1
    var selected by mutableIntStateOf(FILES)
}

@Composable
private fun Root(activity: FragmentActivity, lockVm: AppLockViewModel = viewModel()) {
    val nav = rememberNavController()
    val vm: BrowserViewModel = viewModel()
    var confirmReset by remember { mutableStateOf(false) }
    var backgroundSince by remember { mutableStateOf(0L) }

    // Lock when the app goes to the background (immediately, or after the chosen delay).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) {
                    backgroundSince = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    // Network connections often die while the app is in the background (the phone or the server
                    // drops idle ones). Start fresh instead of waiting for a dead connection to time out, then
                    // reload what's on screen. Running transfers keep their connections and recover on their own.
                    val away = backgroundSince > 0 && System.currentTimeMillis() - backgroundSince > 30_000
                    backgroundSince = 0
                    if (away && Ops.active.isEmpty()) {
                        Thread {
                            App.vfs.dropAll()
                            activity.runOnUiThread { if (vm.location.vfsId.startsWith(RemoteVfs.PREFIX)) vm.load(quiet = vm.error == null) }
                        }.start()
                    } else if (away && vm.location.vfsId.startsWith(RemoteVfs.PREFIX) && vm.error != null) {
                        vm.load()
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Ask once for notifications, used for progress of copies and moves.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !App.settings.askedNotifications) {
            App.settings.askedNotifications = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // After unlocking, show the server folder again: it couldn't connect while the passwords were locked away.
    LaunchedEffect(AppLockState.unlocked) {
        if (AppLockState.unlocked && vm.location.vfsId.startsWith(RemoteVfs.PREFIX)) vm.load(quiet = vm.error == null)
    }

    // The app stays underneath the lock screen (so a half-typed text or server form isn't lost), hidden from
    // touch, screen readers and the keyboard until it's unlocked.
    val locked = lockVm.locked
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(locked) { if (locked) { focus.clearFocus(force = true); keyboard?.hide() } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) { AppContent(nav, vm) }
        if (locked) {
            LockWindow {
                LockScreen(vm = lockVm, onForgot = { confirmReset = true })
                if (confirmReset) ResetLockDialog { confirmReset = false }
            }
        } else if (AppLockState.mustChoosePin) {
            ChooseNewPinScreen()
        } else {
            // Turning the lock on or off, or changing the PIN (from Settings, behind the profile picture), on top of the app.
            PinRequests.request?.let { PinRequestScreen(it) }
        }
    }
}

/** "Forgot PIN?" on the lock screen. */
@Composable
fun ResetLockDialog(onDismiss: () -> Unit) {
    ForgotPinDialog(
        body = stringResource(R.string.lock_forgot_body),
        confirm = stringResource(R.string.lock_forgot_confirm),
        onDismiss = onDismiss,
    ) {
        onDismiss()
        resetLock()
    }
}

@Composable
private fun AppContent(nav: androidx.navigation.NavHostController, vm: BrowserViewModel) {
    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(
            nav, startDestination = "main",
            enterTransition = { slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(220)) },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 8 } + fadeOut(tween(160)) },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 5 } + fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 8 } + fadeOut(tween(160)) },
        ) {
            composable("main") { MainShell(vm) }
            // The profile picture opens the You page; Settings opens from there.
            composable("you") { YouScreen() }
            composable("settings") { SettingsScreen() }
            composable("server/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                ServerEditScreen(id) { openId ->
                    // Guarded: a second call (double tap) or one after the screen already closed does nothing.
                    if (!nav.close("server/{id}")) return@ServerEditScreen
                    if (openId != null) {
                        vm.navigate(Location(RemoteVfs.idFor(openId), "/"))
                        MainTab.selected = MainTab.FILES
                    } else if (id != null && App.servers.get(id) == null && vm.location.vfsId == RemoteVfs.idFor(id)) {
                        // The location we were looking at was removed.
                        vm.navigate(Location(LocalVfs.ID, App.vfs.local.primaryRoot), addToHistory = false)
                    } else if (id != null && vm.location.vfsId == RemoteVfs.idFor(id)) vm.load()
                }
            }
            composable("edit?loc={loc}") { entry ->
                val raw = entry.arguments?.getString("loc").orEmpty()
                val loc = Location.fromKey(raw)
                if (loc == null) LaunchedEffect(Unit) { nav.close("edit?loc={loc}") }
                else EditorScreen(remember(loc) { VfsTextSource(loc) }, onBack = { nav.back() })
            }
        }
    }
}

/**
 * Same shell as the other Ctrl apps: on phones the tabs float in a frosted island over the content; on wide
 * windows (tablets, unfolded foldables, landscape) they sit in a side rail.
 */
@Composable
private fun MainShell(vm: BrowserViewModel) {
    val nav = LocalNav.current
    val tabs = listOf(
        IslandTab(MainTab.FILES, Icons.Outlined.Folder, "Files"),
        IslandTab(MainTab.PLACES, Icons.Outlined.Storage, "Places"),
    )

    // Back from another tab returns to Files; the Files tab handles its own back (folder history).
    BackHandler(MainTab.selected != MainTab.FILES) { MainTab.selected = MainTab.FILES }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        val content: @Composable () -> Unit = {
            AnimatedContent(MainTab.selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    MainTab.PLACES -> Box(Modifier.statusBarsPadding()) {
                        PlacesTab(
                            current = vm.location,
                            onOpen = { vm.jump(it, 0); MainTab.selected = MainTab.FILES },
                            onAddServer = { nav.go("server/new") },
                            onEditServer = { nav.go("server/$it") },
                        )
                    }
                    else -> BrowserScreen(vm, islandSpace = if (rail) 0.dp else 82.dp)
                }
            }
        }

        if (rail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(Modifier.statusBarsPadding()) {
                    Spacer(Modifier.height(8.dp))
                    tabs.forEach { t ->
                        NavigationRailItem(
                            selected = MainTab.selected == t.index,
                            onClick = { MainTab.selected = t.index },
                            icon = {
                                BadgedBox(badge = { if (t.badge > 0) Badge { Text("${t.badge}") } }) { Icon(t.icon, null) }
                            },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize().navigationBarsPadding()) { content() }
            }
        } else {
            // Content runs edge to edge under the floating island, which blurs it.
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) { content() }
                TabIsland(
                    tabs = tabs,
                    selected = MainTab.selected,
                    hazeState = haze,
                    onSelect = { MainTab.selected = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
