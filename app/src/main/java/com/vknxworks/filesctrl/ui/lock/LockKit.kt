package com.vknxworks.filesctrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.filesctrl.data.lock.LockVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// The look and flow of the shared Ctrl series app lock (as in MemoCtrl): the lock screen, choosing,
// changing and confirming the app PIN. Each app keeps its own colours through its theme.

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }

/** The PIN screen shown on top of the app (from Settings), or null. Cleared when the app locks. */
object PinRequests {
    var request by mutableStateOf<PinRequest?>(null)
}

/** PINs that are too easy to guess: one digit repeated, or a straight run up or down. */
fun isObviousPin(pin: String): Boolean =
    pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)

internal tailrec fun Context.lockActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.lockActivity()
    else -> null
}

/** The wrong-PIN shake and buzz. */
internal fun CoroutineScope.shakePin(shake: Animatable<Float, AnimationVector1D>, haptics: HapticFeedback) {
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
}

/** "Too many tries" with the seconds left. */
fun lockoutText(ms: Long) = "Too many tries. Try again in ${(ms + 999) / 1000} s"

@Composable
fun LockBadge() {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
}

/** The "Use fingerprint" and "Forgot PIN?" buttons under the lock screen's PIN pad. */
@Composable
fun LockScreenButtons(showFingerprint: Boolean, onFingerprint: () -> Unit, onForgot: () -> Unit) {
    if (showFingerprint) {
        TextButton(onClick = onFingerprint) {
            Icon(Icons.Outlined.Fingerprint, null)
            Spacer(Modifier.size(8.dp))
            Text("Use fingerprint")
        }
    }
    TextButton(onClick = onForgot) { Text("Forgot PIN?") }
}

/** Asks before "Forgot PIN?" resets the app. [body] says what's removed. */
@Composable
fun ForgotPinDialog(body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot your PIN?") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Choosing a PIN (twice), changing it (current, then new twice) or confirming it to turn the lock off.
 * [onDone] runs in a coroutine; a failure shows "Couldn't save the PIN".
 */
@Composable
fun PinSetupScreen(
    vault: LockVault,
    title: String,
    intro: String,
    verifyCurrent: Boolean,
    cancellable: Boolean,
    onDone: suspend (String) -> Unit,
    onCancel: () -> Unit,
    verifyOnly: Boolean = false,
) {
    val activity = LocalContext.current.lockActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    // 0 = current PIN, 1 = new PIN, 2 = repeat new PIN
    var step by remember { mutableIntStateOf(if (verifyCurrent) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    BackHandler { if (cancellable) onCancel() else activity?.moveTaskToBack(true) }

    fun wrong(text: String) {
        message = text
        pin = ""
        scope.shakePin(shake, haptics)
    }

    fun finish(newPin: String) {
        busy = true
        scope.launch {
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong("Couldn't save the PIN. Try again.") }
        }
    }

    fun next() {
        if (busy || lockout > 0) return
        when (step) {
            0 -> {
                if (pin.length < LockVault.PIN_MIN) return
                val entered = pin
                busy = true
                scope.launch {
                    // Slow on purpose (it makes guessing slow too), and counts towards the wrong-PIN wait.
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    if (result == LockVault.PinResult.Ok) {
                        pin = ""
                        message = null
                        if (verifyOnly) finish(entered) else step = 1
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong("Wrong PIN")
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (isObviousPin(pin)) {
                    wrong("Too easy to guess. Try a less obvious PIN.")
                    return
                }
                first = pin
                pin = ""
                message = null
                step = 2
            }
            else -> {
                if (pin != first) {
                    first = ""
                    step = 1
                    wrong("The PINs didn't match. Start again.")
                    return
                }
                finish(pin)
            }
        }
    }

    PinPadScreen(
        title = title,
        message = when {
            lockout > 0 -> lockoutText(lockout)
            message != null -> message!!
            step == 0 -> "Enter your current PIN"
            step == 1 -> intro
            else -> "Enter the same PIN again"
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = busy,
        enabled = !busy && lockout == 0L,
        onDigit = { d -> if (pin.length < LockVault.PIN_MAX) { message = null; pin += d } },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::next,
        showConfirm = pin.length >= LockVault.PIN_MIN,
        confirmLabel = if (step == 2 || (step == 0 && verifyOnly)) "Done" else "Next",
        bottom = { if (cancellable) TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** The PIN pad screen every lock screen and PIN step uses. */
@Composable
fun PinPadScreen(
    title: String,
    message: String,
    isError: Boolean,
    pin: String,
    shake: Float,
    checking: Boolean,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    showConfirm: Boolean,
    confirmLabel: String = "OK",
    minDots: Int = LockVault.PIN_MIN,
    bottom: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    // The PIN pad is never in screenshots, screen recordings or casting, whatever the app's setting says:
    // a recording would show which keys were pressed.
    KeepOutOfScreenshots()
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockBadge()
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) cs.error else cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxOf(pin.length, minDots)) { i ->
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) cs.primary else cs.surfaceContainerHighest))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { d ->
                            DigitKey(d.toString(), enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(d)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackspace, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(72.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
                    }
                    DigitKey("0", enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit('0')
                    }
                    TextButton(onClick = onConfirm, enabled = enabled && showConfirm, modifier = Modifier.size(72.dp)) { Text(confirmLabel) }
                }
            }
            Spacer(Modifier.height(16.dp))
            bottom()
        }
    }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}
