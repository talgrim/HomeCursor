package com.homecursor.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homecursor.client.protocol.MouseButton
import com.homecursor.client.ui.theme.HomeCursorTheme
import com.homecursor.client.ui.theme.Spacing

private val GlyphWidth = 28.dp
private val GlyphHeight = 40.dp
private val StrokeWidth = 2.dp
private val WheelWidth = 5.dp

/** Where the buttons end and the body of the mouse begins, as a fraction of the body height. */
private const val BUTTON_AREA_FRACTION = 0.42f

/**
 * A small mouse with the zone of [button] filled: the left half of the buttons, the right half, or the wheel. It
 * replaces the text labels on the trackpad's buttons, so they need no translation and cannot be truncated. Drawn
 * in [color] only, so it follows the theme (and the pressed state) of the button it is on.
 */
@Composable
fun MouseGlyph(button: MouseButton, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(GlyphWidth, GlyphHeight)) {
        val stroke = StrokeWidth.toPx()
        val half = stroke / 2
        val bodyWidth = size.width - stroke
        val bodyHeight = size.height - stroke
        val centerX = size.width / 2
        val buttonsBottom = half + bodyHeight * BUTTON_AREA_FRACTION

        val body = Path().apply {
            addRoundRect(RoundRect(half, half, half + bodyWidth, half + bodyHeight, CornerRadius(bodyWidth / 2)))
        }

        clipPath(body) {
            when (button) {
                MouseButton.Left -> drawRect(color, Offset.Zero, Size(centerX, buttonsBottom))
                MouseButton.Right -> drawRect(color, Offset(centerX, 0f), Size(size.width - centerX, buttonsBottom))
                MouseButton.Middle -> Unit
            }
        }
        drawPath(body, color, style = Stroke(stroke))
        drawLine(color, Offset(half, buttonsBottom), Offset(half + bodyWidth, buttonsBottom), stroke)
        drawLine(color, Offset(centerX, half), Offset(centerX, buttonsBottom), stroke)

        if (button == MouseButton.Middle) {
            val wheelWidth = WheelWidth.toPx()
            val wheelTop = half + bodyHeight * 0.1f
            val wheelBottom = buttonsBottom - bodyHeight * 0.06f
            drawRoundRect(
                color,
                topLeft = Offset(centerX - wheelWidth / 2, wheelTop),
                size = Size(wheelWidth, wheelBottom - wheelTop),
                cornerRadius = CornerRadius(wheelWidth / 2),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun MouseGlyphLightPreview() = MouseGlyphPreview(darkTheme = false)

@Preview(name = "Dark", showBackground = true)
@Composable
private fun MouseGlyphDarkPreview() = MouseGlyphPreview(darkTheme = true)

@Composable
private fun MouseGlyphPreview(darkTheme: Boolean) {
    HomeCursorTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(modifier = Modifier.padding(Spacing.lg)) {
                for (button in listOf(MouseButton.Left, MouseButton.Middle, MouseButton.Right)) {
                    MouseGlyph(button, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.padding(Spacing.md))
                }
            }
        }
    }
}
