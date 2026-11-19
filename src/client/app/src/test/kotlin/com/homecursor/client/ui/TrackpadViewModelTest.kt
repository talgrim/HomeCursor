package com.homecursor.client.ui

import com.homecursor.client.input.FakeInputSender
import com.homecursor.client.input.FakeInputSender.Event.Button
import com.homecursor.client.input.FakeInputSender.Event.Move
import com.homecursor.client.input.FakeInputSender.Event.Scroll
import com.homecursor.client.input.FakeTrackpadSettingsStore
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.PointerAcceleration
import com.homecursor.client.input.TouchPoint
import com.homecursor.client.input.TrackpadGestureInterpreter
import com.homecursor.client.input.TrackpadTuning
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.storage.TrackpadSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Slop 10 px, a tap clicks 300 ms after it (the double-tap window), no acceleration unless a test asks for it,
 * a pointer speed scale of 1 unless a test asks for it, 3 scroll units per px, sending every 8 ms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackpadViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val sender = FakeInputSender()
    private val senders = MutableStateFlow<InputSender>(sender)
    private val store = FakeTrackpadSettingsStore()

    private val plainTuning = TrackpadTuning(
        gestures = TrackpadGestureInterpreter.Config(touchSlop = 10f, tapTimeoutMillis = 250, doubleTapWindowMillis = 300),
        acceleration = PointerAcceleration.Config(thresholdPxPerMs = 1_000f),
        scrollUnitsPerPixel = 3f,
        flushIntervalMillis = 8,
        pointerSpeedScale = 1f,
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(tuning: TrackpadTuning = plainTuning): TrackpadViewModel {
        val viewModel = TrackpadViewModel(senders, store, tuning)
        runCurrent() // lets the view model start observing the sender
        return viewModel
    }

    private fun one(x: Float, y: Float) = TouchPoint(1, x, y)

    private fun two(x: Float, y: Float) = TouchPoint(2, x, y)

    private fun TrackpadViewModel.touch(time: Long, vararg pointers: TouchPoint) = onTouch(time, pointers.toList())

    private fun TrackpadViewModel.lift(time: Long) = onTouch(time, emptyList())

    /** Lets one send interval pass. */
    private fun TestScope.tick() {
        advanceTimeBy(plainTuning.flushIntervalMillis)
        runCurrent()
    }

    /** Lets the double-tap window pass, after which a tap that was not followed by a touch clicks. */
    private fun TestScope.afterTapWindow() {
        advanceTimeBy(plainTuning.gestures.doubleTapWindowMillis)
        runCurrent()
    }

    // --- pointer movement ---

    @Test
    fun move_isSentOnTheNextInterval_notImmediately() = runTest(scheduler) {
        val vm = viewModel()

        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        assertTrue(sender.events.isEmpty())

        tick()
        assertEquals(listOf(Move(20, 0)), sender.events)
    }

    @Test
    fun move_manyEventsWithinOneInterval_areSentAsOne() = runTest(scheduler) {
        val vm = viewModel()

        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        vm.touch(40, one(130f, 105f))
        tick()

        assertEquals(listOf(Move(30, 5)), sender.events)
    }

    @Test
    fun move_nothingPending_sendsNothingFurther() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        tick()

        tick()
        tick()

        assertEquals(1, sender.events.size)
    }

    @Test
    fun move_isScaledBySensitivity() = runTest(scheduler) {
        store.saved = TrackpadSettings(sensitivity = 2f)
        val vm = viewModel()

        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        tick()

        assertEquals(listOf(Move(40, 0)), sender.events)
    }

    @Test
    fun move_fractionsCarryOverToTheNextSend() = runTest(scheduler) {
        store.saved = TrackpadSettings(sensitivity = 0.5f)
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(115f, 100f)) // 7.5
        tick()
        assertEquals(listOf(Move(7, 0)), sender.events)

        vm.touch(48, one(116f, 100f)) // +0.5, which completes the leftover 0.5
        tick()

        assertEquals(listOf(Move(7, 0), Move(1, 0)), sender.events)
    }

    @Test
    fun move_fastFingerIsAccelerated() = runTest(scheduler) {
        val accelerating = plainTuning.copy(
            acceleration = PointerAcceleration.Config(thresholdPxPerMs = 0f, slope = 1f, maxGain = 3f),
        )
        val vm = viewModel(accelerating)
        vm.touch(0, one(100f, 100f))
        vm.touch(16, one(116f, 100f)) // 16 px in 16 ms: 1 px/ms, gain 2

        tick()

        assertEquals(listOf(Move(32, 0)), sender.events)
    }

    @Test
    fun move_defaultSensitivityIsHalfOfTheFingerTravel() = runTest(scheduler) {
        val vm = viewModel(plainTuning.copy(pointerSpeedScale = 0.5f))

        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        tick()

        assertEquals(1f, vm.settings.value.sensitivity, 0f)
        assertEquals(listOf(Move(10, 0)), sender.events)
    }

    @Test
    fun move_speedSettingKeepsItsRangeOnTopOfTheScale() = runTest(scheduler) {
        val vm = viewModel(plainTuning.copy(pointerSpeedScale = 0.5f))
        vm.onSensitivityChanged(3f)
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        tick()

        vm.onSensitivityChanged(0.5f)
        vm.touch(48, one(140f, 100f))
        tick()

        // 20 px at 3x and half scale is 30; 20 px at 0.5x and half scale is 5.
        assertEquals(listOf(Move(30, 0), Move(5, 0)), sender.events)
    }

    @Test
    fun move_sensitivityChangeAppliesToLaterMovement() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        tick()

        vm.onSensitivityChanged(3f)
        vm.touch(48, one(130f, 100f))
        tick()

        assertEquals(listOf(Move(20, 0), Move(30, 0)), sender.events)
    }

    // --- clicks and drag ---

    @Test
    fun tap_sendsLeftButtonDownAndUpOnceTheDoubleTapWindowHasPassed() = runTest(scheduler) {
        val vm = viewModel()

        vm.touch(0, one(100f, 100f))
        vm.lift(100)
        assertTrue(sender.events.isEmpty())
        advanceTimeBy(plainTuning.gestures.doubleTapWindowMillis - 1)
        runCurrent()
        assertTrue(sender.events.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(Button(MouseButton.Left, true), Button(MouseButton.Left, false)), sender.events)
    }

    @Test
    fun tap_clicksOnlyOnce() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(100)

        afterTapWindow()
        afterTapWindow()

        assertEquals(2, sender.events.size)
    }

    @Test
    fun tapThenDrag_isASinglePressWithNoClick() = runTest(scheduler) {
        val vm = viewModel()

        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        vm.touch(150, one(100f, 100f))
        vm.touch(182, one(120f, 100f))
        vm.lift(214)
        afterTapWindow() // the waiting tap must not come back as a click once the drag is over

        assertEquals(listOf(Button(MouseButton.Left, true), Move(20, 0), Button(MouseButton.Left, false)), sender.events)
    }

    @Test
    fun tapThenSecondTap_isADoubleClickAtOnce() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        vm.touch(150, one(100f, 100f))

        vm.lift(200)

        assertEquals(
            listOf(
                Button(MouseButton.Left, true), Button(MouseButton.Left, false),
                Button(MouseButton.Left, true), Button(MouseButton.Left, false),
            ),
            sender.events,
        )
        afterTapWindow()
        assertEquals(4, sender.events.size)
    }

    @Test
    fun tapThenALateTouch_sendsTheClickBeforeTheNewGesture() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)

        vm.touch(500, one(100f, 100f)) // event time says the window is over; the timer has not run yet

        assertEquals(listOf(Button(MouseButton.Left, true), Button(MouseButton.Left, false)), sender.events)
        afterTapWindow()
        assertEquals(2, sender.events.size)
    }

    @Test
    fun tapThenTheAppGoesToTheBackground_theWaitingClickIsDropped() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)

        vm.onBackground()
        afterTapWindow()

        assertTrue(sender.events.isEmpty())
    }

    @Test
    fun tapThenTheConnectionChanges_theWaitingClickIsDropped() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        val reconnected = FakeInputSender()

        senders.value = reconnected
        runCurrent()
        afterTapWindow()

        assertTrue(sender.events.isEmpty())
        assertTrue(reconnected.events.isEmpty())
    }

    @Test
    fun twoFingerTap_sendsRightClick() = runTest(scheduler) {
        val vm = viewModel()

        vm.touch(0, one(100f, 100f), two(200f, 100f))
        vm.lift(100)

        assertEquals(listOf(Button(MouseButton.Right, true), Button(MouseButton.Right, false)), sender.events)
    }

    @Test
    fun doubleTapDrag_holdsTheButtonAndSendsMovementBeforeReleasing() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        sender.events.clear()

        vm.touch(150, one(100f, 100f))
        vm.touch(182, one(120f, 100f))
        assertEquals(listOf(Button(MouseButton.Left, true)), sender.events)

        vm.lift(214) // the 20 px of movement still pending must arrive before the release
        assertEquals(listOf(Button(MouseButton.Left, true), Move(20, 0), Button(MouseButton.Left, false)), sender.events)
    }

    @Test
    fun touchCancelled_whileDragging_releasesTheButton() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        vm.touch(150, one(100f, 100f))
        vm.touch(182, one(120f, 100f))
        sender.events.clear()

        vm.onTouchCancelled()

        assertEquals(listOf(Move(20, 0), Button(MouseButton.Left, false)), sender.events)
    }

    // --- haptic feedback ---

    /** Counts feedback events while the test runs. */
    private fun TestScope.countFeedback(vm: TrackpadViewModel): () -> Int {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { vm.clickFeedback.collect { count++ } }
        return { count }
    }

    @Test
    fun feedback_tap_givesOneFeedback() = runTest(scheduler) {
        val vm = viewModel()
        val feedback = countFeedback(vm)

        vm.touch(0, one(100f, 100f))
        vm.lift(100)
        afterTapWindow()

        assertEquals(1, feedback())
    }

    @Test
    fun feedback_dragStart_givesFeedbackWhenTheButtonGoesDown() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        val feedback = countFeedback(vm)

        vm.touch(150, one(100f, 100f))
        assertEquals(0, feedback())
        vm.touch(182, one(120f, 100f))
        assertEquals(1, feedback())
        vm.lift(214) // releasing is silent

        assertEquals(1, feedback())
    }

    @Test
    fun feedback_buttonBarPress_givesFeedbackButReleaseDoesNot() = runTest(scheduler) {
        val vm = viewModel()
        val feedback = countFeedback(vm)

        vm.onButtonPressed(MouseButton.Left)
        vm.onButtonReleased(MouseButton.Left)

        assertEquals(1, feedback())
    }

    @Test
    fun feedback_movingAndScrolling_isSilent() = runTest(scheduler) {
        val vm = viewModel()
        val feedback = countFeedback(vm)

        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        vm.lift(60)
        vm.touch(100, one(100f, 100f), two(200f, 100f))
        vm.touch(132, one(100f, 130f), two(200f, 130f))
        tick()

        assertEquals(0, feedback())
    }

    @Test
    fun feedback_withNobodyListening_doesNotBlockOrFail() = runTest(scheduler) {
        val vm = viewModel()

        repeat(20) {
            vm.touch(it * 1000L, one(100f, 100f))
            vm.lift(it * 1000L + 50)
        }
        afterTapWindow()

        assertEquals(40, sender.events.size)
    }

    // --- scrolling ---

    @Test
    fun scroll_traditional_fingersDownScrollDown() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f), two(200f, 100f))

        vm.touch(32, one(100f, 130f), two(200f, 130f))
        tick()

        // The wire counts up as positive, and 30 px of travel are 90 units.
        assertEquals(listOf(Scroll(0, -90)), sender.events)
    }

    @Test
    fun scroll_traditional_fingersRightScrollRight() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f), two(200f, 100f))

        vm.touch(32, one(130f, 100f), two(230f, 100f))
        tick()

        assertEquals(listOf(Scroll(90, 0)), sender.events)
    }

    @Test
    fun scroll_natural_contentFollowsTheFingers() = runTest(scheduler) {
        store.saved = TrackpadSettings(naturalScroll = true)
        val vm = viewModel()
        vm.touch(0, one(100f, 100f), two(200f, 100f))

        vm.touch(32, one(130f, 130f), two(230f, 130f))
        tick()

        assertEquals(listOf(Scroll(-90, 90)), sender.events)
    }

    @Test
    fun scroll_directionChangeAppliesToLaterMovement() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f), two(200f, 100f))
        vm.touch(32, one(100f, 130f), two(200f, 130f))
        tick()

        vm.onNaturalScrollChanged(true)
        vm.touch(48, one(100f, 140f), two(200f, 140f))
        tick()

        assertEquals(listOf(Scroll(0, -90), Scroll(0, 30)), sender.events)
    }

    @Test
    fun scroll_andMoveInTheSameInterval_areBothSent() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))
        vm.lift(40)
        vm.touch(100, one(100f, 100f), two(200f, 100f))
        vm.touch(132, one(100f, 130f), two(200f, 130f))

        tick()

        assertEquals(listOf(Move(20, 0), Scroll(0, -90)), sender.events)
    }

    // --- button bar ---

    @Test
    fun buttonBar_pressAndRelease_sendDownThenUp() = runTest(scheduler) {
        val vm = viewModel()

        vm.onButtonPressed(MouseButton.Right)
        vm.onButtonReleased(MouseButton.Right)

        assertEquals(listOf(Button(MouseButton.Right, true), Button(MouseButton.Right, false)), sender.events)
    }

    @Test
    fun buttonBar_middlePressAndRelease_sendDownThenUp() = runTest(scheduler) {
        val vm = viewModel()

        vm.onButtonPressed(MouseButton.Middle)
        vm.onButtonReleased(MouseButton.Middle)

        assertEquals(listOf(Button(MouseButton.Middle, true), Button(MouseButton.Middle, false)), sender.events)
    }

    @Test
    fun buttonBar_press_sendsPendingMovementFirst() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))

        vm.onButtonPressed(MouseButton.Left)

        assertEquals(listOf(Move(20, 0), Button(MouseButton.Left, true)), sender.events)
    }

    @Test
    fun buttonBar_releaseWithoutPress_isIgnored() = runTest(scheduler) {
        val vm = viewModel()

        vm.onButtonReleased(MouseButton.Left)

        assertTrue(sender.events.isEmpty())
    }

    @Test
    fun buttonBar_pressTwice_sendsOnePress() = runTest(scheduler) {
        val vm = viewModel()

        vm.onButtonPressed(MouseButton.Left)
        vm.onButtonPressed(MouseButton.Left)

        assertEquals(listOf(Button(MouseButton.Left, true)), sender.events)
    }

    // --- leaving, and the connection changing ---

    @Test
    fun onBackground_releasesHeldBarButtons() = runTest(scheduler) {
        val vm = viewModel()
        vm.onButtonPressed(MouseButton.Left)
        vm.onButtonPressed(MouseButton.Right)
        sender.events.clear()

        vm.onBackground()

        assertEquals(
            setOf(Button(MouseButton.Left, false), Button(MouseButton.Right, false)),
            sender.events.toSet(),
        )
        assertEquals(2, sender.events.size)
    }

    @Test
    fun onBackground_releasesAHeldDragButton() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        vm.touch(150, one(100f, 100f))
        vm.touch(182, one(120f, 100f))
        sender.events.clear()

        vm.onBackground()

        assertEquals(Button(MouseButton.Left, false), sender.events.last())
    }

    @Test
    fun onBackground_dropsMotionThatWasNotSentYet() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.touch(32, one(120f, 100f))

        vm.onBackground()
        tick()

        assertTrue(sender.events.isEmpty())
    }

    @Test
    fun onBackground_thenTouchesWorkAgain() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.onBackground()

        vm.touch(200, one(100f, 100f))
        vm.lift(250)
        afterTapWindow()

        assertEquals(listOf(Button(MouseButton.Left, true), Button(MouseButton.Left, false)), sender.events)
    }

    @Test
    fun senderChanges_dropsPendingMotionAndTheGesture() = runTest(scheduler) {
        val vm = viewModel()
        vm.touch(0, one(100f, 100f))
        vm.lift(50)
        vm.touch(150, one(100f, 100f))
        vm.touch(182, one(120f, 100f)) // dragging, with 20 px pending
        sender.events.clear()
        val reconnected = FakeInputSender()

        senders.value = reconnected
        runCurrent()
        tick()
        vm.lift(300)

        // The old connection's drag means nothing to the new one: no stray release, no stale movement.
        assertTrue(sender.events.isEmpty())
        assertTrue(reconnected.events.isEmpty())
    }

    @Test
    fun senderChanges_laterInputGoesToTheNewSender() = runTest(scheduler) {
        val vm = viewModel()
        val reconnected = FakeInputSender()
        senders.value = reconnected
        runCurrent()

        vm.touch(0, one(100f, 100f))
        vm.lift(100)
        afterTapWindow()

        assertTrue(sender.events.isEmpty())
        assertEquals(listOf(Button(MouseButton.Left, true), Button(MouseButton.Left, false)), reconnected.events)
    }

    // --- settings ---

    @Test
    fun settings_areLoadedFromTheStore() = runTest(scheduler) {
        store.saved = TrackpadSettings(sensitivity = 1.5f, naturalScroll = true)

        assertEquals(TrackpadSettings(1.5f, true), viewModel().settings.value)
    }

    @Test
    fun onSensitivityChanged_updatesAndSaves() = runTest(scheduler) {
        val vm = viewModel()

        vm.onSensitivityChanged(2.25f)

        assertEquals(TrackpadSettings(2.25f, false), vm.settings.value)
        assertEquals(TrackpadSettings(2.25f, false), store.saved)
    }

    @Test
    fun onSensitivityChanged_outOfRange_isMovedIntoRange() = runTest(scheduler) {
        val vm = viewModel()

        vm.onSensitivityChanged(10f)
        assertEquals(3f, vm.settings.value.sensitivity, 0f)

        vm.onSensitivityChanged(0f)
        assertEquals(0.5f, vm.settings.value.sensitivity, 0f)

        vm.onSensitivityChanged(Float.NaN)
        assertEquals(1f, vm.settings.value.sensitivity, 0f)
    }

    @Test
    fun onNaturalScrollChanged_updatesAndSavesWithoutTouchingSensitivity() = runTest(scheduler) {
        store.saved = TrackpadSettings(sensitivity = 2f)
        val vm = viewModel()

        vm.onNaturalScrollChanged(true)

        assertEquals(TrackpadSettings(2f, true), vm.settings.value)
        assertEquals(TrackpadSettings(2f, true), store.saved)
    }
}
