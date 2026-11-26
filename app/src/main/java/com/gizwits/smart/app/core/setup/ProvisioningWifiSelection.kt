package com.gizwits.smart.app.core.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Automatic phone-network updates stop after the user picks or edits a network. */
class ProvisioningWifiSelection {
    var ssid by mutableStateOf(""); private set
    var password by mutableStateOf("")
    var edited = false; private set

    fun reset() { ssid = ""; password = ""; edited = false }
    fun select(value: String) { edited = true; update(value) }
    fun useCurrentSSID(raw: String?) {
        if (edited) return
        val name = raw?.removeSurrounding("\"") ?: return
        if (name.isNotEmpty() && name != "<unknown ssid>" && name != "0x") update(name)
    }
    private fun update(value: String) {
        if (ssid != value) { ssid = value; password = "" }
    }
}
