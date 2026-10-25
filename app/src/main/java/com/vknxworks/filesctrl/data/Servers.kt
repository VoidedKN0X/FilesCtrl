package com.vknxworks.filesctrl.data

import android.content.Context
import com.vknxworks.filesctrl.data.lock.LockVault
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** Network file system protocols FilesCtrl can connect to. */
enum class Proto(val label: String, val defaultPort: Int) {
    NFS("NFS", 0),
    SMB("SMB", 445),
    SFTP("SFTP", 22),
    FTP("FTP", 21),
    WEBDAV("WebDAV", 80);

    fun port(secure: Boolean): Int = when {
        this == WEBDAV && secure -> 443
        else -> defaultPort
    }
}

/** A saved network location. Which fields matter depends on [type]. */
data class NetServer(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: Proto = Proto.NFS,
    val name: String = "",
    val host: String = "",
    /** 0 = the protocol's default. For NFS: the NFS port (0 = portmapper / 2049). */
    val port: Int = 0,
    /** NFS: export path. SMB: "share" or "share/folder" (empty = list shares). Others: starting folder. */
    val path: String = "",
    val username: String = "",
    val password: String = "",
    /** SMB only. */
    val domain: String = "",
    /** SFTP only: private key text (OpenSSH/PEM); [password] is its passphrase when set. */
    val privateKey: String = "",
    /** SFTP only: fingerprint of the server's host key ("SHA256:…"), saved once the user confirms it. */
    val hostKey: String = "",
    /** FTPS / WebDAV over HTTPS with [insecureTls]: SHA-256 of the self-signed certificate the user accepted. */
    val certPin: String = "",
    /** SMB only: refuse servers that don't sign messages (protects against tampering). */
    val smbSigning: Boolean = true,
    /** SMB only: encrypt traffic (SMB 3). */
    val smbEncrypt: Boolean = false,
    /** SMB only: connect as guest, without a username. */
    val guest: Boolean = false,
    /** FTP: FTPS (explicit TLS). WebDAV: HTTPS. */
    val secure: Boolean = false,
    /** Accept self-signed / untrusted TLS certificates (FTPS, WebDAV over HTTPS). */
    val insecureTls: Boolean = false,
    /**
     * WebDAV over plain HTTP: the server address the user accepted the "unencrypted connection" warning for.
     * Empty = not accepted. Nothing is sent to an http:// WebDAV server unless this matches [host].
     */
    val httpAcceptedHost: String = "",
    val readOnly: Boolean = false,
    val timeoutSec: Int = 10,
    // NFS
    /** 0 = automatic (try v3, then v4), 3, 4 (4.0) or 42 (4.2). */
    val version: Int = 0,
    val uid: Int = 1000,
    val gid: Int = 1000,
    val mountPort: Int = 0,
) {
    val title: String get() = name.ifBlank { host }
    val effectivePort: Int get() = if (port > 0) port else type.port(secure)

    /** WebDAV over plain HTTP (not encrypted). FTP without TLS is its own protocol and isn't counted here. */
    val isPlainHttp: Boolean get() = type == Proto.WEBDAV && !secure

    /** Plain HTTP WebDAV the user accepted the warning for, for this exact address. */
    val httpAccepted: Boolean
        get() = isPlainHttp && httpAcceptedHost.isNotBlank() && httpAcceptedHost.trim().equals(host.trim(), ignoreCase = true)
    val scheme: String
        get() = when (type) {
            Proto.NFS -> "nfs"
            Proto.SMB -> "smb"
            Proto.SFTP -> "sftp"
            Proto.FTP -> if (secure) "ftps" else "ftp"
            Proto.WEBDAV -> if (secure) "davs" else "dav"
        }

    /** e.g. smb://nas/media — for display and "copy path". */
    fun url(innerPath: String = "/"): String {
        val base = "/" + path.trim('/')
        val full = (base.trimEnd('/') + "/" + innerPath.trimStart('/')).trimEnd('/').ifEmpty { "/" }
        val defaultPort = port <= 0 || port == type.port(secure)
        return "$scheme://$host${if (defaultPort) "" else ":$port"}$full"
    }

    // NFS names kept for the NFS client.
    val export: String get() = path.ifBlank { "/" }
    val nfsPort: Int get() = if (type == Proto.NFS) port else 0

    fun toJson(seal: (String) -> String): JSONObject = JSONObject()
        .put("id", id).put("type", type.name).put("name", name).put("host", host).put("port", port)
        .put("path", path).put("username", username).put("password", seal(password)).put("domain", domain)
        .put("privateKey", seal(privateKey)).put("hostKey", hostKey).put("certPin", certPin).put("secure", secure)
        .put("smbSigning", smbSigning).put("smbEncrypt", smbEncrypt).put("guest", guest)
        .put("insecureTls", insecureTls).put("httpAcceptedHost", httpAcceptedHost).put("readOnly", readOnly).put("timeoutSec", timeoutSec)
        .put("version", version).put("uid", uid).put("gid", gid).put("mountPort", mountPort)

    companion object {
        fun fromJson(o: JSONObject, open: (String) -> String): NetServer {
            val type = try { Proto.valueOf(o.optString("type", "NFS")) } catch (_: Exception) { Proto.NFS }
            return NetServer(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
                type = type,
                name = o.optString("name"),
                host = o.optString("host"),
                // Older versions saved NFS shares with "export" and "nfsPort".
                port = if (o.has("port")) o.optInt("port", 0) else o.optInt("nfsPort", 0),
                path = if (o.has("path")) o.optString("path") else o.optString("export", "/"),
                username = o.optString("username"),
                password = open(o.optString("password")),
                domain = o.optString("domain"),
                privateKey = open(o.optString("privateKey")),
                hostKey = o.optString("hostKey"),
                certPin = o.optString("certPin"),
                smbSigning = o.optBoolean("smbSigning", true),
                smbEncrypt = o.optBoolean("smbEncrypt", false),
                // Older versions connected SMB servers without a username as guest; keep them working.
                guest = if (o.has("guest")) o.optBoolean("guest", false) else type == Proto.SMB && o.optString("username").isBlank(),
                secure = o.optBoolean("secure", false),
                insecureTls = o.optBoolean("insecureTls", false),
                httpAcceptedHost = o.optString("httpAcceptedHost"),
                readOnly = o.optBoolean("readOnly", false),
                timeoutSec = o.optInt("timeoutSec", 10),
                version = o.optInt("version", 0),
                uid = o.optInt("uid", 1000),
                gid = o.optInt("gid", 1000),
                mountPort = o.optInt("mountPort", 0),
            )
        }
    }
}

/**
 * Saved servers, observable from Compose. Passwords and keys are encrypted with an Android Keystore key and,
 * while the app lock is on, also with the lock's vault key: while the app is locked they aren't in memory and
 * can't be decrypted ([locked]); servers are still listed, without their secrets.
 */
class ServerStore(context: Context, private val vault: LockVault) {
    private val sp = context.getSharedPreferences("servers", Context.MODE_PRIVATE)
    val all = mutableStateListOf<NetServer>()
    private val listeners = mutableListOf<(String) -> Unit>()

    /** Server id -> its stored (vault-sealed) password and key, kept as they are while the app is locked. */
    private val sealed = HashMap<String, Pair<String, String>>()

    /** The app is locked: saved passwords and keys can't be used. */
    @Volatile var locked = false
        private set

    init { load() }

    @Synchronized
    private fun load() {
        val raw = sp.getString("list", "[]") ?: "[]"
        sealed.clear()
        val list = ArrayList<NetServer>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pw = decrypt(o.optString("password"))
                val key = decrypt(o.optString("privateKey"))
                val s = NetServer.fromJson(o) { "" }
                if (LockVault.isSealed(pw) || LockVault.isSealed(key)) {
                    val openPw = if (LockVault.isSealed(pw)) vault.open(pw) else pw
                    val openKey = if (LockVault.isSealed(key)) vault.open(key) else key
                    if (openPw == null || openKey == null) {
                        sealed[s.id] = pw to key
                        list += s
                        continue
                    }
                    list += s.copy(password = openPw, privateKey = openKey)
                } else list += s.copy(password = pw, privateKey = key)
            }
        } catch (_: Exception) {}
        locked = sealed.isNotEmpty() || (vault.enabled && !vault.isOpen)
        all.clear()
        all.addAll(list)
    }

    /** The app locked (call before the vault closes): drop every password and key from memory. */
    @Synchronized
    fun lockSecrets() {
        if (!vault.enabled) return
        for (i in all.indices) {
            val s = all[i]
            if (s.password.isEmpty() && s.privateKey.isEmpty()) continue
            sealed[s.id] = seal(s.password) to seal(s.privateKey)
            all[i] = s.copy(password = "", privateKey = "")
        }
        locked = true
    }

    /** The app unlocked: decrypt the passwords and keys again. Open connections made without them are dropped. */
    fun unlockSecrets() {
        val ids = synchronized(this) {
            val had = sealed.keys.toList()
            load()
            had
        }
        ids.forEach { id -> listeners.forEach { it(id) } }
    }

    /** The app lock was turned on (vault open): encrypt everything with its key too. */
    fun sealAll() = persist()

    /** The app lock is being turned off (still unlocked): save with the Keystore key only. */
    fun unsealAll() = write(serialize(all, plain = true))

    fun get(id: String): NetServer? = all.firstOrNull { it.id == id }

    /** Called with a server id whenever that server is changed or removed. */
    fun onChange(l: (String) -> Unit) { listeners += l }

    /**
     * Saves [s]. Throws [java.io.IOException] (and changes nothing) if its password or key can't be encrypted,
     * rather than silently saving it without them.
     */
    fun save(s: NetServer) {
        val i = all.indexOfFirst { it.id == s.id }
        val next = all.toMutableList().apply { if (i >= 0) set(i, s) else add(s) }
        val json = serialize(next)
        if (i >= 0) all[i] = s else all += s
        write(json)
        listeners.forEach { it(s.id) }
    }

    /** Stores a field learned while connecting (e.g. an upgraded host key) without dropping the connection. */
    fun remember(s: NetServer) {
        val i = all.indexOfFirst { it.id == s.id }
        if (i < 0) return
        all[i] = s
        try { persist() } catch (_: java.io.IOException) {} // Kept in memory; saved with the next change.
    }

    /** Forgets every saved password and key (servers stay). Used when the app lock PIN is forgotten. */
    @Synchronized
    fun forgetSecrets() {
        sealed.clear()
        locked = false
        for (i in all.indices) all[i] = all[i].copy(password = "", privateKey = "")
        write(serialize(all, plain = true))
        all.forEach { s -> listeners.forEach { it(s.id) } }
    }

    fun remove(id: String) {
        all.removeAll { it.id == id }
        try { persist() } catch (_: java.io.IOException) {}
        listeners.forEach { it(id) }
    }

    private fun persist() = write(serialize(all))

    @Synchronized
    private fun serialize(list: List<NetServer>, plain: Boolean = false): String {
        val arr = JSONArray()
        list.forEach { s ->
            val kept = if (plain) null else sealed[s.id]
            if (kept != null) {
                // Locked: the secrets aren't in memory; keep what's stored.
                arr.put(s.toJson { "" }.put("password", encrypt(kept.first)).put("privateKey", encrypt(kept.second)))
            } else {
                arr.put(s.toJson { v -> encrypt(if (plain) v else seal(v)) })
            }
        }
        return arr.toString()
    }

    /** With the app lock on, a secret is encrypted with the vault key first. Empty stays empty. */
    private fun seal(plain: String): String {
        if (plain.isEmpty() || !vault.enabled) return plain
        return vault.seal(plain) ?: throw java.io.IOException(App.str(R.string.filesctrl_is_locked_unlock_it_to))
    }

    private fun write(json: String) { sp.edit().putString("list", json).apply() }

    // --- Keystore AES-GCM for secrets ---
    private val alias = "filesctrl_servers"

    private fun key(): SecretKey {
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

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            "v1:" + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw java.io.IOException(App.str(R.string.couldn_t_encrypt_the_password_with, e.javaClass.simpleName), e)
        }
    }

    private fun decrypt(stored: String): String {
        if (!stored.startsWith("v1:")) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix("v1:"), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (_: Exception) { "" }
    }
}
