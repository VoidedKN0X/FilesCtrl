# FilesCtrl

## Encryption at rest

- Sealed with AES-256-GCM as text inside their SharedPreferences (`data/crypto/SealedText`: `FILESCTRL-REST1:` + Base64 of the `SealedFile` bytes, through `SealedPrefs`): the saved servers (`servers`/`list`, the whole JSON including host keys and certificate pins), the bookmarks (`bookmarks`/`list`), and `tabs` and `last_save_to` in `settings`. The data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`) is wrapped by the Keystore key `filesctrl_data_at_rest` (`KeystoreWrapper`/`KeystoreAes`; no user authentication, so the DocumentsProvider and running transfers work).
- Unchanged and separate: the per-secret Keystore encryption of passwords and SSH keys (`filesctrl_servers`), `VaultPasswords`, the app lock vault (`LockVault`), Cryptomator code. Not encrypted on purpose: plain settings, everything under `cacheDir` (viewer copies, archive copies, provider edit copies, 7z temp files: opened by path by other code, large, short-lived).
- `SealedPrefs.prepare()` (the only slow Keystore call) runs on the `servers-load` and `bookmarks-load` threads before each list is read. `ServerStore` and `Bookmarks` hold everything behind a latch (`awaitLoaded()`); `FilesApplication.onActivityCreated` waits for both. `Settings.tabs`/`lastSaveTo` are only read once a screen is up, when the key is in memory. Don't call `SealedPrefs.prepare/retry/renewIfLost` on the main thread, and don't read the sealed settings from `App.init`.
- Reading must accept both sealed and ordinary text. Without a data key everything is read and written as ordinary text, as before.
- Never overwrite a sealed list that can't be opened, never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). Unreadable servers or bookmarks set `unreadable` (Places shows the "Can't open your saved servers and bookmarks" card), `write`/`persist` do nothing and `ServerStore.save` throws. Unreadable tabs / last folder read as empty (they only remember a place).
- `SealedFileTest` covers the format, the key rules and `SealedPrefs` (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).

## Dependency verification

Gradle checks every downloaded library and plugin against the SHA-256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/FilesCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (between the BEGIN/END markers, `base64 -d | gunzip`), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push as the change. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "FilesCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
