package com.vknxworks.filesctrl.nfs

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

/** An NFS server found on the network. */
data class FoundServer(
    val address: String,
    val hostname: String?,
    /** rpcbind/portmap (111) answered: NFSv3 exports can be listed. */
    val portmap: Boolean,
    /** Port 2049 answered (NFSv3 or v4). */
    val nfs: Boolean,
    /** null = couldn't be listed (often an NFSv4-only server). */
    val exports: List<String>?,
)

/**
 * Finds NFS servers two ways at once: a portmapper broadcast (fast, but some servers and routers ignore it)
 * and a TCP probe of ports 111 and 2049 across the local subnet. Then asks each server for its exports.
 */
object Discovery {

    data class Subnet(val base: Int, val prefix: Int, val self: Int) {
        val label: String get() = "${ip(base)}/$prefix"
        val hosts: List<Int>
            get() {
                val size = 1 shl (32 - prefix)
                return (1 until size - 1).map { base + it }.filter { it != self }
            }
    }

    private fun ip(v: Int) = "${v ushr 24 and 255}.${v ushr 16 and 255}.${v ushr 8 and 255}.${v and 255}"

    /** The IPv4 subnet of the active network, narrowed to at most a /24 around this phone. */
    fun subnet(ctx: Context): Subnet? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val b = la.address.address
        val self = (b[0].toInt() and 255 shl 24) or (b[1].toInt() and 255 shl 16) or (b[2].toInt() and 255 shl 8) or (b[3].toInt() and 255)
        val prefix = la.prefixLength.coerceIn(24, 30)
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        return Subnet(self and mask, prefix, self)
    }

    private fun open(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) { false }

    /**
     * Scans and reports each server as soon as its exports are known. [onProgress] gets (probed, total).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun scan(ctx: Context, onProgress: (Int, Int) -> Unit, onFound: (FoundServer) -> Unit) = withContext(Dispatchers.IO) {
        val sub = subnet(ctx)
        val seen = HashSet<String>()
        val probe = Semaphore(48)
        val ask = Semaphore(4)

        coroutineScope {
            fun report(addr: String, portmap: Boolean, nfs: Boolean) {
                synchronized(seen) { if (!seen.add(addr)) return }
                launch {
                    ask.withPermit {
                        val exports = if (portmap) {
                            try { NfsNative.exports(addr, 0, 4)?.toList() } catch (_: Throwable) { null }
                        } else null
                        val name = try {
                            InetAddress.getByName(addr).canonicalHostName.takeIf { it != addr }
                        } catch (_: Exception) { null }
                        onFound(FoundServer(addr, name, portmap, nfs, exports))
                    }
                }
            }

            // 1. Broadcast: servers that answer are certainly running the portmapper.
            launch {
                val list = try { NfsNative.findServers()?.toList() } catch (_: Throwable) { null }
                list.orEmpty().forEach { a -> report(a, portmap = true, nfs = open(a, 2049, 800)) }
            }

            // 2. Subnet probe.
            val hosts = sub?.hosts.orEmpty()
            var done = 0
            onProgress(0, hosts.size)
            hosts.map { h ->
                async {
                    probe.withPermit {
                        coroutineContext.ensureActive()
                        val a = ip(h)
                        val nfs = open(a, 2049, 350)
                        val pm = open(a, 111, 350)
                        if (nfs || pm) report(a, pm, nfs)
                        synchronized(this@Discovery) { done++; onProgress(done, hosts.size) }
                    }
                }
            }.awaitAll()
        }
    }

    fun subnetLabel(ctx: Context): String? = subnet(ctx)?.label
}
