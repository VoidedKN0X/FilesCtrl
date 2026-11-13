package com.vknxworks.filesctrl.crypt

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.RFC3394WrapEngine
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.KeyParameter
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptomator vault format 8 with the SIV_GCM cipher combo, written from the published specification
 * (docs.cryptomator.org, "Security architecture" and "Vault cryptography"). No code from Cryptomator's own
 * cryptolib is used: that library is AGPL-3.0, which can't be combined into GPL-3.0-only FilesCtrl.
 *
 * - Names: AES-SIV (RFC 5297) with the parent directory ID as associated data, base64url, ".c9r".
 * - Directory paths: d/ + first 2 + next 30 characters of base32(sha1(AES-SIV(directory ID))).
 * - Contents: a 68-byte header (12-byte nonce, AES-GCM of 8 reserved 0xFF bytes and the 32-byte content key under
 *   the encryption masterkey, 16-byte tag), then 32 KiB cleartext chunks, each nonce(12) + ciphertext + tag(16),
 *   with AAD = chunk number (64-bit big-endian) + header nonce.
 */
object Cryptomator {
    const val VAULT_FORMAT = 8
    const val CIPHER_COMBO = "SIV_GCM"
    const val DEFAULT_SHORTENING_THRESHOLD = 220
    const val VAULT_FILE = "vault.cryptomator"
    const val MASTERKEY_FILE = "masterkey.cryptomator"
    const val DIR_FILE = "dir.c9r"
    const val DIR_ID_BACKUP = "dirid.c9r"
    const val NAME_FILE = "name.c9s"
    const val CONTENTS_FILE = "contents.c9r"
    const val SYMLINK_FILE = "symlink.c9r"
    const val C9R = ".c9r"
    const val C9S = ".c9s"

    const val HEADER_NONCE = 12
    const val HEADER_SIZE = 68
    const val CHUNK = 32 * 1024
    const val CHUNK_NONCE = 12
    const val TAG = 16
    const val CHUNK_OVERHEAD = CHUNK_NONCE + TAG
    const val CT_CHUNK = CHUNK + CHUNK_OVERHEAD

    /** Longest directory ID accepted from dir.c9r (the spec says at most 36 ASCII characters). */
    const val MAX_DIR_ID = 64

    /** Cleartext size of a file whose ciphertext is [ct] bytes; -1 if that size is impossible (damaged file). */
    fun cleartextSize(ct: Long): Long {
        if (ct < HEADER_SIZE) return -1
        val body = ct - HEADER_SIZE
        val full = body / CT_CHUNK
        val rest = body % CT_CHUNK
        if (rest in 1 until CHUNK_OVERHEAD) return -1
        return full * CHUNK + if (rest == 0L) 0 else rest - CHUNK_OVERHEAD
    }

    fun ciphertextSize(clear: Long): Long {
        val full = clear / CHUNK
        val rest = clear % CHUNK
        return HEADER_SIZE + full * CT_CHUNK + if (rest == 0L) 0 else rest + CHUNK_OVERHEAD
    }

    // ---------------------------------------------------------------- platform ciphers

    /** The platform's own (hardware-accelerated) provider on Android, not the pure-Java Bouncy Castle FilesCtrl adds first. */
    internal fun cipher(transform: String): Cipher =
        runCatching { Cipher.getInstance(transform, "AndroidOpenSSL") }.getOrNull() ?: Cipher.getInstance(transform)

    internal val random = SecureRandom()

    // ---------------------------------------------------------------- base encodings

    private val b64url = Base64.getUrlEncoder()
    private val b64urlDec = Base64.getUrlDecoder()

    fun base64Url(b: ByteArray): String = b64url.encodeToString(b)

    /** Accepts padded and unpadded base64url; null if it isn't valid. */
    fun fromBase64Url(s: String): ByteArray? = try { b64urlDec.decode(s) } catch (_: IllegalArgumentException) { null }

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** RFC 4648 base32 (upper case, padded only when the input isn't a multiple of 5 bytes). */
    fun base32(data: ByteArray): String {
        val sb = StringBuilder((data.size + 4) / 5 * 8)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }

    fun sha1(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(b)

    /** The name a node gets when its encrypted name is longer than the shortening threshold. */
    fun shortenedName(longName: String): String = base64Url(sha1(longName.toByteArray(Charsets.UTF_8))) + C9S

    // ---------------------------------------------------------------- masterkey file

    class WrongPasswordException : GeneralSecurityException("Wrong password")

    /** The vault's settings are outside what FilesCtrl supports (newer format, other cipher, damaged file). */
    class UnsupportedVaultException(val reason: Reason, detail: String = "") : IOException(reason.name + if (detail.isEmpty()) "" else ": $detail") {
        enum class Reason { Format, Cipher, Damaged, TooMuchMemory }
    }

    /** Most memory scrypt may need (128 · r · N bytes): Cryptomator's defaults need 32 MiB. */
    const val MAX_SCRYPT_MEMORY = 256L * 1024 * 1024

    /**
     * Opens a masterkey.cryptomator file: scrypt(password, salt, N, r, p = 1) gives the key-encryption key, which
     * unwraps (RFC 3394) the encryption and MAC masterkeys; the version MAC is checked against downgrades.
     */
    fun unlockMasterkey(json: String, password: CharArray): Masterkey {
        val m = try { FlatJson.parse(json) } catch (e: IOException) { throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "masterkey") }
        fun str(k: String) = m[k] as? String ?: throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, k)
        fun int(k: String) = (m[k] as? Long)?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
            ?: throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, k)
        val version = int("version")
        val salt = b64(str("scryptSalt"))
        val n = int("scryptCostParam")
        val r = int("scryptBlockSize")
        val wrappedEnc = b64(str("primaryMasterKey"))
        val wrappedMac = b64(str("hmacMasterKey"))
        val versionMac = b64(str("versionMac"))
        if (n < 2 || n and (n - 1) != 0 || r !in 1..64) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "scrypt")
        if (128L * r * n > MAX_SCRYPT_MEMORY) throw UnsupportedVaultException(UnsupportedVaultException.Reason.TooMuchMemory)

        val kek = scrypt(password, salt, n, r)
        try {
            val enc = unwrap(kek, wrappedEnc)
            val mac = unwrap(kek, wrappedMac)
            val key = Masterkey(enc, mac)
            enc.fill(0); mac.fill(0)
            if (!MessageDigest.isEqual(versionMac, key.versionMac(version))) {
                key.destroy()
                throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "versionMac")
            }
            return key
        } finally {
            kek.fill(0)
        }
    }

    private fun b64(s: String): ByteArray = try { Base64.getDecoder().decode(s) } catch (_: IllegalArgumentException) {
        throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "base64")
    }

    internal fun scrypt(password: CharArray, salt: ByteArray, n: Int, r: Int): ByteArray {
        val pw = utf8(password)
        try {
            return SCrypt.generate(pw, salt, n, r, 1, 32)
        } finally {
            pw.fill(0)
        }
    }

    /** UTF-8 bytes of [chars] without leaving a String copy of the password behind. */
    internal fun utf8(chars: CharArray): ByteArray {
        val bb = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
        val out = ByteArray(bb.remaining()).also { bb.get(it) }
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    private fun unwrap(kek: ByteArray, wrapped: ByteArray): ByteArray {
        if (wrapped.size != 40) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "wrapped key")
        val e = RFC3394WrapEngine(AESEngine.newInstance())
        e.init(false, KeyParameter(kek))
        return try { e.unwrap(wrapped, 0, wrapped.size) } catch (_: InvalidCipherTextException) { throw WrongPasswordException() }
    }

    internal fun wrap(kek: ByteArray, key: ByteArray): ByteArray {
        val e = RFC3394WrapEngine(AESEngine.newInstance())
        e.init(true, KeyParameter(kek))
        return e.wrap(key, 0, key.size)
    }

    // ---------------------------------------------------------------- vault.cryptomator (JWT)

    data class VaultConfig(val format: Int, val cipherCombo: String, val shorteningThreshold: Int, val jti: String)

    /** The masterkey file named in the token's "kid" ("masterkeyfile:<name>"), read before the key is known. */
    fun masterkeyFileName(jwt: String): String {
        val parts = jwt.trim().split('.')
        if (parts.size != 3) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "vault.cryptomator")
        val header = jsonPart(parts[0])
        val kid = header["kid"] as? String ?: return MASTERKEY_FILE
        if (!kid.startsWith("masterkeyfile:")) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Format, kid.substringBefore(':'))
        return kid.removePrefix("masterkeyfile:")
    }

    /** Checks the token's HMAC signature with the masterkey (enc + mac) and reads the vault settings from it. */
    fun verifyConfig(jwt: String, key: Masterkey): VaultConfig {
        val parts = jwt.trim().split('.')
        if (parts.size != 3) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "vault.cryptomator")
        val header = jsonPart(parts[0])
        val alg = when (header["alg"]) {
            "HS256" -> "HmacSHA256"; "HS384" -> "HmacSHA384"; "HS512" -> "HmacSHA512"
            else -> throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "alg")
        }
        val sig = fromBase64Url(parts[2]) ?: throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "signature")
        val expected = key.withRaw { raw ->
            Mac.getInstance(alg).run {
                init(SecretKeySpec(raw, alg))
                doFinal("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            }
        }
        if (!MessageDigest.isEqual(sig, expected)) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "signature")
        val p = jsonPart(parts[1])
        val format = (p["format"] as? Long)?.toInt() ?: throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "format")
        val combo = p["cipherCombo"] as? String ?: ""
        val threshold = (p["shorteningThreshold"] as? Long)?.toInt()?.takeIf { it in 36..100_000 } ?: DEFAULT_SHORTENING_THRESHOLD
        if (format != VAULT_FORMAT) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Format, format.toString())
        if (combo != CIPHER_COMBO) throw UnsupportedVaultException(UnsupportedVaultException.Reason.Cipher, combo)
        return VaultConfig(format, combo, threshold, p["jti"] as? String ?: "")
    }

    private fun jsonPart(part: String): Map<String, Any?> {
        val raw = fromBase64Url(part) ?: throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "jwt")
        return try { FlatJson.parse(String(raw, Charsets.UTF_8)) } catch (_: IOException) {
            throw UnsupportedVaultException(UnsupportedVaultException.Reason.Damaged, "jwt")
        }
    }
}

/**
 * A vault's two 256-bit masterkeys, only in memory while the vault is unlocked. [destroy] overwrites them; after
 * that every use throws.
 */
class Masterkey(enc: ByteArray, mac: ByteArray) {
    init { require(enc.size == 32 && mac.size == 32) }
    private val raw = ByteArray(64).also { enc.copyInto(it, 0); mac.copyInto(it, 32) }
    @Volatile var destroyed = false
        private set

    private fun check() { if (destroyed) throw IllegalStateException("Vault locked") }

    /** enc || mac, the JWT signing key. The array is wiped after [block]. */
    internal fun <T> withRaw(block: (ByteArray) -> T): T {
        check()
        val copy = raw.copyOf()
        try { return block(copy) } finally { copy.fill(0) }
    }

    internal fun encKey(): ByteArray { check(); return raw.copyOfRange(0, 32) }
    internal fun macKey(): ByteArray { check(); return raw.copyOfRange(32, 64) }

    fun versionMac(version: Int): ByteArray {
        val mk = macKey()
        try {
            return Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(mk, "HmacSHA256"))
                doFinal(ByteBuffer.allocate(4).putInt(version).array())
            }
        } finally { mk.fill(0) }
    }

    fun destroy() {
        raw.fill(0)
        destroyed = true
    }
}

/** Name, directory and content encryption with one vault's keys. */
class VaultCryptor(val key: Masterkey) {

    // ---------- names ----------

    /** "name" in the directory [dirId] -> "<base64url>.c9r". Names are NFC-normalised first, like Cryptomator does. */
    fun encryptName(name: String, dirId: String): String {
        val clear = Normalizer.normalize(name, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        return siv { mac, ctr -> Cryptomator.base64Url(AesSiv.encrypt(mac, ctr, clear, dirId.toByteArray(Charsets.UTF_8))) } + Cryptomator.C9R
    }

    /** The cleartext name of "<base64url>.c9r" in [dirId], or null if it isn't one of this vault's names. */
    fun decryptName(ciphertextName: String, dirId: String): String? {
        if (!ciphertextName.endsWith(Cryptomator.C9R)) return null
        val bytes = Cryptomator.fromBase64Url(ciphertextName.removeSuffix(Cryptomator.C9R)) ?: return null
        return try {
            siv { mac, ctr -> String(AesSiv.decrypt(mac, ctr, bytes, dirId.toByteArray(Charsets.UTF_8)), Charsets.UTF_8) }
        } catch (_: GeneralSecurityException) { null }
    }

    /** base32(sha1(AES-SIV(dirId))): 32 characters, stored as d/<2>/<30>. */
    fun hashDirId(dirId: String): String {
        val enc = siv { mac, ctr -> AesSiv.encrypt(mac, ctr, dirId.toByteArray(Charsets.UTF_8)) }
        return Cryptomator.base32(Cryptomator.sha1(enc))
    }

    /** Path of the ciphertext directory for [dirId], relative to the vault folder: "d/AB/CDEF…". */
    fun dirPath(dirId: String): String = hashDirId(dirId).let { "d/${it.substring(0, 2)}/${it.substring(2, 32)}" }

    private inline fun <T> siv(block: (mac: ByteArray, ctr: ByteArray) -> T): T {
        val mac = key.macKey()
        val enc = key.encKey()
        try { return block(mac, enc) } finally { mac.fill(0); enc.fill(0) }
    }

    // ---------- file headers ----------

    class Header(val nonce: ByteArray, val contentKey: ByteArray) {
        fun wipe() = contentKey.fill(0)
    }

    fun newHeader(): Header = Header(
        ByteArray(Cryptomator.HEADER_NONCE).also(Cryptomator.random::nextBytes),
        ByteArray(32).also(Cryptomator.random::nextBytes),
    )

    fun encryptHeader(h: Header): ByteArray {
        val payload = ByteArray(40)
        for (i in 0 until 8) payload[i] = 0xFF.toByte()
        h.contentKey.copyInto(payload, 8)
        val enc = key.encKey()
        try {
            val c = Cryptomator.cipher("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"), GCMParameterSpec(128, h.nonce))
            return h.nonce + c.doFinal(payload)
        } finally {
            enc.fill(0); payload.fill(0)
        }
    }

    fun decryptHeader(b: ByteArray): Header {
        if (b.size != Cryptomator.HEADER_SIZE) throw IOException("Bad header")
        val nonce = b.copyOfRange(0, Cryptomator.HEADER_NONCE)
        val enc = key.encKey()
        try {
            val c = Cryptomator.cipher("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(enc, "AES"), GCMParameterSpec(128, nonce))
            val payload = try { c.doFinal(b, Cryptomator.HEADER_NONCE, b.size - Cryptomator.HEADER_NONCE) } catch (_: GeneralSecurityException) {
                throw VaultDataException()
            }
            val ck = payload.copyOfRange(8, 40)
            payload.fill(0)
            return Header(nonce, ck)
        } finally { enc.fill(0) }
    }

    // ---------- chunks ----------

    private fun aad(chunk: Long, headerNonce: ByteArray) = ByteBuffer.allocate(8 + headerNonce.size).putLong(chunk).put(headerNonce).array()

    /** Open streams stop as soon as the vault locks, even though they hold their own file's content key. */
    private fun checkOpen() { if (key.destroyed) throw IOException("Vault locked") }

    fun encryptChunk(clear: ByteArray, off: Int, len: Int, chunk: Long, h: Header, out: ByteArray = ByteArray(len + Cryptomator.CHUNK_OVERHEAD)): Int {
        checkOpen()
        val nonce = ByteArray(Cryptomator.CHUNK_NONCE).also(Cryptomator.random::nextBytes)
        val c = Cryptomator.cipher("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(h.contentKey, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad(chunk, h.nonce))
        nonce.copyInto(out, 0)
        val n = c.doFinal(clear, off, len, out, Cryptomator.CHUNK_NONCE)
        return Cryptomator.CHUNK_NONCE + n
    }

    /** Decrypts one chunk (nonce + ciphertext + tag) into [out]; returns the cleartext length. */
    fun decryptChunk(ct: ByteArray, len: Int, chunk: Long, h: Header, out: ByteArray): Int {
        checkOpen()
        if (len < Cryptomator.CHUNK_OVERHEAD) throw VaultDataException()
        val c =Cryptomator.cipher("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(h.contentKey, "AES"), GCMParameterSpec(128, ct, 0, Cryptomator.CHUNK_NONCE))
        c.updateAAD(aad(chunk, h.nonce))
        return try { c.doFinal(ct, Cryptomator.CHUNK_NONCE, len - Cryptomator.CHUNK_NONCE, out, 0) } catch (_: GeneralSecurityException) {
            throw VaultDataException()
        }
    }

    /** Reads and decrypts the 68-byte header at the start of [input]. */
    fun readHeader(input: InputStream): Header {
        val b = ByteArray(Cryptomator.HEADER_SIZE)
        if (readFully(input, b, b.size) != b.size) throw VaultDataException()
        return decryptHeader(b)
    }

    fun decrypting(input: InputStream, header: Header? = null, firstChunk: Long = 0): InputStream =
        DecryptingInputStream(this, input, header ?: readHeader(input), firstChunk)

    fun encrypting(out: OutputStream): OutputStream = EncryptingOutputStream(this, out)

    /** Encrypts [clear] as a whole file (header + chunks). */
    fun encryptFile(clear: ByteArray): ByteArray {
        val bo = java.io.ByteArrayOutputStream()
        encrypting(bo).use { it.write(clear) }
        return bo.toByteArray()
    }

    fun decryptFile(ct: ByteArray): ByteArray = decrypting(ct.inputStream()).use { it.readBytes() }

    companion object {
        fun readFully(input: InputStream, b: ByteArray, len: Int): Int {
            var n = 0
            while (n < len) {
                val r = input.read(b, n, len - n)
                if (r < 0) break
                n += r
            }
            return n
        }
    }
}

/** A file in the vault was changed or damaged (its authentication tag doesn't match). */
class VaultDataException : IOException("Damaged vault file")

private class DecryptingInputStream(
    private val cryptor: VaultCryptor,
    private val input: InputStream,
    private val header: VaultCryptor.Header,
    private var chunk: Long,
) : InputStream() {
    private val ct = ByteArray(Cryptomator.CT_CHUNK)
    private val clear = ByteArray(Cryptomator.CHUNK)
    private var pos = 0
    private var len = 0
    private var eof = false

    private fun fill(): Boolean {
        while (pos >= len) {
            if (eof) return false
            val n = VaultCryptor.readFully(input, ct, ct.size)
            if (n < ct.size) eof = true
            if (n == 0) return false
            len = cryptor.decryptChunk(ct, n, chunk++, header, clear)
            pos = 0
        }
        return true
    }

    override fun read(): Int {
        if (!fill()) return -1
        return clear[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, n: Int): Int {
        if (n == 0) return 0
        if (!fill()) return -1
        val k = minOf(n, len - pos)
        clear.copyInto(b, off, pos, pos + k)
        pos += k
        return k
    }

    override fun skip(n: Long): Long {
        var left = n
        while (left > 0 && fill()) {
            val k = minOf(left, (len - pos).toLong()).toInt()
            pos += k
            left -= k
        }
        return n - left
    }

    override fun available(): Int = len - pos

    override fun close() {
        header.wipe()
        clear.fill(0)
        input.close()
    }
}

private class EncryptingOutputStream(private val cryptor: VaultCryptor, out: OutputStream) : FilterOutputStream(out) {
    private val header = cryptor.newHeader()
    private val buf = ByteArray(Cryptomator.CHUNK)
    private val ct = ByteArray(Cryptomator.CT_CHUNK)
    private var n = 0
    private var chunk = 0L
    private var closed = false

    init { out.write(cryptor.encryptHeader(header)) }

    override fun write(b: Int) {
        buf[n++] = b.toByte()
        if (n == buf.size) flushChunk()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var left = len
        while (left > 0) {
            val k = minOf(left, buf.size - n)
            b.copyInto(buf, n, o, o + k)
            n += k; o += k; left -= k
            if (n == buf.size) flushChunk()
        }
    }

    private fun flushChunk() {
        val len = cryptor.encryptChunk(buf, 0, n, chunk++, header, ct)
        out.write(ct, 0, len)
        n = 0
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (n > 0) flushChunk()
            out.flush()
        } finally {
            header.wipe()
            buf.fill(0)
            out.close()
        }
    }
}
