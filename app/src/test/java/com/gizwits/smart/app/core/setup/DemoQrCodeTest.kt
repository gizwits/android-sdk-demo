package com.gizwits.smart.app.core.setup

import com.gizwits.smart.app.feature.main.ScanCodeDialogState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoQrCodeTest {
    private val pk = "0123456789abcdef0123456789abcdef"
    private val ps = "abcdef0123456789abcdef0123456789"

    @Test fun productCodeRoutesToCredentialsWithoutBeingTreatedAsDevice() {
        val code = DemoQrCode.parse(" \ngizwitsproduct:$pk:$ps\n") as DemoQrCode.Product
        assertEquals(pk, code.credentials.productKey)
        assertEquals(ps, code.credentials.productSecret)
        assertFalse(code.toString().contains(ps))
    }

    @Test fun deviceCodePreservesBindingParametersIncludingOptionalField() {
        for (suffix in listOf("", ",btmac123")) {
            val code = DemoQrCode.parse(" SSBT://$pk,device123,AA:BB:CC:DD:EE:FF,passcode123$suffix\n") as DemoQrCode.Device
            assertEquals(pk, code.productKey)
            assertEquals("device123", code.deviceId)
            assertEquals("passcode123", code.passcode)
            assertFalse(code.toString().contains("passcode123"))
        }
    }

    @Test fun incompleteAndUnrelatedCodesDoNotReachEitherFlow() {
        listOf(
            "", "gizwitsproduct:$pk", "gizwitsproduct:$pk:bad",
            "gizwitsproduct:$pk:$ps:extra", "SSBT://$pk,device123,mac",
            "SSBT://$pk,,mac,pass", "SSBT://$pk,device,mac,pass,extra,extra",
            "https://example.test/$pk/$ps", "https://example.test/?code=SSBT://$pk,device,mac,pass"
        ).forEach { assertNull(DemoQrCode.parse(it)) }
    }

    @Test fun productScanOpensCredentialFlowWithoutLogin() = runBlocking {
        val state = ScanCodeDialogState(this)
        var openedProduct = false
        state.scan("gizwitsproduct:$pk:$ps", loggedIn = false) { openedProduct = true }
        assertTrue(openedProduct)
        assertFalse(state.isShow)
        assertFalse(state.needsLogin)
        assertFalse(state.busy)
    }

    @Test fun deviceScanRequiresLoginBeforeBinding() = runBlocking {
        val state = ScanCodeDialogState(this)
        state.scan("SSBT://$pk,device123,mac,passcode123", loggedIn = false) {
            fail("Device code must not open product entry")
        }
        assertTrue(state.isShow)
        assertTrue(state.needsLogin)
        assertFalse(state.busy)
    }

    @Test fun retryingWithProductClearsPreviousDeviceLoginPrompt() = runBlocking {
        val state = ScanCodeDialogState(this)
        state.scan("SSBT://$pk,device123,mac,passcode123", loggedIn = false) {}
        assertTrue(state.needsLogin)
        state.scan("invalid", loggedIn = false) { fail("Invalid code must not navigate") }
        assertTrue(state.isShow)
        assertFalse(state.needsLogin)
        var openedProduct = false
        state.scan("gizwitsproduct:$pk:$ps", loggedIn = false) { openedProduct = true }
        assertTrue(openedProduct)
        assertFalse(state.isShow)
    }
}
