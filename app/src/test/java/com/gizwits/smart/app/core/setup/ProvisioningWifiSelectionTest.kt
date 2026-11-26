package com.gizwits.smart.app.core.setup

import org.junit.Assert.*
import org.junit.Test

class ProvisioningWifiSelectionTest {
    @Test fun phoneNetworkIsSelectedAndItsRealWhitespaceIsPreserved() {
        val selection = ProvisioningWifiSelection()
        selection.useCurrentSSID("\" 家中 Wi-Fi \"")
        assertEquals(" 家中 Wi-Fi ", selection.ssid)
        assertFalse(selection.edited)
    }
    @Test fun redactedOrMissingSSIDDoesNotReplaceTheNetwork() {
        val selection = ProvisioningWifiSelection()
        selection.useCurrentSSID("\"Home\"")
        listOf(null, "", "<unknown ssid>", "\"<unknown ssid>\"", "0x").forEach(selection::useCurrentSSID)
        assertEquals("Home", selection.ssid)
    }
    @Test fun automaticUpdatesNeverOverwriteAnExplicitChoiceOrClearedField() {
        val selection = ProvisioningWifiSelection()
        selection.select("Other")
        selection.password = "secret"
        selection.useCurrentSSID("Home")
        assertEquals("Other", selection.ssid); assertEquals("secret", selection.password)
        selection.select("")
        selection.useCurrentSSID("Home")
        assertEquals("", selection.ssid)
    }
    @Test fun changingTheNetworkClearsItsPasswordAndANewVisitUsesCurrentWifi() {
        val selection = ProvisioningWifiSelection()
        selection.useCurrentSSID("Home"); selection.password = "secret"
        selection.useCurrentSSID("New")
        assertEquals("", selection.password)
        selection.select("Other"); selection.reset(); selection.useCurrentSSID("Home")
        assertEquals("Home", selection.ssid); assertFalse(selection.edited)
    }
}
