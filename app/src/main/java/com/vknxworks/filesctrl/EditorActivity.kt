package com.vknxworks.filesctrl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.filesctrl.ui.EditorScreen
import com.vknxworks.filesctrl.ui.FilesTheme
import com.vknxworks.filesctrl.ui.UriTextSource
import com.vknxworks.filesctrl.ui.isAppInDarkTheme
import com.vknxworks.filesctrl.ui.lock.AppLockViewModel
import com.vknxworks.filesctrl.ui.lock.LockScreen

/**
 * The text editor on its own, for "Open with" / "Edit with" from other apps (file managers, mail, chat, the
 * system Files app). Saves back to the same file when the other app allows it; otherwise offers a copy.
 */
class EditorActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        // Keep the file's contents out of the recent-apps thumbnail.
        setRecentsScreenshotEnabled(false)
        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, "No file to open", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Any app can start the editor. FilesCtrl may read (and write) more than the sender can, so only accept
        // content:// URIs, and file:// paths in shared storage, never FilesCtrl's own private files.
        if (!acceptable(uri)) {
            Toast.makeText(this, "FilesCtrl can't open this file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Editing in place needs write access: an "Edit" request, a write grant, or a plain file path.
        val writable = intent.action == Intent.ACTION_EDIT ||
            (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0 ||
            uri.scheme == "file"
        val from = referrer?.host?.let { pkg ->
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { null }
        }?.takeIf { it != getString(R.string.app_name) }

        enableEdgeToEdge()
        setContent {
            FilesTheme {
                val dark = isAppInDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                val secure = App.lock.pinSet && App.lock.hideContent
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val lockVm: AppLockViewModel = viewModel()
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val obs = LifecycleEventObserver { _, e ->
                        when (e) {
                            Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations) lockVm.onBackground()
                            Lifecycle.Event.ON_START -> lockVm.onForeground()
                            else -> Unit
                        }
                    }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                val source = remember { UriTextSource(this, uri, writable, from) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (lockVm.locked && App.lock.pinSet) {
                        LockScreen(vm = lockVm, onForgot = {
                            Toast.makeText(this, "Open FilesCtrl to reset the PIN", Toast.LENGTH_LONG).show()
                        })
                    } else {
                        EditorScreen(source, onBack = { finish() })
                    }
                }
            }
        }
    }

    private fun acceptable(uri: android.net.Uri): Boolean = when (uri.scheme?.lowercase()) {
        "content" -> true
        "file" -> {
            val path = uri.path
            val canonical = try { path?.let { java.io.File(it).canonicalPath } } catch (_: Exception) { null }
            canonical != null && (canonical.startsWith("/storage/") || canonical.startsWith("/sdcard/")) &&
                !canonical.startsWith(java.io.File(applicationInfo.dataDir).canonicalPath) &&
                !canonical.contains("/Android/data/")
        }
        else -> false
    }
}
