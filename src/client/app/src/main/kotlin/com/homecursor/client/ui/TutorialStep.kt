package com.homecursor.client.ui

import androidx.annotation.StringRes
import com.homecursor.client.R

/** A part of the trackpad screen that a tour step can point at. */
enum class TutorialTarget { Pad, LeftButton, MiddleButton, RightButton, Keyboard, Settings }

/** A gesture that a tour step shows as a short animation. */
enum class TutorialGesture { Move, Tap, TwoFingerTap, TwoFingerScroll }

/**
 * One page of the tour. [target] is the part of the screen that is highlighted (none: a page in the middle of the
 * screen), [gesture] the animation shown with the text.
 */
data class TutorialStep(
    val target: TutorialTarget?,
    val gesture: TutorialGesture?,
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
)

/** The pages of the tour, in the order they are shown. */
object TutorialSteps {
    val all: List<TutorialStep> = listOf(
        TutorialStep(null, null, R.string.tutorial_welcome_title, R.string.tutorial_welcome_text),
        TutorialStep(TutorialTarget.Pad, TutorialGesture.Move, R.string.tutorial_move_title, R.string.tutorial_move_text),
        TutorialStep(TutorialTarget.Pad, TutorialGesture.Tap, R.string.tutorial_tap_title, R.string.tutorial_tap_text),
        TutorialStep(
            TutorialTarget.Pad,
            TutorialGesture.TwoFingerTap,
            R.string.tutorial_right_click_title,
            R.string.tutorial_right_click_text,
        ),
        TutorialStep(
            TutorialTarget.Pad,
            TutorialGesture.TwoFingerScroll,
            R.string.tutorial_scroll_title,
            R.string.tutorial_scroll_text,
        ),
        TutorialStep(TutorialTarget.LeftButton, null, R.string.tutorial_left_title, R.string.tutorial_left_text),
        TutorialStep(TutorialTarget.MiddleButton, null, R.string.tutorial_middle_title, R.string.tutorial_middle_text),
        TutorialStep(TutorialTarget.RightButton, null, R.string.tutorial_right_title, R.string.tutorial_right_text),
        TutorialStep(TutorialTarget.Keyboard, null, R.string.tutorial_keyboard_title, R.string.tutorial_keyboard_text),
        TutorialStep(TutorialTarget.Settings, null, R.string.tutorial_settings_title, R.string.tutorial_settings_text),
    )
}
