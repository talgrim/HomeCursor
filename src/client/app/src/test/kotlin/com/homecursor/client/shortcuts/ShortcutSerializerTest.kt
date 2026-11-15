package com.homecursor.client.shortcuts

import com.homecursor.client.protocol.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutSerializerTest {
    private fun roundTrip(shortcuts: List<Shortcut>) =
        ShortcutSerializer.deserialize(ShortcutSerializer.serialize(shortcuts))

    @Test
    fun roundTrip_keepsOrderNamesAndKeys() {
        val shortcuts = listOf(
            Shortcut("a", "Fullscreen", listOf(SpecialKey.Alt, SpecialKey.Enter)),
            Shortcut("b", "Play", listOf(SpecialKey.Space)),
        )

        assertEquals(shortcuts, roundTrip(shortcuts))
    }

    @Test
    fun roundTrip_defaults() {
        assertEquals(DefaultShortcuts.all, roundTrip(DefaultShortcuts.all))
    }

    @Test
    fun roundTrip_nameWithSeparatorsAndEscapes() {
        val shortcuts = listOf(Shortcut("a", "tab\there\nline \\ back\\n \u017C \uD83D\uDE42", listOf(SpecialKey.A)))

        assertEquals(shortcuts, roundTrip(shortcuts))
    }

    @Test
    fun roundTrip_emptyList() {
        assertTrue(roundTrip(emptyList()).isEmpty())
    }

    @Test
    fun deserialize_skipsDamagedLines() {
        val text = listOf(
            "ok\tName\t17,1",
            "missing fields",
            "bad\tName\t17,notanumber",
            "unknown\tName\t250",
            "nokeys\tName\t",
            "\tNo id\t1",
            "ok2\tSecond\t19",
        ).joinToString("\n")

        assertEquals(
            listOf(
                Shortcut("ok", "Name", listOf(SpecialKey.Alt, SpecialKey.Enter)),
                Shortcut("ok2", "Second", listOf(SpecialKey.Space)),
            ),
            ShortcutSerializer.deserialize(text),
        )
    }

    @Test
    fun deserialize_garbage_givesEmptyList() {
        assertTrue(ShortcutSerializer.deserialize("\u0000\u0001 garbage").isEmpty())
    }
}
