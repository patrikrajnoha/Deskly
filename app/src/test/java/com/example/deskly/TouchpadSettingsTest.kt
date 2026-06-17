package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadSettingsTest {
    @Test
    fun defaultsAreTrackpadFriendly() {
        val settings = TouchpadSettings()

        assertEquals(100, settings.cursorSpeedPercent)
        assertEquals(100, settings.scrollSensitivityPercent)
        assertTrue(settings.naturalScroll)
        assertTrue(settings.visualFeedback)
        assertTrue(settings.acceleration)
        assertFalse(settings.leftHanded)
        assertEquals(100, settings.gyroSensitivityPercent)
    }

    @Test
    fun percentSettingsAreClampedForPersistence() {
        val settings = TouchpadSettings(
            cursorSpeedPercent = 5,
            scrollSensitivityPercent = 400,
            naturalScroll = false,
            visualFeedback = false,
            acceleration = false,
            leftHanded = true,
            gyroSensitivityPercent = 500
        ).normalized()

        assertEquals(25, settings.cursorSpeedPercent)
        assertEquals(200, settings.scrollSensitivityPercent)
        assertFalse(settings.naturalScroll)
        assertFalse(settings.visualFeedback)
        assertFalse(settings.acceleration)
        assertTrue(settings.leftHanded)
        assertEquals(200, settings.gyroSensitivityPercent)
    }
}
