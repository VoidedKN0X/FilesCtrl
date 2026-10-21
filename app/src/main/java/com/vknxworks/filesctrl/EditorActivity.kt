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
import com.vknxworks.filesctrl.ui.lock.AppLockState
import com.vknxworks.filesctrl.ui.lock.ChooseNewPinScreen
import com.vknxworks.filesctrl.ui.lock.LockWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics

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
                val secure = App.lock.hideContent || com.vknxworks.filesctrl.ui.lock.SecureScreens.active > 0
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val lockVm: AppLockViewModel = viewModel()
                val source = remember { UriTextSource(this, uri, writable, from) }
                val locked = lockVm.locked
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(locked) { if (locked) { focus.clearFocus(force = true); keyboard?.hide() } }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The editor stays underneath the lock screen, so unsaved text isn't lost.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier),
                        ) { EditorScreen(source, onBack = { finish() }) }
                        if (locked) {
                            LockWindow {
                                LockScreen(vm = lockVm, onForgot = {
                                    Toast.makeText(this@EditorActivity, "Open FilesCtrl to reset the PIN", Toast.LENGTH_LONG).show()
                                })
                            }
                        } else if (AppLockState.mustChoosePin) {
                            ChooseNewPinScreen()
                        }
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
            // Shared storage ignores letter case, so "/ANDROID/DATA/" must be caught as well.
            val lower = canonical?.lowercase()
            canonical != null && lower != null && (lower.startsWith("/storage/") || lower.startsWith("/sdcard/")) &&
                !canonical.startsWith(java.io.File(applicationInfo.dataDir).canonicalPath) &&
                !lower.contains("/android/data/") && !lower.contains("/android/obb/")
        }
        else -> false
    }
}
