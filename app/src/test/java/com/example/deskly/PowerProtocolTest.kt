package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerProtocolTest {
    @Test
    fun dangerousPowerActionsRequireConfirmation() {
        assertFalse(PowerProtocol.requiresConfirmation(PowerProtocol.TYPE_LOCK))
        assertTrue(PowerProtocol.requiresConfirmation(PowerProtocol.TYPE_SLEEP))
        assertTrue(PowerProtocol.requiresConfirmation(PowerProtocol.TYPE_RESTART))
        assertTrue(PowerProtocol.requiresConfirmation(PowerProtocol.TYPE_SHUTDOWN))
    }

    @Test
    fun payloadIncludesTimestampForAllPowerActions() {
        val payload = PowerProtocol.payload(PowerProtocol.TYPE_LOCK, issuedAtUtcMs = 1234L)

        assertEquals(1234L, payload.getLong("issuedAtUtcMs"))
        assertFalse(payload.has("confirmed"))
    }

    @Test
    fun shutdownPayloadIncludesConfirmationAndFadePreference() {
        val payload = PowerProtocol.payload(
            PowerProtocol.TYPE_SHUTDOWN,
            issuedAtUtcMs = 5678L,
            fadeOutVolume = true
        )

        assertEquals(5678L, payload.getLong("issuedAtUtcMs"))
        assertTrue(payload.getBoolean("confirmed"))
        assertTrue(payload.getBoolean("fadeOutVolume"))
    }
}
