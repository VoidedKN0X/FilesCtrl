package com.vknxworks.filesctrl.fs

import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** A place in one of the file systems. */
data class Location(val vfsId: String, val path: String) {
    val key: String get() = "$vfsId|$path"

    companion object {
        fun fromKey(key: String): Location? {
            val i = key.indexOf('|')
            if (i <= 0) return null
            // Saved by older versions, when only NFS existed.
            val vfs = key.substring(0, i).let { if (it.startsWith("nfs:")) "net:" + it.removePrefix("nfs:") else it }
            return Location(vfs, key.substring(i + 1))
        }
    }
}

data class FileEntry(
    val vfsId: String,
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    /** Milliseconds since the epoch. */
    val modified: Long,
    /** Unix mode bits (type + permissions), or 0 if unknown. */
    val mode: Int = 0,
    val uid: Int = -1,
    val gid: Int = -1,
    val isLink: Boolean = false,
) {
    val hidden: Boolean get() = name.startsWith(".")
    val ext: String get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
    val mime: String get() = Mime.of(this)
    val location: Location get() = Location(vfsId, path)
    val parentLocation: Location get() = Location(vfsId, Paths.parent(path))
    val perms: Int get() = mode and 0x1FF
}

data class Space(val total: Long, val free: Long)

/**
 * A file system FilesCtrl can browse. Calls block, so run them off the main thread. Failures are IOExceptions
 * with a message fit to show; a missing path is a FileNotFoundException.
 */
interface Vfs {
    val id: String
    /** Name shown for the file system. */
    val title: String
    val isRemote: Boolean
    val canChmod: Boolean

    /** The topmost path of the volume/share that contains [path], and the label for it in the breadcrumbs. */
    fun rootOf(path: String): Pair<String, String>

    fun list(dir: String): List<FileEntry>
    fun stat(path: String): FileEntry
    fun openRead(path: String): InputStream
    /** Creates or truncates. */
    fun openWrite(path: String): OutputStream
    fun mkdir(path: String)
    fun createFile(path: String)
    /** Deletes one file or one empty folder. */
    fun delete(path: String, isDir: Boolean)
    /** Renames/moves within this file system. */
    fun rename(from: String, to: String)
    fun setModified(path: String, time: Long) {}
    fun chmod(path: String, mode: Int) { throw UnsupportedOperationException("Not supported here") }
    fun space(path: String): Space?

    fun exists(path: String): Boolean = try { stat(path); true } catch (_: FileNotFoundException) { false }
}

object Paths {
    fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"
    fun parent(path: String): String {
        val t = path.trimEnd('/')
        val i = t.lastIndexOf('/')
        return if (i <= 0) "/" else t.substring(0, i)
    }
    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

    /** True if [child] is [dir] itself or somewhere below it. */
    fun isWithin(child: String, dir: String): Boolean {
        val d = dir.trimEnd('/')
        return child == d || child.startsWith("$d/") || d.isEmpty()
    }

    fun validName(name: String): String? = when {
        name.isBlank() -> "Enter a name"
        name == "." || name == ".." -> "That name isn't allowed"
        name.contains('/') -> "Names can't contain /"
        name.contains('\u0000') -> "Invalid character"
        name.toByteArray().size > 255 -> "Name is too long"
        else -> null
    }

    /** "name.ext" -> "name (1).ext", "name (2).ext", ... until [exists] says no. */
    fun unique(dir: String, name: String, exists: (String) -> Boolean): String {
        if (!exists(join(dir, name))) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 1
        while (true) {
            val n = "$base ($i)$ext"
            if (!exists(join(dir, n))) return n
            i++
        }
    }
}

object Mime {
    private val extra = mapOf(
        "kt" to "text/x-kotlin", "kts" to "text/x-kotlin", "md" to "text/markdown", "log" to "text/plain",
        "conf" to "text/plain", "cfg" to "text/plain", "ini" to "text/plain", "yaml" to "text/yaml", "yml" to "text/yaml",
        "toml" to "text/plain", "sh" to "text/x-shellscript", "py" to "text/x-python", "rs" to "text/plain",
        "go" to "text/plain", "ts" to "text/plain", "properties" to "text/plain", "gradle" to "text/plain",
        "env" to "text/plain", "nfo" to "text/plain", "srt" to "application/x-subrip", "mkv" to "video/x-matroska",
        "flac" to "audio/flac", "opus" to "audio/ogg", "heic" to "image/heic", "avif" to "image/avif",
        "7z" to "application/x-7z-compressed", "rar" to "application/vnd.rar", "apk" to "application/vnd.android.package-archive",
    )

    fun of(e: FileEntry): String = if (e.isDir) "inode/directory" else ofName(e.name)

    fun ofName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return extra[ext] ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    enum class Kind { Folder, Image, Video, Audio, Text, Code, Pdf, Archive, Apk, Document, Sheet, Slides, Other }

    private val codeExt = setOf("kt", "kts", "java", "c", "h", "cpp", "hpp", "py", "js", "ts", "sh", "rs", "go", "json", "xml", "html", "css", "yaml", "yml", "toml", "gradle", "sql")
    private val archiveExt = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst", "lz4", "iso")

    fun kind(e: FileEntry): Kind {
        if (e.isDir) return Kind.Folder
        val m = e.mime
        val x = e.ext
        return when {
            m.startsWith("image/") -> Kind.Image
            m.startsWith("video/") -> Kind.Video
            m.startsWith("audio/") -> Kind.Audio
            x == "pdf" -> Kind.Pdf
            x == "apk" -> Kind.Apk
            x in archiveExt -> Kind.Archive
            x in codeExt -> Kind.Code
            m.startsWith("text/") -> Kind.Text
            x in setOf("doc", "docx", "odt", "rtf") -> Kind.Document
            x in setOf("xls", "xlsx", "ods", "csv") -> Kind.Sheet
            x in setOf("ppt", "pptx", "odp") -> Kind.Slides
            else -> Kind.Other
        }
    }

    fun isTextLike(e: FileEntry): Boolean {
        val k = kind(e)
        return k == Kind.Text || k == Kind.Code || e.mime == "application/json" || e.mime == "application/xml"
    }
}
