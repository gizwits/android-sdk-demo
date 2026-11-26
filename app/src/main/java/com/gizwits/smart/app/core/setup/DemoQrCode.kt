package com.gizwits.smart.app.core.setup

/** Only supported Gizwits protocols are routed; arbitrary QR URLs are never opened. */
sealed class DemoQrCode {
    class Product(val credentials: DemoProduct) : DemoQrCode()
    class Device(val productKey: String, val deviceId: String, val passcode: String) : DemoQrCode()

    companion object {
        private val deviceCode = Regex("^SSBT://([a-zA-Z0-9]{32}),([a-zA-Z0-9]+),([^,\\s]+),([a-zA-Z0-9]+)(?:,[a-zA-Z0-9]+)?$")

        fun parse(raw: String): DemoQrCode? {
            ProductCredentials.parseQr(raw)?.let { return Product(it) }
            val match = deviceCode.matchEntire(raw.trim()) ?: return null
            return Device(match.groupValues[1], match.groupValues[2], match.groupValues[4])
        }
    }
}
