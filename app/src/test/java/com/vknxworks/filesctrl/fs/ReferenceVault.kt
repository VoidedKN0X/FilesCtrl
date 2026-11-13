package com.vknxworks.filesctrl.fs

import com.vknxworks.filesctrl.crypt.AesSiv
import org.bouncycastle.crypto.generators.SCrypt
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A plain folder on the test machine as a [Vfs] (LocalVfs needs Android). */
class DirVfs(private val base: File) : Vfs {
    override val id = "dir"
    override val title = "dir"
    override val isRemote = false
    override val canChmod = false
    private fun f(p: String) = File(base, p.trimStart('/'))
    private fun e(p: String, f: File) = FileEntry(id, p, f.name, f.isDirectory, if (f.isDirectory) 0 else f.length(), f.lastModified())
    override fun rootOf(path: String) = "/" to "dir"
    override fun list(dir: String): List<FileEntry> {
        val d = f(dir)
        if (!d.isDirectory) throw FileNotFoundException(dir)
        return d.listFiles()!!.map { e(Paths.join(dir, it.name), it) }
    }
    override fun stat(path: String): FileEntry = f(path).let { if (!it.exists()) throw FileNotFoundException(path) else e(path, it) }
    override fun openRead(path: String): InputStream = f(path).let { if (!it.isFile) throw FileNotFoundException(path) else it.inputStream() }
    override fun openWrite(path: String): OutputStream = f(path).outputStream()
    override fun mkdir(path: String) { if (!f(path).mkdir()) throw IOException("mkdir $path") }
    override fun createFile(path: String) { if (!f(path).createNewFile()) throw IOException("exists") }
    override fun delete(path: String, isDir: Boolean) { if (!f(path).delete()) throw IOException("delete $path") }
    override fun rename(from: String, to: String) { if (f(to).exists() || !f(from).renameTo(f(to))) throw IOException("rename $from") }
    override fun space(path: String): Space? = null
}

/**
 * Builds and reads a Cryptomator vault (format 8, SIV_GCM) straight from the published specification, with the
 * platform's own GCM, scrypt, AES key wrap, SHA-1 and a separate base32, so the app's code is checked against an
 * independent writer and reader. Only AES-SIV is shared (it has its own RFC test).
 */
class ReferenceVault(val dir: File, val password: String = "correct horse", private val n: Int = 1024, val threshold: Int = 220) {
    val enc = ByteArray(32).also(SecureRandom()::nextBytes)
    val mac = ByteArray(32).also(SecureRandom()::nextBytes)
    private val rnd = SecureRandom()

    fun create(format: Int = 8, combo: String = "SIV_GCM", tamperVersionMac: Boolean = false) {
        dir.mkdirs()
        val salt = ByteArray(8).also(rnd::nextBytes)
        val kek = SCrypt.generate(password.toByteArray(Charsets.UTF_8), salt, n, 8, 1, 32)
        fun wrap(k: ByteArray) = Cipher.getInstance("AESWrap").run { init(Cipher.WRAP_MODE, SecretKeySpec(kek, "AES")); wrap(SecretKeySpec(k, "AES")) }
        val vmac = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(mac, "HmacSHA256")); doFinal(ByteBuffer.allocate(4).putInt(if (tamperVersionMac) 998 else 999).array()) }
        val b64 = Base64.getEncoder()
        File(dir, "masterkey.cryptomator").writeText(
            """
            {
              "version": 999,
              "scryptSalt": "${b64.encodeToString(salt)}",
              "scryptCostParam": $n,
              "scryptBlockSize": 8,
              "primaryMasterKey": "${b64.encodeToString(wrap(enc))}",
              "hmacMasterKey": "${b64.encodeToString(wrap(mac))}",
              "versionMac": "${b64.encodeToString(vmac)}"
            }
            """.trimIndent(),
        )
        val u = Base64.getUrlEncoder().withoutPadding()
        val header = u.encodeToString("""{"kid":"masterkeyfile:masterkey.cryptomator","typ":"JWT","alg":"HS256"}""".toByteArray())
        val payload = u.encodeToString("""{"format":$format,"shorteningThreshold":$threshold,"jti":"ce6c3ff5-1e75-4d0a-8f2b-0b6e3a7c9a10","cipherCombo":"$combo"}""".toByteArray())
        val sig = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(enc + mac, "HmacSHA256")); doFinal("$header.$payload".toByteArray()) }
        File(dir, "vault.cryptomator").writeText("$header.$payload.${u.encodeToString(sig)}")
        dirFor("").mkdirs()
    }

    // ---------- names and folders ----------

    private fun siv(pt: ByteArray, vararg ad: ByteArray) = AesSiv.encrypt(mac, enc, pt, *ad)
    private fun unsiv(ct: ByteArray, vararg ad: ByteArray) = AesSiv.decrypt(mac, enc, ct, *ad)

    /** RFC 4648 base32, written differently from the app's (via BigInteger bits). */
    fun base32(b: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bits = BigInteger(1, b).toString(2).padStart(b.size * 8, '0')
        val padded = bits.padEnd((bits.length + 4) / 5 * 5, '0')
        return padded.chunked(5).joinToString("") { alphabet[it.toInt(2)].toString() }
    }

    fun dirFor(dirId: String): File {
        val h = base32(MessageDigest.getInstance("SHA-1").digest(siv(dirId.toByteArray())))
        return File(dir, "d/${h.substring(0, 2)}/${h.substring(2)}")
    }

    fun encName(name: String, parentId: String) = Base64.getUrlEncoder().encodeToString(siv(name.toByteArray(), parentId.toByteArray())) + ".c9r"

    fun decName(stored: String, parentId: String) = String(unsiv(Base64.getUrlDecoder().decode(stored.removeSuffix(".c9r")), parentId.toByteArray()))

    /** The node for [name] in [parentId]: "<x>.c9r", or "<sha1>.c9s" with name.c9s when the name is too long. */
    fun node(name: String, parentId: String): File {
        val long = encName(name, parentId)
        val parent = dirFor(parentId)
        if (long.length <= threshold) return File(parent, long)
        val short = Base64.getUrlEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(long.toByteArray())) + ".c9s"
        return File(parent, short).also { it.mkdirs(); File(it, "name.c9s").writeText(long) }
    }

    fun addDir(name: String, parentId: String, id: String = java.util.UUID.randomUUID().toString()): String {
        val n = node(name, parentId)
        n.mkdirs()
        File(n, "dir.c9r").writeText(id)
        dirFor(id).mkdirs()
        return id
    }

    fun addFile(name: String, parentId: String, content: ByteArray) {
        val n = node(name, parentId)
        val target = if (n.name.endsWith(".c9s")) File(n, "contents.c9r") else n
        target.writeBytes(encryptContent(content))
    }

    // ---------- contents ----------

    fun encryptContent(clear: ByteArray): ByteArray {
        val headerNonce = ByteArray(12).also(rnd::nextBytes)
        val contentKey = ByteArray(32).also(rnd::nextBytes)
        val out = java.io.ByteArrayOutputStream()
        out.write(headerNonce)
        out.write(gcm(Cipher.ENCRYPT_MODE, enc, headerNonce, null, ByteArray(8) { -1 } + contentKey))
        var i = 0L
        var off = 0
        while (off < clear.size) {
            val len = minOf(32768, clear.size - off)
            val nonce = ByteArray(12).also(rnd::nextBytes)
            out.write(nonce)
            out.write(gcm(Cipher.ENCRYPT_MODE, contentKey, nonce, ByteBuffer.allocate(20).putLong(i).put(headerNonce).array(), clear.copyOfRange(off, off + len)))
            off += len; i++
        }
        return out.toByteArray()
    }

    fun decryptContent(ct: ByteArray): ByteArray {
        val headerNonce = ct.copyOfRange(0, 12)
        val payload = gcm(Cipher.DECRYPT_MODE, enc, headerNonce, null, ct.copyOfRange(12, 68))
        check(payload.copyOfRange(0, 8).all { it == (-1).toByte() })
        val contentKey = payload.copyOfRange(8, 40)
        val out = java.io.ByteArrayOutputStream()
        var off = 68
        var i = 0L
        while (off < ct.size) {
            val len = minOf(32768 + 28, ct.size - off)
            val nonce = ct.copyOfRange(off, off + 12)
            out.write(gcm(Cipher.DECRYPT_MODE, contentKey, nonce, ByteBuffer.allocate(20).putLong(i).put(headerNonce).array(), ct.copyOfRange(off + 12, off + len)))
            off += len; i++
        }
        return out.toByteArray()
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray?, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            aad?.let { updateAAD(it) }
            doFinal(data)
        }
}
