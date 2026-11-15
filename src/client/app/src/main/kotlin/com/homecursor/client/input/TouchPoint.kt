package com.homecursor.client.input

/** One finger currently on the pad: a stable [id] for as long as it stays down, and its position in pixels. */
data class TouchPoint(val id: Int, val x: Float, val y: Float)
