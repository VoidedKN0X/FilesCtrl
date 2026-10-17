# Changelog

What changed in each FilesCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases) also lists the exact changes in that build.

## 1.0.1 (2026-10-16 – 2026-10-17)

### Changed
- Removed the Transfers tab. Progress and cancel stay in the notification, and failures or skipped items show as a message while the app is open (build 17)

### Security (build 16)
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
- Connections that went stale while FilesCtrl was in the background now reconnect by themselves (build 15)

### Files
- Background copy, move, delete and zip with a progress notification
- Breadcrumbs, list and grid views, sort, multi-select, search, properties and permissions
- **Built-in text editor**, also offered to other apps under "Open with" / "Edit with" for text, config and code files. It keeps the file's encoding and saves back when the other app allows it (build 14)
- Bookmarks

### Design
- Same layout as the other Ctrl apps: a floating glass tab island on phones (rail on wide screens) with Files, Places and Settings (build 13)
- App lock with PIN or fingerprint
- New icon: a two-tone white folder on deep purple (build 8)

### Fixes during 1.0.0
- Pull to refresh no longer gets stuck (builds 7 and 12)
- WebDAV folders on Apache-style servers open correctly, and FTP/SFTP start in your home folder (build 11)
- Pasting into a folder can no longer delete the item being pasted, failed copies only clean up what they created, and non-UTF-8 file names no longer crash the app (build 6)
