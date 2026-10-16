package com.vknxworks.filesctrl.data

import android.content.Context
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
    /** SFTP only: fingerprint of the server's host key, remembered on first connect. */
    val hostKey: String = "",
    /** FTP: FTPS (explicit TLS). WebDAV: HTTPS. */
    val secure: Boolean = false,
    /** Accept self-signed / untrusted TLS certificates (FTPS, WebDAV over HTTPS). */
    val insecureTls: Boolean = false,
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
        .put("privateKey", seal(privateKey)).put("hostKey", hostKey).put("secure", secure)
        .put("insecureTls", insecureTls).put("readOnly", readOnly).put("timeoutSec", timeoutSec)
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
                secure = o.optBoolean("secure", false),
                insecureTls = o.optBoolean("insecureTls", false),
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

/** Saved servers, observable from Compose. Passwords and keys are encrypted with an Android Keystore key. */
class ServerStore(context: Context) {
    private val sp = context.getSharedPreferences("servers", Context.MODE_PRIVATE)
    val all = mutableStateListOf<NetServer>()
    private val listeners = mutableListOf<(String) -> Unit>()

    init {
        val raw = sp.getString("list", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) all += NetServer.fromJson(arr.getJSONObject(i), ::decrypt)
        } catch (_: Exception) {}
    }

    fun get(id: String): NetServer? = all.firstOrNull { it.id == id }

    /** Called with a server id whenever that server is changed or removed. */
    fun onChange(l: (String) -> Unit) { listeners += l }

    fun save(s: NetServer) {
        val i = all.indexOfFirst { it.id == s.id }
        if (i >= 0) all[i] = s else all += s
        persist()
        listeners.forEach { it(s.id) }
    }

    /** Stores a field learned while connecting (e.g. an SFTP host key) without dropping the connection. */
    fun remember(s: NetServer) {
        val i = all.indexOfFirst { it.id == s.id }
        if (i < 0) return
        all[i] = s
        persist()
    }

    /** Forgets every saved password and key (servers stay). Used when the app lock PIN is forgotten. */
    fun forgetSecrets() {
        for (i in all.indices) all[i] = all[i].copy(password = "", privateKey = "")
        persist()
        all.forEach { s -> listeners.forEach { it(s.id) } }
    }

    fun remove(id: String) {
        all.removeAll { it.id == id }
        persist()
        listeners.forEach { it(id) }
    }

    private fun persist() {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson(::encrypt)) }
        sp.edit().putString("list", arr.toString()).apply()
    }

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
        } catch (_: Exception) { "" }
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
