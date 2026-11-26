package com.gizwits.smart.app.ui.component

import com.gizwits.smart.sdk.exception.GizConnectException
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleScanPermissionTest {
    @Test fun legacyAndroidRequiresLocationEvenAfterPermissionGranted() {
        for (version in 26..30) {
            assertEquals(BleScanRequirement.LocationOff, bleScanRequirement(version, true, true, true, false))
            assertNull(bleScanRequirement(version, true, true, true, true))
        }
    }

    @Test fun nearbyDevicePermissionAllowsScanningWithoutLocationOnModernAndroid() {
        for (version in 31..36) {
            assertNull(bleScanRequirement(version, true, true, true, false))
        }
    }

    @Test fun missingPermissionIsResolvedBeforeSystemSwitches() {
        for (version in listOf(30, 31, 36)) {
            assertEquals(BleScanRequirement.Permission, bleScanRequirement(version, false, true, false, false))
        }
    }

    @Test fun bluetoothMustBeEnabledOnAllVersions() {
        for (version in listOf(26, 30, 31, 36)) {
            assertEquals(BleScanRequirement.BluetoothOff, bleScanRequirement(version, true, true, false, true))
        }
    }

    @Test fun unsupportedDeviceDoesNotRequestPermissions() {
        assertEquals(BleScanRequirement.BluetoothUnavailable, bleScanRequirement(36, false, false, false, false))
    }
    @Test fun asynchronousScanFailureExplainsTheNextAction() {
        assertTrue(bleScanErrorMessage(GizConnectException.GIZ_SDK_BLE_NO_PERMISSION).contains("权限"))
        assertTrue(bleScanErrorMessage(GizConnectException.GIZ_SDK_BLE_SCAN_FAILED).contains("稍后重试"))
    }
}
