package com.vknxworks.filesctrl.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock preferences. Only a salted PBKDF2 hash of the PIN is stored, never the PIN itself.
 * Same design as CraftyCtrl's lock.
 */
class LockSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private var _pinSet by mutableStateOf(prefs.contains(KEY_PIN_HASH))
    val pinSet: Boolean get() = _pinSet

    private var _bio by mutableStateOf(prefs.getBoolean(KEY_BIOMETRIC, false))
    var biometricEnabled: Boolean
        get() = _pinSet && _bio
        set(v) { _bio = v; prefs.edit().putBoolean(KEY_BIOMETRIC, v).apply() }

    /** How long the app may sit in the background before it locks again. 0 = immediately. */
    private var _after by mutableIntStateOf(prefs.getInt(KEY_LOCK_AFTER, 0))
    var lockAfterSeconds: Int
        get() = _after
        set(v) { _after = v; prefs.edit().putInt(KEY_LOCK_AFTER, v).apply() }

    /** Hide the app in the recent-apps screen and block screenshots/screen recording. */
    private var _hide by mutableStateOf(prefs.getBoolean(KEY_HIDE, true))
    var hideContent: Boolean
        get() = _hide
        set(v) { _hide = v; prefs.edit().putBoolean(KEY_HIDE, v).apply() }

    /** Lets the lock screen submit as soon as enough digits are entered. */
    val pinLength: Int get() = prefs.getInt(KEY_PIN_LENGTH, 0)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, b64(salt))
            .putString(KEY_PIN_HASH, b64(hash(pin, salt)))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putInt(KEY_FAILED, 0)
            .apply()
        _pinSet = true
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_LENGTH).remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC).remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL)
            .remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
            .apply()
        _pinSet = false
        _bio = false
        com.vknxworks.filesctrl.ui.lock.clearBiometricKey()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::unb64) ?: return false
        return MessageDigest.isEqual(expected, hash(pin, salt))
    }

    /** Wrong-PIN counter that survives restarts, so killing the app doesn't reset the lockout. */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(v) { prefs.edit().putInt(KEY_FAILED, v).commit() }

    /**
     * Wrong-PIN lockout, measured with the monotonic clock so changing the date/time can't end it. The remaining
     * time is saved with the elapsedRealtime and boot count it was measured at; after a reboot (boot count
     * changed, or elapsedRealtime went backwards) the remaining time continues from the last checkpoint.
     */
    fun startLockout(durationMs: Long) {
        prefs.edit()
            .putLong(KEY_LOCKOUT_REMAINING, durationMs)
            .putLong(KEY_LOCKOUT_BASE, SystemClock.elapsedRealtime())
            .putInt(KEY_LOCKOUT_BOOT, bootCount())
            .remove(KEY_LOCKOUT_UNTIL)
            .commit()
    }

    /** Milliseconds of lockout left (0 when none); also checkpoints the remaining time for reboots. */
    fun lockoutRemaining(): Long {
        migrateWallClockLockout()
        val remaining = prefs.getLong(KEY_LOCKOUT_REMAINING, 0L)
        if (remaining <= 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val base = prefs.getLong(KEY_LOCKOUT_BASE, now)
        val rebooted = prefs.getInt(KEY_LOCKOUT_BOOT, -1) != bootCount() || now < base
        val left = if (rebooted) remaining else (remaining - (now - base)).coerceAtLeast(0L)
        val edit = prefs.edit()
        if (left <= 0L) edit.remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
        else edit.putLong(KEY_LOCKOUT_REMAINING, left).putLong(KEY_LOCKOUT_BASE, now).putInt(KEY_LOCKOUT_BOOT, bootCount())
        edit.apply()
        return left
    }

    /** Older versions stored the lockout end as wall-clock time; carry over what's left of it. */
    private fun migrateWallClockLockout() {
        if (!prefs.contains(KEY_LOCKOUT_UNTIL)) return
        val left = (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceIn(0L, 900_000L)
        prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
        if (left > 0L) startLockout(left)
    }

    private fun bootCount(): Int =
        runCatching { android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_HIDE = "hide_content"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_LOCKOUT_REMAINING = "pin_lockout_remaining"
        const val KEY_LOCKOUT_BASE = "pin_lockout_base_elapsed"
        const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
    }
}
