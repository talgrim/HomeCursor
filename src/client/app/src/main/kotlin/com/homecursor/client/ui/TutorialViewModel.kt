package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import com.homecursor.client.storage.TutorialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * State of the guided tour of the trackpad. It starts by itself the first time the trackpad is shown ([onTrackpadShown])
 * and can be started again from the settings ([start]). The tour counts as seen as soon as it starts, so closing the
 * app in the middle of it does not bring it back; skipping and finishing are the same thing, they just end the tour.
 */
class TutorialViewModel(
    private val store: TutorialStore,
    private val stepCount: Int = TutorialSteps.all.size,
) : ViewModel() {
    private val mutableStepIndex = MutableStateFlow<Int?>(null)

    /** The page being shown, or null while no tour runs. */
    val stepIndex: StateFlow<Int?> = mutableStepIndex

    init {
        require(stepCount > 0) { "A tour needs at least one step" }
    }

    /** Starts the tour if the user has never seen it. Safe to call whenever the trackpad appears. */
    fun onTrackpadShown() {
        if (mutableStepIndex.value == null && !store.isSeen()) start()
    }

    fun start() {
        store.markSeen()
        mutableStepIndex.value = 0
    }

    /** Goes to the next page; on the last page it ends the tour. */
    fun next() {
        val index = mutableStepIndex.value ?: return
        mutableStepIndex.value = if (index + 1 < stepCount) index + 1 else null
    }

    fun back() {
        val index = mutableStepIndex.value ?: return
        if (index > 0) mutableStepIndex.value = index - 1
    }

    fun skip() {
        mutableStepIndex.value = null
    }
}
