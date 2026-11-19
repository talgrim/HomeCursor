package com.homecursor.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialKeyTest {
    @Test
    fun codes_matchTheSpec() {
        assertEquals(1, SpecialKey.Enter.code)
        assertEquals(18, SpecialKey.Windows.code)
        assertEquals(19, SpecialKey.Space.code)
        assertEquals(32, SpecialKey.F1.code)
        assertEquals(43, SpecialKey.F12.code)
        assertEquals(64, SpecialKey.A.code)
        assertEquals(89, SpecialKey.Z.code)
        assertEquals(96, SpecialKey.D0.code)
        assertEquals(105, SpecialKey.D9.code)
        assertEquals(112, SpecialKey.Minus.code)
        assertEquals(122, SpecialKey.Grave.code)
    }

    @Test
    fun codes_areUnique() {
        assertEquals(SpecialKey.entries.size, SpecialKey.entries.map { it.code }.toSet().size)
    }

    @Test
    fun fromCode_findsEveryKey_andRejectsUnknown() {
        for (key in SpecialKey.entries) assertEquals(key, SpecialKey.fromCode(key.code))
        assertNull(SpecialKey.fromCode(0))
        assertNull(SpecialKey.fromCode(255))
    }

    @Test
    fun isModifier_isTrueOnlyForTheFourModifiers() {
        val modifiers = SpecialKey.entries.filter { it.isModifier }.toSet()
        assertEquals(setOf(SpecialKey.Shift, SpecialKey.Control, SpecialKey.Alt, SpecialKey.Windows), modifiers)
        assertFalse(SpecialKey.Enter.isModifier)
        assertTrue(SpecialKey.Alt.isModifier)
    }
}
