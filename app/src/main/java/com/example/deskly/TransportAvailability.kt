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

    fun bluetoothUnavailable(reason: String = "Bluetooth transport is not implemented yet."): Status =
        Status(
            type = TransportType.BLUETOOTH,
            available = false,
            label = "Bluetooth",
            reason = reason
        )

    fun choosePreferred(statuses: List<Status>): Status? =
        statuses.firstOrNull { it.type == TransportType.BLUETOOTH && it.available }
            ?: statuses.firstOrNull { it.type == TransportType.LAN && it.available }
}
