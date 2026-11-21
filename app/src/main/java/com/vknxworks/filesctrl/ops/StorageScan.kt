package com.vknxworks.filesctrl.ops

/**
 * What a storage analysis found under one folder (pure, unit-tested). Every folder is kept with its total size;
 * of the files only the biggest few per folder and overall are kept, so a folder with a million small files
 * doesn't fill the memory.
 */
class ScanNode(val name: String, val path: String, val isDir: Boolean, val vfsId: String) {
    /** Bytes in this folder and everything below it (for a file: its size). */
    var size: Long = 0
    /** Files in this folder and below. */
    var files: Int = 0
    /** Sub-folders, and the biggest files directly in this folder. */
    val children = ArrayList<ScanNode>()
    /** Files directly in this folder that aren't in [children] (only the biggest are kept), and their bytes. */
    var otherFiles: Int = 0
    var otherBytes: Long = 0
    /** Folders here that couldn't be read (no permission, a server error). */
    var unreadable: Int = 0
}

/** Collects the scan: per folder the biggest files, overall the biggest files and the bytes per file type. */
class ScanTotals(private val keepLargest: Int = 50) {
    val byType = HashMap<String, LongArray>() // ext -> [bytes, count]
    private val largest = java.util.PriorityQueue<ScanNode>(compareBy { it.size })

    fun addFile(f: ScanNode, ext: String) {
        val a = byType.getOrPut(ext.ifEmpty { "—" }) { LongArray(2) }
        a[0] += f.size
        a[1]++
        largest.add(f)
        if (largest.size > keepLargest) largest.poll()
    }

    fun largestFiles(): List<ScanNode> = largest.sortedByDescending { it.size }

    fun types(): List<Triple<String, Long, Int>> =
        byType.map { (k, v) -> Triple(k, v[0], v[1].toInt()) }.sortedByDescending { it.second }

    companion object {
        /** How many files are kept per folder. */
        const val FILES_PER_FOLDER = 30

        /** Keeps only the [FILES_PER_FOLDER] biggest of a folder's [files]; the rest are counted in the folder. */
        fun keepBiggest(folder: ScanNode, files: List<ScanNode>) {
            val sorted = files.sortedByDescending { it.size }
            folder.children += sorted.take(FILES_PER_FOLDER)
            for (f in sorted.drop(FILES_PER_FOLDER)) {
                folder.otherFiles++
                folder.otherBytes += f.size
            }
        }

        /** Children biggest first (after the scan). */
        fun sortTree(n: ScanNode) {
            n.children.sortByDescending { it.size }
            n.children.forEach { if (it.isDir) sortTree(it) }
        }
    }
}
