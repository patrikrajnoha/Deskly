package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchpadGestureInterpreterTest {
    @Test
    fun pinchOutProducesZoomIn() {
        val interpreter = TouchpadGestureInterpreter()
        interpreter.startTwoFingerGesture(averageY = 100f, distance = 120f)

        assertEquals(
            TouchpadGestureInterpreter.Result.ZoomIn,
            interpreter.interpretMove(averageY = 102f, distance = 156f)
        )
    }

    @Test
    fun pinchInProducesZoomOut() {
        val interpreter = TouchpadGestureInterpreter()
        interpreter.startTwoFingerGesture(averageY = 100f, distance = 160f)

        assertEquals(
            TouchpadGestureInterpreter.Result.ZoomOut,
            interpreter.interpretMove(averageY = 99f, distance = 126f)
        )
    }

    @Test
    fun smallPinchUnderThresholdIsNoOp() {
        val interpreter = TouchpadGestureInterpreter()
        interpreter.startTwoFingerGesture(averageY = 100f, distance = 120f)

        assertEquals(
            TouchpadGestureInterpreter.Result.None,
            interpreter.interpretMove(averageY = 100f, distance = 145f)
        )
    }

    @Test
    fun twoFingerScrollDoesNotAccidentallyZoom() {
        val interpreter = TouchpadGestureInterpreter()
        interpreter.startTwoFingerGesture(averageY = 100f, distance = 120f)

        assertEquals(
            TouchpadGestureInterpreter.Result.Scroll(deltaY = -3),
            interpreter.interpretMove(averageY = 154f, distance = 138f)
        )
    }
}
