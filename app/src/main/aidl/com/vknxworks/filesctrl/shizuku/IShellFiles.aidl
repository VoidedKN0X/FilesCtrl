// File access through Shizuku, for Android/data and Android/obb. Runs as the shell user (the same access as adb).
// Errors come back as errno values (0 = success), because only a few exception types cross a binder.
package com.vknxworks.filesctrl.shizuku;

interface IShellFiles {
    // Called by Shizuku when FilesCtrl unbinds the service.
    void destroy() = 16777114;

    // A pipe the folder listing is written to: an int errno, then one record per entry (see ShellFiles.readList).
    ParcelFileDescriptor list(String dir) = 1;

    // [errno] on failure, else [0, isDir, size, modifiedMs, mode, uid, gid, isLink].
    long[] stat(String path) = 2;

    // Opens a file with open(2) flags; null on failure, with the errno in err[0].
    ParcelFileDescriptor open(String path, int flags, out int[] err) = 3;

    int mkdir(String path) = 4;
    int createFile(String path) = 5;
    // One file, or one empty folder.
    int remove(String path) = 6;
    int rename(String from, String to) = 7;
    int setModified(String path, long time) = 8;

    // [errno] on failure, else [0, total, free].
    long[] space(String path) = 9;

    // A link's target, or null.
    String readlink(String path) = 10;
}
