package com.vknxworks.filesctrl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vknxworks.filesctrl.fs.Location
import com.vknxworks.filesctrl.fs.RemoteVfs
import com.vknxworks.filesctrl.ui.BrowserScreen
import com.vknxworks.filesctrl.ui.BrowserViewModel
import com.vknxworks.filesctrl.ui.DrawerContent
import com.vknxworks.filesctrl.ui.EditorScreen
import com.vknxworks.filesctrl.ui.FilesTheme
import com.vknxworks.filesctrl.ui.LocalNav
import com.vknxworks.filesctrl.ui.ServerEditScreen
import com.vknxworks.filesctrl.ui.SettingsScreen
import com.vknxworks.filesctrl.ui.TasksSheet
import com.vknxworks.filesctrl.ui.isAppInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }

    override fun onStart() { super.onStart(); inForeground = true }
    override fun onStop() { super.onStop(); inForeground = false }

    companion object {
        @Volatile var inForeground = false
    }
}

@Composable
private fun Root() {
    val nav = rememberNavController()
    val vm: BrowserViewModel = viewModel()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tasksOpen by remember { mutableStateOf(false) }

    // Ask once for notifications, used for progress of copies and moves.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !App.settings.askedNotifications) {
            App.settings.askedNotifications = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(
            nav, startDestination = "browser",
            enterTransition = { slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(200)) },
            exitTransition = { slideOutHorizontally(tween(280)) { -it / 8 } + fadeOut(tween(150)) },
            popEnterTransition = { slideInHorizontally(tween(280)) { -it / 5 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(280)) { it / 8 } + fadeOut(tween(150)) },
        ) {
            composable("browser") { BrowserWithDrawer(vm, onTasks = { tasksOpen = true }) }
            composable("server/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                ServerEditScreen(id) { openId ->
                    nav.popBackStack()
                    if (openId != null) vm.navigate(Location(RemoteVfs.idFor(openId), "/"))
                    else if (id != null && App.servers.get(id) == null && vm.location.vfsId == RemoteVfs.idFor(id)) {
                        // The share we were looking at was removed.
                        vm.navigate(Location(com.vknxworks.filesctrl.fs.LocalVfs.ID, App.vfs.local.primaryRoot), addToHistory = false)
                    } else if (id != null && vm.location.vfsId == RemoteVfs.idFor(id)) vm.load()
                }
            }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("edit?loc={loc}") { entry ->
                val raw = entry.arguments?.getString("loc").orEmpty()
                val loc = Location.fromKey(raw)
                if (loc == null) LaunchedEffect(Unit) { nav.popBackStack() }
                else EditorScreen(loc, onBack = { nav.popBackStack() })
            }
        }
    }
    if (tasksOpen) TasksSheet { tasksOpen = false }
}

@Composable
private fun BrowserWithDrawer(vm: BrowserViewModel, onTasks: () -> Unit) {
    val nav = LocalNav.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val permanent = maxWidth >= 840.dp
        val content: @Composable (closeDrawer: () -> Unit) -> Unit = { close ->
            DrawerContent(
                current = vm.location,
                onOpen = { close(); vm.jump(it, 0) },
                onAddServer = { close(); nav.navigate("server/new") },
                onEditServer = { close(); nav.navigate("server/$it") },
                onSettings = { close(); nav.navigate("settings") },
                onTasks = { close(); onTasks() },
            )
        }
        if (permanent) {
            Row(Modifier.fillMaxSize()) {
                PermanentDrawerSheet(Modifier.width(320.dp)) { content {} }
                VerticalDivider()
                BrowserScreen(vm, showMenuButton = false, openDrawer = {})
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.width(320.dp)) { content { scope.launch { drawer.close() } } }
                },
            ) {
                BrowserScreen(vm, showMenuButton = true, openDrawer = { scope.launch { drawer.open() } })
                // Registered after the browser's handler, so it wins while the drawer is open.
                BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            }
        }
    }
}
