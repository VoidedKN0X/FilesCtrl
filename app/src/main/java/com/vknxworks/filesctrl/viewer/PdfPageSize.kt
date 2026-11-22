package com.vknxworks.filesctrl.viewer

/**
 * The bitmap size a PDF page is drawn at: the screen width, scaled down so one page never takes more than
 * [MAX_PIXELS] (about 32 MB). Twice the screen width, as before, made a normal A4 page about 26 MB and a tall
 * page (a receipt, an exported web page) up to 128 MB, which ran out of memory and left the page blank.
 */
object PdfPageSize {
    const val MAX_PIXELS = 8_000_000L

    /** Width and height in pixels for a page of [pageWidth] x [pageHeight] points on a [screenWidth] wide screen. */
    fun of(screenWidth: Int, pageWidth: Int, pageHeight: Int): Pair<Int, Int> {
        var w = screenWidth.coerceIn(1, 4096)
        val ratio = pageHeight.coerceAtLeast(1).toDouble() / pageWidth.coerceAtLeast(1)
        var h = (w * ratio).toLong().coerceAtLeast(1)
        if (w.toLong() * h > MAX_PIXELS) {
            val scale = kotlin.math.sqrt(MAX_PIXELS.toDouble() / (w.toLong() * h))
            w = (w * scale).toInt().coerceAtLeast(1)
            h = (w * ratio).toLong().coerceAtLeast(1)
        }
        // A page can be so long that even 1 pixel wide is too much: cap the height too.
        return w to h.coerceAtMost(MAX_PIXELS / w).toInt().coerceAtLeast(1)
    }
}
