package com.vknxworks.filesctrl.ops

/** A file or folder in a sync, by its path relative to the folder being synced ("a/b.txt"). */
data class SyncMeta(val isDir: Boolean, val size: Long, val modified: Long)

/**
 * What a one-way sync does to make the destination match the source (pure, unit-tested):
 * - [create]: folders to make, parents first;
 * - [copy]: files missing in the destination;
 * - [update]: files that are there but differ (another size, or the source is newer by more than 2 seconds, the
 *   coarsest clock storage uses);
 * - [delete]: with "mirror" on, what's in the destination but not in the source, deepest first;
 * - [conflicts]: a file where the other side has a folder (or the other way round); with mirror on it's replaced,
 *   otherwise left alone.
 * Nothing in the source is ever changed.
 */
data class SyncPlan(
    val create: List<String>,
    val copy: List<String>,
    val update: List<String>,
    val delete: List<String>,
    val conflicts: List<String>,
    val bytes: Long,
    /** How many files and folders the source had when this plan was made (-1: not known). */
    val sourceItems: Int = -1,
) {
    /**
     * The deletes of this (fresh) plan that may really run, after the user confirmed the dry run [confirmed]:
     * only paths that were in the confirmed delete list too, so a sync never deletes something the user wasn't
     * shown. Nothing at all is deleted when the source now lists as empty while it wasn't at the dry run (a share
     * that isn't mounted, or a swapped SD card, lists as an empty folder without an error). Returns the deletes
     * to run and the ones left out.
     */
    fun deletesAllowedBy(confirmed: SyncPlan): Pair<List<String>, List<String>> {
        if (sourceItems == 0 && confirmed.sourceItems != 0) return emptyList<String>() to delete
        val ok = confirmed.delete.toHashSet()
        return delete.partition { it in ok }
    }

    val isEmpty: Boolean get() = create.isEmpty() && copy.isEmpty() && update.isEmpty() && delete.isEmpty() && conflicts.isEmpty()

    companion object {
        const val CLOCK_SLACK_MS = 2_000L

        /** "a/b/c" -> "a/b", "a". */
        private fun ancestors(rel: String): Sequence<String> = generateSequence(rel.substringBeforeLast('/', "").ifEmpty { null }) { p ->
            p.substringBeforeLast('/', "").ifEmpty { null }
        }

        fun of(src: Map<String, SyncMeta>, dst: Map<String, SyncMeta>, mirror: Boolean): SyncPlan {
            val create = ArrayList<String>()
            val copy = ArrayList<String>()
            val update = ArrayList<String>()
            val delete = ArrayList<String>()
            val conflicts = ArrayList<String>()
            var bytes = 0L
            // Paths under a destination item that gets replaced wholesale (a conflict with mirror on).
            val replaced = HashSet<String>()
            for (rel in src.keys.sortedWith(compareBy<String> { it.count { c -> c == '/' } }.thenBy { it })) {
                val s = src.getValue(rel)
                val d = dst[rel]
                val underReplaced = replaced.isNotEmpty() && ancestors(rel).any { it in replaced }
                when {
                    d == null || underReplaced -> if (s.isDir) create += rel else { copy += rel; bytes += s.size }
                    d.isDir != s.isDir -> {
                        conflicts += rel
                        if (mirror) {
                            replaced += rel
                            if (s.isDir) create += rel else { copy += rel; bytes += s.size }
                        }
                    }
                    s.isDir -> Unit
                    s.size != d.size || s.modified > d.modified + CLOCK_SLACK_MS -> { update += rel; bytes += s.size }
                }
            }
            if (mirror) {
                for (rel in dst.keys) {
                    if (rel in src && rel !in replaced) continue
                    // Inside a folder that's deleted anyway: the folder's delete takes it.
                    val parentGone = ancestors(rel).any { p -> dst[p]?.isDir == true && (p !in src || p in replaced) }
                    if (!parentGone) delete += rel
                }
            }
            delete.sortByDescending { it.count { c -> c == '/' } }
            return SyncPlan(create, copy, update, delete, conflicts, bytes, src.size)
        }
    }
}
