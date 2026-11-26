package com.gizwits.smart.app.core.setup

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class OnboardingCompletionTest {
    private val gson = Gson()
    private val product = DemoProduct("a".repeat(32), "b".repeat(32))

    @Test fun firstLaunchStillShowsGuide() {
        assertFalse(DemoConfiguration().hasCompletedOnboarding(false))
    }

    @Test fun completionSurvivesColdStartWithoutProductsOrSession() {
        val saved = DemoConfiguration().withOnboardingCompleted(true)
        val restored = gson.fromJson(gson.toJson(saved), DemoConfiguration::class.java)
        assertTrue(restored.hasCompletedOnboarding(false))
    }

    @Test fun addingAProductDuringAnUnfinishedGuideDoesNotCompleteIt() {
        val saved = DemoConfiguration(products = listOf(product), onboardingCompleted = false)
        val restored = gson.fromJson(gson.toJson(saved), DemoConfiguration::class.java)
        assertFalse(restored.hasCompletedOnboarding(false))
    }

    @Test fun legacyProductsRecoverHomeAfterTheOldSaveClearedLogin() {
        val legacy = gson.fromJson("""{"products":[{"productKey":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","productSecret":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}""", DemoConfiguration::class.java)
        assertNull(legacy.onboardingCompleted)
        assertTrue(legacy.hasCompletedOnboarding(false))
    }

    @Test fun legacyAnonymousSessionWithoutProductsRecoversHome() {
        assertTrue(DemoConfiguration().hasCompletedOnboarding(true))
    }

    @Test fun defaultAppMigrationDoesNotResetCompletion() {
        val saved = DemoConfiguration(products = listOf(product)).withOnboardingCompleted(true)
        assertTrue(saved.needsDefaultSessionRefresh)
        assertTrue(saved.hasCompletedOnboarding(false))
        assertEquals(product, saved.products.single())
    }

    @Test fun removingLastProductKeepsCompletionAndApplicationOnColdStart() {
        val original = DemoConfiguration("c".repeat(32), "d".repeat(32), "usapi.gizwits.com",
            listOf(product), onboardingCompleted = true)
        val saved = original.withProducts(emptyList())
        val restored = gson.fromJson(gson.toJson(saved), DemoConfiguration::class.java)
        assertTrue(restored.products.isEmpty())
        assertTrue(restored.hasCompletedOnboarding(false))
        assertEquals(original.appId, restored.appId)
        assertEquals(original.appSecret, restored.appSecret)
        assertEquals(original.server, restored.server)
    }
}
