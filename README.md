# FilesCtrl

A Material You file manager for Android, in the spirit of [Material Files](https://github.com/zhanghai/MaterialFiles), with built-in **NFS** (v3, v4.0 and v4.2) support.

## Features

- **Local storage** — internal storage, SD cards and USB drives, with free-space shown in the menu
- **NFS shares** — NFSv3 and NFSv4 via [libnfs](https://github.com/sahlberg/libnfs), no root needed
  - List a server's exports and find servers on the network from the setup screen
  - Auto mode tries v3, then v4; custom ports for servers without rpcbind
  - Per-share uid/gid, read-only mode and timeout
  - Reconnects automatically after Wi-Fi drops
- **Open NFS files in any app** — files stream through Android's document provider, so video players can seek in large files without downloading them first
- **NFS in the system file picker** — saved shares show up under "Browse" in any app that opens or saves files
- Copy, move, delete, rename, between local storage and NFS in any direction, in the background with a progress notification and cancel
- Paste conflict handling (keep both / replace and merge / skip)
- Zip compress and extract (on either side)
- Breadcrumb path bar, list and grid views, sort by name (natural order) / size / date / type, folders first, hidden files
- Multi-select with copy, cut, delete, share, compress
- Filter as you type, or search subfolders recursively
- Image and video thumbnails (images on NFS too)
- Properties with folder size, permissions and owner; change permissions (chmod) on NFS
- Built-in text editor for config files and notes
- Bookmarks, back-history with scroll position restore
- Tablet / unfolded layout with a permanent drawer
- Material You colours, light/dark/pure black

## NFS server notes

- **`insecure` export option.** Android apps can't bind privileged ports (< 1024). Linux `knfsd` exports need `insecure` for FilesCtrl to mount, e.g.

  ```
  /srv/share  192.168.1.0/24(rw,insecure,no_subtree_check)
  ```

  On Synology: *Shared Folder → Edit → NFS Permissions → "Allow connections from non-privileged ports"*. On TrueNAS: enable *Allow non-root mount* on the share.
- **uid/gid.** NFS (AUTH_SYS) trusts the IDs the client sends. Set the share's uid/gid in FilesCtrl to the owner of your files on the server (often 1000 on Linux, 1026+ on Synology).
- **NFSv4 paths** are relative to the server's NFSv4 pseudo-root. With a Linux `fsid=0` export, use paths below it.

## Building

The APK is built by GitHub Actions on every push to `main` and published as a release. Locally:

```
./gradlew assembleRelease
```

Needs the Android SDK with NDK and CMake 3.22.1 (Gradle installs them). libnfs is fetched by CMake at build time.

Optional signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key.

## Licence

libnfs is LGPL-2.1 (library) / BSD.
