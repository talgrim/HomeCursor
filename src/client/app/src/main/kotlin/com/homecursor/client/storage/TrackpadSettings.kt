package com.homecursor.client.storage

/**
 * How the trackpad feels.
 *
 * @property sensitivity multiplies finger travel to get pointer travel, within [MIN_SENSITIVITY]..[MAX_SENSITIVITY].
 * @property naturalScroll false (the default) scrolls like a mouse wheel: fingers moving down scroll the page down.
 *   True makes the content follow the fingers, like on a phone.
 */
data class TrackpadSettings(
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    val naturalScroll: Boolean = false,
) {
    init {
        require(sensitivity in MIN_SENSITIVITY..MAX_SENSITIVITY) {
            "sensitivity must be between $MIN_SENSITIVITY and $MAX_SENSITIVITY."
        }
    }

    companion object {
        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 3f
        const val DEFAULT_SENSITIVITY = 1f

        /**
         * Builds settings from stored or user-entered values that may be out of range: a sensitivity outside the
         * range is moved to the nearest limit, and one that is not a number falls back to the default.
         */
        fun coerced(sensitivity: Float, naturalScroll: Boolean) = TrackpadSettings(
            sensitivity = if (sensitivity.isNaN()) DEFAULT_SENSITIVITY else sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
            naturalScroll = naturalScroll,
        )
    }
}
