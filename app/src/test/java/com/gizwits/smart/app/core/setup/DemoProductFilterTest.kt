package com.gizwits.smart.app.core.setup

import com.gizwits.smart.sdk.common.ProductInfoStruct
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoProductFilterTest {
    @Test fun noConfiguredProductsAllowsAnyProduct() {
        assertTrue(allowsDemoProduct(emptyList(), "a".repeat(32)))
    }

    @Test fun configuredProductsMatchIgnoringCaseAndRejectUnknownProducts() {
        val products = listOf(ProductInfoStruct("a".repeat(32), "b".repeat(32)))
        assertTrue(allowsDemoProduct(products, "A".repeat(32)))
        assertFalse(allowsDemoProduct(products, "c".repeat(32)))
    }
}
