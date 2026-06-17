package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Test

class DesklyPrefsTest {
    @Test
    fun volumeButtonModeAllowsOnlyPhoneOrPc() {
        assertEquals("phone", DesklyPrefs.normalizeVolumeButtonMode(null))
        assertEquals("phone", DesklyPrefs.normalizeVolumeButtonMode("tablet"))
        assertEquals("phone", DesklyPrefs.normalizeVolumeButtonMode("PC"))
        assertEquals("pc", DesklyPrefs.normalizeVolumeButtonMode("pc"))
    }
}
