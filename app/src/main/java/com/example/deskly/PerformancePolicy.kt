package com.example.deskly

data class PerformancePolicy(
    val lowPowerEnabled: Boolean = false,
    val systemPowerSaveMode: Boolean = false,
    val foreground: Boolean = true
) {
    val effectiveLowPower: Boolean
        get() = lowPowerEnabled || systemPowerSaveMode

    val heartbeatMs: Long
        get() = if (effectiveLowPower || !foreground) 30_000L else 10_000L

    val mouseMoveThrottleMs: Long
        get() = if (effectiveLowPower) 24L else 16L

    val timerPollMs: Long
        get() = if (effectiveLowPower) 5_000L else 1_000L

    val reconnectBaseDelayMs: Long
        get() = if (effectiveLowPower || !foreground) 5_000L else 1_000L

    fun shouldPollTimer(timerRunning: Boolean): Boolean =
        foreground && timerRunning
}
