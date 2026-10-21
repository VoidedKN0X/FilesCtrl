package com.vknxworks.filesctrl.ui.lock

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import com.vknxworks.filesctrl.data.lock.LockVault
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.ui.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.math.roundToInt

const val PIN_MIN = LockVault.PIN_MIN
const val PIN_MAX = LockVault.PIN_MAX

/** Old PINs (FilesCtrl 1.1.4 and older) could be 4 digits; they're accepted once, then a new one is chosen. */
const val LEGACY_PIN_MIN = 4

/** Only class-3 ("strong") biometrics — weak face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** A PIN that's too easy to guess: one repeated digit, or a straight run up or down. */
fun obviousPin(pin: String): Boolean =
    pin.toSet().size == 1 || "01234567890123".contains(pin) || "98765432109876".contains(pin)

/** The fingerprint key of FilesCtrl 1.1.4 and older; deleted when the lock moves to the vault. */
fun clearLegacyBiometricKey() {
    runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("filesctrl_biometric_unlock") }
}

/**
 * Shows the fingerprint prompt for [cipher], a vault Keystore key that only works right after a strong match.
 * [onSuccess] gets the unlocked cipher, so a hooked "success" callback alone can't open the app.
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    cipher: Cipher,
    subtitle: String? = null,
    onError: (String?) -> Unit = {},
    onSuccess: (Cipher) -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onError("Fingerprint didn't unlock the key")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (cancelled) null else errString.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build(),
        BiometricPrompt.CryptoObject(cipher),
    )
}

/**
 * Whether FilesCtrl is unlocked, for the whole app (file browser, text editor, the file picker's view of your
 * servers). A fresh process always starts locked. Locking wipes the vault key and every saved password from memory.
 */
object AppLockState {
    var unlocked by mutableStateOf(false)
        private set

    /** Goes up on every lock, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /** Unlocked with an old (FilesCtrl 1.1.4) PIN that's too short or too easy: a new PIN must be chosen now. */
    var mustChoosePin by mutableStateOf(false)

    /** The app lock is on (a PIN is set, old or new). */
    val enabled: Boolean get() = App.vault.enabled || App.lock.pinSet

    private val handler = Handler(Looper.getMainLooper())
    private val lockTask = Runnable { lock() }
    private var backgroundAt = 0L
    private var exemptTrip = false
    private var expectReturnAt = 0L

    /**
     * FilesCtrl itself is opening another app (open with…, share, a file or folder picker, importing a key).
     * Leaving for it doesn't lock right away, so a video keeps streaming from your server and you're not asked
     * again when you come back. Coming back after more than 5 minutes (or the chosen time, if longer) still locks.
     */
    fun expectReturn() { expectReturnAt = SystemClock.elapsedRealtime() }

    fun onBackground() {
        handler.removeCallbacks(lockTask)
        val now = SystemClock.elapsedRealtime()
        exemptTrip = expectReturnAt > 0 && now - expectReturnAt < 5_000
        expectReturnAt = 0L
        backgroundAt = now
        if (!enabled || !unlocked) return
        val timeout = App.lock.lockTimeoutSeconds
        if (exemptTrip || timeout < 0) return
        // Locks while in the background, so the key isn't kept in memory longer than chosen.
        if (timeout == 0) lock() else handler.postDelayed(lockTask, timeout * 1000L)
    }

    fun onForeground() {
        handler.removeCallbacks(lockTask)
        val since = backgroundAt
        backgroundAt = 0L
        if (!enabled || !unlocked || since == 0L) return
        val timeout = App.lock.lockTimeoutSeconds
        if (timeout < 0) return
        val limit = if (exemptTrip) maxOf(timeout, EXEMPT_TRIP_SECONDS) else timeout
        if (SystemClock.elapsedRealtime() - since >= limit * 1000L) lock()
    }

    fun lock() {
        handler.removeCallbacks(lockTask)
        if (!enabled || !unlocked) return
        unlocked = false
        mustChoosePin = false
        PinRequests.request = null
        lockCount++
        App.onLocked()
    }

    fun markUnlocked() {
        backgroundAt = 0L
        unlocked = true
    }

    private const val EXEMPT_TRIP_SECONDS = 300
}

/** Lock screen state for one activity. The locked/unlocked state itself is [AppLockState], shared by the app. */
class AppLockViewModel : ViewModel() {
    val settings = App.lock
    val vault = App.vault

    val locked: Boolean get() = AppLockState.enabled && !AppLockState.unlocked
    var pinError by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    private var ticker: kotlinx.coroutines.Job? = null

    init { refreshLockout() }

    /** Digits that complete the PIN, so it's checked without pressing OK. 0 = unknown. */
    val pinLength: Int get() = if (vault.enabled) vault.pinLength else settings.pinLength

    /** The fingerprint can be used now (strong sensor, turned on, PIN not due after a restart or 72 hours). */
    fun fingerprintReady(context: Context) = vault.biometricEnabled && !vault.pinRequired() && biometricAvailable(context)

    /** Typing again clears "Wrong PIN". */
    fun clearError() {
        pinError = null
    }

    fun unlocked() {
        pinError = null
        AppLockState.markUnlocked()
        App.onUnlocked()
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        if (checking || currentLockout() > 0) return
        checking = true
        viewModelScope.launch {
            // Slow on purpose (it makes guessing slow too), so off the main thread.
            val ok = withContext(Dispatchers.Default) { if (vault.enabled) checkVault(pin) else checkLegacy(pin) }
            checking = false
            if (ok) { unlocked(); return@launch }
            refreshLockout()
            pinError = "Wrong PIN"
            onWrong()
        }
    }

    private fun checkVault(pin: String): Boolean = vault.unlockWithPin(pin) == LockVault.PinResult.Ok

    /**
     * The PIN of FilesCtrl 1.1.4 and older. When it's right, the lock moves to the vault: with a strong enough
     * PIN right away; otherwise the app opens once and asks for a new PIN of at least $PIN_MIN digits.
     */
    private fun checkLegacy(pin: String): Boolean {
        val fails = settings.failedAttempts + 1
        settings.failedAttempts = fails
        if (!settings.checkPin(pin)) {
            if (fails >= MAX_TRIES) {
                val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
                settings.startLockout(wait)
            }
            return false
        }
        settings.failedAttempts = 0
        if (pin.length >= PIN_MIN && !obviousPin(pin)) migrate(pin) else AppLockState.mustChoosePin = true
        return true
    }

    private fun currentLockout(): Long = if (vault.enabled) vault.lockoutRemaining() else settings.lockoutRemaining()

    // Uses the monotonic clock, so changing the phone's time can't end it.
    private fun refreshLockout() {
        lockoutRemaining = currentLockout()
        if (lockoutRemaining <= 0 || ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val left = currentLockout()
                lockoutRemaining = left
                if (left <= 0) break
                delay(500)
            }
        }
    }

    private companion object { const val MAX_TRIES = 5 }
}

/**
 * Turns the lock on with [pin] (or moves an old lock to it): makes the vault, encrypts the saved passwords with it,
 * and deletes the old PIN hash and fingerprint key. Slow; call off the main thread.
 */
fun migrate(pin: String) {
    App.vault.setUp(pin)
    App.servers.sealAll()
    if (App.lock.pinSet) App.lock.clearPin()
    clearLegacyBiometricKey()
}

/** Turns the lock off (the PIN was just confirmed): saved passwords go back to Keystore-only encryption. */
fun disableLock() {
    App.servers.unsealAll()
    App.vault.disable()
    if (App.lock.pinSet) App.lock.clearPin()
    clearLegacyBiometricKey()
}

/** "Forgot PIN?": the saved passwords and keys can't be decrypted any more, so they're forgotten with the PIN. */
fun resetLock() {
    App.servers.forgetSecrets()
    App.vault.disable()
    if (App.lock.pinSet) App.lock.clearPin()
    clearLegacyBiometricKey()
    AppLockState.mustChoosePin = false
    AppLockState.markUnlocked()
    App.notifyRoots()
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?" (the same look and flow as every Ctrl app). */
@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    val context = LocalContext.current
    // The lock is drawn in its own window (a dialog), whose context wraps the activity.
    val activity = remember(context) { context.lockActivity() as? FragmentActivity }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    val canBio = remember(AppLockState.lockCount) { vm.fingerprintReady(context) } && activity != null
    var bioMessage by remember { mutableStateOf<String?>(null) }
    val legacy = !vm.vault.enabled

    val askBiometric: () -> Unit = {
        val cipher = vm.vault.biometricUnlockCipher()
        if (cipher == null) {
            // Invalidated by a newly added fingerprint: the vault turned fingerprint unlock off.
            if (!vm.vault.biometricEnabled) bioMessage = "A fingerprint was added or removed. Unlock with your PIN, then turn fingerprint unlock on again."
        } else if (activity != null) runCatching {
            showBiometricPrompt(activity, "Unlock FilesCtrl", cipher, subtitle = "Confirm it's you to see your files", onError = { bioMessage = it }) { unlockedCipher ->
                if (vm.vault.unlockWithBiometric(unlockedCipher)) vm.unlocked()
                else bioMessage = "Couldn't unlock with your fingerprint. Use your PIN."
            }
        }
    }
    LaunchedEffect(AppLockState.lockCount) { if (canBio) askBiometric() }
    // Back leaves the app rather than revealing anything underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    val wrong: () -> Unit = {
        pin = ""
        scope.shakePin(shake, haptics)
    }
    val min = if (legacy) LEGACY_PIN_MIN else PIN_MIN

    PinPadScreen(
        title = "FilesCtrl is locked",
        message = when {
            vm.lockoutRemaining > 0 -> lockoutText(vm.lockoutRemaining)
            vm.pinError != null -> vm.pinError!!
            bioMessage != null -> bioMessage!!
            legacy -> "Enter your PIN to finish the security update"
            vm.vault.biometricEnabled && vm.vault.pinRequired() -> "Enter your PIN. It's needed after a restart and every 72 hours."
            else -> "Enter your PIN"
        },
        isError = vm.pinError != null || vm.lockoutRemaining > 0 || bioMessage != null,
        pin = pin,
        shake = shake.value,
        checking = vm.checking,
        enabled = vm.lockoutRemaining == 0L && !vm.checking,
        onDigit = { d ->
            if (pin.length < PIN_MAX) {
                bioMessage = null
                vm.clearError()
                pin += d
                if (pin.length == vm.pinLength) vm.submitPin(pin, wrong)
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = { if (pin.length >= min) vm.submitPin(pin, wrong) },
        showConfirm = pin.length >= min && pin.length != vm.pinLength,
        minDots = if (legacy) maxOf(min, vm.pinLength) else PIN_MIN,
        bottom = { LockScreenButtons(showFingerprint = canBio, onFingerprint = askBiometric, onForgot = onForgot) },
    )
}

/** After unlocking with an old PIN that's too short or too easy: choose a new one (twice) before the app opens. */
@Composable
fun ChooseNewPinScreen() {
    PinSetupScreen(
        vault = App.vault,
        title = "Choose an app PIN",
        intro = "PINs now need at least $PIN_MIN digits, different from your phone's PIN. " +
            "Your saved passwords and keys are encrypted with it while the app is locked.",
        verifyCurrent = false,
        cancellable = false,
        onDone = { pin ->
            withContext(Dispatchers.Default) { migrate(pin) }
            AppLockState.mustChoosePin = false
        },
        onCancel = {},
    )
}

/** Turning the lock on or off, or changing the PIN, from Settings: a PIN screen on top of the app. */
@Composable
fun PinRequestScreen(request: PinRequest) {
    val vault = App.vault
    val done = { PinRequests.request = null }
    when (request) {
        PinRequest.ENABLE -> PinSetupScreen(
            vault = vault,
            title = "Choose an app PIN",
            intro = "At least $PIN_MIN digits, different from your phone's PIN. If you forget it, the saved passwords and " +
                "keys of your network locations are lost (the locations stay).",
            verifyCurrent = false,
            cancellable = true,
            onDone = { pin ->
                // Unlocked first, so the lock screen doesn't flash up while the new lock is saved.
                AppLockState.markUnlocked()
                withContext(Dispatchers.Default) { migrate(pin) }
                done()
            },
            onCancel = done,
        )
        PinRequest.CHANGE -> PinSetupScreen(
            vault = vault,
            title = "Change app PIN",
            intro = "At least $PIN_MIN digits, different from your phone's PIN.",
            verifyCurrent = true,
            cancellable = true,
            // Same key, new PIN: fingerprint unlock keeps working.
            onDone = { pin -> withContext(Dispatchers.Default) { check(vault.changePin(pin)) }; done() },
            onCancel = done,
        )
        PinRequest.DISABLE -> PinSetupScreen(
            vault = vault,
            title = "Turn off app lock",
            intro = "Enter your app PIN to turn the lock off.",
            verifyCurrent = true,
            verifyOnly = true,
            cancellable = true,
            onDone = { withContext(Dispatchers.Default) { disableLock() }; done() },
            onCancel = done,
        )
    }
}

/** The lock screen in a full-screen window of its own, so dialogs, menus and sheets that were open can't show above it. */
@Composable
fun LockWindow(content: @Composable () -> Unit) {
    // Opaque cover in the app's own window (also behind the system bars).
    Surface(Modifier.fillMaxSize().clearAndSetSemantics { }, color = MaterialTheme.colorScheme.background) {}
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) { content() }
}
