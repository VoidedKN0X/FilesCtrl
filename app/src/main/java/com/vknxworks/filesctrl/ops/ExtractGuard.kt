package com.vknxworks.filesctrl.ops

/**
 * Stops an extraction that would fill the phone: an archive can be made so that a few kilobytes unpack to far
 * more than any storage holds (an "archive bomb").
 *
 * The sizes an archive claims for its files say nothing (a bomb lies about them, and some formats don't give
 * any), so only what has really been written counts. Nothing is checked for the first [limit] bytes, so an
 * ordinary extraction is never touched. After that the free space is looked at again every [every] bytes
 * written, and the extraction has to stop once less than [reserve] is free. With no free-space figure
 * ([freeBytes] gives null) it never stops anything.
 */
class ExtractGuard(
    private val freeBytes: () -> Long?,
    private val limit: Long = LIMIT,
    private val every: Long = EVERY,
    private val reserve: Long = RESERVE,
) {
    private var written = 0L
    private var nextCheck = limit

    /** Counts [n] more bytes as written. True when the extraction has to stop now. */
    fun wrote(n: Int): Boolean {
        if (n > 0) written += n
        if (written < nextCheck) return false
        nextCheck = written + every
        val free = freeBytes() ?: return false
        return free < reserve
    }

    companion object {
        /** Written in one extraction before the free space is looked at at all: 1 GiB. */
        const val LIMIT = 1L shl 30
        /** How often it's looked at after that: every 8 MiB written. */
        const val EVERY = 8L shl 20
        /** The free space below which the extraction stops: 16 MiB. */
        const val RESERVE = 16L shl 20
    }
}
