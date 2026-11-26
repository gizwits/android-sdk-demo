package com.gizwits.smart.app.core.setup

import org.junit.Assert.*
import org.junit.Test

class ProductCredentialsTest {
    private val pk = "0123456789abcdef0123456789abcdef"
    private val ps = "abcdef0123456789abcdef0123456789"
    @Test fun developerCenterCodePreservesBothCredentials() {
        val value = ProductCredentials.parseQr("gizwitsproduct:$pk:$ps")!!
        assertEquals(pk, value.productKey); assertEquals(ps, value.productSecret)
    }
    @Test fun surroundingClipboardWhitespaceIsIgnored() {
        assertNotNull(ProductCredentials.parseQr(" \ngizwitsproduct:$pk:$ps\n"))
    }
    @Test fun deviceAndUntrustedUrlCodesAreNotProducts() {
        listOf("SSBT://$pk,device,mac,pass", "https://example.test/$pk/$ps", "gizwitsproduct://$pk:$ps", "other:$pk:$ps").forEach {
            assertNull(ProductCredentials.parseQr(it))
        }
    }
    @Test fun incompleteAndExtraFieldsAreRejected() {
        listOf("gizwitsproduct:$pk", "gizwitsproduct:$pk:", "gizwitsproduct:$pk:$ps:extra", "gizwitsproduct:$pk:bad").forEach {
            assertNull(ProductCredentials.parseQr(it))
        }
    }
    @Test fun manualValidationRejectsPunctuationAndInternalWhitespace() {
        assertTrue(ProductCredentials.valid(pk))
        assertFalse(ProductCredentials.valid(pk.dropLast(1)))
        assertFalse(ProductCredentials.valid(pk.dropLast(1) + "_"))
        assertFalse(ProductCredentials.valid(pk.take(16) + " " + pk.drop(16)))
    }
    @Test fun duplicateKeyReplacesCredentialsWithoutDuplicatingProduct() {
        val other = DemoProduct("b".repeat(32), "c".repeat(32))
        val existing = listOf(DemoProduct(pk, ps), other)
        val result = ProductCredentials.upsert(existing, DemoProduct(pk, "d".repeat(32)))
        assertEquals(2, result.size)
        assertEquals("d".repeat(32), result.single { it.productKey == pk }.productSecret)
        assertEquals(ps, existing.first().productSecret)
        assertSame(other, result.single { it.productKey != pk })
    }
    @Test fun credentialObjectsNeverPrintSecrets() {
        assertFalse(DemoProduct(pk, ps).toString().contains(ps))
        assertFalse(DemoConfiguration(pk, ps).toString().contains(ps))
    }
    @Test fun freshInstallUsesDefaultIdentityButNoSampleProducts() {
        val config = DemoConfiguration()
        assertTrue(config.usesDefaultApp)
        assertTrue(config.products.isEmpty())
        assertEquals("api.gizwits.com", config.server)
    }
}
