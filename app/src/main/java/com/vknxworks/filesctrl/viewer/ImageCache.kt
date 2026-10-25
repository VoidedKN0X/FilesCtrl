package com.vknxworks.filesctrl.viewer

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.fs.FileEntry
import com.vknxworks.filesctrl.fs.LocalVfs
import com.vknxworks.filesctrl.fs.Mime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** A picture in any of FilesCtrl's file systems, for the image viewer. */
data class VfsImage(val entry: FileEntry)

/**
 * Pictures on servers (and inside archives) are read through FilesCtrl's own file systems and kept in a private
 * cache folder, so swiping back and forth or opening the info sheet doesn't download them again. Copies are
 * dropped after a day, and the oldest go first once the folder passes [MAX_BYTES].
 */
object ImageCache {
    private const val MAX_BYTES = 256L * 1024 * 1024
    private val locks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var lastPrune = 0L

    private val dir: File get() = File(App.context.cacheDir, "viewer").apply { mkdirs() }

    fun key(e: FileEntry): String =
        MessageDigest.getInstance("SHA-256").digest("${e.vfsId}|${e.path}|${e.size}|${e.modified}".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(40)

    /** The picture as a file on the phone: the file itself when it's local, else a cached copy (downloaded once). */
    suspend fun file(e: FileEntry): File {
        if (e.vfsId == LocalVfs.ID) return File(e.path)
        val k = key(e)
        val f = File(dir, k)
        return locks.getOrPut(k) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                if (f.isFile && (e.size <= 0 || f.length() == e.size)) {
                    f.setLastModified(System.currentTimeMillis())
                    return@withContext f
                }
                prune()
                if (e.size > MAX_BYTES) throw IOException(App.str(R.string.viewer_too_large))
                val tmp = File(dir, "$k.part")
                try {
                    App.vfs.get(e.vfsId).openRead(e.path).use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (!tmp.renameTo(f)) throw IOException(App.str(R.string.viewer_cache_failed))
                } finally {
                    tmp.delete()
                }
                f
            }
        }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        if (now - lastPrune < 60_000) return
        lastPrune = now
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        files.forEach { if (now - it.lastModified() > 24 * 3600_000L) it.delete() }
        var total = files.filter { it.exists() }.sumOf { it.length() }
        for (f in files.filter { it.exists() }.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /** Everything the viewer cached (e.g. when the app locks, so no pictures from servers stay on the phone). */
    fun clear() { runCatching { File(App.context.cacheDir, "viewer").listFiles()?.forEach { it.delete() } } }
}

/** Loads [VfsImage]s for Coil: local files directly, everything else through [ImageCache]. */
class VfsImageFetcher(private val data: VfsImage, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val e = data.entry
        val f = ImageCache.file(e)
        return SourceFetchResult(
            source = ImageSource(f.source().buffer(), options.fileSystem),
            mimeType = Mime.ofName(e.name),
            dataSource = if (e.vfsId == LocalVfs.ID) DataSource.DISK else DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<VfsImage> {
        override fun create(data: VfsImage, options: Options, imageLoader: ImageLoader): Fetcher = VfsImageFetcher(data, options)
    }

    companion object {
        val keyer: Keyer<VfsImage> = object : Keyer<VfsImage> {
            override fun key(data: VfsImage, options: Options): String = "vfs:" + ImageCache.key(data.entry)
        }
    }
}
