/*
 * FilesCtrl JNI bridge to libnfs (NFSv3 / NFSv4).
 *
 * Every function takes the context handle returned by create(). libnfs contexts are not thread safe,
 * so the Kotlin side serialises all calls on one context with a lock.
 *
 * Errors: functions returning int give 0 (or a byte count) on success and -errno on failure.
 * Functions returning a handle or object give 0 / null on failure; lastError() then has -errno and
 * error() has libnfs's message.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/time.h>

#include <nfsc/libnfs.h>
#include <nfsc/libnfs-raw.h>
#include "libnfs-raw-mount.h" /* from libnfs/mount */

#define STAT_FIELDS 10
#define IO_CHUNK (1024 * 1024)

typedef struct {
    struct nfs_context *nfs;
    int last; /* last -errno from a call that returns a handle/object */
} ctx_t;

#define CTX(h) ((ctx_t *)(intptr_t)(h))
#define FH(h) ((struct nfsfh *)(intptr_t)(h))
/* A zero handle would crash inside libnfs; refuse it instead. */
#define NEED_FH(fh) do { if ((fh) == 0) return -EBADF; } while (0)

static int neg(int rc) { return rc < 0 ? rc : -EIO; }

/* Fill out[] = {mode, size, mtimeMs, atimeMs, ctimeMs, uid, gid, nlink, ino, blocks*512(used)} */
static void put_stat64(JNIEnv *env, jlongArray out, const struct nfs_stat_64 *st) {
    jlong v[STAT_FIELDS];
    v[0] = (jlong)st->nfs_mode;
    v[1] = (jlong)st->nfs_size;
    v[2] = (jlong)st->nfs_mtime * 1000 + (jlong)(st->nfs_mtime_nsec / 1000000);
    v[3] = (jlong)st->nfs_atime * 1000 + (jlong)(st->nfs_atime_nsec / 1000000);
    v[4] = (jlong)st->nfs_ctime * 1000 + (jlong)(st->nfs_ctime_nsec / 1000000);
    v[5] = (jlong)st->nfs_uid;
    v[6] = (jlong)st->nfs_gid;
    v[7] = (jlong)st->nfs_nlink;
    v[8] = (jlong)st->nfs_ino;
    v[9] = (jlong)st->nfs_used;
    (*env)->SetLongArrayRegion(env, out, 0, STAT_FIELDS, v);
}

static const char *jstr(JNIEnv *env, jstring s) { return s ? (*env)->GetStringUTFChars(env, s, NULL) : NULL; }
static void jstr_rel(JNIEnv *env, jstring s, const char *c) { if (s && c) (*env)->ReleaseStringUTFChars(env, s, c); }

#define FN(name) Java_com_vknxworks_filesctrl_nfs_NfsNative_##name

JNIEXPORT jlong JNICALL FN(create)(JNIEnv *env, jobject thiz) {
    ctx_t *c = calloc(1, sizeof(ctx_t));
    if (!c) return 0;
    c->nfs = nfs_init_context();
    if (!c->nfs) { free(c); return 0; }
    return (jlong)(intptr_t)c;
}

JNIEXPORT void JNICALL FN(destroy)(JNIEnv *env, jobject thiz, jlong h) {
    ctx_t *c = CTX(h);
    if (!c) return;
    if (c->nfs) nfs_destroy_context(c->nfs);
    free(c);
}

JNIEXPORT jstring JNICALL FN(error)(JNIEnv *env, jobject thiz, jlong h) {
    ctx_t *c = CTX(h);
    const char *e = (c && c->nfs) ? nfs_get_error(c->nfs) : NULL;
    return e ? (*env)->NewStringUTF(env, e) : NULL;
}

JNIEXPORT jint JNICALL FN(lastError)(JNIEnv *env, jobject thiz, jlong h) { return CTX(h)->last; }

/*
 * version: 3 or 4. nfsPort / mountPort 0 = ask the portmapper (v3) or use 2049 (v4).
 * uid/gid: sent as AUTH_SYS credentials; -1 keeps libnfs's default.
 */
JNIEXPORT jint JNICALL FN(mount)(JNIEnv *env, jobject thiz, jlong h, jstring jserver, jstring jexport,
                                 jint version, jint uid, jint gid, jint nfsPort, jint mountPort,
                                 jint timeoutMs, jboolean readOnly, jboolean traverseMounts) {
    ctx_t *c = CTX(h);
    /* 3 = NFS_V3, 4 = NFS_V4 (4.0), 42 = NFS_V4_2 */
    int rcv = nfs_set_version(c->nfs, version == 4 || version == 42 ? version : 3);
    if (rcv < 0) return rcv;
    if (uid >= 0) nfs_set_uid(c->nfs, uid);
    if (gid >= 0) nfs_set_gid(c->nfs, gid);
    if (nfsPort > 0) nfs_set_nfsport(c->nfs, nfsPort);
    if (mountPort > 0) nfs_set_mountport(c->nfs, mountPort);
    if (timeoutMs > 0) nfs_set_timeout(c->nfs, timeoutMs);
    nfs_set_autoreconnect(c->nfs, 3);
    if (readOnly) nfs_set_readonly(c->nfs, 1);
    /* Follow nested exports below the mounted one. Needs the server's EXPORT call; some minimal servers lack it. */
    nfs_set_auto_traverse_mounts(c->nfs, traverseMounts ? 1 : 0);
    const char *server = jstr(env, jserver);
    const char *export = jstr(env, jexport);
    int rc = nfs_mount(c->nfs, server, export);
    jstr_rel(env, jserver, server);
    jstr_rel(env, jexport, export);
    return rc;
}

JNIEXPORT jint JNICALL FN(stat)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jboolean follow, jlongArray out) {
    struct nfs_stat_64 st;
    const char *p = jstr(env, jpath);
    int rc = follow ? nfs_stat64(CTX(h)->nfs, p, &st) : nfs_lstat64(CTX(h)->nfs, p, &st);
    jstr_rel(env, jpath, p);
    if (rc == 0) put_stat64(env, out, &st);
    return rc;
}

JNIEXPORT jint JNICALL FN(fstat)(JNIEnv *env, jobject thiz, jlong h, jlong fh, jlongArray out) {
    NEED_FH(fh);
    struct nfs_stat_64 st;
    int rc = nfs_fstat64(CTX(h)->nfs, FH(fh), &st);
    if (rc == 0) put_stat64(env, out, &st);
    return rc;
}

/*
 * Lists a directory without "." and "..". Returns Object[]{String[] names, long[] data} where data holds
 * STAT_FIELDS longs per entry (same layout as stat) plus one more: the NF3 type (5 = symlink).
 */
JNIEXPORT jobjectArray JNICALL FN(list)(JNIEnv *env, jobject thiz, jlong h, jstring jpath) {
    ctx_t *c = CTX(h);
    struct nfsdir *dir = NULL;
    const char *p = jstr(env, jpath);
    int rc = nfs_opendir(c->nfs, p, &dir);
    jstr_rel(env, jpath, p);
    if (rc < 0) { c->last = rc; return NULL; }

    int n = 0;
    struct nfsdirent *e;
    while ((e = nfs_readdir(c->nfs, dir)) != NULL) {
        if (strcmp(e->name, ".") && strcmp(e->name, "..")) n++;
    }
    nfs_rewinddir(c->nfs, dir);

    const int per = STAT_FIELDS + 1;
    jclass strCls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray names = (*env)->NewObjectArray(env, n, strCls, NULL);
    jlongArray data = (*env)->NewLongArray(env, n * per);
    jlong *d = malloc(sizeof(jlong) * (size_t)(n > 0 ? n * per : 1));
    if (!names || !data || !d) { free(d); nfs_closedir(c->nfs, dir); c->last = -ENOMEM; return NULL; }

    int i = 0;
    while ((e = nfs_readdir(c->nfs, dir)) != NULL && i < n) {
        if (!strcmp(e->name, ".") || !strcmp(e->name, "..")) continue;
        jstring s = (*env)->NewStringUTF(env, e->name);
        if ((*env)->ExceptionCheck(env)) {
            /* Not valid modified UTF-8 (e.g. Latin-1 name); show a replacement instead of crashing. */
            (*env)->ExceptionClear(env);
            s = (*env)->NewStringUTF(env, "?");
        }
        (*env)->SetObjectArrayElement(env, names, i, s);
        (*env)->DeleteLocalRef(env, s);
        jlong *v = d + i * per;
        v[0] = e->mode;
        v[1] = (jlong)e->size;
        v[2] = (jlong)e->mtime.tv_sec * 1000 + e->mtime.tv_usec / 1000;
        v[3] = (jlong)e->atime.tv_sec * 1000 + e->atime.tv_usec / 1000;
        v[4] = (jlong)e->ctime.tv_sec * 1000 + e->ctime.tv_usec / 1000;
        v[5] = e->uid;
        v[6] = e->gid;
        v[7] = e->nlink;
        v[8] = (jlong)e->inode;
        v[9] = (jlong)e->used;
        v[10] = e->type;
        i++;
    }
    nfs_closedir(c->nfs, dir);
    (*env)->SetLongArrayRegion(env, data, 0, i * per, d);
    free(d);

    jclass objCls = (*env)->FindClass(env, "java/lang/Object");
    jobjectArray res = (*env)->NewObjectArray(env, 3, objCls, NULL);
    (*env)->SetObjectArrayElement(env, res, 0, names);
    (*env)->SetObjectArrayElement(env, res, 1, data);
    jintArray count = (*env)->NewIntArray(env, 1);
    jint ci = i;
    (*env)->SetIntArrayRegion(env, count, 0, 1, &ci);
    (*env)->SetObjectArrayElement(env, res, 2, count);
    return res;
}

/* flags: 0 = read, 1 = write, 2 = read/write; plus 0x10 = create, 0x20 = truncate, 0x40 = append */
static int map_flags(jint f) {
    int o = (f & 3) == 1 ? O_WRONLY : (f & 3) == 2 ? O_RDWR : O_RDONLY;
    if (f & 0x10) o |= O_CREAT;
    if (f & 0x20) o |= O_TRUNC;
    if (f & 0x40) o |= O_APPEND;
    return o;
}

JNIEXPORT jlong JNICALL FN(open)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jint flags, jint mode) {
    ctx_t *c = CTX(h);
    struct nfsfh *fh = NULL;
    const char *p = jstr(env, jpath);
    int rc = nfs_open2(c->nfs, p, map_flags(flags), mode, &fh);
    jstr_rel(env, jpath, p);
    if (rc < 0 || !fh) { c->last = neg(rc); return 0; }
    return (jlong)(intptr_t)fh;
}

JNIEXPORT jlong JNICALL FN(creat)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jint mode) {
    ctx_t *c = CTX(h);
    struct nfsfh *fh = NULL;
    const char *p = jstr(env, jpath);
    int rc = nfs_creat(c->nfs, p, mode, &fh);
    jstr_rel(env, jpath, p);
    if (rc < 0 || !fh) { c->last = neg(rc); return 0; }
    return (jlong)(intptr_t)fh;
}

JNIEXPORT jint JNICALL FN(close)(JNIEnv *env, jobject thiz, jlong h, jlong fh) {
    NEED_FH(fh);
    return nfs_close(CTX(h)->nfs, FH(fh));
}

/* Reads up to len bytes at offset into buf[off..]; returns bytes read (0 at EOF) or -errno. */
JNIEXPORT jint JNICALL FN(pread)(JNIEnv *env, jobject thiz, jlong h, jlong fh, jlong offset,
                                 jbyteArray buf, jint off, jint len) {
    NEED_FH(fh);
    if (len <= 0) return 0;
    if (len > IO_CHUNK) len = IO_CHUNK;
    char *tmp = malloc((size_t)len);
    if (!tmp) return -ENOMEM;
    int total = 0;
    /* libnfs may return short reads (server rsize); loop until full or EOF. */
    while (total < len) {
        int rc = nfs_pread(CTX(h)->nfs, FH(fh), tmp + total, (size_t)(len - total), (uint64_t)(offset + total));
        if (rc < 0) { if (total == 0) { free(tmp); return rc; } break; }
        if (rc == 0) break;
        total += rc;
    }
    if (total > 0) (*env)->SetByteArrayRegion(env, buf, off, total, (const jbyte *)tmp);
    free(tmp);
    return total;
}

/* Writes len bytes from buf[off..] at offset; returns bytes written or -errno. */
JNIEXPORT jint JNICALL FN(pwrite)(JNIEnv *env, jobject thiz, jlong h, jlong fh, jlong offset,
                                  jbyteArray buf, jint off, jint len) {
    NEED_FH(fh);
    if (len <= 0) return 0;
    char *tmp = malloc((size_t)len);
    if (!tmp) return -ENOMEM;
    (*env)->GetByteArrayRegion(env, buf, off, len, (jbyte *)tmp);
    int total = 0;
    while (total < len) {
        int rc = nfs_pwrite(CTX(h)->nfs, FH(fh), tmp + total, (size_t)(len - total), (uint64_t)(offset + total));
        if (rc < 0) { free(tmp); return rc; }
        if (rc == 0) { free(tmp); return -EIO; }
        total += rc;
    }
    free(tmp);
    return total;
}

JNIEXPORT jint JNICALL FN(fsync)(JNIEnv *env, jobject thiz, jlong h, jlong fh) {
    NEED_FH(fh);
    return nfs_fsync(CTX(h)->nfs, FH(fh));
}

JNIEXPORT jint JNICALL FN(ftruncate)(JNIEnv *env, jobject thiz, jlong h, jlong fh, jlong length) {
    NEED_FH(fh);
    return nfs_ftruncate(CTX(h)->nfs, FH(fh), (uint64_t)length);
}

#define PATH_OP(name, call)                                                                 \
    JNIEXPORT jint JNICALL FN(name)(JNIEnv *env, jobject thiz, jlong h, jstring jpath) {    \
        const char *p = jstr(env, jpath);                                                   \
        int rc = call(CTX(h)->nfs, p);                                                      \
        jstr_rel(env, jpath, p);                                                            \
        return rc;                                                                          \
    }

PATH_OP(mkdir, nfs_mkdir)
PATH_OP(rmdir, nfs_rmdir)
PATH_OP(unlink, nfs_unlink)

JNIEXPORT jint JNICALL FN(rename)(JNIEnv *env, jobject thiz, jlong h, jstring jfrom, jstring jto) {
    const char *a = jstr(env, jfrom), *b = jstr(env, jto);
    int rc = nfs_rename(CTX(h)->nfs, a, b);
    jstr_rel(env, jfrom, a);
    jstr_rel(env, jto, b);
    return rc;
}

JNIEXPORT jint JNICALL FN(symlink)(JNIEnv *env, jobject thiz, jlong h, jstring jtarget, jstring jlink) {
    const char *a = jstr(env, jtarget), *b = jstr(env, jlink);
    int rc = nfs_symlink(CTX(h)->nfs, a, b);
    jstr_rel(env, jtarget, a);
    jstr_rel(env, jlink, b);
    return rc;
}

JNIEXPORT jstring JNICALL FN(readlink)(JNIEnv *env, jobject thiz, jlong h, jstring jpath) {
    char buf[4096];
    const char *p = jstr(env, jpath);
    memset(buf, 0, sizeof buf);
    int rc = nfs_readlink(CTX(h)->nfs, p, buf, sizeof buf - 1);
    jstr_rel(env, jpath, p);
    if (rc < 0) { CTX(h)->last = rc; return NULL; }
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jint JNICALL FN(chmod)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jint mode) {
    const char *p = jstr(env, jpath);
    int rc = nfs_chmod(CTX(h)->nfs, p, mode);
    jstr_rel(env, jpath, p);
    return rc;
}

JNIEXPORT jint JNICALL FN(chown)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jint uid, jint gid) {
    const char *p = jstr(env, jpath);
    int rc = nfs_chown(CTX(h)->nfs, p, uid, gid);
    jstr_rel(env, jpath, p);
    return rc;
}

/* Sets access and modification time (milliseconds since the epoch). */
JNIEXPORT jint JNICALL FN(utimes)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jlong atimeMs, jlong mtimeMs) {
    struct timeval tv[2];
    tv[0].tv_sec = atimeMs / 1000; tv[0].tv_usec = (atimeMs % 1000) * 1000;
    tv[1].tv_sec = mtimeMs / 1000; tv[1].tv_usec = (mtimeMs % 1000) * 1000;
    const char *p = jstr(env, jpath);
    int rc = nfs_utimes(CTX(h)->nfs, p, tv);
    jstr_rel(env, jpath, p);
    return rc;
}

/* out = {fragment size, total blocks, free blocks, available blocks} */
JNIEXPORT jint JNICALL FN(statvfs)(JNIEnv *env, jobject thiz, jlong h, jstring jpath, jlongArray out) {
    struct statvfs sv;
    const char *p = jstr(env, jpath);
    int rc = nfs_statvfs(CTX(h)->nfs, p, &sv);
    jstr_rel(env, jpath, p);
    if (rc == 0) {
        jlong v[4] = { (jlong)(sv.f_frsize ? sv.f_frsize : sv.f_bsize), (jlong)sv.f_blocks, (jlong)sv.f_bfree, (jlong)sv.f_bavail };
        (*env)->SetLongArrayRegion(env, out, 0, 4, v);
    }
    return rc;
}

JNIEXPORT jlong JNICALL FN(readMax)(JNIEnv *env, jobject thiz, jlong h) {
    return (jlong)nfs_get_readmax(CTX(h)->nfs);
}

/* Exports offered by an NFSv3 server's mount daemon, or null if it couldn't be asked. */
JNIEXPORT jobjectArray JNICALL FN(exports)(JNIEnv *env, jobject thiz, jstring jserver, jint mountPort, jint timeoutSec) {
    const char *s = jstr(env, jserver);
    struct exportnode *list = mountPort > 0 ? mount_getexports_mountport(s, mountPort)
                                            : mount_getexports_timeout(s, timeoutSec > 0 ? timeoutSec : 5);
    jstr_rel(env, jserver, s);
    if (!list) return NULL;
    int n = 0;
    for (struct exportnode *e = list; e; e = e->ex_next) n++;
    jclass strCls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, n, strCls, NULL);
    int i = 0;
    for (struct exportnode *e = list; e; e = e->ex_next) {
        jstring js = (*env)->NewStringUTF(env, e->ex_dir ? e->ex_dir : "");
        (*env)->SetObjectArrayElement(env, arr, i++, js);
        (*env)->DeleteLocalRef(env, js);
    }
    mount_free_export_list(list);
    return arr;
}

/* NFS servers that answer a portmapper broadcast on the local networks. */
JNIEXPORT jobjectArray JNICALL FN(findServers)(JNIEnv *env, jobject thiz) {
    struct nfs_server_list *list = nfs_find_local_servers();
    int n = 0;
    for (struct nfs_server_list *s = list; s; s = s->next) n++;
    jclass strCls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, n, strCls, NULL);
    int i = 0;
    for (struct nfs_server_list *s = list; s; s = s->next) {
        jstring js = (*env)->NewStringUTF(env, s->addr ? s->addr : "");
        (*env)->SetObjectArrayElement(env, arr, i++, js);
        (*env)->DeleteLocalRef(env, js);
    }
    if (list) free_nfs_srvr_list(list);
    return arr;
}
