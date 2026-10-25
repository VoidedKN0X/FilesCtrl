package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.data.NetServer
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.Pipe
import okio.buffer
import org.w3c.dom.Element
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** WebDAV over HTTP or HTTPS (Nextcloud, ownCloud, Apache/nginx DAV, NAS WebDAV servers). */
class WebDavVfs(override val server: NetServer) : RemoteVfs {
    override val id = RemoteVfs.idFor(server.id)
    override val title: String get() = server.title
    override val canChmod = false
    @Volatile private var used = false
    override val connected: Boolean get() = used

    /** Set when the user accepted a self-signed certificate; checks it by SHA-256. */
    private val pinTm: TlsPin.PinningTrustManager? =
        if (server.secure && server.insecureTls) TlsPin.PinningTrustManager(server.certPin) else null

    /** Credentials go only to the configured server (same scheme, host and port), never to a redirect elsewhere. */
    private fun sameServer(u: HttpUrl) = u.scheme == base.scheme && u.host.equals(base.host, ignoreCase = true) && u.port == base.port

    private val client: OkHttpClient by lazy {
        val t = server.timeoutSec.coerceIn(2, 120).toLong()
        val b = OkHttpClient.Builder()
            .connectTimeout(t, TimeUnit.SECONDS)
            .readTimeout(t * 3, TimeUnit.SECONDS)
            .writeTimeout(t * 3, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        // Plain HTTP only for the server address the user accepted the "unencrypted" warning for; never to any
        // other host, and never as a downgrade from HTTPS (redirects aren't followed across schemes either).
        val httpGuard = okhttp3.Interceptor { chain ->
            val req = chain.request()
            if (!req.isHttps && !(server.httpAccepted && sameServer(req.url))) throw IOException(App.str(R.string.webdav_http_not_accepted, server.host.trim()))
            chain.proceed(req)
        }
        // Before credentials are added, and again for every request actually sent (sign-in retries included).
        b.addInterceptor(httpGuard).addNetworkInterceptor(httpGuard)
        if (server.username.isNotBlank()) {
            // Answer challenges, and send credentials up front so PUT bodies aren't sent twice.
            val cred = Credentials.basic(server.username, server.password, Charsets.UTF_8)
            b.authenticator { _, resp ->
                if (resp.request.header("Authorization") != null || !sameServer(resp.request.url)) null
                else resp.request.newBuilder().header("Authorization", cred).build()
            }
            b.addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(if (sameServer(req.url)) req.newBuilder().header("Authorization", cred).build() else req.newBuilder().removeHeader("Authorization").build())
            }
        }
        pinTm?.let { tm ->
            b.sslSocketFactory(tm.sslContext().socketFactory, tm).hostnameVerifier(tm.hostnameVerifier(server.host.trim()))
        }
        b.build()
    }

    private val base: HttpUrl by lazy {
        val scheme = if (server.secure) "https" else "http"
        HttpUrl.Builder().scheme(scheme).host(server.host.trim()).port(server.effectivePort).build()
    }

    private fun url(path: String, dir: Boolean = false): HttpUrl {
        val b = base.newBuilder()
        RemoteVfs.join(server.path, path).split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        if (dir) b.addPathSegment("")
        return b.build()
    }

    override fun close() { used = false }

    private fun exec(req: Request, what: String, path: String, redirects: Int = 0): Response {
        // Nothing (not even the request without a password) goes to an http:// server the user hasn't accepted.
        if (server.isPlainHttp && !server.httpAccepted) throw IOException(App.str(R.string.webdav_http_not_accepted, server.host.trim()))
        used = true
        val resp = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            pinTm?.explain()?.let { throw it }
            throw friendly(e, what)
        }
        if (resp.isSuccessful) return resp
        // Many servers redirect "/folder" to "/folder/"; follow that one kind of redirect, keeping the method.
        if (resp.code in setOf(301, 302, 307, 308)) {
            val target = resp.header("Location")?.let { req.url.resolve(it) }
            if (redirects < 3 && target != null && sameServer(target) && target.encodedPath.trimEnd('/') == req.url.encodedPath.trimEnd('/') &&
                target.encodedPath != req.url.encodedPath
            ) {
                resp.close()
                return exec(req.newBuilder().url(target).build(), what, path, redirects + 1)
            }
        }
        val code = resp.code
        resp.close()
        throw when (code) {
            404 -> FileNotFoundException(App.str(R.string.not_found, path))
            401 -> IOException(App.str(R.string.wrong_username_or_password))
            403 -> IOException(App.str(R.string.failed_permission_denied_2, what, Paths.name(path)))
            405 -> IOException(App.str(R.string.failed_not_allowed_here, what, Paths.name(path)))
            409 -> IOException(App.str(R.string.failed_the_parent_folder_doesn_t, what))
            412 -> IOException(App.str(R.string.failed_already_exists_2, what, Paths.name(path)))
            423 -> IOException(App.str(R.string.failed_is_locked, what, Paths.name(path)))
            507 -> IOException(App.str(R.string.failed_no_space_left_on_the_server, what))
            301, 302, 307, 308 -> IOException(App.str(R.string.webdav_redirected, what, resp.header("Location").orEmpty()))
            else -> IOException(App.str(R.string.failed_http, what, code))
        }
    }

    private companion object {
        const val MAX_PROPFIND_BYTES = 64 * 1024 * 1024
        private val DOCTYPE = "<!doctype".toByteArray(Charsets.US_ASCII)

        /** Finds "<!DOCTYPE" in any case, skipping NUL bytes so UTF-16 documents are caught too. */
        fun containsDoctype(b: ByteArray): Boolean {
            var m = 0
            for (x in b) {
                if (x == 0.toByte()) continue
                val c = if (x in 'A'.code.toByte()..'Z'.code.toByte()) (x + 32).toByte() else x
                m = if (c == DOCTYPE[m]) m + 1 else if (c == DOCTYPE[0]) 1 else 0
                if (m == DOCTYPE.size) return true
            }
            return false
        }
    }

    private val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:quota-available-bytes/><d:quota-used-bytes/></d:prop></d:propfind>"""

    private class Prop(val href: String, val dir: Boolean, val size: Long, val modified: Long, val quotaFree: Long, val quotaUsed: Long)

    private fun propfind(path: String, depth: Int, dir: Boolean): List<Prop> {
        val req = Request.Builder().url(url(path, dir))
            .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", depth.toString())
            .build()
        val resp = exec(req, if (depth == 0) App.str(R.string.reading) else App.str(R.string.listing), path)
        return resp.use { parse(readCapped(it.body!!.byteStream())) }
    }

    /** Reads a PROPFIND reply, refusing absurdly large ones (a hostile server could otherwise exhaust memory). */
    private fun readCapped(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_PROPFIND_BYTES) throw IOException(App.str(R.string.listing_failed_the_server_s_reply_is))
        }
        return out.toByteArray()
    }

    private fun parse(xml: ByteArray): List<Prop> {
        // WebDAV replies never need a DOCTYPE. Refusing one rules out entity tricks (XXE, "billion laughs").
        if (containsDoctype(xml)) throw IOException(App.str(R.string.listing_failed_the_server_sent_an))
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            for (feature in listOf("http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING to true)) {
                try { setFeature(feature.first, feature.second) } catch (_: Exception) {}
            }
        }
        val builder = f.newDocumentBuilder().apply {
            // Android's parser ignores most of the features above, so also refuse to load any external entity or DTD.
            setEntityResolver { _, _ -> throw org.xml.sax.SAXException(App.str(R.string.external_entities_are_not_allowed)) }
        }
        val doc = builder.parse(java.io.ByteArrayInputStream(xml))
        val out = ArrayList<Prop>()
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
        for (i in 0 until responses.length) {
            val r = responses.item(i) as Element
            val href = r.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent?.trim() ?: continue
            var dir = false; var size = 0L; var mod = 0L; var qf = -1L; var qu = -1L
            val propstats = r.getElementsByTagNameNS("DAV:", "propstat")
            for (j in 0 until propstats.length) {
                val ps = propstats.item(j) as Element
                val status = ps.getElementsByTagNameNS("DAV:", "status").item(0)?.textContent.orEmpty()
                if (status.isNotEmpty() && !status.contains(" 200")) continue
                val prop = ps.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element ?: continue
                if (prop.getElementsByTagNameNS("DAV:", "collection").length > 0) dir = true
                prop.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull()?.let { size = it }
                prop.getElementsByTagNameNS("DAV:", "getlastmodified").item(0)?.textContent?.trim()?.let {
                    mod = try { dateFmt.parse(it)?.time ?: 0 } catch (_: Exception) { 0 }
                }
                prop.getElementsByTagNameNS("DAV:", "quota-available-bytes").item(0)?.textContent?.trim()?.toLongOrNull()?.let { qf = it }
                prop.getElementsByTagNameNS("DAV:", "quota-used-bytes").item(0)?.textContent?.trim()?.toLongOrNull()?.let { qu = it }
            }
            out += Prop(href, dir, size, mod, qf, qu)
        }
        return out
    }

    /** The decoded path of an href ("/dav/a%20b/" -> "/dav/a b"). */
    private fun hrefPath(href: String): String {
        val raw = if (href.startsWith("http://") || href.startsWith("https://")) href.toHttpUrl().encodedPath else href
        return raw.split('/').joinToString("/") { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.trimEnd('/')
    }

    /** The decoded last segment of an href, or "" if it can't be decoded. */
    private fun hrefName(href: String): String {
        val raw = if (href.startsWith("http://") || href.startsWith("https://")) href.toHttpUrl().encodedPath else href
        val last = raw.trimEnd('/').substringAfterLast('/')
        return try { URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { "" }
    }

    private fun entryOf(path: String, name: String, p: Prop) =
        FileEntry(id, path, name, p.dir, if (p.dir) 0 else p.size, p.modified, if (p.dir) 0x4000 or 0b111_101_101 else 0x8000 or 0b110_100_100)

    override fun list(dir: String): List<FileEntry> {
        val self = hrefPath(url(dir, true).encodedPath)
        return propfind(dir, 1, true).mapNotNull { p ->
            val hp = hrefPath(p.href)
            if (hp == self || hp.isEmpty()) return@mapNotNull null
            // Decoding can turn "%2E%2E" or "%2F" into ".." or a slash; never accept those names.
            val name = hrefName(p.href)
            if (!Paths.isSafeName(name)) null else entryOf(Paths.join(dir, name), name, p)
        }
    }

    override fun stat(path: String): FileEntry {
        val p = propfind(path, 0, false).firstOrNull() ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        return entryOf(path, Paths.name(path), p)
    }

    override fun openRead(path: String): InputStream = openRead(path, 0)

    override fun openRead(path: String, offset: Long): InputStream {
        val b = Request.Builder().url(url(path)).get()
        if (offset > 0) b.header("Range", "bytes=$offset-")
        val resp = exec(b.build(), App.str(R.string.opening), path)
        val s = resp.body!!.byteStream()
        // A server that ignores Range sends the whole file; skip to the offset ourselves.
        if (offset > 0 && resp.code == 200) {
            var left = offset
            while (left > 0) { val n = s.skip(left); if (n <= 0) break; left -= n }
        }
        return s
    }

    override fun openWrite(path: String): OutputStream {
        // Stream the upload: what's written here is piped into the PUT body on a background thread.
        val pipe = Pipe(1L shl 20)
        var error: IOException? = null
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) { sink.writeAll(pipe.source) }
        }
        val req = Request.Builder().url(url(path)).put(body).build()
        val worker = thread(name = "webdav-put") {
            try { exec(req, App.str(R.string.saving), path).close() } catch (e: IOException) { error = e } finally {
                try { pipe.source.close() } catch (_: Exception) {}
            }
        }
        val sink = pipe.sink.buffer()
        return object : OutputStream() {
            override fun write(b: Int) { error?.let { throw it }; sink.writeByte(b) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                error?.let { throw it }
                try { sink.write(b, off, len) } catch (e: IOException) { throw error ?: e }
            }
            override fun flush() = sink.flush()
            override fun close() {
                try { sink.close() } catch (e: IOException) { if (error == null) error = e }
                worker.join()
                error?.let { throw it }
            }
        }
    }

    override fun mkdir(path: String) {
        if (exists(path)) throw IOException(App.str(R.string.already_exists, Paths.name(path)))
        exec(Request.Builder().url(url(path, true)).method("MKCOL", null).build(), App.str(R.string.creating_folder), path).close()
    }

    override fun createFile(path: String) {
        if (exists(path)) throw IOException(App.str(R.string.already_exists, Paths.name(path)))
        exec(Request.Builder().url(url(path)).put(ByteArray(0).toRequestBody()).build(), App.str(R.string.creating_file), path).close()
    }

    override fun delete(path: String, isDir: Boolean) {
        exec(Request.Builder().url(url(path, isDir)).delete().build(), App.str(R.string.deleting), path).close()
    }

    override fun rename(from: String, to: String) {
        val dir = try { stat(from).isDir } catch (_: Exception) { false }
        val req = Request.Builder().url(url(from, dir)).method("MOVE", null)
            .header("Destination", url(to, dir).toString())
            .header("Overwrite", "F")
            .build()
        exec(req, App.str(R.string.renaming), from).close()
    }

    override fun space(path: String): Space? = try {
        val p = propfind("/", 0, true).firstOrNull()
        if (p == null || p.quotaFree < 0 || p.quotaUsed < 0) null else Space(p.quotaFree + p.quotaUsed, p.quotaFree)
    } catch (_: Exception) { null }

    private fun friendly(e: IOException, what: String): IOException = when (e) {
        is javax.net.ssl.SSLException -> IOException(
            if (server.insecureTls) App.str(R.string.failed_tls_error, what, e.message)
            else App.str(R.string.failed_the_server_s_certificate_isn, what, server.host),
        )
        is java.net.SocketTimeoutException -> IOException(App.str(R.string.failed_the_server_stopped_responding, what))
        is java.net.ConnectException -> IOException(App.str(R.string.couldn_t_reach_on_port, server.host, server.effectivePort))
        is java.net.UnknownHostException -> IOException(App.str(R.string.unknown_server_address, server.host))
        // Android's network security policy refused plain HTTP.
        is java.net.UnknownServiceException -> IOException(App.str(R.string.err_cleartext_blocked, server.host))
        else -> IOException(App.str(R.string.failed_3, what, e.message ?: e.javaClass.simpleName))
    }
}
