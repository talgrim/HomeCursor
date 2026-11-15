package com.homecursor.client.input

import com.homecursor.client.protocol.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun everyRow_addsUpToTheRowWidth() {
        for (row in KeyboardLayout.rows) {
            assertEquals(KeyboardLayout.ROW_WIDTH, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
        }
    }

    @Test
    fun layout_hasEveryLetterAndDigitOnce() {
        val keys = KeyboardLayout.rows.flatten().mapNotNull { it.key }
        for (letter in 'A'..'Z') assertEquals(1, keys.count { it.name == letter.toString() })
        for (digit in 0..9) assertEquals(1, keys.count { it == SpecialKey.valueOf("D$digit") })
    }

    @Test
    fun layout_hasTheKeysShortcutsNeed() {
        val keys = KeyboardLayout.rows.flatten().mapNotNull { it.key }.toSet()
        assertTrue(keys.containsAll(listOf(
            SpecialKey.Control, SpecialKey.Alt, SpecialKey.Shift, SpecialKey.Windows, SpecialKey.Space,
            SpecialKey.Enter, SpecialKey.Escape, SpecialKey.Tab, SpecialKey.Backspace,
        )))
    }

    @Test
    fun labels_areCapsOfTheKeys() {
        assertEquals("A", KeyLabels.of(SpecialKey.A))
        assertEquals("5", KeyLabels.of(SpecialKey.D5))
        assertEquals("F11", KeyLabels.of(SpecialKey.F11))
        assertEquals("Ctrl", KeyLabels.of(SpecialKey.Control))
        assertEquals("Alt + Enter", KeyLabels.combination(listOf(SpecialKey.Alt, SpecialKey.Enter)))
    }

    @Test
    fun labels_existForEveryKey() {
        for (key in SpecialKey.entries) assertTrue(KeyLabels.of(key).isNotEmpty())
    }
}
