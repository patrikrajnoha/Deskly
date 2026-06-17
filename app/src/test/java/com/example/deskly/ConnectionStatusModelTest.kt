package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStatusModelTest {
    @Test
    fun pairedAuthorizedStateShowsConnectedPcAndLanType() {
        val viewState = ConnectionStatusModel.build(
            state = DesklyClient.State(connected = true, authorized = true),
            savedPcName = "Office PC",
            savedIp = "192.168.1.10",
            savedPort = 5050,
            hasToken = true,
            authRejected = false
        )

        assertEquals("Connected", viewState.status)
        assertEquals("Authorized", viewState.auth)
        assertEquals("Office PC", viewState.pcName)
        assertEquals("192.168.1.10:5050", viewState.address)
        assertEquals(ConnectionStatusModel.ConnectionType.LAN, viewState.connectionType)
    }

    @Test
    fun wrongTokenShowsAuthFailed() {
        val viewState = ConnectionStatusModel.build(
            state = DesklyClient.State(connected = true, lastError = "Unauthorized"),
            savedPcName = "Deskly PC",
            savedIp = "192.168.1.10",
            savedPort = 5050,
            hasToken = true,
            authRejected = true
        )

        assertEquals("Auth Failed", viewState.status)
        assertEquals("Auth failed", viewState.auth)
    }

    @Test
    fun untrustedConnectedDeviceRequiresPairing() {
        val viewState = ConnectionStatusModel.build(
            state = DesklyClient.State(connected = true),
            savedPcName = null,
            savedIp = "192.168.1.10",
            savedPort = 5050,
            hasToken = false,
            authRejected = false
        )

        assertEquals("Pair Required", viewState.status)
        assertEquals("Pair Required", viewState.auth)
    }
}
