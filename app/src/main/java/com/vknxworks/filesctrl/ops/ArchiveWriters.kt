package com.vknxworks.filesctrl.ops

import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R

/** Formats FilesCtrl can create. [passwords]: the archive can be password-protected (AES-256). */
enum class ArchiveFormat(val label: String, val ext: String, val passwords: Boolean, val hint: String) {
    ZIP("ZIP", ".zip", true, App.str(R.string.opens_everywhere)),
    SEVENZ("7z", ".7z", true, App.str(R.string.smallest_password_also_hides_file)),
    TAR_GZ("TAR.GZ", ".tar.gz", false, App.str(R.string.usual_on_linux_keeps_permissions)),
    TAR_XZ("TAR.XZ", ".tar.xz", false, App.str(R.string.smaller_than_tar_gz_slower)),
    TAR_ZST("TAR.ZST", ".tar.zst", false, App.str(R.string.fast_and_small)),
    TAR("TAR", ".tar", false, App.str(R.string.no_compression)),
}

/** Receives folders and files, in order, and writes them into an archive. */
interface ArchiveSink : Closeable {
    /** [name] ends with "/". */
    fun dir(name: String, modified: Long)
    fun file(name: String, size: Long, modified: Long, input: InputStream, progress: (Int) -> Unit)
}

private fun copy(input: InputStream, out: OutputStream, progress: (Int) -> Unit) {
    val buf = ByteArray(256 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        progress(n)
    }
}

/** Plain zip with Android's built-in zip writer. */
class ZipSink(out: OutputStream) : ArchiveSink {
    private val zip = ZipOutputStream(out)
    override fun dir(name: String, modified: Long) {
        zip.putNextEntry(ZipEntry(name).apply { if (modified > 0) time = modified })
        zip.closeEntry()
    }
    override fun file(name: String, size: Long, modified: Long, input: InputStream, progress: (Int) -> Unit) {
        zip.putNextEntry(ZipEntry(name).apply { if (modified > 0) time = modified })
        copy(input, zip, progress)
        zip.closeEntry()
    }
    override fun close() = zip.close()
}

/** Password-protected zip, AES-256 (opens in 7-Zip, WinRAR, macOS Archive Utility 12+, and most file managers). */
class Zip4jSink(out: OutputStream, password: CharArray) : ArchiveSink {
    private val zip = net.lingala.zip4j.io.outputstream.ZipOutputStream(out, password)
    private fun params(name: String, modified: Long, encrypt: Boolean) = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = CompressionMethod.DEFLATE
        isEncryptFiles = encrypt
        if (encrypt) {
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        if (modified > 0) lastModifiedFileTime = modified
    }
    override fun dir(name: String, modified: Long) {
        zip.putNextEntry(params(name, modified, false))
        zip.closeEntry()
    }
    override fun file(name: String, size: Long, modified: Long, input: InputStream, progress: (Int) -> Unit) {
        zip.putNextEntry(params(name, modified, true))
        copy(input, zip, progress)
        zip.closeEntry()
    }
    override fun close() = zip.close()
}

/** 7z with LZMA2; with a password both contents and file names are encrypted (AES-256). */
class SevenZSink(private val z: SevenZOutputFile) : ArchiveSink {
    private val out = object : OutputStream() {
        override fun write(b: Int) = z.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = z.write(b, off, len)
    }
    override fun dir(name: String, modified: Long) {
        val e = SevenZArchiveEntry().apply {
            this.name = name.trimEnd('/')
            isDirectory = true
            if (modified > 0) lastModifiedDate = Date(modified)
        }
        z.putArchiveEntry(e)
        z.closeArchiveEntry()
    }
    override fun file(name: String, size: Long, modified: Long, input: InputStream, progress: (Int) -> Unit) {
        val e = SevenZArchiveEntry().apply {
            this.name = name
            isDirectory = false
            if (modified > 0) lastModifiedDate = Date(modified)
        }
        z.putArchiveEntry(e)
        copy(input, out, progress)
        z.closeArchiveEntry()
    }
    override fun close() {} // The caller finishes and closes the 7z file.
}

/** tar, optionally compressed. Long names and big files use the POSIX extensions every modern tar reads. */
class TarSink(raw: OutputStream, format: ArchiveFormat) : ArchiveSink {
    private val compressed: OutputStream = when (format) {
        ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(raw)
        ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(raw)
        ArchiveFormat.TAR_ZST -> ZstdCompressorOutputStream(raw)
        else -> raw
    }
    private val tar = TarArchiveOutputStream(compressed).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
        setAddPaxHeadersForNonAsciiNames(true)
    }
    override fun dir(name: String, modified: Long) {
        val e = TarArchiveEntry(name).apply { if (modified > 0) setModTime(modified) }
        tar.putArchiveEntry(e)
        tar.closeArchiveEntry()
    }
    override fun file(name: String, size: Long, modified: Long, input: InputStream, progress: (Int) -> Unit) {
        val e = TarArchiveEntry(name).apply {
            this.size = size
            if (modified > 0) setModTime(modified)
        }
        tar.putArchiveEntry(e)
        // tar needs the exact size up front; refuse to write a different amount rather than corrupt the archive.
        var written = 0L
        copy(input, tar) { n -> written += n; progress(n) }
        if (written != size) throw java.io.IOException(App.str(R.string.changed_size_while_it_was_being, name.substringAfterLast('/')))
        tar.closeArchiveEntry()
    }
    override fun close() {
        tar.finish()
        tar.close()
    }
}
