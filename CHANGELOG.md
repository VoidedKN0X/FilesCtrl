# Changelog

What changed in each FilesCtrl version. Every release on the [Releases page](https://github.com/VoidedKN0X/FilesCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 1.4.0 (2026-10-25)

FilesCtrl gets the new **Ctrl / Signal** look that the whole Ctrl series is moving to: dark, high contrast, one violet accent and two new typefaces. Only the look changes. Every screen, button, setting and feature works as before, and nothing you saved (servers, bookmarks, PIN, settings) is touched.

### New

- **Two bundled typefaces.** All text is now set in **Space Grotesk**, and paths, sizes, dates, addresses and section labels are set in **JetBrains Mono**. Both are built into the app (SIL Open Font License 1.1, licence texts included under `assets/licenses/`), so nothing is downloaded and no font service is used.
- **FILES/CTRL wordmark** at the top left of the Files and Places tabs, with the "/" in the accent colour.
- **Places has a storage card.** Your phone's main storage is now a large accent card at the top of Places, showing the free space as a big number, "x free of y", a usage bar and how full it is (or **Open** when you're browsing it). Tap it to open your phone's storage as before.
- **Fonts row on the You page** under About, linking to the SIL Open Font License.

### Changed

- **Dark only.** FilesCtrl now always uses its dark Signal theme: a near-black background (#08080A), dark grey cards with a thin border, light text and a single violet accent (#B48CFF). The status bar and navigation bar take the same background colour with light icons, edge to edge.
- **Top bar of the Files tab**: the wordmark, then search, sort and ⋮, then your profile button. Below it, the folder you're in is shown in large bold type, with where it is (this phone, a server's address, or a share's storage use, plus "read-only" when it applies) as a small mono label above it.
- **Path bar** under the folder name: the path is now in mono with an accent "/" between folders; the current folder is bright and the folders above it muted. Tap any part to go back up to it, as before.
- **File list**: plain rows separated by thin lines, a slightly smaller file icon, the name in medium weight, and **size · date** in mono below it (size now comes first). Hidden files stay dimmed.
- **Grid view**: tiles are dark cards with a thin border and rounder corners, and files show their size under the name.
- **Selection mode**: the top bar turns into an accent-coloured bar with the number selected in mono and the close, copy, cut, delete and ⋮ buttons in dark. Selected rows get an accent edge on the left and a light accent tint; selected tiles get an accent border.
- **New folder / new file button**: a violet rounded-square button with flat, bordered labels when it opens. The paste card above the bottom bar is a bordered dark card with an accent icon and an accent **Paste** button.
- **Bottom bar**: the frosted floating tab bar is replaced by a solid floating bar (rounded rectangle with a thin border) 28dp above the navigation bar. The open tab is an accent block with its icon and name; the other tab shows only its icon. Lists leave room below so nothing hides behind it. On tablets and unfolded foldables the side rail stays, in the same colours.
- **Profile button**: a rounded square with your phone's initials (instead of the round placeholder picture). A small accent square appears on its corner while you're browsing a network location. Tapping it opens the You page, as before.
- **Places**: SD cards and USB drives, network locations and bookmarks are now plain rows under mono labels. Network locations show their protocol (NFS, SMB, SFTP, FTP, WebDAV) as a mono tag, the address or storage use in mono, and a small square that is violet while that location is open or connected. **Add network** is a full-width bordered button under the list.
- **You page**: a close button at the top, a large violet square with your phone's initials, the phone's name and its Android version and number of saved locations in mono. **Settings** is under **App**; **Report a bug**, **Source code**, **Licence** and **Fonts** are under **About**. **Support FilesCtrl** is now a large violet button at the bottom, followed by "GOOGLE-FREE · TELEMETRY-FREE · ARM64" and the version.
- **Settings**: every group is a dark card with a mono, upper-case title (APPEARANCE, PRIVACY AND SECURITY, FILES, PERMISSIONS, OTHER APPS) and thin lines between rows. Icons are outlined and no longer sit on coloured circles. Choices such as **Lock again** show the current value in mono on the right, and open a dark bottom sheet where the current choice is violet with a small square.
- **Switches** are new rounded-rectangle switches: violet with a dark knob when on, grey when off. They still read as switches to screen readers, and the whole row toggles as before. This applies to every switch in Settings and in the server editor, including **Hide in screenshots** and **Hide in recent apps** (both still off by default).
- **Lock screen and PIN screens**: a violet rounded-square lock icon, "VKNXWORKS · CTRL" in mono, **Locked** in large type and your hint text below. The PIN is shown as small squares that fill in violet. The keypad is a full-width 3 × 4 grid of rounded dark keys with mono digits. **Use fingerprint** is now the violet-outlined key at the bottom left of the keypad (it turns into the confirm key while a PIN long enough to confirm is typed, where one is needed), and delete is an icon-only key at the bottom right. **Forgot PIN?** and **Cancel** sit under the keypad. The lock itself (PIN, fingerprint, lock timing, wrong-PIN waits) works exactly as before.
- **Server editor**: square back button and an accent **Save** button at the top, mono section labels, protocol choices as mono chips that turn violet when chosen, NFS version as a squared segmented control, dark bordered text fields that get a violet border and cursor while you type, an accent **Test connection** button, and the result in a bordered card (violet when it worked, red when it didn't).
- **Dialogs and menus** (new folder, rename, delete, paste conflicts, properties, permissions, passwords, compress, extract, trust a server, forgot PIN, unsaved changes) are dark cards with rounder 26dp corners and a thin border, with violet buttons and red for destructive actions. Text fields in dialogs use the same dark, bordered style.
- **Search** opens as a dark bordered search field next to a square back button.
- **Text editor**: square back button, file name in the new type with the location and encoding in mono, and the text itself in JetBrains Mono with a violet cursor.
- **Empty, error and permission screens**: the icon sits on a small bordered dark tile, with **Retry**, **Enter password** and **Grant access** as violet buttons and **Edit server** as a bordered button.
- **Progress**: the loading line under the path bar and the storage bars are flat bars in the new colours.

### Removed

- **Theme setting** (Follow system, Light, Dark, Pure black) in Settings > Appearance, because FilesCtrl is dark only now. If you had picked a light theme, the app switches to the dark look.
- **Colours from your wallpaper** (Material You dynamic colour) switch in Settings > Appearance. The violet accent is always used. **Grid view** stays in Appearance.
- The frosted-glass blur behind the tab bar, and the library that drew it (Haze), which makes the app a little smaller.

## 1.3.5 (2026-10-22)

### Fixed

- **FilesCtrl asks for notifications at the right moment.** On Android 13 and newer an app has to ask before it can show notifications. FilesCtrl asked the moment it started, even while the app lock screen was still up, and never again afterwards, so on some phones the question was lost and the progress of copies and moves (and the note when they finish in the background) silently never showed. It now asks once, the first time the file browser itself is on screen: after you've unlocked, with no PIN screen on top. If you've already answered, it doesn't ask again.

### New

- **Notifications under Settings > Permissions.** A new row shows whether FilesCtrl may show notifications. When they're blocked it says **Notifications are blocked** with an **Allow** button: it shows Android's question again if Android still allows that, or otherwise opens FilesCtrl's notification settings so you can turn them on there. The row updates as soon as you come back from Android's settings. When notifications are allowed, tapping the row opens those settings too, for example to change how the finished-copy notification behaves.

## 1.3.4 (2026-10-21)

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left FilesCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** could do nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while FilesCtrl was still going to the background or coming back on screen, the phone dropped it without answering, and the lock screen only asked once per lock. Now the prompt only opens once FilesCtrl (the file browser or the text editor) is fully back on screen, a prompt that never answered can't block the next one (tapping **Use fingerprint** always opens a fresh one), and the lock screen offers your fingerprint again every time you come back to it. The same applies to the prompt for turning on fingerprint unlock in **Settings**.

## 1.3.3 (2026-10-21)

### Privacy

- **No hidden dependency list in the app file any more**: the build tools used to tuck an encrypted list of the libraries FilesCtrl is built with into the APK's signature block. Only Google can read it, so nobody checking the app could see what it holds. It's now left out, the same as in the other Ctrl apps. Nothing changes in how the app works.

## 1.3.2 (2026-10-21)

### Changed

- **Whole sentences instead of joined pieces, ready for translations**: archive types ("TAR · gzip") and the " · " between details in the file list, the text editor and the You page now come from the app's text resources as single pieces of text. The wording stays the same.
- No change in the app: the automatic protocol and archive tests now read the app's text from its text resources, so they check the same error messages you see.

## 1.3.1 (2026-10-21)

### Changed

- **All text is now in one place, ready for translations.** About 400 pieces of text that were written straight into the code (the file browser and its menus, Places, the server editor, properties and permission dialogs, the text editor, compress and extract, file-operation progress and notifications, and the error messages from NFS, SMB, SFTP, FTP, WebDAV and archives) now come from the app's text resources. The wording stays the same.

### Fixed

- **Counts read correctly in the singular and plural**: "Left out 2 entries" instead of "2 entrys", and "1 result", "1 item to copy", "Moving 1 item", "Deleting 1 item", "1 operation running", "Done with 1 problem" and "1 share found" now use the right form everywhere, including the progress notification.

## 1.3.0 (2026-10-21)

Settings now open from your profile picture, the same in every Ctrl app.

### New

- **A profile picture at the top right** of the Files and Places tabs (on Files, after the search, sort and ⋮ buttons). FilesCtrl has no accounts, so it's the same round placeholder picture the other Ctrl apps use. Tapping it opens the new **You** page
- **The You page** has a card for this phone (its name, Android version and how many network locations you've saved), then **Settings**, and **About** (Support FilesCtrl, Report a bug, Source code, Licence) with the version underneath, in the same layout as the other Ctrl apps

### Changed

- **Settings moved to your profile picture**: before, Settings was the third tab in the bottom bar. The Settings tab is gone, so the bottom bar (or the side rail on tablets and unfolded foldables) only has **Files** and **Places**. Tap your profile picture, then **Settings** for Appearance, Privacy and security, Files, Permissions and Other apps. It's the only way in, as in every Ctrl app. Settings now opens as its own page with a back arrow
- **About moved from the bottom of Settings to the bottom of the You page**, with the same four rows
- **README**: Getting started says where Settings and About are; the App lock, Privacy and Support sections say "tap your profile picture" instead of the Settings tab

## 1.2.3 (2026-10-21)

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in Settings › Privacy and security, both off by default, the same in every Ctrl app. Hide in screenshots makes screenshots and screen recordings show a blank screen; Hide in recent apps shows a blank card in the recent-apps view instead of the app (screenshots still work). This applies to the file browser and the text editor alike. Before, one setting, "Hide in screenshots and recent apps", did both, and the recent-apps view always showed a blank card. **What changes for you:** if you had turned that setting on yourself, both new switches are on. If you never changed it, both are now off, so screenshots work and the recent-apps view shows FilesCtrl; turn them on if you want the old behaviour. With the app lock on, the recent-apps view always shows a blank card.
- **Always protected, whatever the switches say:** adding or editing a network location, the lock screen and the PIN screens are never in screenshots, screen recordings or casting.
- The app lock's text and the Privacy and security rows now come from the app's translatable text resources, with the same wording as the other Ctrl apps. Nothing else changes in how they work.
- **README**: the App lock section now uses the same wording as the other Ctrl apps and describes the two switches.

## 1.2.2 (2026-10-21)

### Security

- **Adding or editing a network location, the lock screen and the PIN screens are never in screenshots, screen recordings or casting**, even with "Hide in screenshots and recent apps" turned off. That's where you type server passwords and key passphrases, and a recording of the PIN pad would show which keys were pressed. This covers the file browser and the text editor.

## 1.2.1 (2026-10-21)

The app lock and Settings now look and work the same as in every other Ctrl app.

### Changed

- **New lock screen look**, the same as in the other Ctrl apps: a round lock badge, PIN dots that show a spinner while your PIN is checked, and **Forgot PIN?**, which now asks "Forgot your PIN?" first and explains what a reset forgets.
- **Turning the app lock on, changing the PIN and turning the lock off now use the same full-screen PIN pad** as the lock screen, instead of a dialog with text boxes. Choosing a PIN asks for it twice; changing it asks for your current PIN first; turning the lock off asks for your PIN. After updating from an older, shorter PIN, the new PIN is chosen on the same full-screen pad.
- **Settings are grouped like in the other Ctrl apps**: **Appearance** first, then **Privacy and security** (formerly "Security"), Files, Permissions, Other apps and **About** at the bottom. Rows have the same look as the other apps (also on the Places tab), and choices such as Theme and Lock again open a list to pick from.
- **Privacy and security** has the same rows, wording and icons as every Ctrl app: **App lock**, **Change app PIN**, **Unlock with fingerprint**, **Lock again** and **Hide in screenshots and recent apps** (formerly "Hide from recents & screenshots"). A note under the section says FilesCtrl only connects to the network locations you add.
- **Hide in screenshots and recent apps is now a setting of its own**, also without the app lock, like in the other Ctrl apps. Before, it only worked while the app lock was on. If you update without the app lock, it starts off so nothing changes; new installs have it on.
- **Theme choices are the same as in the other Ctrl apps**: Follow system, Dark, Light or **Pure black**. Pure black is now one of the themes instead of a separate switch: if you had Dark with pure black on, you now get the Pure black theme; with "System" and pure black on, FilesCtrl follows the system with the normal dark colours (pick Pure black again if you want it). "Material You colours" is now called **Use wallpaper colours**.
- **The fingerprint prompts** now say what they're for ("Confirm it's you to see your files" when unlocking, "Confirm your fingerprint" when turning fingerprint unlock on).
- **README**: a new App lock section that explains the PIN, fingerprint unlock, the wrong-PIN wait, Lock again, hiding the app in screenshots and recent apps, and Forgot PIN. The Ctrl series table now lists AdCtrl and MemoCtrl.

### Security

- **Open dialogs and menus are covered by the lock.** When FilesCtrl locked while a dialog, menu or sheet was open (for example a rename or a server's password), that window could stay on top of the lock screen. The lock screen is now a full-screen window of its own above everything else, in the file browser and in the text editor.

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
