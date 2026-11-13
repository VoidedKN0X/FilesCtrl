package com.vknxworks.filesctrl.crypt

import java.io.IOException

/**
 * A tiny parser for the flat JSON objects in a Cryptomator vault (masterkey.cryptomator, the JWT parts of
 * vault.cryptomator): one object of strings, numbers, booleans and nulls. Nested values are refused. Pure Kotlin so
 * it runs in the JVM tests (Android's org.json is a stub there), and bounded so a hostile file can't blow up memory.
 */
internal object FlatJson {
    const val MAX_LENGTH = 64 * 1024

    fun parse(text: String): Map<String, Any?> {
        if (text.length > MAX_LENGTH) throw IOException("JSON too large")
        val p = P(text)
        p.ws()
        p.expect('{')
        val out = LinkedHashMap<String, Any?>()
        p.ws()
        if (p.peek() == '}') { p.i++; p.end(); return out }
        while (true) {
            p.ws()
            val k = p.string()
            p.ws(); p.expect(':'); p.ws()
            out[k] = p.value()
            p.ws()
            when (p.next()) {
                ',' -> continue
                '}' -> { p.end(); return out }
                else -> throw IOException("Bad JSON")
            }
        }
    }

    fun write(map: Map<String, Any?>): String = map.entries.joinToString(",", "{", "}") { (k, v) ->
        quote(k) + ":" + when (v) {
            null -> "null"
            is String -> quote(v)
            is Number, is Boolean -> v.toString()
            else -> throw IllegalArgumentException("Unsupported value")
        }
    }

    private fun quote(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    private class P(val s: String) {
        var i = 0
        fun peek(): Char = if (i < s.length) s[i] else throw IOException("Bad JSON")
        fun next(): Char = peek().also { i++ }
        fun expect(c: Char) { if (next() != c) throw IOException("Bad JSON") }
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun end() { ws(); if (i != s.length) throw IOException("Bad JSON") }

        fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                val c = next()
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b'); 'f' -> b.append('\u000C'); 'n' -> b.append('\n')
                        'r' -> b.append('\r'); 't' -> b.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) throw IOException("Bad JSON")
                            b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: throw IOException("Bad JSON"))
                            i += 4
                        }
                        else -> throw IOException("Bad JSON")
                    }
                    c < ' ' -> throw IOException("Bad JSON")
                    else -> b.append(c)
                }
            }
        }

        fun value(): Any? = when (val c = peek()) {
            '"' -> string()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> if (c == '-' || c.isDigit()) number() else throw IOException("Bad JSON")
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw IOException("Bad JSON")
            i += w.length
            return v
        }

        private fun number(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw IOException("Bad JSON")
        }
    }
}
