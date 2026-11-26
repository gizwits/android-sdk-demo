package com.gizwits.smart.app.core.setup

import org.junit.Assert.*
import org.junit.Test

class ProductMetadataTest {
    @Test fun readsCloudProductName() {
        assertEquals("香愿香薰机_V2", ProductMetadata.name("""{"name":" 香愿香薰机_V2 ","entities":[]}"""))
    }
    @Test fun unavailableAndErrorResponsesHaveNoName() {
        listOf(null, "invalid", "[]", "{}", """{"name":"  "}""", """{"name":123}""",
            """{"name":"错误","error_code":400}""").forEach { assertNull(ProductMetadata.name(it)) }
    }
}
