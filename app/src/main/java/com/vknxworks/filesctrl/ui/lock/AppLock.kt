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

const val PIN_MIN = 4
const val PIN_MAX = 8

/** Only class-3 ("strong") biometrics — weak face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * A Keystore key that can only be used right after a strong biometric match. Unlocking runs a cipher operation
 * with it inside the prompt's CryptoObject, so a hooked "success" callback alone can't open the app. Enrolling a
 * new fingerprint invalidates the key, which turns fingerprint unlock off until it's confirmed again.
 */
private object BiometricKey {
    private const val ALIAS = "filesctrl_biometric_unlock"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun create() {
        delete()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // 0 = a fresh biometric match for every single use.
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        gen.generateKey()
    }

    fun delete() { runCatching { keyStore().deleteEntry(ALIAS) } }

    /** A cipher ready to be unlocked by the prompt, or null when the key is missing or was invalidated. */
    fun cipher(): Cipher? {
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            null
        } catch (e: Exception) {
            null
        }
    }
}

/** Forgets the fingerprint key (when app lock or fingerprint unlock is turned off). */
fun clearBiometricKey() = BiometricKey.delete()

/**
 * Shows the fingerprint prompt bound to the biometric Keystore key. [onSuccess] runs only if the key could
 * actually be used after the match. With [enrol] a fresh key is made first (turning the option on); otherwise a
 * missing or invalidated key calls [onUnavailable] instead of prompting.
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    enrol: Boolean = false,
    onUnavailable: () -> Unit = {},
    onSuccess: () -> Unit,
) {
    if (enrol) runCatching { BiometricKey.create() }.onFailure { onUnavailable(); return }
    val cipher = BiometricKey.cipher() ?: run { onUnavailable(); return }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                val ok = runCatching { c.doFinal(ByteArray(16).also { SecureRandom().nextBytes(it) }) }.isSuccess
                if (ok) onSuccess()
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

/** Whether the app is locked. Survives rotation; a fresh process always starts locked. */
class AppLockViewModel : ViewModel() {
    val settings = App.lock

    var locked by mutableStateOf(settings.pinSet)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    /** Set once the lock screen has shown the biometric prompt, so rotation doesn't show it twice. */
    var biometricShown = false

    private var backgroundedAt = 0L
    private var ticker: kotlinx.coroutines.Job? = null

    init { refreshLockout() }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        // "Immediately" locks before the app is hidden, so your files aren't visible on return.
        if (settings.pinSet && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        refreshLockout()
        val since = backgroundedAt
        backgroundedAt = 0L
        if (!settings.pinSet || locked || since == 0L) return
        if (SystemClock.elapsedRealtime() - since >= settings.lockAfterSeconds * 1000L) lockNow()
    }

    fun lockNow() {
        if (settings.pinSet && !locked) {
            locked = true
            biometricShown = false
        }
    }

    fun unlock() {
        settings.failedAttempts = 0
        pinError = null
        locked = false
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        if (checking || settings.lockoutRemaining() > 0) return
        viewModelScope.launch {
            checking = true
            // PBKDF2 takes a moment; keep it off the main thread.
            val ok = withContext(Dispatchers.Default) { settings.checkPin(pin) }
            checking = false
            if (ok) { unlock(); return@launch }
            val fails = settings.failedAttempts + 1
            settings.failedAttempts = fails
            if (fails >= MAX_TRIES) {
                // 30 s, doubling for every further batch of wrong PINs, capped at 15 minutes.
                val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
                settings.startLockout(wait)
                refreshLockout()
            }
            pinError = "Wrong PIN"
            onWrong()
        }
    }

    // Uses the monotonic clock (see LockSettings.lockoutRemaining), so changing the phone's time can't end it.
    private fun refreshLockout() {
        lockoutRemaining = settings.lockoutRemaining()
        if (lockoutRemaining <= 0 || ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val left = settings.lockoutRemaining()
                lockoutRemaining = left
                if (left <= 0) break
                delay(500)
            }
        }
    }

    private companion object { const val MAX_TRIES = 5 }
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
    val canBio = vm.settings.biometricEnabled && activity != null && biometricAvailable(context)

    val askBiometric: () -> Unit = {
        if (canBio && activity != null) runCatching {
            showBiometricPrompt(
                activity, "Unlock FilesCtrl",
                // Key gone or invalidated by a newly enrolled fingerprint: fall back to the PIN until re-confirmed.
                onUnavailable = { vm.settings.biometricEnabled = false },
            ) { vm.unlock() }
        }
    }
    LaunchedEffect(Unit) {
        if (!vm.biometricShown) { vm.biometricShown = true; askBiometric() }
    }
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
        if (pin.length == vm.settings.pinLength) vm.submitPin(pin, wrong)
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
            val err = vm.pinError != null || vm.lockoutRemaining > 0
            Text(
                when {
                    vm.lockoutRemaining > 0 -> "Too many tries. Try again in ${(vm.lockoutRemaining + 999) / 1000} s"
                    vm.pinError != null -> vm.pinError!!
                    else -> "Enter your PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (err) cs.error else cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) { PinDots(pin.length) }
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= PIN_MIN) vm.submitPin(pin, wrong) },
                confirmEnabled = pin.length >= PIN_MIN && !vm.checking && vm.lockoutRemaining == 0L,
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
fun PinDots(count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(count, PIN_MIN)) { i ->
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
