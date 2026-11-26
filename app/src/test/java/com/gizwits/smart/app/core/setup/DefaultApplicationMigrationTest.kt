package com.gizwits.smart.app.core.setup

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DefaultApplicationMigrationTest {
    @Test fun oldDefaultConfigurationRequiresSessionResetAndKeepsProducts() {
        val config = Gson().fromJson("""{"customAppId":"","customAppSecret":"","server":"api.gizwits.com","products":[{"productKey":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","productSecret":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}""", DemoConfiguration::class.java)
        assertTrue(config.needsDefaultSessionRefresh)
        assertEquals(DemoDefaults.APP_ID, config.appId)
        assertEquals(1, config.products.size)
    }
    @Test fun persistedDefaultIdentityDoesNotRequireResetAgain() {
        val current = DemoConfiguration(appliedDefaultAppId = DemoDefaults.APP_ID)
        val restored = Gson().fromJson(Gson().toJson(current), DemoConfiguration::class.java)
        assertFalse(restored.needsDefaultSessionRefresh)
    }
    @Test fun existingCustomApplicationsRemainUnchanged() {
        val config = DemoConfiguration("a".repeat(32), "b".repeat(32))
        assertFalse(config.needsDefaultSessionRefresh)
        assertEquals("a".repeat(32), config.appId)
        assertTrue(config.appSecret == "b".repeat(32))
    }
}
