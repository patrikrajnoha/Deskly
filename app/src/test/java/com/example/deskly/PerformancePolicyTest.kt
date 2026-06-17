package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePolicyTest {
    @Test
    fun foregroundNormalModeKeepsResponsiveCadence() {
        val policy = PerformancePolicy(lowPowerEnabled = false, systemPowerSaveMode = false, foreground = true)

        assertFalse(policy.effectiveLowPower)
        assertEquals(10_000L, policy.heartbeatMs)
        assertEquals(16L, policy.mouseMoveThrottleMs)
        assertEquals(1_000L, policy.timerPollMs)
        assertTrue(policy.shouldPollTimer(timerRunning = true))
    }

    @Test
    fun lowPowerModeReducesPollingAndPointerCadence() {
        val policy = PerformancePolicy(lowPowerEnabled = true, systemPowerSaveMode = false, foreground = true)

        assertTrue(policy.effectiveLowPower)
        assertEquals(30_000L, policy.heartbeatMs)
        assertEquals(24L, policy.mouseMoveThrottleMs)
        assertEquals(5_000L, policy.timerPollMs)
    }

    @Test
    fun systemBatterySaverActsLikeLowPower() {
        val policy = PerformancePolicy(lowPowerEnabled = false, systemPowerSaveMode = true, foreground = true)

        assertTrue(policy.effectiveLowPower)
        assertEquals(30_000L, policy.heartbeatMs)
    }

    @Test
    fun backgroundModeStopsTimerPolling() {
        val policy = PerformancePolicy(lowPowerEnabled = false, systemPowerSaveMode = false, foreground = false)

        assertFalse(policy.shouldPollTimer(timerRunning = true))
        assertEquals(30_000L, policy.heartbeatMs)
        assertEquals(5_000L, policy.reconnectBaseDelayMs)
    }
}
