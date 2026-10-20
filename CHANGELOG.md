# Changelog

What changed in each FilesCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 1.2.0 (2026-10-21)

The app lock now works the same way in every Ctrl app, with the strongest protection of the options compared.

### Changed

- **App PINs now need at least 6 digits** (up to 12), and very obvious ones such as 111111 or 123456 are refused.
- **Updating with the app lock on:** unlock once with your current PIN. If it has 6 or more digits and isn't obvious, it keeps working and nothing else changes. If it's shorter, FilesCtrl opens this one time and then asks you to choose a new PIN before you can carry on.
- **Turn fingerprint unlock on again after updating** (**Settings > Security > Unlock with fingerprint**). It now unlocks the key that encrypts your saved passwords, so the old fingerprint setting can't be carried over.
- **Fingerprint unlock needs your PIN after the phone restarts, and at least every 72 hours**, the same as Android does for your screen lock. Adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **More "Lock again" choices:** as soon as you leave the app (the default), after 1, 5 or 15 minutes away, after 1 hour away, or only when the app restarts. If you had chosen 30 seconds, it's now "As soon as you leave the app", so it never got less strict.
- **The lock now covers the whole app at once.** Unlocking the file browser also unlocks the text editor, and the other way round, and both lock together.
- **Opening a file in another app, sharing, picking a folder or file to save to, choosing an SFTP key or opening Android's settings from FilesCtrl doesn't lock the app straight away.** A video keeps streaming from your server while you watch it in another app. If you're away for more than 5 minutes (or longer than your "Lock again" time), FilesCtrl still locks when you come back. Going to the home screen or another app yourself locks as chosen.
- **With a "Lock again" delay, FilesCtrl now locks once that time has passed in the background**, instead of when you come back. Your passwords don't stay in memory any longer than you chose.
- **Half-typed text and server settings aren't lost when the app locks.** The text editor and the server form are still there, exactly as you left them, after you unlock.
- **Turning the app lock off, changing the PIN and the lock screen share one wrong-PIN limit**, so the current-PIN boxes in Settings can't be used to guess either.
- **"Forgot PIN?"** explains that the PIN can't be recovered because it encrypts your saved passwords and keys. Resetting removes the PIN and forgets those passwords and keys; your network locations stay.

### Security

- **Saved passwords and SFTP keys are encrypted with your PIN while FilesCtrl is locked.** They used to be encrypted with a key on the phone alone. Now, with the app lock on, they're encrypted again with a key that only exists in memory while the app is unlocked, and both that key and the passwords are wiped from memory the moment it locks. A locked FilesCtrl can't connect to your servers, and neither can a copy of its files.
- **Locking closes your server connections.** Copies and moves that are already running finish first; nothing new can connect until you unlock.
- **Other apps can't reach your servers while FilesCtrl is locked.** Your network locations disappear from Android's file picker ("Browse" in other apps) until you unlock, and files from them can't be opened.
- **Your PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying FilesCtrl's files to a computer isn't enough to try PINs there. Older versions stored a salted PBKDF2 hash of 120,000 rounds; it's deleted as soon as you unlock once.
- **Fingerprint unlock can't be faked:** the fingerprint has to unlock the key in the phone's secure hardware that decrypts your saved passwords, so a tampered "success" on a rooted phone doesn't open the app.
- **Wrong PINs lock the screen** as before: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait.

### Fixed

- **The text editor no longer keeps its own lock timer.** It used to lock separately from the file browser, so one could ask for the PIN while the other didn't.

## 1.1.4 (2026-10-20)

### Changed

- **64-bit ARM phones only.** FilesCtrl now ships only the 64-bit ARM (arm64-v8a) build that nearly every phone from the last several years uses, like every Ctrl app, so the download is smaller. Old 32-bit phones and x86 devices (emulators, some Chromebooks) are no longer supported.
- **README**: Requirements say 64-bit ARM phones only

## 1.1.3 (2026-10-20)

FilesCtrl is now de-Googled. Nothing changes in how you use it.

### Privacy

- **No mention of Google left in the app's own code.** FilesCtrl has no Google Play services, Firebase or other Google services and never contacts Google: it only talks to the servers you add. The last mention of Google in FilesCtrl's own files, a technical note in its setup file, is gone. It works the same on de-Googled phones such as GrapheneOS, CalyxOS, /e/OS and LineageOS without Google apps.

### Changed

- **README**: the Google-free line now reads "Google-free and de-Googled": no Google Play services, Firebase or other Google services, the app never contacts Google, and it works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps). It's the only place Google is mentioned in the README.

## 1.1.2 (2026-10-20)

### Privacy

- **No more usage stats: FilesCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting and the one-time question. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. FilesCtrl now only talks to the NFS, SMB, SFTP, FTP and WebDAV servers you add.

### Changed

- **README**: a "Telemetry-free" badge, and a Privacy section listing exactly what FilesCtrl connects to, in place of the old Usage stats section. The link preview image (`.github/assets/social-preview.png`) now carries a "Telemetry-free" tag too.

## 1.1.1 (2026-10-20)

### Changed

- **FilesCtrl always opens in phone storage.** It used to reopen the last folder you were in when you closed it. Now every fresh start begins in the top folder of your phone's internal storage. Your bookmarks, servers and settings stay as they were.

### Fixed

- **The back gesture goes back through your folders instead of closing the app.** Back first returns to the folders you opened before, in order, at the same scroll position. When there's nothing left to go back to (for example after you opened a folder from Places or a bookmark), back goes to the parent folder, one level at a time, then out of a network share or archive to phone storage. FilesCtrl only closes when you press back in phone storage's top folder. Back still closes selection, search and the other tabs first, as before.

## 1.1.0 (2026-10-19)

### New

- **Open compressed files like folders.** Tap a ZIP, 7z, RAR, TAR (also .tar.gz, .tgz, .tar.bz2, .tar.xz, .tar.zst, .tar.lz4 and others), CPIO, AR or DEB file to look inside it. You can move through its folders, tap files to view them in FilesCtrl's viewer or editor, open them in other apps, share them, and copy them out to any folder or network share. The top bar shows the archive's format and that it's read-only, and Back or the path bar takes you out again.
- **Single compressed files** (.gz, .bz2, .xz, .lzma, .zst, .lz4, .br, .sz, .Z) open the same way and show the one file inside, for example notes.txt inside notes.txt.gz.
- **Archives are recognised by their content**, so a ZIP with the wrong extension still opens. Files that are really ZIPs, such as .apk, .jar, .epub and Office documents (.docx, .xlsx, .pptx, .odt), have a **Browse contents** option in their menu.
- **Extract here** for every supported format, from a file's menu or the selection menu, and **Extract all** in the top bar while you're inside an archive. An archive with several files is extracted into a new folder named after it; a single compressed file is extracted next to it. Extraction runs in the background with progress, like copying, and works on your phone and on network shares.
- **Password-protected archives**: FilesCtrl asks for the password when you open or extract an encrypted ZIP (ZipCrypto or AES), 7z or RAR, and tells you when the password was wrong so you can try again.
- **Compress in more formats.** Compress now lets you pick ZIP, 7z, TAR.GZ, TAR.XZ, TAR.ZST or plain TAR, with a short note on what each one is good for. ZIP and 7z can be protected with a password (AES-256); with 7z, the contents are encrypted.

### Changed

- **Compress** now asks for the name, format and optional password in one dialog, and **Extract here** handles every supported format instead of only ZIP.
- **README**: a Screenshots section (screenshots are coming soon), and a link preview image with FilesCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere. The feature list now describes archive support.

### Security

- **Extracting can't write outside the destination folder.** Entries whose names try to escape it (with ".." or similar) are left out, and so are symbolic and hard links inside TAR archives. FilesCtrl tells you how many entries were skipped.
- Files inside archives are handed to other apps through FilesCtrl's own document provider and are always read-only. FilesCtrl's private folders are still never shared.

### Good to know

- RAR5 archives (made by WinRAR 5 and newer by default) can't be opened yet; FilesCtrl says so clearly. Older RAR archives work.
- Archives are read-only: to change something, extract it, edit it and compress it again.
- To open a ZIP, 7z or RAR stored on a network share, FilesCtrl first downloads a temporary copy, because these formats need to be read out of order. The copy is removed after a day. TAR and single compressed files are read straight from the share.
- Thumbnails aren't shown for pictures inside archives.

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
