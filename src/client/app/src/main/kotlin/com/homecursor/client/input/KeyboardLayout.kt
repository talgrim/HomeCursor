package com.homecursor.client.input

import com.homecursor.client.protocol.SpecialKey

/** One cell of a keyboard row: a key, or a gap when [key] is null. [width] is relative to the other cells of the row. */
data class KeyCell(val key: SpecialKey?, val width: Float = 1f)

/** The US QWERTY layout of the on-screen keyboard. Every row adds up to [ROW_WIDTH], so the rows line up. */
object KeyboardLayout {
    const val ROW_WIDTH = 15f

    val rows: List<List<KeyCell>> = listOf(
        listOf(cell(SpecialKey.Escape)) + (1..12).map { cell(SpecialKey.valueOf("F$it")) } +
            cell(SpecialKey.Delete, 2f),
        listOf(cell(SpecialKey.Grave)) + digits() + listOf(
            cell(SpecialKey.Minus), cell(SpecialKey.Equals), cell(SpecialKey.Backspace, 2f),
        ),
        listOf(cell(SpecialKey.Tab, 1.5f)) + letters("QWERTYUIOP") + listOf(
            cell(SpecialKey.LeftBracket), cell(SpecialKey.RightBracket), cell(SpecialKey.Backslash, 1.5f),
        ),
        listOf(KeyCell(null, 1.75f)) + letters("ASDFGHJKL") + listOf(
            cell(SpecialKey.Semicolon), cell(SpecialKey.Quote), cell(SpecialKey.Enter, 2.25f),
        ),
        listOf(cell(SpecialKey.Shift, 2.25f)) + letters("ZXCVBNM") + listOf(
            cell(SpecialKey.Comma), cell(SpecialKey.Period), cell(SpecialKey.Slash), cell(SpecialKey.Shift, 2.75f),
        ),
        listOf(
            cell(SpecialKey.Control, 1.5f), cell(SpecialKey.Windows, 1.5f), cell(SpecialKey.Alt, 1.5f),
            cell(SpecialKey.Space, 6.5f),
            cell(SpecialKey.Left), cell(SpecialKey.Down), cell(SpecialKey.Up), cell(SpecialKey.Right),
        ),
    )

    private fun cell(key: SpecialKey, width: Float = 1f) = KeyCell(key, width)

    private fun letters(letters: String) = letters.map { cell(SpecialKey.valueOf(it.toString())) }

    private fun digits() = (1..9).map { cell(SpecialKey.valueOf("D$it")) } + cell(SpecialKey.D0)
}

/** What a key is called on screen: the cap of the key on the keyboard, and its name in a shortcut. */
object KeyLabels {
    fun of(key: SpecialKey): String = when (key) {
        SpecialKey.Enter -> "Enter"
        SpecialKey.Backspace -> "⌫"
        SpecialKey.Tab -> "Tab"
        SpecialKey.Escape -> "Esc"
        SpecialKey.Delete -> "Del"
        SpecialKey.Insert -> "Ins"
        SpecialKey.Left -> "←"
        SpecialKey.Right -> "→"
        SpecialKey.Up -> "↑"
        SpecialKey.Down -> "↓"
        SpecialKey.Home -> "Home"
        SpecialKey.End -> "End"
        SpecialKey.PageUp -> "PgUp"
        SpecialKey.PageDown -> "PgDn"
        SpecialKey.Shift -> "Shift"
        SpecialKey.Control -> "Ctrl"
        SpecialKey.Alt -> "Alt"
        SpecialKey.Windows -> "Win"
        SpecialKey.Space -> "Space"
        SpecialKey.Minus -> "-"
        SpecialKey.Equals -> "="
        SpecialKey.LeftBracket -> "["
        SpecialKey.RightBracket -> "]"
        SpecialKey.Backslash -> "\\"
        SpecialKey.Semicolon -> ";"
        SpecialKey.Quote -> "'"
        SpecialKey.Comma -> ","
        SpecialKey.Period -> "."
        SpecialKey.Slash -> "/"
        SpecialKey.Grave -> "`"
        // Letters and F-keys are named like their caps; D0..D9 are the digits.
        else -> if (key.name.length == 2 && key.name[0] == 'D' && key.name[1].isDigit()) key.name.substring(1) else key.name
    }

    /** "Alt + Enter". */
    fun combination(keys: List<SpecialKey>): String = keys.joinToString(" + ") { of(it) }
}
