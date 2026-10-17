# FilesCtrl

**A file manager that opens your network shares.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/FilesCtrl?label=download)](https://github.com/VoidedKN0X/FilesCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

FilesCtrl is a Material You file manager for Android, in the spirit of [Material Files](https://github.com/zhanghai/MaterialFiles), with network storage built in: **NFS, SMB, SFTP, FTP and WebDAV**. No root needed.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FilesCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="48"></a>

## Features

- **Local storage**: internal storage, SD cards and USB drives
- **Network storage** from one *Add network* button: NFS (v3 and v4), SMB (2/3), SFTP, FTP/FTPS and WebDAV (Nextcloud, ownCloud and others)
- **Open network files in any app**: videos stream and can be skipped through without downloading first
- **Your servers in the system file picker**, so other apps can open and save files on them
- **Copy, move, rename and delete** between your phone and any server, in the background
- **Zip** and unzip, multi-select, search, bookmarks
- **Thumbnails** for photos and videos, list and grid views, sorting and hidden files
- **Built-in text editor** for config files and notes
- Passwords are stored encrypted; light, dark and pure black themes; tablet layout

## Requirements

- Android 13 or newer

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates with Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium), then tap the button below on your phone. Obtainium adds FilesCtrl and installs every new release for you.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FilesCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="48"></a>

If the button doesn't open Obtainium, add `https://github.com/VoidedKN0X/FilesCtrl` in Obtainium by hand.

## Getting started

1. Tap **Add network**.
2. Choose the type (NFS, SMB, SFTP, FTP or WebDAV) and enter your server's address and login.
3. Your server now appears next to your phone's storage.

## Tips and troubleshooting

**NFS server setup.** Android apps can't use privileged ports, so your NFS export must allow them:

- **Linux:** add `insecure` to the export, e.g. `/srv/share 192.168.1.0/24(rw,insecure,no_subtree_check)`
- **Synology:** *Shared Folder → Edit → NFS Permissions →* allow connections from non-privileged ports
- **TrueNAS:** turn on *Allow non-root mount* for the share

Set the share's **uid/gid** in FilesCtrl to the owner of your files on the server (often `1000` on Linux, `1026` or higher on Synology).

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/FilesCtrl.git
   cd FilesCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

Gradle downloads the Android NDK and CMake it needs automatically on the first build, so the first build takes a little longer.

## Support the project

FilesCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/FilesCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| **FilesCtrl** (this app) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

FilesCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

FilesCtrl uses libnfs (LGPL-2.1), smbj, sshj and Apache Commons Net (Apache-2.0).
