package com.gizwits.smart.app.core.setup

import com.gizwits.smart.sdk.common.ProductInfoStruct

// Use the API available in published SDK 1.4.20 as well as the source checkout.
internal fun allowsDemoProduct(products: List<ProductInfoStruct>, productKey: String): Boolean =
    products.isEmpty() || products.any { it.productKey.equals(productKey, ignoreCase = true) }
