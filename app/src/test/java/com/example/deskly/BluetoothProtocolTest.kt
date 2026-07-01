package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothProtocolTest {
    @Test
    fun bluetoothDeviceKeyIsStableAndDistinctFromLan() {
        assertEquals(
            "bluetooth_AA:BB:CC:DD:EE:FF",
            BluetoothProtocol.deviceKey("aa:bb:cc:dd:ee:ff")
        )
    }
}
