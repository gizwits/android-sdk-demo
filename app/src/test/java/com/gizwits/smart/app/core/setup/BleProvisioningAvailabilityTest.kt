package com.gizwits.smart.app.core.setup

import org.junit.Assert.*
import org.junit.Test

class BleProvisioningAvailabilityTest {
    @Test fun onlyKnownOpenAndOnlineDevicesCanBeProvisioned() {
        assertTrue(canProvisionBleDevice(false, true))
        assertFalse(canProvisionBleDevice(true, true))
        assertFalse(canProvisionBleDevice(null, true))
        assertFalse(canProvisionBleDevice(false, false))
    }
    @Test fun windowCanCloseAndReopenWithoutRediscoveringDevice() {
        val availability = listOf(false, true, false).map { canProvisionBleDevice(it, true) }
        assertEquals(listOf(true, false, true), availability)
    }
}
