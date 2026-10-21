package com.vknxworks.filesctrl.fs

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** The archive needs a password ([wrong] = the one given didn't work). */
class ArchivePasswordException(val wrong: Boolean) :
    IOException(if (wrong) App.str(R.string.wrong_password_for_this_archive) else App.str(R.string.this_archive_is_password_protected))

/** Where an archive's bytes come from: a stream for one pass, or a local file for random access. */
interface ArchiveSource {
    val name: String
    /** Size of the archive file itself. */
    val size: Long
    fun openStream(): InputStream
    /** A local copy (or the file itself) for formats that need random access (zip, 7z, rar). */
    fun localFile(): File
}

/**
 * Any archive as a read-only folder tree: zip (with ZipCrypto/AES passwords), 7z (with passwords), rar (RAR4),
 * tar, cpio and ar, plain or compressed with gzip, bzip2, xz, lzma, zstd, lz4, brotli, snappy or Unix compress,
 * and single compressed files (.gz, .xz, ... of something that isn't an archive), which show as one file inside.
 *
 * Entry names are cleaned up: "." segments dropped, and any entry whose name would climb out (".."), is empty,
 * or contains NUL is left out, so nothing can be read or extracted outside the folder it belongs to.
 */
class ArchiveVfs(override val id: String, private val source: ArchiveSource) : Vfs {
    override val title: String get() = source.name
    override val isRemote = false
    override val canChmod = false

    /** Set by the UI after asking; used by every read. */
    @Volatile var password: CharArray? = null

    enum class Format { ZIP, SEVENZ, RAR, STREAM, SINGLE }

    private class Node(
        val path: String, val name: String, var isDir: Boolean, var size: Long, var mtime: Long, var mode: Int,
        /** Name of the entry inside the archive (null for folders that only exist implicitly). */
        var key: String?, var isLink: Boolean = false,
    )

    private val lock = Any()
    private var nodes: Map<String, Node>? = null
    private var children: Map<String, List<Node>> = emptyMap()
    private var format: Format? = null
    /** For STREAM / SINGLE: the compressor around the archive, if any (commons-compress name). */
    private var compressor: String? = null
    /** For STREAM: the archive format (tar, cpio, ar, ...). */
    private var archiver: String? = null
    /** Symbolic links in a tar that extraction left out. */
    @Volatile var skippedLinks = 0
        private set

    /** Entries left out because their names weren't safe. */
    @Volatile var skippedUnsafe = 0
        private set

    val formatLabel: String
        get() {
            index()
            return when (format) {
                Format.ZIP -> "ZIP"
                Format.SEVENZ -> "7z"
                Format.RAR -> "RAR"
                Format.STREAM -> (archiver ?: "tar").uppercase() + (compressor?.let { " · " + compressorLabel(it) } ?: "")
                Format.SINGLE -> App.str(R.string.compressed_file, compressorLabel(compressor ?: ""))
                null -> ""
            }
        }

    override fun rootOf(path: String) = "/" to source.name

    /** The format, once the archive has been read (never starts reading it). */
    val loadedFormatLabel: String? get() = if (nodes == null) null else formatLabel

    // ---------- Index ----------

    private fun index(): Map<String, Node> {
        nodes?.let { return it }
        synchronized(lock) {
            nodes?.let { return it }
            detect()
            val map = LinkedHashMap<String, Node>()
            map["/"] = Node("/", source.name, true, 0, 0, 0x41ED, null)
            fun add(rawName: String, isDir: Boolean, size: Long, mtime: Long, mode: Int, isLink: Boolean = false) {
                val parts = rawName.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
                if (parts.isEmpty() || parts.any { !Paths.isSafeName(it) }) { if (rawName.trim('/', '.').isNotEmpty()) skippedUnsafe++; return }
                var p = ""
                for ((i, part) in parts.withIndex()) {
                    p += "/$part"
                    val last = i == parts.size - 1
                    val existing = map[p]
                    if (last) {
                        if (existing != null) {
                            // A folder seen implicitly before, or a duplicate entry: the later one wins.
                            existing.isDir = isDir; existing.size = size; existing.mtime = mtime; existing.mode = mode
                            existing.key = rawName; existing.isLink = isLink
                        } else map[p] = Node(p, part, isDir, size, mtime, mode, rawName, isLink)
                    } else if (existing == null) {
                        map[p] = Node(p, part, true, 0, mtime, 0x41ED, null)
                    }
                }
            }
            when (format) {
                Format.ZIP -> withZip { z ->
                    val headers = z.fileHeaders
                    if (headers.any { it.isEncrypted } && password == null) throw ArchivePasswordException(false)
                    for (h in headers) add(h.fileName, h.isDirectory, h.uncompressedSize, h.lastModifiedTimeEpoch, if (h.isDirectory) 0x41ED else 0x81A4)
                }
                Format.SEVENZ -> withSevenZ { z ->
                    // File names can be readable while the contents are encrypted: ask for the password up front.
                    // The coders are only known once decoding starts, so try the first file's stream.
                    if (password == null) {
                        z.entries.firstOrNull { it.hasStream() && it.size > 0 }?.let { first ->
                            try { z.getInputStream(first).use { it.read() } }
                            catch (_: org.apache.commons.compress.PasswordRequiredException) { throw ArchivePasswordException(false) }
                        }
                    }
                    for (e in z.entries) {
                        add(e.name ?: continue, e.isDirectory, if (e.hasStream()) e.size else 0,
                            if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0, if (e.isDirectory) 0x41ED else 0x81A4)
                    }
                }
                Format.RAR -> withRar { r ->
                    if (r.isPasswordProtected && password == null) throw ArchivePasswordException(false)
                    for (h in r.fileHeaders) {
                        add(h.fileName, h.isDirectory, h.fullUnpackSize, h.mTime?.time ?: 0, if (h.isDirectory) 0x41ED else 0x81A4)
                    }
                }
                Format.STREAM -> streamEntries { e, _ ->
                    val tar = e as? TarArchiveEntry
                    val mode = tar?.mode?.let { m -> (if (e.isDirectory) 0x4000 else if (tar.isSymbolicLink) 0xA000 else 0x8000) or (m and 0xFFF) }
                        ?: if (e.isDirectory) 0x41ED else 0x81A4
                    add(e.name, e.isDirectory, if (e.isDirectory) 0 else e.size.coerceAtLeast(0),
                        e.lastModifiedDate?.time ?: 0, mode, isLink = tar?.isSymbolicLink == true)
                    true
                }
                Format.SINGLE -> add(singleName(), false, singleSize(), 0, 0x81A4)
                null -> {}
            }
            val kids = HashMap<String, MutableList<Node>>()
            for (n in map.values) if (n.path != "/") kids.getOrPut(Paths.parent(n.path)) { ArrayList() }.add(n)
            children = kids
            nodes = map
            return map
        }
    }

    /** Works out the format from the first bytes (not the file name), so misnamed archives still open. */
    private fun detect() {
        if (format != null) return
        BufferedInputStream(source.openStream(), 64 * 1024).use { s ->
            s.mark(16)
            val head = ByteArray(8)
            var n = 0
            while (n < 8) { val r = s.read(head, n, 8 - n); if (r < 0) break; n += r }
            s.reset()
            fun starts(vararg b: Int) = n >= b.size && b.indices.all { head[it] == b[it].toByte() }
            when {
                starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> { format = Format.SEVENZ; return }
                starts(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> {
                    if (n >= 7 && head[6] == 0x01.toByte()) throw IOException(App.str(R.string.rar5_archives_can_t_be_opened_yet))
                    format = Format.RAR; return
                }
                starts(0x50, 0x4B, 0x03, 0x04) || starts(0x50, 0x4B, 0x05, 0x06) || starts(0x50, 0x4B, 0x07, 0x08) -> { format = Format.ZIP; return }
            }
            val comp = try { CompressorStreamFactory.detect(s) } catch (_: Exception) { null }
            if (comp != null) {
                compressor = comp
                val inner = BufferedInputStream(CompressorStreamFactory().createCompressorInputStream(comp, s, true), 64 * 1024)
                archiver = try { ArchiveStreamFactory.detect(inner) } catch (_: Exception) { null }
                format = if (archiver != null) Format.STREAM else Format.SINGLE
                return
            }
            archiver = try { ArchiveStreamFactory.detect(s) } catch (_: Exception) { null }
            if (archiver == "zip") { format = Format.ZIP; return }
            if (archiver == "7z") { format = Format.SEVENZ; return }
            if (archiver != null) { format = Format.STREAM; return }
        }
        throw IOException(App.str(R.string.isn_t_an_archive_filesctrl_can_open, source.name))
    }

    private fun singleName(): String {
        val n = source.name
        val lower = n.lowercase()
        val ext = listOf(".gz", ".gzip", ".bz2", ".bzip2", ".xz", ".lzma", ".zst", ".zstd", ".lz4", ".br", ".sz", ".z", ".deflate")
            .firstOrNull { lower.endsWith(it) }
        return (if (ext != null) n.dropLast(ext.length) else "$n.out").ifEmpty { "file" }
    }

    /** Size of a single compressed file, by decompressing it (only for small ones; 0 = unknown). */
    private fun singleSize(): Long = try {
        if (source.size > 64L * 1024 * 1024) 0 else decompressed().use { s ->
            val buf = ByteArray(256 * 1024)
            var total = 0L
            while (true) { val r = s.read(buf); if (r < 0) break; total += r }
            total
        }
    } catch (_: Exception) { 0 }

    private fun decompressed(): InputStream {
        val s = BufferedInputStream(source.openStream(), 64 * 1024)
        return CompressorStreamFactory().createCompressorInputStream(compressor!!, s, true)
    }

    // ---------- Format helpers ----------

    private inline fun <T> withZip(block: (net.lingala.zip4j.ZipFile) -> T): T {
        val z = net.lingala.zip4j.ZipFile(source.localFile(), password)
        try { return block(z) } catch (e: net.lingala.zip4j.exception.ZipException) { throw mapZip(e) } finally { try { z.close() } catch (_: Exception) {} }
    }

    private fun mapZip(e: net.lingala.zip4j.exception.ZipException): IOException = when {
        e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD -> ArchivePasswordException(password != null)
        e.message.orEmpty().contains("password", true) -> ArchivePasswordException(password != null)
        else -> IOException(App.str(R.string.couldn_t_read_the_zip, e.message))
    }

    private fun openSevenZ(): SevenZFile {
        val b = SevenZFile.builder().setFile(source.localFile())
        password?.let { b.setPassword(it) }
        return try { b.get() } catch (e: PasswordRequiredException) { throw ArchivePasswordException(false) } catch (e: IOException) {
            // With encrypted headers, a wrong password makes the file list unreadable.
            throw if (password != null) ArchivePasswordException(true) else map7z(e)
        }
    }

    private inline fun <T> withSevenZ(block: (SevenZFile) -> T): T {
        val z = openSevenZ()
        try { return block(z) } catch (e: IOException) { throw map7z(e) } finally { try { z.close() } catch (_: Exception) {} }
    }

    private fun map7z(e: IOException): IOException = when {
        e is ArchivePasswordException -> e
        e is PasswordRequiredException -> ArchivePasswordException(false)
        e is FileNotFoundException -> e
        // A wrong key decrypts to garbage, which shows up as a checksum, CRC or "corrupt data" error.
        password != null -> ArchivePasswordException(true)
        else -> e
    }

    private fun openRar(): Archive = try {
        Archive(source.localFile(), password?.let { String(it) })
    } catch (e: UnsupportedRarV5Exception) {
        throw IOException(App.str(R.string.rar5_archives_can_t_be_opened_yet))
    } catch (e: UnsupportedRarEncryptedException) {
        throw IOException(App.str(R.string.this_rar_archive_uses_encryption))
    } catch (e: com.github.junrar.exception.RarException) {
        val m = e.message.orEmpty()
        throw if (m.contains("password", true) || m.contains("crypt", true)) ArchivePasswordException(password != null)
        else IOException(App.str(R.string.couldn_t_read_the_rar_archive, m.ifBlank { e.javaClass.simpleName }))
    }

    private inline fun <T> withRar(block: (Archive) -> T): T {
        val r = openRar()
        try { return block(r) } finally { try { r.close() } catch (_: Exception) {} }
    }

    /** One pass over a stream archive; [block] returns false to stop. */
    private fun streamEntries(block: (ArchiveEntry, ArchiveInputStream<out ArchiveEntry>) -> Boolean) {
        val outer = BufferedInputStream(source.openStream(), 64 * 1024)
        val raw: InputStream = compressor?.let { BufferedInputStream(CompressorStreamFactory().createCompressorInputStream(it, outer, true), 64 * 1024) } ?: outer
        val ais = ArchiveStreamFactory().createArchiveInputStream<ArchiveInputStream<out ArchiveEntry>>(archiver!!, raw)
        ais.use {
            while (true) {
                val e = ais.nextEntry ?: break
                if (!ais.canReadEntryData(e)) continue
                if (!block(e, ais)) break
            }
        }
    }

    // ---------- Vfs ----------

    private fun node(path: String): Node {
        val p = Paths.normalize(path) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        return index()[p] ?: throw FileNotFoundException(App.str(R.string.not_found, path))
    }

    private fun entry(n: Node) = FileEntry(id, n.path, n.name, n.isDir, n.size, n.mtime, n.mode, isLink = n.isLink)

    override fun list(dir: String): List<FileEntry> {
        val n = node(dir)
        if (!n.isDir) throw IOException(App.str(R.string.not_a_folder))
        return children[n.path].orEmpty().map(::entry)
    }

    override fun stat(path: String): FileEntry = entry(node(path))

    override fun openRead(path: String): InputStream {
        val n = node(path)
        if (n.isDir) throw IOException(App.str(R.string.that_s_a_folder))
        val key = n.key ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        return when (format) {
            Format.ZIP -> {
                val z = net.lingala.zip4j.ZipFile(source.localFile(), password)
                try {
                    val h = z.getFileHeader(key) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
                    if (h.isEncrypted && password == null) throw ArchivePasswordException(false)
                    val s = z.getInputStream(h)
                    object : FilterInputStream(s) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int = try { super.read(b, off, len) } catch (e: net.lingala.zip4j.exception.ZipException) { throw mapZip(e) }
                        override fun close() { try { super.close() } finally { z.close() } }
                    }
                } catch (e: net.lingala.zip4j.exception.ZipException) {
                    z.close(); throw mapZip(e)
                } catch (e: IOException) {
                    z.close(); throw e
                }
            }
            Format.SEVENZ -> {
                val z = openSevenZ()
                try {
                    val e = z.entries.firstOrNull { it.name == key } ?: throw FileNotFoundException(App.str(R.string.not_found, path))
                    val s = z.getInputStream(e)
                    object : FilterInputStream(s) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int = try { super.read(b, off, len) } catch (e: IOException) { throw map7z(e) }
                        override fun close() { try { super.close() } finally { z.close() } }
                    }
                } catch (e: IOException) { z.close(); throw map7z(e) }
            }
            Format.RAR -> {
                val r = openRar()
                try {
                    val h = r.fileHeaders.firstOrNull { it.fileName == key } ?: throw FileNotFoundException(App.str(R.string.not_found, path))
                    val s = r.getInputStream(h)
                    object : FilterInputStream(s) {
                        override fun close() { try { super.close() } finally { r.close() } }
                    }
                } catch (e: IOException) { r.close(); throw e }
            }
            Format.STREAM -> {
                // Stream formats have no index on disk: read up to the entry and hand out its data.
                val outer = BufferedInputStream(source.openStream(), 64 * 1024)
                val raw: InputStream = compressor?.let { BufferedInputStream(CompressorStreamFactory().createCompressorInputStream(it, outer, true), 64 * 1024) } ?: outer
                val ais = ArchiveStreamFactory().createArchiveInputStream<ArchiveInputStream<out ArchiveEntry>>(archiver!!, raw)
                try {
                    while (true) {
                        val e = ais.nextEntry ?: throw FileNotFoundException(App.str(R.string.not_found, path))
                        if (e.name == key && ais.canReadEntryData(e)) break
                    }
                    object : FilterInputStream(ais) {}
                } catch (e: IOException) { ais.close(); throw e }
            }
            Format.SINGLE -> decompressed()
            null -> throw IOException(App.str(R.string.unknown_archive))
        }
    }

    /** Reads from [offset], for streaming a file inside the archive to another app. */
    fun openRead(path: String, offset: Long): InputStream {
        val s = openRead(path)
        var left = offset
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val skipped = s.skip(left)
            if (skipped > 0) { left -= skipped; continue }
            val r = s.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
        return s
    }

    /**
     * Calls [block] for every entry in archive order, with a stream for files (null for folders), in a single
     * pass. Much faster than [openRead] per file for stream formats like .tar.gz. Paths are already cleaned up.
     */
    fun forEachEntry(block: (FileEntry, InputStream?) -> Unit) {
        val idx = index()
        val byKey = HashMap<String, Node>()
        for (n in idx.values) n.key?.let { byKey[it] = n }
        fun nodeFor(key: String): Node? = byKey[key]
        val empty = { java.io.ByteArrayInputStream(ByteArray(0)) }
        when (format) {
            Format.ZIP -> withZip { z ->
                for (h in z.fileHeaders) {
                    val n = nodeFor(h.fileName) ?: continue
                    if (n.isDir) { block(entry(n), null); continue }
                    z.getInputStream(h).use { s -> block(entry(n), s) }
                }
            }
            Format.SEVENZ -> withSevenZ { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = nodeFor(e.name ?: continue) ?: continue
                    if (n.isDir) { block(entry(n), null); continue }
                    if (!e.hasStream()) { block(entry(n), empty()); continue } // An empty file, not a folder.
                    val s = object : InputStream() {
                        override fun read(): Int = z.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = z.read(b, off, len)
                    }
                    block(entry(n), s)
                }
            }
            Format.RAR -> withRar { r ->
                for (h in r.fileHeaders) {
                    val n = nodeFor(h.fileName) ?: continue
                    if (n.isDir) { block(entry(n), null); continue }
                    r.getInputStream(h).use { s -> block(entry(n), s) }
                }
            }
            Format.STREAM -> streamEntries { e, ais ->
                val n = nodeFor(e.name)
                when {
                    n == null -> {}
                    n.isLink -> skippedLinks++ // Links aren't recreated: they could point anywhere on the phone.
                    n.isDir -> block(entry(n), null)
                    else -> block(entry(n), object : FilterInputStream(ais) { override fun close() {} })
                }
                true
            }
            Format.SINGLE -> {
                val n = idx.values.first { it.path != "/" }
                decompressed().use { s -> block(entry(n), s) }
            }
            null -> {}
        }
    }

    /** Total uncompressed size of everything (0 if unknown), for progress. */
    fun totalSize(): Long = index().values.sumOf { if (it.isDir) 0L else it.size.coerceAtLeast(0) }

    /** Name for the folder an "Extract here" creates: "photos.tar.gz" -> "photos". */
    val baseName: String
        get() {
            val n = source.name
            val lower = n.lowercase()
            val ext = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.lz4", ".tar.lzma", ".tar.br", ".tar.z",
                ".tgz", ".tbz2", ".tbz", ".txz", ".tzst", ".tlz", ".zip", ".7z", ".rar", ".tar", ".cpio", ".ar", ".jar", ".apk",
                ".gz", ".bz2", ".xz", ".zst", ".lz4", ".lzma", ".br").firstOrNull { lower.endsWith(it) }
            return (if (ext != null) n.dropLast(ext.length) else n.substringBeforeLast('.')).ifBlank { "extracted" }
        }

    /** True when this is a single compressed file (".log.gz"), not an archive of several. */
    val isSingleFile: Boolean get() { index(); return format == Format.SINGLE }

    private fun readOnly(): Nothing = throw IOException(App.str(R.string.archives_are_read_only_here_extract))
    override fun openWrite(path: String): OutputStream = readOnly()
    override fun mkdir(path: String) = readOnly()
    override fun createFile(path: String) = readOnly()
    override fun delete(path: String, isDir: Boolean) = readOnly()
    override fun rename(from: String, to: String) = readOnly()
    override fun space(path: String): Space? = null

    companion object {
        const val PREFIX = "arc:"

        /** The id of the archive file at [loc], viewed as a folder. Base64 keeps the "|" in location keys out. */
        fun idFor(loc: Location): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(loc.key.toByteArray())

        /** The archive file an archive id refers to. */
        fun archiveOf(id: String): Location? = try {
            Location.fromKey(String(Base64.getUrlDecoder().decode(id.removePrefix(PREFIX))))
        } catch (_: Exception) { null }

        fun compressorLabel(c: String): String = when (c) {
            "gz" -> "gzip"; "bzip2" -> "bzip2"; "xz" -> "xz"; "lzma" -> "LZMA"; "zstd" -> "Zstandard"; "lz4-framed", "lz4-block" -> "LZ4"
            "br" -> "Brotli"; "snappy-framed", "snappy-raw" -> "Snappy"; "z" -> "compress"; "deflate", "deflate64" -> "Deflate"
            else -> c
        }
    }
}
