package com.vknxworks.filesctrl.ops

/**
 * The rules for renaming several files at once, and the new names they give (pure, unit-tested).
 *
 * In order: the name without its extension goes through *find and replace*, then into the *template* ("{name}" is
 * that name, a run of "#" is the counter with that many digits, e.g. "Holiday ###" gives "Holiday 001"), then the
 * *case* change. The extension is kept as it is. Folders have no extension: the whole name is used.
 */
data class RenameRules(
    val find: String = "",
    val replace: String = "",
    val matchCase: Boolean = false,
    val template: String = NAME,
    val start: Int = 1,
    val case: RenameCase = RenameCase.Keep,
) {
    companion object {
        const val NAME = "{name}"
    }
}

enum class RenameCase { Keep, Lower, Upper }

object BatchRename {
    /** The new name for [name] (a folder when [isDir]), the [index]-th in the list (0-based). */
    fun newName(name: String, isDir: Boolean, index: Int, rules: RenameRules): String {
        val dot = name.lastIndexOf('.')
        val hasExt = !isDir && dot > 0 && dot < name.length - 1
        var base = if (hasExt) name.substring(0, dot) else name
        val ext = if (hasExt) name.substring(dot) else ""
        if (rules.find.isNotEmpty()) base = replaceAll(base, rules.find, rules.replace, rules.matchCase)
        base = applyTemplate(rules.template.ifEmpty { RenameRules.NAME }, base, rules.start + index)
        base = when (rules.case) {
            RenameCase.Keep -> base
            RenameCase.Lower -> base.lowercase()
            RenameCase.Upper -> base.uppercase()
        }
        return base + ext
    }

    private fun replaceAll(s: String, find: String, replace: String, matchCase: Boolean): String {
        if (matchCase) return s.replace(find, replace)
        val out = StringBuilder()
        var i = 0
        while (true) {
            val j = s.indexOf(find, i, ignoreCase = true)
            if (j < 0) { out.append(s, i, s.length); break }
            out.append(s, i, j).append(replace)
            i = j + find.length
        }
        return out.toString()
    }

    private fun applyTemplate(template: String, name: String, counter: Int): String {
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            when {
                template.startsWith(RenameRules.NAME, i) -> { out.append(name); i += RenameRules.NAME.length }
                template[i] == '#' -> {
                    var n = 0
                    while (i < template.length && template[i] == '#') { n++; i++ }
                    out.append(counter.toString().padStart(n, '0'))
                }
                else -> { out.append(template[i]); i++ }
            }
        }
        return out.toString()
    }

    /** Why a planned name can't be used, for the preview. */
    enum class Problem { Invalid, Duplicate, Exists }

    /**
     * The plan for [names] (with [dirs] telling folders apart): the new name of each, and a problem when there is
     * one. [taken] are the other names already in the folder (not being renamed). Names that don't change have no
     * problem and are left alone. Two items getting the same new name, or a new name that another file already has,
     * are problems; a new name that one of the renamed items has now is fine (it's renamed in two steps).
     */
    fun plan(names: List<String>, dirs: List<Boolean>, rules: RenameRules, taken: Set<String>, valid: (String) -> Boolean): List<Pair<String, Problem?>> {
        val out = names.mapIndexed { i, n -> newName(n, dirs[i], i, rules) }
        val counts = HashMap<String, Int>()
        out.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return out.mapIndexed { i, n ->
            val problem = when {
                n == names[i] -> null
                !valid(n) -> Problem.Invalid
                (counts[n] ?: 0) > 1 -> Problem.Duplicate
                n in taken -> Problem.Exists
                else -> null
            }
            n to problem
        }
    }
}
