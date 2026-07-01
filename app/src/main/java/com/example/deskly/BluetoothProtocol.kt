package com.example.deskly

import java.util.UUID

object BluetoothProtocol {
    const val SERVICE_UUID_STRING = "6f5f7a04-2b5a-41d4-9f5f-3e8e0fd8c901"
    const val SERVICE_NAME = "Deskly"

    val SERVICE_UUID: UUID = UUID.fromString(SERVICE_UUID_STRING)

    fun deviceKey(address: String): String =
        "bluetooth_${address.trim().uppercase()}"
}
