package com.homecursor.client.shortcuts

import com.homecursor.client.protocol.SpecialKey

/**
 * Turns a shortcut list into one string and back, for storing it in a single preference (`org.json` is not available
 * in JVM unit tests). One shortcut per line: `id TAB name TAB code,code,...`. Backslash, tab and line breaks in the
 * name are escaped. Anything unreadable is skipped, so a damaged value costs a shortcut, not the app.
 */
object ShortcutSerializer {
    fun serialize(shortcuts: List<Shortcut>): String = shortcuts.joinToString("\n") { shortcut ->
        listOf(escape(shortcut.id), escape(shortcut.name), shortcut.keys.joinToString(",") { it.code.toString() })
            .joinToString("\t")
    }

    fun deserialize(text: String): List<Shortcut> =
        text.lineSequence().filter { it.isNotEmpty() }.mapNotNull(::parseLine).toList()

    private fun parseLine(line: String): Shortcut? {
        val parts = line.split('\t')
        if (parts.size != FIELD_COUNT) return null
        val keys = parts[2].split(',').map { code -> code.toIntOrNull()?.let(SpecialKey::fromCode) ?: return null }
        val id = unescape(parts[0])
        if (id.isEmpty() || keys.isEmpty()) return null
        return Shortcut(id, unescape(parts[1]), keys)
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val c = value[index++]
            if (c != '\\' || index == value.length) {
                append(c)
                continue
            }
            when (val next = value[index++]) {
                't' -> append('\t')
                'n' -> append('\n')
                'r' -> append('\r')
                else -> append(next)
            }
        }
    }

    private const val FIELD_COUNT = 3
}
