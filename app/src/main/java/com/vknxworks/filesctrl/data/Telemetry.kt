package com.vknxworks.filesctrl.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.BuildConfig
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.RemoteVfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage stats sent to the project's own self-hosted Aptabase server.
 *
 * Nothing is sent unless the user turned "Share anonymous usage stats" on, and builds without an
 * Aptabase key (anyone building from source) can't send anything at all.
 *
 * What an event contains: its name, a few fixed-value properties chosen in code (a screen's name, a protocol
 * like "smb" or "nfs", an operation like "copy" or "delete"), the app version, the Android version, the phone
 * model, the system language and a random session ID that changes every launch or after an hour of inactivity.
 * Never sent: server addresses, usernames, passwords, share or export names, file or folder names or paths, search
 * text, or any persistent device or install ID. (Like any web request, the stats server sees the phone's IP
 * address; Aptabase uses it for a country lookup and doesn't store it.)
 */
class Telemetry(
    private val context: Context,
    private val settings: Settings,
    scope: CoroutineScope,
) {
    /** Whether this build can send stats at all (it has a key and an HTTPS server address). */
    val available: Boolean = BuildConfig.APTABASE_KEY.startsWith("A-SH-") && BuildConfig.APTABASE_HOST.startsWith("https://")

    // A separate client from the WebDAV ones: no credentials, no pinned or self-signed certificates, no redirects.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val systemProps: JSONObject by lazy {
        JSONObject()
            .put("isDebug", BuildConfig.DEBUG)
            .put("osName", "Android")
            .put("osVersion", Build.VERSION.RELEASE ?: "")
            .put("locale", Locale.getDefault().language)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appBuildNumber", BuildConfig.VERSION_CODE.toString())
            .put("sdkVersion", "filesctrl@${BuildConfig.VERSION_NAME}")
            .put("deviceModel", Build.MODEL ?: "")
    }

    private val queue = Channel<Queued>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Bumped when stats are switched off: anything queued before that is thrown away, never sent. */
    @Volatile private var generation = 0
    @Volatile private var sessionId = newSessionId()
    @Volatile private var lastEventAt = 0L

    init {
        if (available) {
            // One event at a time, in order. Failures are dropped: stats are never stored or retried.
            scope.launch(Dispatchers.IO) {
                for (item in queue) send(item)
            }
        }
    }

    // Settings are SharedPreferences, read when the app starts, so this is the stored answer (never a placeholder).
    private val enabled: Boolean
        get() = available && settings.usageStats

    /** Called when the app's main screen is opened (not for the file picker or the editor opened by other apps). */
    fun appOpened() {
        if (!available) return
        trackOpened()
    }

    /** The answer to the first-run question or the Settings switch. Switching on starts a fresh session. */
    fun setOptIn(on: Boolean) {
        val was = settings.usageStats
        settings.usageStats = on
        settings.usageStatsAsked = true
        if (on && !was) {
            resetSession()
            trackOpened()
        }
        // Switched off: drop whatever is still waiting to be sent.
        if (!on) discardQueued()
    }

    private fun trackOpened() = track(
        "app_opened",
        mapOf(
            "form_factor" to formFactor(),
            "theme" to settings.theme.name.lowercase(),
        ),
    )

    /** Records an event if the user opted in. [props] values must be fixed strings or numbers from code. */
    fun track(name: String, props: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt > SESSION_TIMEOUT_MS) sessionId = newSessionId()
        lastEventAt = now
        val event = JSONObject()
            .put("timestamp", timestamp(now))
            .put("sessionId", sessionId)
            .put("eventName", name)
            .put("systemProps", systemProps)
            .put("props", JSONObject(props))
        queue.trySend(Queued(generation, event))
    }

    /** Throws away every event that hasn't been sent yet, so switching stats off takes effect at once. */
    private fun discardQueued() {
        generation++
        while (queue.tryReceive().isSuccess) Unit
    }

    /** A network location was added: only its protocol ("smb", "nfs", …). */
    fun connectionAdded(type: Proto) = track("connection_added", mapOf("protocol" to protocol(type)))

    /** A network location was opened for browsing: only its protocol, never which server, share or folder. */
    fun networkBrowsed(vfsId: String) {
        val kind = storageKind(vfsId) ?: return
        if (kind != LOCAL) track("network_browsed", mapOf("protocol" to kind))
    }

    /** A file operation by kind ("copy", "move", "delete", "share", "zip", "unzip") and the kind of storage. */
    fun fileOperation(kind: String, vfsId: String?) {
        val props = mutableMapOf<String, Any>("kind" to kind)
        vfsId?.let(::storageKind)?.let { props["storage"] = it }
        track("file_operation", props)
    }

    /** "local" or the network protocol ("nfs", "smb", …) of a location's storage; never which server. */
    private fun storageKind(vfsId: String): String? = when {
        vfsId == LocalVfs.ID -> LOCAL
        vfsId.startsWith(RemoteVfs.PREFIX) -> App.servers.get(vfsId.removePrefix(RemoteVfs.PREFIX))?.type?.let { protocol(it) }
        else -> null
    }

    /** A fresh session after opting back in, so stats from before and after aren't linked. */
    private fun resetSession() {
        sessionId = newSessionId()
        lastEventAt = 0L
    }

    private fun send(item: Queued) {
        // The user may have opted out (and maybe back in) while this was queued.
        if (!enabled || item.generation != generation) return
        val event = item.event
        val request = Request.Builder()
            .url("${BuildConfig.APTABASE_HOST.trimEnd('/')}/api/v0/event")
            .header("App-Key", BuildConfig.APTABASE_KEY)
            .header("User-Agent", "FilesCtrl/${BuildConfig.VERSION_NAME} (Android)")
            .post(event.toString().toRequestBody(JSON))
            .build()
        runCatching { http.newCall(request).execute().close() }
    }

    /** "phone" or "tablet", from the screen's smallest width (the same split as the app's side rail). */
    fun formFactor(): String {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && sw >= 600) "tablet" else "phone"
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private class Queued(val generation: Int, val event: JSONObject)

    companion object {
        private val JSON = "application/json".toMediaType()
        private val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)
        private const val LOCAL = "local"

        /** "nfs", "smb", "sftp", "ftp" or "webdav" — only the kind of storage, never which server. */
        fun protocol(type: Proto): String = type.name.lowercase()
    }
}
