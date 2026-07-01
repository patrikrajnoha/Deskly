package com.example.deskly

object TransportAvailability {
    enum class TransportType {
        LAN,
        BLUETOOTH
    }

    data class Status(
        val type: TransportType,
        val available: Boolean,
        val label: String,
        val reason: String? = null
    )

    fun lanAvailable(): Status =
        Status(
            type = TransportType.LAN,
            available = true,
            label = "LAN"
        )

    fun bluetoothUnavailable(reason: String = "Bluetooth permission, pairing, or hardware is unavailable."): Status =
        Status(
            type = TransportType.BLUETOOTH,
            available = false,
            label = "Bluetooth",
            reason = reason
        )

    fun bluetoothAvailable(): Status =
        Status(
            type = TransportType.BLUETOOTH,
            available = true,
            label = "Bluetooth"
        )

    fun choosePreferred(statuses: List<Status>): Status? =
        statuses.firstOrNull { it.type == TransportType.BLUETOOTH && it.available }
            ?: statuses.firstOrNull { it.type == TransportType.LAN && it.available }
}
