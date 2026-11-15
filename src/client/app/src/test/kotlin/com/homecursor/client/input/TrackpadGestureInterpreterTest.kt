package com.homecursor.client.input

import com.homecursor.client.input.PadCommand.ButtonDown
import com.homecursor.client.input.PadCommand.ButtonUp
import com.homecursor.client.input.PadCommand.Click
import com.homecursor.client.input.PadCommand.Move
import com.homecursor.client.input.PadCommand.Scroll
import com.homecursor.client.protocol.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slop 10 px, tap timeout 250 ms, double-tap window 300 ms. Fingers 1 and 2 are the first and second finger. */
class TrackpadGestureInterpreterTest {
    private val interpreter = TrackpadGestureInterpreter(
        TrackpadGestureInterpreter.Config(touchSlop = 10f, tapTimeoutMillis = 250, doubleTapWindowMillis = 300),
    )

    private fun one(x: Float, y: Float) = TouchPoint(1, x, y)

    private fun two(x: Float, y: Float) = TouchPoint(2, x, y)

    private fun touch(time: Long, vararg pointers: TouchPoint) = interpreter.onTouch(time, pointers.toList())

    private fun lift(time: Long) = interpreter.onTouch(time, emptyList())

    private val leftClick = Click(MouseButton.Left)
    private val rightClick = Click(MouseButton.Right)

    // --- one finger: tap ---

    @Test
    fun touches_singleTap_emitsLeftClickOnRelease() {
        assertTrue(touch(0, one(100f, 100f)).isEmpty())

        assertEquals(listOf(leftClick), lift(100))
    }

    @Test
    fun touches_tapReleasedAtTheTimeout_stillClicks() {
        touch(0, one(100f, 100f))

        assertEquals(listOf(leftClick), lift(250))
    }

    @Test
    fun touches_longPress_emitsNothing() {
        touch(0, one(100f, 100f))

        assertTrue(lift(251).isEmpty())
    }

    @Test
    fun touches_tapWithJitterBelowSlop_clicksWithoutMoving() {
        touch(0, one(100f, 100f))

        assertTrue(touch(20, one(104f, 103f)).isEmpty())
        assertEquals(listOf(leftClick), lift(80))
    }

    // --- one finger: move ---

    @Test
    fun touches_fingerExactlyAtSlop_doesNotMoveYet() {
        touch(0, one(100f, 100f))

        assertTrue(touch(16, one(110f, 100f)).isEmpty())
    }

    @Test
    fun touches_fingerPastSlop_firstMoveIncludesTheDistanceAlreadyTravelled() {
        touch(0, one(100f, 100f))
        assertTrue(touch(16, one(104f, 100f)).isEmpty())

        // The move is timed from the touch-down, not from the previous event, so it does not look like a flick.
        assertEquals(listOf(Move(15f, 0f, 32)), touch(32, one(115f, 100f)))
    }

    @Test
    fun touches_fingerKeepsMoving_emitsDeltasWithTheirElapsedTime() {
        touch(0, one(100f, 100f))
        touch(32, one(115f, 100f))

        assertEquals(listOf(Move(0f, 10f, 16)), touch(48, one(115f, 110f)))
        assertEquals(listOf(Move(-5f, -3f, 16)), touch(64, one(110f, 107f)))
    }

    @Test
    fun touches_fingerMovingButStandingStill_emitsNothing() {
        touch(0, one(100f, 100f))
        touch(32, one(115f, 100f))

        assertTrue(touch(48, one(115f, 100f)).isEmpty())
    }

    @Test
    fun touches_fingerMovesBackWithinSlop_afterMovingStillMoves() {
        touch(0, one(100f, 100f))
        touch(32, one(115f, 100f))

        // Slop only guards the start of a movement; once moving, small steps count.
        assertEquals(listOf(Move(-14f, 0f, 16)), touch(48, one(101f, 100f)))
    }

    @Test
    fun touches_dragThenQuickRelease_isNotAClick() {
        touch(0, one(100f, 100f))
        touch(32, one(115f, 100f))

        assertTrue(lift(100).isEmpty())
    }

    // --- two fingers: right click ---

    @Test
    fun touches_twoFingerTap_emitsRightClick() {
        assertTrue(touch(0, one(100f, 100f), two(200f, 100f)).isEmpty())

        assertEquals(listOf(rightClick), lift(100))
    }

    @Test
    fun touches_twoFingerTapLiftedOneByOne_emitsASingleRightClick() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertEquals(listOf(rightClick), touch(80, two(200f, 100f)))
        assertTrue(lift(100).isEmpty())
    }

    @Test
    fun touches_secondFingerLandsAfterTheFirst_stillTapsWithinTheTimeoutOfTheFirst() {
        touch(0, one(100f, 100f))
        assertTrue(touch(30, one(100f, 100f), two(200f, 100f)).isEmpty())

        assertEquals(listOf(rightClick), lift(100))
    }

    @Test
    fun touches_twoFingersHeldTooLong_noClick() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertTrue(lift(300).isEmpty())
    }

    @Test
    fun touches_twoFingerTapWithJitter_noScrollButClick() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertTrue(touch(20, one(103f, 104f), two(203f, 104f)).isEmpty())
        assertEquals(listOf(rightClick), lift(60))
    }

    @Test
    fun touches_oneFingerMovedThenSecondLands_noRightClick() {
        touch(0, one(100f, 100f))
        touch(16, one(120f, 100f))
        assertTrue(touch(32, one(120f, 100f), two(200f, 100f)).isEmpty())

        assertTrue(lift(100).isEmpty())
    }

    // --- two fingers: scroll ---

    @Test
    fun touches_twoFingerDrag_scrollsByTheMovementOfTheirMidpoint() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertTrue(touch(16, one(100f, 105f), two(200f, 105f)).isEmpty())
        assertEquals(listOf(Scroll(0f, 20f)), touch(32, one(100f, 120f), two(200f, 120f)))
        assertEquals(listOf(Scroll(10f, 0f)), touch(48, one(110f, 120f), two(210f, 120f)))
    }

    @Test
    fun touches_twoFingerDrag_scrollsHorizontallyAndVertically() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertEquals(listOf(Scroll(-12f, 16f)), touch(16, one(88f, 116f), two(188f, 116f)))
    }

    @Test
    fun touches_twoFingerScrollThenRelease_noClick() {
        touch(0, one(100f, 100f), two(200f, 100f))
        touch(32, one(100f, 130f), two(200f, 130f))

        assertTrue(lift(100).isEmpty())
    }

    @Test
    fun touches_scrollThenLiftOneFinger_remainingFingerDoesNotMoveThePointer() {
        touch(0, one(100f, 100f), two(200f, 100f))
        touch(32, one(100f, 130f), two(200f, 130f))

        assertTrue(touch(48, two(200f, 130f)).isEmpty())
        assertTrue(touch(64, two(300f, 300f)).isEmpty())
        assertTrue(lift(80).isEmpty())
    }

    @Test
    fun touches_afterAScroll_aNewGestureWorksNormally() {
        touch(0, one(100f, 100f), two(200f, 100f))
        touch(32, one(100f, 130f), two(200f, 130f))
        lift(48)

        touch(200, one(50f, 50f))
        assertEquals(listOf(Move(20f, 0f, 16)), touch(216, one(70f, 50f)))
    }

    @Test
    fun touches_bothFingersArriveInTheSameFrame_canScroll() {
        // The state machine must not depend on seeing one finger first.
        touch(0, one(100f, 100f), two(200f, 100f))

        assertEquals(listOf(Scroll(0f, 15f)), touch(16, one(100f, 115f), two(200f, 115f)))
    }

    // --- double tap and drag ---

    @Test
    fun touches_tapThenTouchAndMove_dragsWithTheLeftButtonHeld() {
        touch(0, one(100f, 100f))
        assertEquals(listOf(leftClick), lift(50))

        assertTrue(touch(150, one(100f, 100f)).isEmpty())
        assertTrue(touch(166, one(103f, 100f)).isEmpty())
        assertEquals(listOf(ButtonDown(MouseButton.Left), Move(20f, 0f, 32)), touch(182, one(120f, 100f)))
        assertEquals(listOf(Move(10f, 0f, 16)), touch(198, one(130f, 100f)))
        assertEquals(listOf(ButtonUp(MouseButton.Left)), lift(214))
    }

    @Test
    fun touches_dragCandidateWithinTheWindowBoundary_stillDrags() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(350, one(100f, 100f)) // exactly 300 ms after the tap

        assertEquals(ButtonDown(MouseButton.Left), touch(366, one(120f, 100f)).first())
    }

    @Test
    fun touches_touchAfterTheWindow_isAnOrdinaryMove() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(351, one(100f, 100f)) // 301 ms after the tap

        assertEquals(listOf(Move(20f, 0f, 32)), touch(383, one(120f, 100f)))
    }

    @Test
    fun touches_tapThenSecondTapWithoutMoving_isASecondClick() {
        touch(0, one(100f, 100f))
        assertEquals(listOf(leftClick), lift(50))
        touch(150, one(100f, 100f))

        assertEquals(listOf(leftClick), lift(200))
    }

    @Test
    fun touches_thirdTouchAfterADoubleTap_startsFresh() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(150, one(100f, 100f))
        lift(200)
        touch(250, one(100f, 100f))

        // Within the window of the second tap, but the second tap was already part of a double tap.
        assertEquals(listOf(Move(20f, 0f, 32)), touch(282, one(120f, 100f)))
    }

    @Test
    fun touches_afterADragStartedByAMove_aQuickNewTouchIsNotADrag() {
        touch(0, one(100f, 100f))
        touch(32, one(120f, 100f))
        lift(60)
        touch(100, one(100f, 100f))

        assertEquals(listOf(Move(20f, 0f, 32)), touch(132, one(120f, 100f)))
    }

    @Test
    fun touches_heldButtonThenSecondFingerLands_releasesTheButton() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(150, one(100f, 100f))
        touch(182, one(120f, 100f))

        assertEquals(listOf(ButtonUp(MouseButton.Left)), touch(200, one(120f, 100f), two(220f, 100f)))
        assertTrue(lift(260).isEmpty())
    }

    // --- three fingers ---

    @Test
    fun touches_thirdFinger_endsTheGestureUntilEveryFingerIsLifted() {
        touch(0, one(100f, 100f), two(200f, 100f))

        assertTrue(touch(16, one(100f, 100f), two(200f, 100f), TouchPoint(3, 300f, 100f)).isEmpty())
        assertTrue(touch(32, one(100f, 150f), two(200f, 150f)).isEmpty())
        assertTrue(touch(48, one(100f, 200f)).isEmpty())
        assertTrue(lift(64).isEmpty())

        touch(100, one(0f, 0f))
        assertEquals(listOf(leftClick), lift(150))
    }

    @Test
    fun touches_thirdFingerWhileDragging_releasesTheHeldButton() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(150, one(100f, 100f))
        touch(182, one(120f, 100f))

        val commands = touch(200, one(120f, 100f), two(200f, 100f), TouchPoint(3, 300f, 100f))

        assertEquals(listOf(ButtonUp(MouseButton.Left)), commands)
    }

    @Test
    fun touches_threeFingersFromTheStart_doNothing() {
        assertTrue(touch(0, one(1f, 1f), two(2f, 2f), TouchPoint(3, 3f, 3f)).isEmpty())
        assertTrue(touch(16, one(50f, 50f), two(60f, 60f), TouchPoint(3, 70f, 70f)).isEmpty())
        assertTrue(lift(32).isEmpty())
    }

    // --- cancel ---

    @Test
    fun cancel_whileDragging_releasesTheButton() {
        touch(0, one(100f, 100f))
        lift(50)
        touch(150, one(100f, 100f))
        touch(182, one(120f, 100f))

        assertEquals(listOf(ButtonUp(MouseButton.Left)), interpreter.cancel())
        assertTrue(interpreter.cancel().isEmpty())
    }

    @Test
    fun cancel_whenIdle_returnsNothing() {
        assertTrue(interpreter.cancel().isEmpty())
    }

    @Test
    fun cancel_forgetsAPendingTap_soTheNextTouchIsNotADrag() {
        touch(0, one(100f, 100f))
        lift(50)
        interpreter.cancel()
        touch(100, one(100f, 100f))

        assertEquals(listOf(Move(20f, 0f, 32)), touch(132, one(120f, 100f)))
    }

    @Test
    fun cancel_midTap_sendsNoClick() {
        touch(0, one(100f, 100f))
        interpreter.cancel()

        assertTrue(lift(50).isEmpty())
    }

    @Test
    fun cancel_whileScrolling_thenNewGestureWorks() {
        touch(0, one(100f, 100f), two(200f, 100f))
        touch(32, one(100f, 130f), two(200f, 130f))

        assertTrue(interpreter.cancel().isEmpty())

        touch(100, one(0f, 0f))
        assertEquals(listOf(leftClick), lift(150))
    }

    // --- misc ---

    @Test
    fun touches_emptyFramesWhileIdle_doNothing() {
        assertTrue(lift(0).isEmpty())
        assertTrue(lift(16).isEmpty())
    }

    @Test
    fun config_negativeValues_throw() {
        assertThrows(IllegalArgumentException::class.java) { TrackpadGestureInterpreter.Config(touchSlop = -1f) }
        assertThrows(IllegalArgumentException::class.java) { TrackpadGestureInterpreter.Config(tapTimeoutMillis = -1) }
        assertThrows(IllegalArgumentException::class.java) { TrackpadGestureInterpreter.Config(doubleTapWindowMillis = -1) }
    }
}
