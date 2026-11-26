package com.gizwits.smart.app.core.setup

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class DeviceSnapshotsTest {
    @Test fun partialReportKeepsPreviousValuesAndDoesNotRequireAlertsOrFaults() {
        val old = mapOf("power" to JsonPrimitive(true))
        val values = mergeDeviceValues(old, JsonParser.parseString("""{"data":{"level":42}}""").asJsonObject)
        assertTrue(values.getValue("power").asBoolean)
        assertEquals(42, values.getValue("level").asInt)
        assertFalse(old.containsKey("level"))
    }
    @Test fun binaryBytesAreStringsRatherThanUnquotedJsonNumbers() {
        val values = mergeDeviceValues(emptyMap(), JsonParser.parseString("""{"data":{"bytes":[0,10,255]},"alerts":{"water":true},"faults":{"sensor":false}}""").asJsonObject)
        assertEquals("000AFF", values.getValue("bytes").asString)
        assertTrue(values.getValue("bytes").asJsonPrimitive.isString)
        assertTrue(values.getValue("water").asBoolean)
        assertFalse(values.getValue("sensor").asBoolean)
    }
    @Test fun snapshotsAreIsolatedByDeviceAndChannelAndClearedOnIdentityChange() {
        DeviceSnapshots.clear()
        DeviceSnapshots.write("device1:Bluetooth", mapOf("power" to JsonPrimitive(true)))
        assertTrue(DeviceSnapshots.read("device1:Mqtt").isEmpty())
        assertTrue(DeviceSnapshots.read("device2:Bluetooth").isEmpty())
        assertTrue(DeviceSnapshots.read("device1:Bluetooth").getValue("power").asBoolean)
        DeviceSnapshots.clear()
        assertTrue(DeviceSnapshots.read("device1:Bluetooth").isEmpty())
    }
}
