# FilesCtrl

**A file manager that opens your network shares.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/FilesCtrl?label=download)](https://github.com/VoidedKN0X/FilesCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

FilesCtrl is a Material You file manager for Android, in the spirit of [Material Files](https://github.com/zhanghai/MaterialFiles), with network storage built in: **NFS, SMB, SFTP, FTP and WebDAV**. No root needed.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/FilesCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Local storage**: internal storage, SD cards and USB drives
- **Network storage** from one *Add network* button: NFS (v3 and v4), SMB (2/3), SFTP, FTP/FTPS and WebDAV (Nextcloud, ownCloud and others)
- **Open network files in any app**: videos stream and can be skipped through without downloading first; *Edit with* lets an app save changes back
- **Your servers in the system file picker**, so other apps can open and save files on them
- **Copy, move, rename and delete** between your phone and any server, in the background
- **Zip** and unzip, multi-select, search (also in subfolders), bookmarks, properties and permissions
- **Thumbnails** for photos and videos on the phone, and for photos on servers (can be turned off), list and grid views, sorting and hidden files
- **Built-in text editor** for config files and notes, also offered to other apps
- **Read-only servers**: switch it on and FilesCtrl hides every option that would change something there, and refuses writes from other apps
- **Checked connections**: SFTP host keys and self-signed WebDAV/FTPS certificates are only trusted after you confirm their fingerprint; SMB signing is on by default, with optional encryption
- Passwords and keys are stored encrypted; **app lock** with PIN and fingerprint
- Light, dark and pure black themes with Material You colours; tablet layout

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer on a 64-bit phone (ARM or x86_64)
- **All files access**, to browse your phone's own storage. Network shares work without it
- **Google-free:** works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font

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

## Usage stats

FilesCtrl can share anonymous usage stats, **only if you say yes**. It asks once when you first open it, and you can change it any time in **Settings > Usage stats**.

- **Shared:** which screens you open (Files, Places, Settings, server settings or the editor), which kinds of network storage you add and open (NFS, SMB, SFTP, FTP or WebDAV), whether you copy, move, delete, share, zip or unzip files and on which kind of storage, the app version, Android version, phone model, language, phone or tablet, and your theme setting.
- **Never shared:** your server addresses, usernames or passwords, share or export names, file or folder names or paths, what you search for, or any ID that stays the same between sessions.
- **Where:** VKNXWorks' own self-hosted [Aptabase](https://aptabase.com) server. Nothing goes to Google or analytics companies. Like any website, the server sees your IP address; Aptabase uses it only to work out the country and doesn't store it. Apart from this (and only when switched on), FilesCtrl only connects to the servers you add.
- **Switching stats off** stops them straight away and throws away anything not yet sent.

Builds from source don't include the stats key, so they can't send anything and the setting is hidden. The code is in [`Telemetry.kt`](app/src/main/java/com/vknxworks/filesctrl/data/Telemetry.kt).

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
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| **FilesCtrl** (this app) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

FilesCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

FilesCtrl uses libnfs (LGPL-2.1), smbj, sshj and Apache Commons Net (Apache-2.0).
