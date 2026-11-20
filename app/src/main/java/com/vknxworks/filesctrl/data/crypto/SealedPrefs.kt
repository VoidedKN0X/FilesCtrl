package com.vknxworks.filesctrl.data.crypto

/**
 * Seals and opens the text values FilesCtrl keeps in SharedPreferences (the saved servers, the bookmarks, the
 * open tabs and the last "Save to" folder) with the app's data key.
 *
 * [prepare] asks the phone's secure hardware for the key and is slow: it runs on the background threads that
 * load the saved servers and bookmarks when the app starts. [seal] and [open] are meant for after that, when
 * the key is in memory (or known to be missing) and they cost next to nothing.
 */
class SealedPrefs(private val keys: DataKeyStore) {
    /** Gets the data key ready, making it at the first start. Slow; never on the main thread. Never throws. */
    fun prepare() {
        keys.get(create = true)
    }

    /** [plain] as it should be stored: sealed when there's a data key, the ordinary text otherwise (as before). */
    fun seal(plain: String): String {
        val key = keys.get(create = false) ?: return plain
        return SealedText.seal(key, plain) ?: plain
    }

    /** The text behind a stored value: ordinary text as it is, sealed text opened. Null when it can't be opened. */
    fun open(stored: String): String? {
        if (!SealedText.isSealed(stored)) return stored
        val key = keys.get(create = false) ?: return null
        return try {
            SealedText.open(key, stored)
        } catch (e: Exception) {
            null
        }
    }

    /** Asks the secure hardware for the key again, after it couldn't be had. Slow; never on the main thread. */
    fun retry() {
        keys.reset()
        keys.get(create = false)
    }

    /**
     * After the unreadable values were given up: a key that still works stays; one that's gone makes way for a
     * new one (with [keep] its file is set aside as `data.key.unreadable`, not deleted). Slow; off the main thread.
     */
    fun renewIfLost(keep: Boolean) {
        if (keys.get(create = false) == null) {
            keys.discard(keep)
            keys.get(create = true)
        }
    }
}
