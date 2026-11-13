package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.App
import com.vknxworks.filesctrl.R
import com.vknxworks.filesctrl.crypt.Cryptomator
import com.vknxworks.filesctrl.crypt.Cryptomator.UnsupportedVaultException
import com.vknxworks.filesctrl.crypt.VaultCryptor
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

/** The vault is locked: its keys aren't in memory. The browser asks for the password. */
class VaultLockedException(val vaultId: String) : IOException(App.str(R.string.vault_locked))

/**
 * A Cryptomator vault (format 8, SIV_GCM) in any other file system — phone storage, a server, even another vault —
 * shown as a normal folder tree once unlocked. Everything is encrypted and decrypted on the fly; nothing decrypted
 * is written anywhere but into the vault itself.
 *
 * The keys live only in [cryptor] while unlocked; [lock] destroys them. Decrypted names are checked like names from
 * a server: anything empty, "." / "..", or with "/" or NUL is left out, so no name can climb out of a folder.
 */
class CryptoVfs(override val id: String, private val innerSource: () -> Vfs, private val root: String) : Vfs {
    /** Looked up on every use, so a server whose settings changed is reached through its new connection. */
    private val inner: Vfs get() = innerSource()

    override val title: String get() = Paths.name(root)
    override val isRemote: Boolean get() = inner.isRemote
    override val canChmod = false

    /** The file system and folder the vault's encrypted files are in. */
    val innerVfs: Vfs get() = inner
    val vaultFolder: String get() = root

    @Volatile private var cryptor: VaultCryptor? = null
    @Volatile private var threshold = Cryptomator.DEFAULT_SHORTENING_THRESHOLD

    val unlocked: Boolean get() = cryptor != null

    /** Cleartext folder path -> directory ID, so deep folders don't read every dir.c9r on the way each time. */
    private val dirIds = HashMap<String, String>()

    /** Entries left out of listings because their decrypted names weren't safe (or didn't decrypt). */
    @Volatile var skipped = 0
        private set

    // ---------------------------------------------------------------- lock / unlock

    /**
     * Unlocks with [password] (slow: scrypt; call off the main thread). Throws [VaultPasswordException] for a wrong
     * password and an IOException with a readable message for vaults FilesCtrl can't open.
     */
    fun unlock(password: CharArray) {
        val jwt = readSmall(Paths.join(root, Cryptomator.VAULT_FILE), 64 * 1024).toString(Charsets.UTF_8)
        val keyName = mapUnsupported { Cryptomator.masterkeyFileName(jwt) }
        if (!Paths.isSafeName(keyName) || keyName.contains('\\')) throw IOException(App.str(R.string.vault_damaged))
        val json = readSmall(Paths.join(root, keyName), 64 * 1024).toString(Charsets.UTF_8)
        val key = try {
            mapUnsupported { Cryptomator.unlockMasterkey(json, password) }
        } catch (_: Cryptomator.WrongPasswordException) {
            throw VaultPasswordException(true)
        }
        try {
            val cfg = mapUnsupported { Cryptomator.verifyConfig(jwt, key) }
            synchronized(dirIds) { dirIds.clear() }
            threshold = cfg.shorteningThreshold
            cryptor = VaultCryptor(key)
        } catch (e: Throwable) {
            key.destroy()
            throw e
        }
    }

    private inline fun <T> mapUnsupported(block: () -> T): T = try { block() } catch (e: UnsupportedVaultException) {
        throw IOException(when (e.reason) {
            UnsupportedVaultException.Reason.Format -> App.str(R.string.vault_unsupported_format)
            UnsupportedVaultException.Reason.Cipher -> App.str(R.string.vault_unsupported_cipher)
            UnsupportedVaultException.Reason.TooMuchMemory -> App.str(R.string.vault_too_much_memory)
            UnsupportedVaultException.Reason.Damaged -> App.str(R.string.vault_damaged)
        })
    }

    /** Wipes the keys. Open streams stop working; the next use asks for the password again. */
    fun lock() {
        val c = cryptor
        cryptor = null
        c?.key?.destroy()
        synchronized(dirIds) { dirIds.clear() }
    }

    private fun c(): VaultCryptor = cryptor?.takeIf { !it.key.destroyed } ?: throw VaultLockedException(id)

    // ---------------------------------------------------------------- inner helpers

    private fun ip(rel: String) = Paths.join(root, rel)

    private fun readSmall(path: String, max: Int): ByteArray = inner.openRead(path).use { s ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException(App.str(R.string.vault_damaged))
        }
        out.toByteArray()
    }

    private fun writeSmall(path: String, data: ByteArray) = inner.openWrite(path).use { it.write(data) }

    private fun segments(path: String): List<String> {
        val p = Paths.normalize(path) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        return p.split('/').filter { it.isNotEmpty() }
    }

    /** A node (file or folder) as stored: its name in the parent's ciphertext folder, shortened or not. */
    private inner class Node(val parentDir: String, val longName: String) {
        val shortened = longName.length > threshold
        val storedName = if (shortened) Cryptomator.shortenedName(longName) else longName
        val path = Paths.join(parentDir, storedName)
        val contentPath get() = if (shortened) Paths.join(path, Cryptomator.CONTENTS_FILE) else path
        val dirFile get() = Paths.join(path, Cryptomator.DIR_FILE)
        val nameFile get() = Paths.join(path, Cryptomator.NAME_FILE)
    }

    private fun cipherDir(dirId: String) = ip(c().dirPath(dirId))

    /** Directory ID of the cleartext folder [path] ("" for the vault's top folder). */
    private fun dirIdOf(path: String): String {
        val segs = segments(path)
        var id = ""
        var p = ""
        for (s in segs) {
            p += "/$s"
            val cached = synchronized(dirIds) { dirIds[p] }
            if (cached != null) { id = cached; continue }
            val node = node(id, s)
            val raw = try { readSmall(node.dirFile, Cryptomator.MAX_DIR_ID) } catch (e: FileNotFoundException) {
                // Either missing, a file, or a symbolic link (symlink.c9r), which FilesCtrl doesn't follow.
                if (inner.exists(node.path)) throw IOException(App.str(R.string.not_a_folder))
                throw FileNotFoundException(App.str(R.string.not_found, path))
            }
            id = raw.toString(Charsets.US_ASCII)
            synchronized(dirIds) { dirIds[p] = id }
        }
        return id
    }

    private fun node(parentId: String, name: String) = Node(cipherDir(parentId), c().encryptName(name, parentId))

    private fun nodeOf(path: String): Pair<String, Node> {
        val segs = segments(path)
        if (segs.isEmpty()) throw IOException(App.str(R.string.the_top_folder_can_t_be_renamed))
        val parentId = dirIdOf("/" + segs.dropLast(1).joinToString("/"))
        return parentId to node(parentId, segs.last())
    }

    private fun forget(path: String) {
        val p = Paths.normalize(path) ?: return
        synchronized(dirIds) { dirIds.keys.removeAll { it == p || it.startsWith("$p/") } }
    }

    private enum class Kind { File, Dir, Other }

    /** What [n] is, with its stat for size and date; null when it doesn't exist. */
    private fun kind(n: Node): Pair<Kind, FileEntry>? {
        val st = try { inner.stat(n.path) } catch (_: FileNotFoundException) { return null }
        if (!n.shortened) return (if (st.isDir) Kind.Dir else Kind.File) to st
        if (!st.isDir) return Kind.Other to st
        val kids = inner.list(n.path)
        kids.firstOrNull { it.name == Cryptomator.CONTENTS_FILE && !it.isDir }?.let { return Kind.File to it }
        if (kids.any { it.name == Cryptomator.DIR_FILE }) return Kind.Dir to st
        return Kind.Other to st
    }

    private fun entry(path: String, name: String, kind: Kind, st: FileEntry): FileEntry {
        val size = if (kind == Kind.File) Cryptomator.cleartextSize(st.size).coerceAtLeast(0) else 0
        return FileEntry(id, path, name, kind == Kind.Dir, size, st.modified)
    }

    // ---------------------------------------------------------------- Vfs

    override fun rootOf(path: String) = "/" to title

    override fun list(dir: String): List<FileEntry> {
        val cr = c()
        val clearDir = Paths.normalize(dir) ?: throw FileNotFoundException(App.str(R.string.not_found, dir))
        val dirId = dirIdOf(clearDir)
        val cdir = cipherDir(dirId)
        val kids = try { inner.list(cdir) } catch (e: FileNotFoundException) {
            throw IOException(App.str(R.string.vault_folder_missing))
        }
        val out = ArrayList<FileEntry>(kids.size)
        val seen = HashSet<String>()
        var bad = 0
        for (k in kids) {
            when {
                k.name == Cryptomator.DIR_ID_BACKUP -> continue
                k.name.endsWith(Cryptomator.C9R) -> {
                    val name = cr.decryptName(k.name, dirId)
                    if (name == null || !Paths.isSafeName(name) || !seen.add(name)) { bad++; continue }
                    // A .c9r folder is a directory (or, rarely, a symbolic link, which opening reports).
                    out += entry(Paths.join(clearDir, name), name, if (k.isDir) Kind.Dir else Kind.File, k)
                }
                k.name.endsWith(Cryptomator.C9S) && k.isDir -> {
                    val inside = try { inner.list(k.path) } catch (_: IOException) { bad++; continue }
                    val longName = try {
                        readSmall(Paths.join(k.path, Cryptomator.NAME_FILE), 16 * 1024).toString(Charsets.UTF_8)
                    } catch (_: IOException) { bad++; continue }
                    // The folder's name must be the hash of the long name it claims to stand for.
                    if (Cryptomator.shortenedName(longName) != k.name) { bad++; continue }
                    val name = cr.decryptName(longName, dirId)
                    if (name == null || !Paths.isSafeName(name) || !seen.add(name)) { bad++; continue }
                    val contents = inside.firstOrNull { it.name == Cryptomator.CONTENTS_FILE && !it.isDir }
                    when {
                        contents != null -> out += entry(Paths.join(clearDir, name), name, Kind.File, contents)
                        inside.any { it.name == Cryptomator.DIR_FILE } -> out += entry(Paths.join(clearDir, name), name, Kind.Dir, k)
                        else -> bad++ // Symbolic links aren't shown.
                    }
                }
                else -> bad++
            }
        }
        skipped = bad
        return out
    }

    override fun stat(path: String): FileEntry {
        c()
        val segs = segments(path)
        if (segs.isEmpty()) {
            val st = inner.stat(root)
            return FileEntry(id, "/", title, true, 0, st.modified)
        }
        val (_, n) = nodeOf(path)
        val (kind, st) = kind(n) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        if (kind == Kind.Other) throw FileNotFoundException(App.str(R.string.not_found, path))
        return entry("/" + segs.joinToString("/"), segs.last(), kind, st)
    }

    override fun openRead(path: String): InputStream {
        val cr = c()
        val (_, n) = nodeOf(path)
        val (kind, _) = kind(n) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        if (kind != Kind.File) throw IOException(App.str(R.string.that_s_a_folder))
        val s = inner.openRead(n.contentPath)
        return try { cr.decrypting(s) } catch (e: Throwable) { s.close(); throw e }
    }

    /**
     * Reads from cleartext [offset]: decrypts the header, jumps to the chunk that holds the offset and drops the
     * bytes before it. Used to stream vault files to other apps with seeking.
     */
    fun openRead(path: String, offset: Long): InputStream {
        if (offset <= 0) return openRead(path)
        val cr = c()
        val (_, n) = nodeOf(path)
        val header = inner.openRead(n.contentPath).use { cr.readHeader(it) }
        val chunk = offset / Cryptomator.CHUNK
        val ctOffset = Cryptomator.HEADER_SIZE + chunk * Cryptomator.CT_CHUNK
        val raw = when (val v = inner) {
            is RemoteVfs -> v.openRead(n.contentPath, ctOffset)
            is CryptoVfs -> v.openRead(n.contentPath, ctOffset)
            else -> v.openRead(n.contentPath).also { skipFully(it, ctOffset) }
        }
        val s = cr.decrypting(raw, header, chunk)
        skipFully(s, offset - chunk * Cryptomator.CHUNK)
        return s
    }

    private fun skipFully(s: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val k = s.skip(left)
            if (k <= 0) { if (s.read() < 0) break else left-- } else left -= k
        }
    }

    override fun openWrite(path: String): OutputStream {
        val cr = c()
        val (_, n) = nodeOf(path)
        val existing = kind(n)
        if (existing != null && existing.first != Kind.File) throw IOException(App.str(R.string.that_s_a_folder))
        if (n.shortened && existing == null) {
            if (!inner.exists(n.path)) inner.mkdir(n.path)
            writeSmall(n.nameFile, n.longName.toByteArray(Charsets.UTF_8))
        }
        val raw = inner.openWrite(n.contentPath)
        return try { cr.encrypting(raw) } catch (e: Throwable) { raw.close(); throw e }
    }

    override fun createFile(path: String) {
        c()
        val (_, n) = nodeOf(path)
        if (kind(n) != null) throw IOException(App.str(R.string.already_exists_2))
        openWrite(path).close()
    }

    override fun mkdir(path: String) {
        val cr = c()
        val (_, n) = nodeOf(path)
        if (inner.exists(n.path)) throw IOException(App.str(R.string.already_exists_2))
        val newId = UUID.randomUUID().toString()
        // The ciphertext folder first: if anything fails later there's at worst an unused empty folder.
        val rel = cr.dirPath(newId)
        val d = ip("d")
        if (!inner.exists(d)) inner.mkdir(d)
        val two = ip(rel.substringBeforeLast('/'))
        if (!inner.exists(two)) inner.mkdir(two)
        val cdir = ip(rel)
        inner.mkdir(cdir)
        // Backup of the ID, so Cryptomator can recover the folder if dir.c9r is ever lost.
        writeSmall(Paths.join(cdir, Cryptomator.DIR_ID_BACKUP), cr.encryptFile(newId.toByteArray(Charsets.US_ASCII)))
        inner.mkdir(n.path)
        if (n.shortened) writeSmall(n.nameFile, n.longName.toByteArray(Charsets.UTF_8))
        writeSmall(n.dirFile, newId.toByteArray(Charsets.US_ASCII))
    }

    override fun delete(path: String, isDir: Boolean) {
        c()
        val (_, n) = nodeOf(path)
        val (kind, _) = kind(n) ?: throw FileNotFoundException(App.str(R.string.not_found, path))
        when (kind) {
            Kind.File -> {
                inner.delete(n.contentPath, false)
                if (n.shortened) {
                    runCatching { inner.delete(n.nameFile, false) }
                    inner.delete(n.path, true)
                }
            }
            Kind.Dir -> {
                val id = dirIdOf(path)
                val cdir = cipherDir(id)
                val inside = try { inner.list(cdir) } catch (_: FileNotFoundException) { emptyList() }
                if (inside.any { it.name != Cryptomator.DIR_ID_BACKUP }) throw IOException(App.str(R.string.vault_folder_not_empty))
                inside.forEach { inner.delete(it.path, it.isDir) }
                if (inner.exists(cdir)) inner.delete(cdir, true)
                // The two-letter folder above it goes too once it's empty.
                val two = Paths.parent(cdir)
                runCatching { if (inner.list(two).isEmpty()) inner.delete(two, true) }
                inner.delete(n.dirFile, false)
                if (n.shortened) runCatching { inner.delete(n.nameFile, false) }
                inner.delete(n.path, true)
                forget(path)
            }
            Kind.Other -> throw IOException(App.str(R.string.not_supported_here))
        }
    }

    override fun rename(from: String, to: String) {
        c()
        val (_, src) = nodeOf(from)
        val (_, dst) = nodeOf(to)
        val (kind, _) = kind(src) ?: throw FileNotFoundException(App.str(R.string.not_found, from))
        if (kind == Kind.Other) throw IOException(App.str(R.string.not_supported_here))
        if (src.path == dst.path) return
        if (inner.exists(dst.path)) throw IOException(App.str(R.string.already_exists, Paths.name(to)))
        if (!src.shortened && !dst.shortened) {
            // Files and folders alike: one rename of the encrypted node (a folder's ID stays the same).
            inner.rename(src.path, dst.path)
        } else {
            if (dst.shortened || kind == Kind.Dir) inner.mkdir(dst.path)
            if (dst.shortened) writeSmall(dst.nameFile, dst.longName.toByteArray(Charsets.UTF_8))
            if (kind == Kind.Dir) inner.rename(src.dirFile, dst.dirFile) else inner.rename(src.contentPath, dst.contentPath)
            if (src.shortened) runCatching { inner.delete(src.nameFile, false) }
            if (src.shortened || kind == Kind.Dir) inner.delete(src.path, true)
        }
        if (kind == Kind.Dir) forget(from)
    }

    override fun setModified(path: String, time: Long) {
        c()
        val (_, n) = nodeOf(path)
        val k = kind(n) ?: return
        inner.setModified(if (k.first == Kind.File) n.contentPath else n.path, time)
    }

    override fun space(path: String): Space? = inner.space(root)

    companion object {
        const val PREFIX = "crypt:"

        /** The id of the vault whose folder is at [loc]. Base64 keeps the "|" in location keys out. */
        fun idFor(loc: Location): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(loc.key.toByteArray())

        /** The vault folder a vault id refers to. */
        fun folderOf(id: String): Location? = try {
            Location.fromKey(String(Base64.getUrlDecoder().decode(id.removePrefix(PREFIX))))
        } catch (_: Exception) { null }

        /** A folder listing that looks like a Cryptomator vault (the two files every password vault has). */
        fun looksLikeVault(entries: List<FileEntry>): Boolean =
            entries.any { !it.isDir && it.name == Cryptomator.VAULT_FILE } && entries.any { !it.isDir && it.name == Cryptomator.MASTERKEY_FILE }
    }
}

/** The vault needs a password ([wrong] = the one given didn't work). */
class VaultPasswordException(val wrong: Boolean) :
    IOException(if (wrong) App.str(R.string.that_password_didn_t_work_try_again) else App.str(R.string.vault_locked))

/** Random reads of a vault file for other apps: keeps one decrypting stream and reopens it at the chunk on seeks. */
class VaultFileReader(private val vfs: CryptoVfs, private val path: String, private val length: Long) : RandomFile {
    private var stream: InputStream? = null
    private var pos = 0L

    override fun size() = length

    @Synchronized
    override fun read(offset: Long, buf: ByteArray, len: Int): Int {
        if (offset >= length) return 0
        val s = stream?.takeIf { pos == offset } ?: run {
            stream?.let { try { it.close() } catch (_: Exception) {} }
            vfs.openRead(path, offset).also { stream = it; pos = offset }
        }
        var done = 0
        while (done < len) {
            val n = s.read(buf, done, len - done)
            if (n < 0) break
            done += n
        }
        pos += done
        return done
    }

    @Synchronized
    override fun close() {
        stream?.let { try { it.close() } catch (_: Exception) {} }
        stream = null
    }
}
