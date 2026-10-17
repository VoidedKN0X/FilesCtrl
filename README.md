# FilesCtrl

A Material You file manager for Android, in the spirit of [Material Files](https://github.com/zhanghai/MaterialFiles), with built-in network file systems: **NFS** (v3, v4.0, v4.2), **SMB** (2/3), **SFTP**, **FTP/FTPS** and **WebDAV**.

## Features

- **Local storage** — internal storage, SD cards and USB drives, with free-space shown in the menu
- **Network locations**, all from one "Add network" screen, no root needed:
  - **NFS** v3/v4 via [libnfs](https://github.com/sahlberg/libnfs): export listing, server scan, per-share uid/gid, custom ports
  - **SMB** 2/3 via smbj: guest or user login, domain, lists all shares when none is given
  - **SFTP** via sshj: password or private key, host key remembered on first connect and checked after
  - **FTP / FTPS** via Apache Commons Net (explicit TLS, optional self-signed certificates)
  - **WebDAV** over HTTP/HTTPS (Nextcloud, ownCloud, Apache/nginx)
  - Passwords and keys are encrypted with the Android Keystore; reconnects automatically after Wi-Fi drops
- **Open network files in any app** — files stream through Android's document provider, so video players can seek in large files without downloading them first
- **Network locations in the system file picker** — saved servers show up under "Browse" in any app that opens or saves files
- Copy, move, delete, rename, between local storage and any network location in any direction, in the background with a progress notification and cancel button
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

## Tests

The *Network tests* workflow runs the same file operations (create, 3 MB write and read-back, ranged and random reads, in-place edits, rename, delete) against Samba, SFTP, FTP and WebDAV servers in Docker on every push. NFS is tested against userspace NFSv3/v4 servers.

## Building

The APK is built by GitHub Actions on every push to `main` and published as a release. Locally:

```
./gradlew assembleRelease
```

Needs the Android SDK with NDK and CMake 3.22.1 (Gradle installs them). libnfs is fetched by CMake at build time.

Optional signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key.

## Licence

libnfs is LGPL-2.1 (library) / BSD. smbj, sshj and Commons Net are Apache-2.0.
