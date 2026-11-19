package com.homecursor.client.ui

import com.homecursor.client.input.FakeInputSender
import com.homecursor.client.input.FakeInputSender.Event.Key
import com.homecursor.client.input.FakeShortcutStore
import com.homecursor.client.input.InputSender
import com.homecursor.client.protocol.SpecialKey
import com.homecursor.client.shortcuts.DefaultShortcuts
import com.homecursor.client.shortcuts.Shortcut
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsViewModelTest {
    private val sender = FakeInputSender()
    private val store = FakeShortcutStore()
    private var nextId = 0

    private fun viewModel() = ShortcutsViewModel(MutableStateFlow<InputSender>(sender), store) { "custom-${nextId++}" }

    @Test
    fun freshInstall_startsWithTheDefaults() {
        assertEquals(DefaultShortcuts.all, viewModel().shortcuts.value)
    }

    @Test
    fun savedList_isLoadedInsteadOfTheDefaults() {
        val saved = listOf(Shortcut("x", "Mine", listOf(SpecialKey.F5)))
        store.saved = saved

        assertEquals(saved, viewModel().shortcuts.value)
    }

    @Test
    fun run_sendsTheKeysToTheCurrentSender() {
        val viewModel = viewModel()

        viewModel.run(DefaultShortcuts.all.first { it.id == "default.fullscreen" })

        assertEquals(
            listOf(
                Key(SpecialKey.Alt, true), Key(SpecialKey.Enter, true),
                Key(SpecialKey.Enter, false), Key(SpecialKey.Alt, false),
            ),
            sender.events,
        )
    }

    @Test
    fun add_appendsAndSaves_withModifiersFirst() {
        val viewModel = viewModel()

        viewModel.add("  Close tab ", listOf(SpecialKey.W, SpecialKey.Control))

        val added = viewModel.shortcuts.value.last()
        assertEquals(Shortcut("custom-0", "Close tab", listOf(SpecialKey.Control, SpecialKey.W)), added)
        assertEquals(viewModel.shortcuts.value, store.saved)
    }

    @Test
    fun add_withoutKeys_isIgnored() {
        val viewModel = viewModel()

        viewModel.add("Nothing", emptyList())

        assertEquals(DefaultShortcuts.all, viewModel.shortcuts.value)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun update_changesNameAndKeysInPlace() {
        val viewModel = viewModel()

        viewModel.update("default.copy", "Copy text", listOf(SpecialKey.Control, SpecialKey.Control, SpecialKey.Insert))

        val index = DefaultShortcuts.all.indexOfFirst { it.id == "default.copy" }
        assertEquals(
            Shortcut("default.copy", "Copy text", listOf(SpecialKey.Control, SpecialKey.Insert)),
            viewModel.shortcuts.value[index],
        )
        assertEquals(DefaultShortcuts.all.size, viewModel.shortcuts.value.size)
    }

    @Test
    fun delete_removesAndSaves() {
        val viewModel = viewModel()

        viewModel.delete("default.copy")

        assertTrue(viewModel.shortcuts.value.none { it.id == "default.copy" })
        assertEquals(viewModel.shortcuts.value, store.saved)
    }

    @Test
    fun move_swapsWithNeighbour_andStopsAtTheEnds() {
        val viewModel = viewModel()
        val first = DefaultShortcuts.all[0].id
        val second = DefaultShortcuts.all[1].id

        viewModel.move(first, 1)
        assertEquals(listOf(second, first), viewModel.shortcuts.value.take(2).map { it.id })

        viewModel.move(second, -1) // already first
        assertEquals(listOf(second, first), viewModel.shortcuts.value.take(2).map { it.id })

        val last = viewModel.shortcuts.value.last().id
        viewModel.move(last, 5) // already last
        assertEquals(last, viewModel.shortcuts.value.last().id)
    }

    @Test
    fun move_unknownId_changesNothing() {
        val viewModel = viewModel()

        viewModel.move("nope", 1)

        assertEquals(0, store.saveCount)
    }

    @Test
    fun restoreDefaults_addsOnlyTheMissingOnes_andKeepsEdits() {
        val viewModel = viewModel()
        viewModel.delete("default.copy")
        viewModel.update("default.paste", "My paste", listOf(SpecialKey.Control, SpecialKey.Insert))

        viewModel.restoreDefaults()

        val list = viewModel.shortcuts.value
        assertEquals("default.copy", list.last().id)
        assertEquals("My paste", list.first { it.id == "default.paste" }.name)
        assertEquals(DefaultShortcuts.all.size, list.size)
    }

    @Test
    fun restoreDefaults_whenNothingIsMissing_doesNotSave() {
        val viewModel = viewModel()

        viewModel.restoreDefaults()

        assertEquals(0, store.saveCount)
    }
}
