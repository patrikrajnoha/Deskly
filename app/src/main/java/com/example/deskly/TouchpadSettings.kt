package com.example.deskly

data class TouchpadSettings(
    val cursorSpeedPercent: Int = DEFAULT_CURSOR_SPEED_PERCENT,
    val scrollSensitivityPercent: Int = DEFAULT_SCROLL_SENSITIVITY_PERCENT,
    val naturalScroll: Boolean = DEFAULT_NATURAL_SCROLL,
    val visualFeedback: Boolean = DEFAULT_VISUAL_FEEDBACK,
    val acceleration: Boolean = DEFAULT_ACCELERATION,
    val leftHanded: Boolean = DEFAULT_LEFT_HANDED,
    val gyroSensitivityPercent: Int = DEFAULT_GYRO_SENSITIVITY_PERCENT
) {
    fun normalized(): TouchpadSettings {
        return copy(
            cursorSpeedPercent = cursorSpeedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            scrollSensitivityPercent = scrollSensitivityPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            gyroSensitivityPercent = gyroSensitivityPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        )
    }

    companion object {
        const val MIN_PERCENT = 25
        const val MAX_PERCENT = 200
        const val DEFAULT_CURSOR_SPEED_PERCENT = 100
        const val DEFAULT_SCROLL_SENSITIVITY_PERCENT = 100
        const val DEFAULT_NATURAL_SCROLL = true
        const val DEFAULT_VISUAL_FEEDBACK = true
        const val DEFAULT_ACCELERATION = true
        const val DEFAULT_LEFT_HANDED = false
        const val DEFAULT_GYRO_SENSITIVITY_PERCENT = 100
    }
}
