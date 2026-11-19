package com.homecursor.client.ui

import com.homecursor.client.input.FakeTutorialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialViewModelTest {
    private val store = FakeTutorialStore()

    private fun viewModel(stepCount: Int = 3) = TutorialViewModel(store, stepCount)

    @Test
    fun beforeAnything_noTourRuns() {
        assertNull(viewModel().stepIndex.value)
    }

    @Test
    fun firstTimeTheTrackpadIsShown_startsTheTourAtTheFirstStep() {
        val viewModel = viewModel()

        viewModel.onTrackpadShown()

        assertEquals(0, viewModel.stepIndex.value)
    }

    @Test
    fun starting_marksTheTourAsSeen() {
        viewModel().onTrackpadShown()

        assertTrue(store.seen)
    }

    @Test
    fun trackpadShownAgain_afterTheTourWasSeen_doesNotStartIt() {
        store.seen = true
        val viewModel = viewModel()

        viewModel.onTrackpadShown()

        assertNull(viewModel.stepIndex.value)
    }

    @Test
    fun trackpadShownWhileTheTourRuns_keepsTheCurrentStep() {
        val viewModel = viewModel()
        viewModel.onTrackpadShown()
        viewModel.next()

        viewModel.onTrackpadShown()

        assertEquals(1, viewModel.stepIndex.value)
    }

    @Test
    fun start_runsTheTourAgainEvenIfItWasSeen() {
        store.seen = true
        val viewModel = viewModel()

        viewModel.start()

        assertEquals(0, viewModel.stepIndex.value)
    }

    @Test
    fun next_movesThroughTheSteps() {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.next()

        assertEquals(1, viewModel.stepIndex.value)
    }

    @Test
    fun next_onTheLastStep_endsTheTour() {
        val viewModel = viewModel(stepCount = 2)
        viewModel.start()
        viewModel.next()

        viewModel.next()

        assertNull(viewModel.stepIndex.value)
    }

    @Test
    fun back_returnsToThePreviousStep() {
        val viewModel = viewModel()
        viewModel.start()
        viewModel.next()

        viewModel.back()

        assertEquals(0, viewModel.stepIndex.value)
    }

    @Test
    fun back_onTheFirstStep_staysThere() {
        val viewModel = viewModel()
        viewModel.start()

        viewModel.back()

        assertEquals(0, viewModel.stepIndex.value)
    }

    @Test
    fun skip_endsTheTourFromAnyStep() {
        val viewModel = viewModel()
        viewModel.start()
        viewModel.next()

        viewModel.skip()

        assertNull(viewModel.stepIndex.value)
    }

    @Test
    fun skippedTour_doesNotComeBackWhenTheTrackpadIsShownAgain() {
        val viewModel = viewModel()
        viewModel.onTrackpadShown()
        viewModel.skip()

        viewModel.onTrackpadShown()

        assertNull(viewModel.stepIndex.value)
    }

    @Test
    fun navigationWithoutATour_doesNothing() {
        val viewModel = viewModel()

        viewModel.next()
        viewModel.back()
        viewModel.skip()

        assertNull(viewModel.stepIndex.value)
        assertFalse(store.seen)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aTourWithoutSteps_isRejected() {
        viewModel(stepCount = 0)
    }
}
