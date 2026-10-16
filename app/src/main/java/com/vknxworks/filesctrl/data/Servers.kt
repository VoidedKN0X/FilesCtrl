package com.vknxworks.filesctrl.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved NFS share. */
data class NfsServer(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "",
    val host: String = "",
    val export: String = "/",
    /** 0 = automatic (try v3, then v4), 3, 4 (4.0) or 42 (4.2). */
    val version: Int = 0,
    /** AUTH_SYS credentials sent to the server. */
    val uid: Int = 1000,
    val gid: Int = 1000,
    /** 0 = ask the portmapper (v3) / 2049 (v4). */
    val nfsPort: Int = 0,
    val mountPort: Int = 0,
    val timeoutSec: Int = 10,
    val readOnly: Boolean = false,
) {
    val title: String get() = name.ifBlank { host }
    val summary: String get() = "$host:$export"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host).put("export", export)
        .put("version", version).put("uid", uid).put("gid", gid)
        .put("nfsPort", nfsPort).put("mountPort", mountPort)
        .put("timeoutSec", timeoutSec).put("readOnly", readOnly)

    companion object {
        fun fromJson(o: JSONObject) = NfsServer(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
            name = o.optString("name"),
            host = o.optString("host"),
            export = o.optString("export", "/"),
            version = o.optInt("version", 0),
            uid = o.optInt("uid", 1000),
            gid = o.optInt("gid", 1000),
            nfsPort = o.optInt("nfsPort", 0),
            mountPort = o.optInt("mountPort", 0),
            timeoutSec = o.optInt("timeoutSec", 10),
            readOnly = o.optBoolean("readOnly", false),
        )
    }
}

/** Saved servers, observable from Compose. */
class ServerStore(context: Context) {
    private val sp = context.getSharedPreferences("servers", Context.MODE_PRIVATE)
    val all = mutableStateListOf<NfsServer>()
    private val listeners = mutableListOf<(String) -> Unit>()

    init {
        val raw = sp.getString("list", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) all += NfsServer.fromJson(arr.getJSONObject(i))
        } catch (_: Exception) {}
    }

    fun get(id: String): NfsServer? = all.firstOrNull { it.id == id }

    /** Called with a server id whenever that server is changed or removed. */
    fun onChange(l: (String) -> Unit) { listeners += l }

    fun save(s: NfsServer) {
        val i = all.indexOfFirst { it.id == s.id }
        if (i >= 0) all[i] = s else all += s
        persist()
        listeners.forEach { it(s.id) }
    }

    fun remove(id: String) {
        all.removeAll { it.id == id }
        persist()
        listeners.forEach { it(id) }
    }

    fun move(from: Int, to: Int) {
        if (from !in all.indices || to !in all.indices) return
        all.add(to, all.removeAt(from))
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        sp.edit().putString("list", arr.toString()).apply()
    }
}
