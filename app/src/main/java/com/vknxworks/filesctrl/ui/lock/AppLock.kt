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

@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    val canBio = remember(AppLockState.lockCount) { vm.fingerprintReady(context) } && activity != null
    var bioMessage by remember { mutableStateOf<String?>(null) }

    val askBiometric: () -> Unit = {
        val cipher = vm.vault.biometricUnlockCipher()
        if (cipher == null) {
            // Invalidated by a newly added fingerprint: the vault turned fingerprint unlock off.
            if (!vm.vault.biometricEnabled) bioMessage = "A fingerprint was added or removed. Unlock with your PIN, then turn fingerprint unlock on again."
        } else if (activity != null) runCatching {
            showBiometricPrompt(activity, "Unlock FilesCtrl", cipher, onError = { bioMessage = it }) { unlockedCipher ->
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
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
        }
    }

    fun press(d: Char) {
        if (vm.lockoutRemaining > 0 || vm.checking || pin.length >= PIN_MAX) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pin += d
        if (pin.length == vm.pinLength) vm.submitPin(pin, wrong)
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("FilesCtrl is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val err = vm.pinError != null || vm.lockoutRemaining > 0 || bioMessage != null
            Text(
                when {
                    vm.lockoutRemaining > 0 -> "Too many tries. Try again in ${(vm.lockoutRemaining + 999) / 1000} s"
                    vm.pinError != null -> vm.pinError!!
                    bioMessage != null -> bioMessage!!
                    vm.vault.biometricEnabled && vm.vault.pinRequired() -> "Enter your PIN. It's needed after a restart and every 72 hours."
                    else -> "Enter your PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (err) cs.error else cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) { PinDots(pin.length, if (vm.pinLength > 0) vm.pinLength else PIN_MIN) }
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= LEGACY_PIN_MIN) vm.submitPin(pin, wrong) },
                confirmEnabled = pin.length >= LEGACY_PIN_MIN && !vm.checking && vm.lockoutRemaining == 0L,
            )
            Spacer(Modifier.height(20.dp))
            if (canBio) {
                FilledTonalButton(onClick = askBiometric) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = onForgot) { Text("Forgot PIN?") }
        }
    }
}

@Composable
fun PinDots(count: Int, minDots: Int = PIN_MIN) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(count, minDots)) { i ->
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < count) cs.primary else cs.surfaceContainerHighest)
            )
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onConfirm: () -> Unit, confirmEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { row.forEach { d -> DigitKey(d.toString()) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
            }
            DigitKey("0") { onDigit('0') }
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.size(72.dp)) { Text("OK") }
        }
    }
}

@Composable
private fun DigitKey(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp).pressScale(source, 0.9f),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}
