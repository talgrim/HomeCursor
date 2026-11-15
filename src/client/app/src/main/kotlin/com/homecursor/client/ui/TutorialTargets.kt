package com.homecursor.client.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the parts of the trackpad screen are, in window coordinates, so the tour can highlight them. The screen marks its
 * parts with [tutorialTarget]; the tour reads them. A part that is not on screen (the keyboard screen is shown instead)
 * simply has no bounds.
 */
@Stable
class TutorialTargets {
    private val bounds = mutableStateMapOf<TutorialTarget, Rect>()

    operator fun get(target: TutorialTarget): Rect? = bounds[target]

    internal fun update(target: TutorialTarget, rect: Rect) {
        if (bounds[target] != rect) bounds[target] = rect
    }
}

/** Reports the bounds of this element to [targets] under [target]. Put it after the size modifiers. */
fun Modifier.tutorialTarget(targets: TutorialTargets, target: TutorialTarget): Modifier =
    onGloballyPositioned { targets.update(target, it.boundsInWindow()) }
