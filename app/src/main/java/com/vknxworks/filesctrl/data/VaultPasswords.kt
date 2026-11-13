package com.vknxworks.filesctrl.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.data.lock.LockVault
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cryptomator vault passwords the user chose to remember (never saved otherwise). Stored like the server passwords:
 * encrypted with a non-exportable Android Keystore AES-GCM key and, while the app lock is on, first with the lock's
 * vault key, so they can't be read while FilesCtrl is locked. Entries are keyed by a hash of the vault's location,
 * so the file doesn't even list where the vaults are.
 */
class VaultPasswords(context: Context, private val vault: LockVault) {
    private val sp = context.getSharedPreferences("vault_passwords", Context.MODE_PRIVATE)

    /** How many are saved, for Settings. */
    var count by mutableIntStateOf(sp.all.size)
        private set

    private fun key(vaultId: String): String =
        MessageDigest.getInstance("SHA-256").digest(vaultId.toByteArray()).joinToString("") { "%02x".format(it) }

    fun has(vaultId: String): Boolean = sp.contains(key(vaultId))

    /** The saved password, or null when none is saved or it can't be read now (the app is locked). */
    fun get(vaultId: String): CharArray? {
        val stored = sp.getString(key(vaultId), null) ?: return null
        val plain = decrypt(stored) ?: return null
        val pw = if (LockVault.isSealed(plain)) vault.open(plain) ?: return null else plain
        return pw.toCharArray()
    }

    /** Saves [password] for [vaultId]. Throws IOException (and saves nothing) if it can't be encrypted. */
    fun save(vaultId: String, password: CharArray) {
        val s = String(password)
        val sealed = if (vault.enabled) vault.seal(s) ?: throw IOException(App.str(R.string.filesctrl_is_locked_unlock_it_to)) else s
        sp.edit().putString(key(vaultId), encrypt(sealed)).commit()
        count = sp.all.size
    }

    fun forget(vaultId: String) {
        sp.edit().remove(key(vaultId)).commit()
        count = sp.all.size
    }

    fun forgetAll() {
        sp.edit().clear().commit()
        count = 0
    }

    /** The app lock was turned on (its vault is open): encrypt every saved password with its key too. */
    fun sealAll() = rewrite { plain -> if (LockVault.isSealed(plain)) plain else vault.seal(plain) }

    /** The app lock is being turned off (still unlocked): back to Keystore-only encryption. */
    fun unsealAll() = rewrite { plain -> if (LockVault.isSealed(plain)) vault.open(plain) else plain }

    private fun rewrite(change: (String) -> String?) {
        val edit = sp.edit()
        for ((k, v) in sp.all) {
            val plain = (v as? String)?.let(::decrypt)
            val next = plain?.let(change)
            // Anything that can't be carried over is forgotten rather than kept unreadable.
            if (next == null) edit.remove(k) else edit.putString(k, encrypt(next))
        }
        edit.commit()
        count = sp.all.size
    }

    // --- Keystore AES-GCM ---
    private val alias = "filesctrl_vault_passwords"

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        "v1:" + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    } catch (e: Exception) {
        throw IOException(App.str(R.string.couldn_t_encrypt_the_password_with, e.javaClass.simpleName), e)
    }

    private fun decrypt(stored: String): String? {
        if (!stored.startsWith("v1:")) return null
        return try {
            val raw = Base64.decode(stored.removePrefix("v1:"), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) { null }
    }
}
