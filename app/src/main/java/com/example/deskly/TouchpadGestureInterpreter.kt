package com.example.deskly

import kotlin.math.abs
import kotlin.math.roundToInt

class TouchpadGestureInterpreter(
    private val scrollStepPx: Float = DEFAULT_SCROLL_STEP_PX,
    private val pinchZoomStepPx: Float = DEFAULT_PINCH_ZOOM_STEP_PX,
    private val pinchDominanceRatio: Float = DEFAULT_PINCH_DOMINANCE_RATIO
) {
    private var lastScrollY = 0f
    private var lastZoomDistance = 0f

    fun startTwoFingerGesture(averageY: Float, distance: Float) {
        lastScrollY = averageY
        lastZoomDistance = distance
    }

    fun interpretMove(averageY: Float, distance: Float): Result {
        if (lastZoomDistance <= 0f) {
            startTwoFingerGesture(averageY, distance)
            return Result.None
        }

        val pinchDelta = distance - lastZoomDistance
        val scrollDelta = averageY - lastScrollY
        val pinchIsDominant = abs(pinchDelta) >= abs(scrollDelta) * pinchDominanceRatio

        if (abs(pinchDelta) >= pinchZoomStepPx && pinchIsDominant) {
            lastZoomDistance = distance
            lastScrollY = averageY
            return if (pinchDelta > 0f) Result.ZoomIn else Result.ZoomOut
        }

        if (abs(scrollDelta) >= scrollStepPx) {
            val steps = (scrollDelta / scrollStepPx).roundToInt()
            if (steps != 0) {
                lastScrollY += steps * scrollStepPx
                return Result.Scroll(deltaY = -steps)
            }
        }

        return Result.None
    }

    fun reset() {
        lastScrollY = 0f
        lastZoomDistance = 0f
    }

    sealed class Result {
        data object None : Result()
        data object ZoomIn : Result()
        data object ZoomOut : Result()
        data class Scroll(val deltaY: Int) : Result()
    }

    companion object {
        const val DEFAULT_SCROLL_STEP_PX = 18f
        const val DEFAULT_PINCH_ZOOM_STEP_PX = 32f
        const val DEFAULT_PINCH_DOMINANCE_RATIO = 1.25f
    }
}
