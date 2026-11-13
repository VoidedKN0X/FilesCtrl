package com.vknxworks.filesctrl.nfs

/**
 * Bindings to libnfs (see src/main/cpp/nfs_jni.c). Int results are 0 / a byte count on success and
 * -errno on failure. Handle- and object-returning calls give 0 / null on failure; [lastError] then has -errno.
 * A context must only be used by one thread at a time ([NfsSession] takes care of that).
 */
object NfsNative {
    init { System.loadLibrary("filesctrl_nfs") }

    /** Fields in a stat array. */
    const val ST_MODE = 0
    const val ST_SIZE = 1
    const val ST_MTIME = 2
    const val ST_ATIME = 3
    const val ST_CTIME = 4
    const val ST_UID = 5
    const val ST_GID = 6
    const val ST_NLINK = 7
    const val ST_INO = 8
    const val ST_USED = 9
    const val STAT_FIELDS = 10
    /** list() rows have one extra field: the NFS3 file type. */
    const val LIST_FIELDS = 11
    const val TYPE_SYMLINK = 5L

    const val O_READ = 0
    const val O_WRITE = 1
    const val O_RDWR = 2
    const val O_CREATE = 0x10
    const val O_TRUNC = 0x20
    const val O_APPEND = 0x40

    external fun create(): Long
    external fun destroy(h: Long)
    external fun error(h: Long): String?
    external fun lastError(h: Long): Int

    external fun mount(
        h: Long, server: String, export: String, version: Int, uid: Int, gid: Int,
        nfsPort: Int, mountPort: Int, timeoutMs: Int, readOnly: Boolean, traverseMounts: Boolean,
    ): Int

    external fun stat(h: Long, path: String, follow: Boolean, out: LongArray): Int
    external fun fstat(h: Long, fh: Long, out: LongArray): Int

    /** Object[]{String[] names, long[] rows (LIST_FIELDS per entry), int[]{count}} or null. */
    external fun list(h: Long, path: String): Array<Any>?

    external fun open(h: Long, path: String, flags: Int, mode: Int): Long
    external fun creat(h: Long, path: String, mode: Int): Long
    external fun close(h: Long, fh: Long): Int
    external fun pread(h: Long, fh: Long, offset: Long, buf: ByteArray, off: Int, len: Int): Int
    external fun pwrite(h: Long, fh: Long, offset: Long, buf: ByteArray, off: Int, len: Int): Int
    external fun fsync(h: Long, fh: Long): Int
    external fun ftruncate(h: Long, fh: Long, length: Long): Int

    external fun mkdir(h: Long, path: String): Int
    external fun rmdir(h: Long, path: String): Int
    external fun unlink(h: Long, path: String): Int
    external fun rename(h: Long, from: String, to: String): Int
    external fun symlink(h: Long, target: String, link: String): Int
    external fun readlink(h: Long, path: String): String?
    external fun chmod(h: Long, path: String, mode: Int): Int
    external fun chown(h: Long, path: String, uid: Int, gid: Int): Int
    external fun utimes(h: Long, path: String, atimeMs: Long, mtimeMs: Long): Int

    /** out = {fragment size, total blocks, free blocks, available blocks} */
    external fun statvfs(h: Long, path: String, out: LongArray): Int
    external fun readMax(h: Long): Long

    /** Export paths from a server's mount daemon (NFSv3), or null. */
    external fun exports(server: String, mountPort: Int, timeoutSec: Int): Array<String>?

    /** Addresses of NFS servers answering a broadcast on the local network. */
    external fun findServers(): Array<String>?
}
