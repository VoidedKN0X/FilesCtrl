package com.vknxworks.filesctrl.fs

import java.io.IOException
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/**
 * A server whose identity (SSH host key or TLS certificate) isn't trusted yet. The UI shows [fingerprint] and,
 * if the user accepts, saves it on the server and connects again. Nothing secret is sent before this is thrown.
 */
open class UntrustedIdentityException(val fingerprint: String, message: String) : IOException(message)

/** First connection to an SFTP server: its host key needs confirming. */
class HostKeyUnknownException(fingerprint: String) : UntrustedIdentityException(
    fingerprint,
    App.str(R.string.this_sftp_server_hasn_t_been_trusted, fingerprint),
)

/** A TLS server with a certificate the system doesn't trust, and no pin saved yet. */
class CertUnknownException(fingerprint: String) : UntrustedIdentityException(
    fingerprint,
    App.str(R.string.the_server_s_certificate_isn_t, fingerprint),
)

/** A pinned TLS certificate that no longer matches. */
class CertChangedException(val fingerprint: String) : IOException(
    App.str(R.string.cert_changed, fingerprint),
)

/** Trust-on-first-use certificate pinning for servers with self-signed certificates (FTPS, WebDAV over HTTPS). */
object TlsPin {
    /** "AB:CD:…" SHA-256 of the certificate. */
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    fun systemTrustManager(): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(null as KeyStore?)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Accepts a chain the system trusts (with its normal hostname checks), or one whose leaf matches [pin]
     * exactly. Remembers the last leaf it saw so a refusal can be turned into [CertUnknownException] /
     * [CertChangedException].
     */
    class PinningTrustManager(private val pin: String) : X509ExtendedTrustManager() {
        private val system = systemTrustManager()
        @Volatile private var lastLeaf: X509Certificate? = null
        /** The leaf certificate of the last chain this refused. */
        @Volatile private var refusedLeaf: X509Certificate? = null

        private fun pinned(chain: Array<out X509Certificate>?): Boolean {
            val leaf = chain?.firstOrNull() ?: return false
            lastLeaf = leaf
            return pin.isNotBlank() && sha256(leaf).equals(pin, ignoreCase = true)
        }

        private fun refuse(e: CertificateException): Nothing {
            refusedLeaf = lastLeaf
            throw CertificateException(if (pin.isBlank()) App.str(R.string.certificate_not_trusted_yet) else App.str(R.string.certificate_doesn_t_match_the_saved), e)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (pinned(chain)) return
            try { system.checkServerTrusted(chain, authType) } catch (e: CertificateException) { refuse(e) }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            if (pinned(chain)) return
            try {
                (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) { refuse(e) }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            if (pinned(chain)) return
            try {
                (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) { refuse(e) }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw CertificateException(App.str(R.string.not_a_server)) }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) { throw CertificateException(App.str(R.string.not_a_server)) }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) { throw CertificateException(App.str(R.string.not_a_server)) }
        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

        fun sslContext(): SSLContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(this@PinningTrustManager), null) }

        /**
         * Normal hostname checks, except that the pinned certificate is accepted for the configured [host] only
         * (a self-signed certificate often doesn't name the address it's reached at).
         */
        fun hostnameVerifier(host: String): HostnameVerifier {
            val default = HttpsURLConnection.getDefaultHostnameVerifier()
            return HostnameVerifier { h, session ->
                if (default.verify(h, session)) return@HostnameVerifier true
                val leaf = try { session.peerCertificates.firstOrNull() as? X509Certificate } catch (_: Exception) { null }
                h.equals(host, ignoreCase = true) && leaf != null && pin.isNotBlank() && sha256(leaf).equals(pin, ignoreCase = true)
            }
        }

        /** The error to show for a TLS failure, if it was this trust manager refusing the certificate. */
        fun explain(): IOException? {
            val leaf = refusedLeaf ?: return null
            refusedLeaf = null
            val fp = sha256(leaf)
            return if (pin.isBlank()) CertUnknownException(fp) else CertChangedException(fp)
        }
    }

    /** Finds an [UntrustedIdentityException] or [CertChangedException] in [e]'s causes. */
    fun identityError(e: Throwable): IOException? {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 10) {
            if (t is UntrustedIdentityException || t is CertChangedException || t is HostKeyChangedException) return t as IOException
            t = t.cause
        }
        return null
    }
}
