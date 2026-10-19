# Changelog

What changed in each FilesCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 1.0.10 (2026-10-19)

### Changed

- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of the Settings tab. The rows are in the same order, with the same wording and icons, as in the other Ctrl apps. The new **Report a bug** row ("Found a problem or have an idea? Open an issue on GitHub") opens FilesCtrl's issue tracker, and the new **Licence** row opens the GNU General Public License v3.0. The separate version row is gone: the version ("FilesCtrl 1.0.10 · VKNXWorks") is now shown under the section.

## 1.0.9 (2026-10-19)

### New

- **Support FilesCtrl** under Settings → About opens VKNXWorks' GitHub Sponsors page in your browser. FilesCtrl is free and open source, and this is the place to sponsor its development if you'd like to. It's the first row in the About section at the bottom of the Settings tab, above the version and Source code rows.
- The README's **Support the project** section now also points to the **Support FilesCtrl** button.

## 1.0.8 (2026-10-19)

FilesCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy

- **Emoji no longer make FilesCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time FilesCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **Checked: FilesCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed

- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font.
- **README brought up to date with FilesCtrl 1.0.7.** The feature list now also mentions the app lock with PIN and fingerprint, the Read-only switch for servers, the fingerprint checks for SFTP host keys and self-signed WebDAV/FTPS certificates, SMB signing and encryption, *Edit with*, search in subfolders, properties and permissions, and that the text editor is offered to other apps. Thumbnails are now described exactly: photos and videos on the phone, photos only on servers. Requirements now say the app needs a 64-bit phone and the All files access permission to browse the phone's own storage (network shares work without it), and Getting started says the **Add network** button is on the **Places** tab. No change to the app itself

## 1.0.7 (2026-10-19)

A privacy review of the new optional usage stats. They're still off unless you said yes, and FilesCtrl still connects only to the servers you add (plus the stats server, only when stats are on).

### Security

- **Switching usage stats off now throws away anything not yet sent.** Before, a stat recorded just before you switched them off could still be waiting in the app, and if you switched stats back on within a few seconds it could have been sent after all. Now everything waiting is discarded the moment you switch stats off, so nothing recorded before that point can ever go out.

## 1.0.6 (2026-10-19)

### Changed

- **New app icon, matching the rest of the Ctrl series.** The icon is now the same dark keycap as the other Ctrl apps, with a yellow folder on its screen. The themed (monochrome) icon on Android 13+ home screens uses the same design.
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates.

## 1.0.5 (2026-10-19)

### New

- **Optional anonymous usage stats.** The first time you open FilesCtrl after this update (after the app lock, if you use one), it asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in the new **Settings > Usage stats** section
- **What's shared:** which screens you open (Files, Places, Settings, server settings or the text editor), which kinds of network storage you add and open (NFS, SMB, SFTP, FTP or WebDAV), whether you copy, move, delete, share, zip or unzip files and on which kind of storage (phone or a network protocol), the app version, Android version, phone model, language, phone or tablet, and your theme setting
- **What's never shared:** your server addresses, usernames, passwords, share or export names, file or folder names or paths, what you search for, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. If sending fails, the stats are dropped, never stored. Builds you make from source can't send stats at all, and the setting doesn't appear in them

### Changed

- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.
- The README has a new **Usage stats** section listing exactly what's collected

## 1.0.4 (2026-10-18)

### Security
- **App lock:** a wrong PIN now counts the moment you enter it, before it's checked. Before, closing the app while a PIN was being checked didn't count that try, which could be used to get around the lockout
- **SFTP:** the encryption library used for SFTP keys and connections (BouncyCastle) is updated from 1.78.1 to 1.86, which fixes a known flaw (CVE-2025-8916) where specially crafted certificate or key data could make the app use excessive memory
- **WebDAV:** on top of refusing replies that contain a document type declaration, the XML reader now also refuses to load anything external a reply points to, as a second safeguard against XML entity attacks
- **NFS:** a file opened by another app is now closed safely even if it's being read at the same moment the connection is re-established; before, a rare timing could close a handle from an older connection that no longer exists
- **NFS:** if the phone runs out of memory while listing a huge NFS folder, the listing now fails cleanly instead of crashing the app
- **Text editor:** when another app opens a file:// path in FilesCtrl's editor, other apps' private folders (Android/data and Android/obb) are refused however the path is capitalised

## 1.0.3 (2026-10-18)

### Fixed
- Going back (from the server settings or the text editor) could leave an empty black screen when back was tapped twice quickly or while a screen was still sliding in. Extra taps are now ignored and the main screen is never closed by accident
- Opening a server or the editor twice by double-tapping no longer stacks two copies of the same screen

### Security
- **NFS:** the NFS client library is updated with the upstream fix that makes it check the size of every field a server sends; before, a malicious NFS server could overflow memory in the app
- **NFS:** the app's own NFS code now checks every length, count and offset coming from the server or the system: no reading or writing past a buffer, no counting overflows on huge folders, no stale memory ending up in downloaded files if a server lies about how much it sent, and huge folder or export lists are refused instead of crashing the app
- **NFS:** a file opened by another app through the file picker can no longer be closed twice, which could corrupt memory
- **App lock:** too many wrong PINs now locks you out for real: changing the phone's date or time no longer ends the wait early, and it carries on after a restart
- **App lock:** fingerprint unlock now uses a key in the phone's secure hardware that only a real fingerprint match can unlock. If you used fingerprint unlock, turn it on again once in Settings → App lock (it also turns itself off when a new fingerprint is added to the phone)
- **Text editor:** when another app opens a file in FilesCtrl's editor, only files in shared storage are accepted, never FilesCtrl's own private files. The editor is also hidden from the Recents screen
- **Sharing:** FilesCtrl's private folders can no longer be handed to other apps at all; only phone storage, SD cards and USB drives can be opened or shared
- **WebDAV:** server replies that try XML entity tricks are refused, very large replies are rejected instead of filling memory, and a redirect loop no longer crashes the app
- **Notifications:** copy and move notifications no longer show file names on a secure lock screen, and with the app lock on they're hidden from the lock screen entirely
- Temporary copies made while another app edits a file on FTP or WebDAV are deleted if the edit fails, and leftovers from a crash are cleaned up
- The FTP and WebDAV settings now say plainly when a password and files would travel unencrypted, and picking a huge file as an SFTP key no longer freezes the app
- Release builds are signed only with the signing key from encrypted repository secrets, and the key passwords are never put on a command line during the build

## 1.0.2 (2026-10-18)

- Version numbers are now plain, with no build number on the end, and a new version only comes out when something in the app actually changes. Nothing else in the app has changed.

## 1.0.1 (2026-10-16 – 2026-10-17)

### Changed
- Removed the Transfers tab. Progress and cancel stay in the notification, and failures or skipped items show as a message while the app is open

### Security
- Files with unsafe names on a server (`.`, `..`, `/`) are hidden, and copy, move and unzip can never write outside the folder you chose
- "Open" gives other apps read-only access; the new **Edit with** gives write access only when you choose it (never for read-only servers)
- FTPS checks the server name. Self-signed WebDAV/FTPS certificates are pinned after you confirm their fingerprint, instead of trusting anything
- SMB: **Require signing** (on by default) and **Encrypt** options; guest access is now an explicit switch
- SFTP shows SHA-256 host key fingerprints and asks before trusting a server the first time
- Saving a server now fails visibly if its password can't be encrypted
- Nothing is included in cloud backups, and the Recents screen never shows a snapshot

## 1.0.0 (2026-10-16)

First release: a Material-style file manager with network storage.

### Network locations
- **NFS v3 and v4**, **SMB 2/3**, **SFTP**, **FTP/FTPS** and **WebDAV**, all behind one **Add network** button
- Other apps can open and save files on any of these through the Android file picker
- Network shares show used and total space under the folder name
- Passwords and keys are encrypted with the Android Keystore
- Connections that went stale while FilesCtrl was in the background now reconnect by themselves

### Files
- Background copy, move, delete and zip with a progress notification
- Breadcrumbs, list and grid views, sort, multi-select, search, properties and permissions
- **Built-in text editor**, also offered to other apps under "Open with" / "Edit with" for text, config and code files. It keeps the file's encoding and saves back when the other app allows it
- Bookmarks

### Design
- Same layout as the other Ctrl apps: a floating glass tab island on phones (rail on wide screens) with Files, Places and Settings
- App lock with PIN or fingerprint
- New icon: a two-tone white folder on deep purple

### Fixes during 1.0.0
- Pull to refresh no longer gets stuck
- WebDAV folders on Apache-style servers open correctly, and FTP/SFTP start in your home folder
- Pasting into a folder can no longer delete the item being pasted, failed copies only clean up what they created, and non-UTF-8 file names no longer crash the app
