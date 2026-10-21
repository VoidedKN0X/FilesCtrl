# FilesCtrl

**A file manager that opens your network shares.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/FilesCtrl?label=download)](https://github.com/VoidedKN0X/FilesCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

FilesCtrl is a Material You file manager for Android, in the spirit of [Material Files](https://github.com/zhanghai/MaterialFiles), with network storage built in: **NFS, SMB, SFTP, FTP and WebDAV**. No root needed.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FilesCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Local storage**: internal storage, SD cards and USB drives
- **Network storage** from one *Add network* button: NFS (v3 and v4), SMB (2/3), SFTP, FTP/FTPS and WebDAV (Nextcloud, ownCloud and others)
- **Open network files in any app**: videos stream and can be skipped through without downloading first; *Edit with* lets an app save changes back
- **Your servers in the system file picker**, so other apps can open and save files on them
- **Copy, move, rename and delete** between your phone and any server, in the background
- **Archives**: open ZIP, 7z, RAR (RAR4), TAR (.tar.gz, .tar.xz, .tar.zst, .tar.bz2 and more), CPIO, AR/DEB and single compressed files (.gz, .xz, .zst, .bz2 …) like folders, extract them, and compress to ZIP, 7z or TAR.* with optional AES-256 passwords
- Multi-select, search (also in subfolders), bookmarks, properties and permissions
- **Thumbnails** for photos and videos on the phone, and for photos on servers (can be turned off), list and grid views, sorting and hidden files
- **Built-in text editor** for config files and notes, also offered to other apps
- **Read-only servers**: switch it on and FilesCtrl hides every option that would change something there, and refuses writes from other apps
- **Checked connections**: SFTP host keys and self-signed WebDAV/FTPS certificates are only trusted after you confirm their fingerprint; SMB signing is on by default, with optional encryption
- Passwords and keys are stored encrypted; **app lock** with its own PIN and optional fingerprint unlock, which keeps saved passwords and keys encrypted while the app is locked
- Follow system, dark, light or pure black themes, colours from your wallpaper, and a tablet layout

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- **All files access**, to browse your phone's own storage. Network shares work without it
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds FilesCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FilesCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/FilesCtrl` in Obtainium by hand.

## Getting started

1. Open the **Places** tab and tap **Add network**.
2. Choose the type (NFS, SMB, SFTP, FTP or WebDAV) and enter your server's address and login.
3. Your server now appears next to your phone's storage.

## Tips and troubleshooting

**NFS server setup.** Android apps can't use privileged ports, so your NFS export must allow them:

- **Linux:** add `insecure` to the export, e.g. `/srv/share 192.168.1.0/24(rw,insecure,no_subtree_check)`
- **Synology:** *Shared Folder → Edit → NFS Permissions →* allow connections from non-privileged ports
- **TrueNAS:** turn on *Allow non-root mount* for the share

Set the share's **uid/gid** in FilesCtrl to the owner of your files on the server (often `1000` on Linux, `1026` or higher on Synology).

## App lock

Turn it on in **Settings → Privacy and security → App lock**. It's off until you turn it on.

- **An app PIN of its own**, at least 6 digits and separate from your phone's. Very obvious PINs, like 111111 or 123456, are refused. The PIN is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key that never leaves the phone's secure hardware, so a copy of the app's files isn't enough to guess it somewhere else.
- **Unlock with fingerprint** (optional): strong biometrics only, checked by the phone's secure hardware. Adding a fingerprint to the phone turns it off until you turn it on again. The PIN is still needed after the phone restarts and at least every 72 hours.
- **Wrong PINs make you wait**: 30 seconds after 5 wrong tries, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't end the wait.
- **Saved passwords and keys stay encrypted while FilesCtrl is locked**, with a key that only exists in memory while the app is unlocked and is wiped the moment it locks. Your servers don't show in other apps' file pickers while it's locked. Copies and moves that are already running finish; nothing new can connect until you unlock.
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a file in another app, sharing, picking a file or folder, choosing an SFTP key or opening Android's settings from FilesCtrl doesn't count as leaving, unless you're away for more than 5 minutes.
- The file browser and the text editor lock and unlock together.
- **Hide in screenshots and recent apps** (on for new installs, also without the app lock): screenshots, screen recordings and the recent apps view show a blank screen. Adding or editing a network location, the lock screen and the PIN screens are always hidden.
- **Forgot PIN?** on the lock screen turns the app lock off and forgets the saved passwords and keys. Your network locations stay; you enter their passwords again.

## Privacy

**Telemetry-free.** FilesCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add: your NFS, SMB, SFTP, FTP/FTPS and WebDAV servers.

- Files on your phone never leave it unless you copy, move or share them yourself.
- The Support, Report a bug, Source code and Licence links in Settings open in your browser, only when you tap them.

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

FilesCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, **Settings → About** has **Support FilesCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/FilesCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| **FilesCtrl** (this app) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

FilesCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

Archive support uses Apache Commons Compress, zip4j and zstd-jni's Java parts under their own licences (Apache-2.0, Apache-2.0, BSD-2-Clause), XZ for Java (public domain / 0BSD), Brotli (MIT) and junrar (unRAR licence: it may only be used to extract RAR archives, not to create them).

FilesCtrl uses libnfs (LGPL-2.1), smbj, sshj and Apache Commons Net (Apache-2.0).
