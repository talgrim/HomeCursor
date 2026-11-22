package com.homecursor.client.ui

import com.homecursor.client.input.FakeInputSender
import com.homecursor.client.input.FakeInputSender.Event.Key
import com.homecursor.client.input.FakeInputSender.Event.Text
import com.homecursor.client.input.InputSender
import com.homecursor.client.protocol.SpecialKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {
    private val sender = FakeInputSender()
    private val senders = MutableStateFlow<InputSender>(sender)

    // Unconfined: the view model's collector of the sender runs at once, no scheduler needed.
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = KeyboardViewModel(senders)

    @Test
    fun startsClosedInRealTimeMode() {
        val viewModel = viewModel()

        assertFalse(viewModel.isOpen.value)
        assertEquals(KeyboardMode.RealTime, viewModel.mode.value)
    }

    @Test
    fun openAndClose_toggleIsOpen() {
        val viewModel = viewModel()

        viewModel.open()
        assertTrue(viewModel.isOpen.value)
        viewModel.close()
        assertFalse(viewModel.isOpen.value)
    }

    @Test
    fun ordinaryKey_isPressedAndReleased() {
        viewModel().onKeyTapped(SpecialKey.A)

        assertEquals(listOf(Key(SpecialKey.A, true), Key(SpecialKey.A, false)), sender.events)
    }

    @Test
    fun modifier_staysHeldAfterAnOrdinaryKey() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Control)
        viewModel.onKeyTapped(SpecialKey.C)

        assertEquals(
            listOf(Key(SpecialKey.Control, true), Key(SpecialKey.C, true), Key(SpecialKey.C, false)),
            sender.events,
        )
        assertEquals(setOf(SpecialKey.Control), viewModel.heldModifiers.value)
    }

    @Test
    fun windowsHeld_movesAWindowWithSeveralArrowsWithoutReleasing() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Windows)
        repeat(4) { viewModel.onKeyTapped(SpecialKey.Right) }
        viewModel.onKeyTapped(SpecialKey.Up)
        viewModel.onKeyTapped(SpecialKey.Windows)

        val expected = buildList {
            add(Key(SpecialKey.Windows, true))
            repeat(4) { addAll(listOf(Key(SpecialKey.Right, true), Key(SpecialKey.Right, false))) }
            addAll(listOf(Key(SpecialKey.Up, true), Key(SpecialKey.Up, false)))
            add(Key(SpecialKey.Windows, false))
        }
        assertEquals(expected, sender.events)
        assertTrue(viewModel.heldModifiers.value.isEmpty())
    }

    @Test
    fun shiftHeld_typesSeveralLettersWithoutReleasing() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Shift)
        viewModel.onKeyTapped(SpecialKey.H)
        viewModel.onKeyTapped(SpecialKey.I)

        assertEquals(
            listOf(
                Key(SpecialKey.Shift, true),
                Key(SpecialKey.H, true), Key(SpecialKey.H, false),
                Key(SpecialKey.I, true), Key(SpecialKey.I, false),
            ),
            sender.events,
        )
        assertEquals(setOf(SpecialKey.Shift), viewModel.heldModifiers.value)
    }

    @Test
    fun altHeld_cyclesWindowsWithRepeatedTabAndReleasesOnlyWhenTappedAgain() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Alt)
        repeat(3) { viewModel.onKeyTapped(SpecialKey.Tab) }
        assertEquals(1 + 3 * 2, sender.events.size)
        assertEquals(setOf(SpecialKey.Alt), viewModel.heldModifiers.value)
        viewModel.onKeyTapped(SpecialKey.Alt)

        assertEquals(Key(SpecialKey.Alt, true), sender.events.first())
        assertEquals(Key(SpecialKey.Alt, false), sender.events.last())
        assertEquals(1, sender.events.count { it == Key(SpecialKey.Alt, false) })
        assertTrue(viewModel.heldModifiers.value.isEmpty())
    }

    @Test
    fun severalModifiers_allStayHeldAfterAKey() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Control)
        viewModel.onKeyTapped(SpecialKey.Shift)
        viewModel.onKeyTapped(SpecialKey.Escape)

        assertEquals(
            listOf(
                Key(SpecialKey.Control, true), Key(SpecialKey.Shift, true),
                Key(SpecialKey.Escape, true), Key(SpecialKey.Escape, false),
            ),
            sender.events,
        )
        assertEquals(setOf(SpecialKey.Control, SpecialKey.Shift), viewModel.heldModifiers.value)
    }

    @Test
    fun tappingAHeldModifierAgain_releasesIt() {
        val viewModel = viewModel()

        viewModel.onKeyTapped(SpecialKey.Alt)
        viewModel.onKeyTapped(SpecialKey.Alt)

        assertEquals(listOf(Key(SpecialKey.Alt, true), Key(SpecialKey.Alt, false)), sender.events)
        assertTrue(viewModel.heldModifiers.value.isEmpty())
    }

    @Test
    fun close_releasesHeldModifiers() {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onKeyTapped(SpecialKey.Windows)

        viewModel.close()

        assertEquals(listOf(Key(SpecialKey.Windows, true), Key(SpecialKey.Windows, false)), sender.events)
        assertTrue(viewModel.heldModifiers.value.isEmpty())
    }

    @Test
    fun onBackground_releasesHeldModifiersAndClosesTheKeyboard() {
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onKeyTapped(SpecialKey.Shift)

        viewModel.onBackground()

        assertEquals(Key(SpecialKey.Shift, false), sender.events.last())
        assertFalse(viewModel.isOpen.value)
    }

    @Test
    fun switchingMode_releasesHeldModifiers() {
        val viewModel = viewModel()
        viewModel.onKeyTapped(SpecialKey.Control)

        viewModel.onModeChanged(KeyboardMode.Compose)

        assertEquals(KeyboardMode.Compose, viewModel.mode.value)
        assertEquals(Key(SpecialKey.Control, false), sender.events.last())
    }

    @Test
    fun onTextSent_sendsTheWholeText_afterReleasingModifiers() {
        val viewModel = viewModel()
        viewModel.onKeyTapped(SpecialKey.Control)

        viewModel.onTextSent("hello \u017C")

        assertEquals(
            listOf(Key(SpecialKey.Control, true), Key(SpecialKey.Control, false), Text("hello \u017C")),
            sender.events,
        )
    }

    @Test
    fun onTextSent_empty_sendsNothing() {
        viewModel().onTextSent("")

        assertTrue(sender.events.isEmpty())
    }

    @Test
    fun newConnection_forgetsHeldModifiersWithoutSendingAnything() {
        val viewModel = viewModel()
        viewModel.onKeyTapped(SpecialKey.Alt)
        val second = FakeInputSender()

        senders.value = second

        assertTrue(viewModel.heldModifiers.value.isEmpty())
        assertTrue(second.events.isEmpty())
    }
}
