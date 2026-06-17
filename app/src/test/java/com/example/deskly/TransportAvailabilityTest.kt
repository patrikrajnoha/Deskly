package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportAvailabilityTest {
    @Test
    fun bluetoothUnavailableIsExplicitFallbackState() {
        val status = TransportAvailability.bluetoothUnavailable()

        assertEquals(TransportAvailability.TransportType.BLUETOOTH, status.type)
        assertEquals("Bluetooth", status.label)
        assertFalse(status.available)
        assertTrue(status.reason!!.contains("not implemented"))
    }

    @Test
    fun preferredTransportUsesBluetoothOnlyWhenAvailable() {
        val selected = TransportAvailability.choosePreferred(
            listOf(
                TransportAvailability.bluetoothUnavailable(),
                TransportAvailability.lanAvailable()
            )
        )

        assertEquals(TransportAvailability.TransportType.LAN, selected?.type)
    }

    @Test
    fun preferredTransportReturnsNullWhenNothingIsAvailable() {
        val selected = TransportAvailability.choosePreferred(
            listOf(TransportAvailability.bluetoothUnavailable())
        )

        assertNull(selected)
    }
}
