package com.example.deskly

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class TouchpadMotionFilter {
    private var carryX = 0f
    private var carryY = 0f

    fun filter(rawDx: Float, rawDy: Float, speedPercent: Int, accelerationEnabled: Boolean = true): Delta {
        val magnitude = hypot(rawDx, rawDy)
        if (magnitude < DEADZONE_PX) return Delta.Zero

        val speed = speedPercent.coerceIn(
            TouchpadSettings.MIN_PERCENT,
            TouchpadSettings.MAX_PERCENT
        ) / 100f
        val acceleration = if (accelerationEnabled) accelerationFor(magnitude) else 1.0f

        val scaledX = rawDx * speed * acceleration + carryX
        val scaledY = rawDy * speed * acceleration + carryY
        val dx = scaledX.roundToInt()
        val dy = scaledY.roundToInt()

        carryX = (scaledX - dx).coerceIn(-MAX_CARRY_PX, MAX_CARRY_PX)
        carryY = (scaledY - dy).coerceIn(-MAX_CARRY_PX, MAX_CARRY_PX)

        return if (dx == 0 && dy == 0) Delta.Zero else Delta(dx, dy)
    }

    fun reset() {
        carryX = 0f
        carryY = 0f
    }

    private fun accelerationFor(magnitude: Float): Float {
        return when {
            magnitude < 1.5f -> 0.7f
            magnitude < 6f -> 1.0f
            else -> (1.0f + (magnitude - 6f) / 24f).coerceAtMost(1.8f)
        }
    }

    data class Delta(val dx: Int, val dy: Int) {
        companion object {
            val Zero = Delta(0, 0)
        }
    }

    companion object {
        private const val DEADZONE_PX = 0.18f
        private const val MAX_CARRY_PX = 0.95f
    }
}
