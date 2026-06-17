package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadMotionFilterTest {
    @Test
    fun tinyNoiseDoesNotMoveCursor() {
        val filter = TouchpadMotionFilter()

        assertEquals(TouchpadMotionFilter.Delta.Zero, filter.filter(0.05f, 0.05f, 100))
    }

    @Test
    fun fractionalMovementAccumulatesForPrecision() {
        val filter = TouchpadMotionFilter()

        assertEquals(TouchpadMotionFilter.Delta.Zero, filter.filter(0.4f, 0f, 100))
        assertEquals(TouchpadMotionFilter.Delta(1, 0), filter.filter(0.4f, 0f, 100))
    }

    @Test
    fun fastMovementAcceleratesButStaysBounded() {
        val filter = TouchpadMotionFilter()

        val delta = filter.filter(18f, 0f, 100)

        assertTrue(delta.dx > 18)
        assertTrue(delta.dx <= 33)
    }

    @Test
    fun accelerationCanBeDisabled() {
        val filter = TouchpadMotionFilter()

        val delta = filter.filter(18f, 0f, 100, accelerationEnabled = false)

        assertEquals(TouchpadMotionFilter.Delta(18, 0), delta)
    }
}
