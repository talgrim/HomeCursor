package com.homecursor.client.shortcuts

import com.homecursor.client.input.FakeInputSender
import com.homecursor.client.input.FakeInputSender.Event.Key
import com.homecursor.client.protocol.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShortcutTest {
    @Test
    fun send_pressesKeysInOrderThenReleasesThemInReverse() {
        val sender = FakeInputSender()

        Shortcut("id", "Fullscreen", listOf(SpecialKey.Control, SpecialKey.Alt, SpecialKey.Enter)).send(sender)

        assertEquals(
            listOf(
                Key(SpecialKey.Control, true), Key(SpecialKey.Alt, true), Key(SpecialKey.Enter, true),
                Key(SpecialKey.Enter, false), Key(SpecialKey.Alt, false), Key(SpecialKey.Control, false),
            ),
            sender.events,
        )
    }

    @Test
    fun send_singleKey_isPressAndRelease() {
        val sender = FakeInputSender()

        Shortcut("id", "Play", listOf(SpecialKey.Space)).send(sender)

        assertEquals(listOf(Key(SpecialKey.Space, true), Key(SpecialKey.Space, false)), sender.events)
    }

    @Test
    fun shortcutWithoutKeys_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { Shortcut("id", "Nothing", emptyList()) }
    }

    @Test
    fun defaults_haveUniqueIds_andModifiersFirst() {
        assertEquals(DefaultShortcuts.all.size, DefaultShortcuts.all.map { it.id }.toSet().size)
        for (shortcut in DefaultShortcuts.all) {
            assertEquals(shortcut.keys.sortedBy { !it.isModifier }, shortcut.keys)
        }
    }

    @Test
    fun defaults_includeTheExamplesFromTheRequirements() {
        val byKeys = DefaultShortcuts.all.map { it.keys }
        assertEquals(true, listOf(SpecialKey.Alt, SpecialKey.Enter) in byKeys)
        assertEquals(true, listOf(SpecialKey.Space) in byKeys)
    }
}
