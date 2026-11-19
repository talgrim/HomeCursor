package com.homecursor.client.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.homecursor.client.R
import com.homecursor.client.ui.theme.Spacing
import kotlin.math.abs

/**
 * The guided tour: the screen is dimmed with a cut-out around the part that [step] explains, and a card says what it
 * does. It is a full-screen dialog, so it also covers the system bar areas, takes all touches (nothing reaches the pad
 * while it is shown) and treats Back as "skip".
 *
 * Stateless: [index] and the callbacks come from [TutorialViewModel].
 */
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    index: Int,
    count: Int,
    targets: TutorialTargets,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // The scrim is drawn below; the dialog window's own dim would darken the screen twice.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            SideEffect { window.setDimAmount(0f) }
        }
        TutorialContent(step, index, count, targets, onNext, onBack, onSkip)
    }
}

@Composable
private fun TutorialContent(
    step: TutorialStep,
    index: Int,
    count: Int,
    targets: TutorialTargets,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    // Targets are in window coordinates and this dialog fills the screen, but go through its own origin anyway.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val hole = step.target?.let { targets[it] }?.translate(-origin)
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
    val ring = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                size = it.size
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawScrim(hole, scrim, ring) }

        // The pad is most of the screen, so its card goes in the middle; for the others, to the side with more room.
        val cardAlignment = when {
            hole == null || step.target == TutorialTarget.Pad -> Alignment.Center
            hole.center.y < size.height / 2f -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        TutorialCard(
            step = step,
            index = index,
            count = count,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip,
            modifier = Modifier.align(cardAlignment).safeDrawingPadding().padding(Spacing.lg),
        )
    }
}

private fun DrawScope.drawScrim(hole: Rect?, scrim: Color, ring: Color) {
    val everything = Rect(Offset.Zero, size)
    if (hole == null) {
        drawRect(scrim)
        return
    }
    val cutOut = RoundRect(hole.inflate(HOLE_PADDING.toPx()), CornerRadius(HOLE_RADIUS.toPx()))
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(everything)
        addRoundRect(cutOut)
    }
    drawPath(path, scrim)
    drawRoundRect(
        ring,
        topLeft = Offset(cutOut.left, cutOut.top),
        size = Size(cutOut.width, cutOut.height),
        cornerRadius = cutOut.bottomLeftCornerRadius,
        style = Stroke(RING_WIDTH.toPx()),
    )
}

@Composable
private fun TutorialCard(
    step: TutorialStep,
    index: Int,
    count: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLast = index == count - 1
    Surface(
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = CARD_ELEVATION,
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            step.gesture?.let { GestureHint(it, Modifier.align(Alignment.CenterHorizontally)) }
            Text(
                stringResource(step.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(step.text), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.tutorial_progress, index + 1, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The last page ends the tour with its own button, so a "Skip" next to it would only repeat that.
                if (!isLast) TextButton(onClick = onSkip) { Text(stringResource(R.string.tutorial_skip)) }
                Box(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = onBack) { Text(stringResource(R.string.tutorial_back)) }
                Button(onClick = onNext) {
                    Text(stringResource(if (isLast) R.string.tutorial_done else R.string.tutorial_next))
                }
            }
        }
    }
}

/**
 * A looping animation of [gesture]: dots for the fingers on a miniature pad. It is decoration; the text next to it
 * says the same, so it is hidden from assistive technology.
 */
@Composable
private fun GestureHint(gesture: TutorialGesture, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tutorialGesture")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HINT_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "tutorialGestureProgress",
    )
    val padColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val padBorder = MaterialTheme.colorScheme.outlineVariant
    val finger = MaterialTheme.colorScheme.primary

    Canvas(modifier.size(HINT_WIDTH, HINT_HEIGHT)) {
        val corner = CornerRadius(HOLE_RADIUS.toPx())
        drawRoundRect(padColor, cornerRadius = corner)
        drawRoundRect(padBorder, cornerRadius = corner, style = Stroke(1.dp.toPx()))

        val radius = FINGER_RADIUS.toPx()
        // 0 -> 1 -> 0, so a swipe goes there and back.
        val swing = 1f - abs(2f * progress - 1f)
        val midY = size.height / 2f
        when (gesture) {
            TutorialGesture.Move -> {
                val x = lerp(size.width * 0.25f, size.width * 0.75f, swing)
                drawCircle(finger, radius, Offset(x, midY))
            }
            TutorialGesture.Tap -> tap(finger, radius, Offset(size.width / 2f, midY), progress)
            TutorialGesture.TwoFingerTap -> {
                tap(finger, radius, Offset(size.width * 0.4f, midY), progress)
                tap(finger, radius, Offset(size.width * 0.6f, midY), progress)
            }
            TutorialGesture.TwoFingerScroll -> {
                val y = lerp(size.height * 0.3f, size.height * 0.7f, swing)
                drawCircle(finger, radius, Offset(size.width * 0.4f, y))
                drawCircle(finger, radius, Offset(size.width * 0.6f, y))
            }
        }
    }
}

/** A finger that touches down and a ring that spreads out from it. */
private fun DrawScope.tap(color: Color, radius: Float, center: Offset, progress: Float) {
    drawCircle(color, radius, center)
    drawCircle(color.copy(alpha = 1f - progress), radius * (1f + 1.5f * progress), center, style = Stroke(2.dp.toPx()))
}

private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

private const val SCRIM_ALPHA = 0.72f
private const val HINT_PERIOD_MILLIS = 1600

private val CARD_MAX_WIDTH = 400.dp
private val CARD_ELEVATION = 6.dp
private val HOLE_PADDING = 4.dp
private val HOLE_RADIUS = 16.dp
private val RING_WIDTH = 2.dp
private val HINT_WIDTH = 160.dp
private val HINT_HEIGHT = 72.dp
private val FINGER_RADIUS = 10.dp
