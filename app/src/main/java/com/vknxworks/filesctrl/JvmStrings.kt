package com.vknxworks.filesctrl

import java.io.File

/**
 * Only for the JVM unit tests (no Android resources there): reads the same text from strings.xml, so error
 * messages in tests match the app's. The app itself always uses the Context in [App.str].
 */
internal object JvmStrings {
    private val strings: Map<String, String> by lazy { load("string") }
    private val plurals: Map<String, Map<String, String>> by lazy { loadPlurals() }

    private fun xml(): String =
        listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File).firstOrNull { it.isFile }?.readText().orEmpty()

    private fun unescape(raw: String) = raw.removeSurrounding("\"").replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun load(tag: String): Map<String, String> =
        Regex("<$tag name=\"([^\"]+)\"[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).findAll(xml())
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun loadPlurals(): Map<String, Map<String, String>> =
        Regex("<plurals name=\"([^\"]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).findAll(xml()).associate { p ->
            p.groupValues[1] to Regex("<item quantity=\"(\\w+)\">(.*?)</item>").findAll(p.groupValues[2])
                .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        }

    private fun name(kind: String, id: Int): String =
        runCatching { Class.forName("com.vknxworks.filesctrl.R\$$kind").fields.first { it.getInt(null) == id }.name }.getOrDefault("#$id")

    fun str(id: Int, vararg args: Any?): String {
        val n = name("string", id)
        return strings[n]?.let { if (args.isEmpty()) it else it.format(*args) } ?: n
    }

    fun plural(id: Int, count: Int, vararg args: Any?): String {
        val n = name("plurals", id)
        val p = plurals[n] ?: return n
        return (if (count == 1) p["one"] else p["other"])?.format(*args) ?: n
    }
}
