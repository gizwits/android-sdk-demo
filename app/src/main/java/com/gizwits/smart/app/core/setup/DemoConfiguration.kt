package com.gizwits.smart.app.core.setup

/** Credentials deliberately do not use a generated data-class toString. */
class DemoProduct(val productKey: String, val productSecret: String) {
    override fun toString() = "DemoProduct(credentials=hidden)"
}

class DemoConfiguration(
    val customAppId: String = "",
    val customAppSecret: String = "",
    val server: String = "api.gizwits.com",
    val products: List<DemoProduct> = emptyList(),
    val appliedDefaultAppId: String? = null,
    val onboardingCompleted: Boolean? = null
) {
    // Older demos did not store completion. Preserve access for existing installations.
    fun hasCompletedOnboarding(hasSavedSession: Boolean): Boolean =
        onboardingCompleted ?: (hasSavedSession || products.isNotEmpty())

    fun withOnboardingCompleted(completed: Boolean) = DemoConfiguration(
        customAppId, customAppSecret, server, products, appliedDefaultAppId, completed)

    fun withProducts(products: List<DemoProduct>) = DemoConfiguration(
        customAppId, customAppSecret, server, products, appliedDefaultAppId, onboardingCompleted)

    val usesDefaultApp get() = customAppId.isEmpty()
    val needsDefaultSessionRefresh get() = usesDefaultApp && appliedDefaultAppId != DemoDefaults.APP_ID
    val appId get() = customAppId.ifEmpty { DemoDefaults.APP_ID }
    val appSecret get() = customAppSecret.ifEmpty { DemoDefaults.APP_SECRET }
    override fun toString() = "DemoConfiguration(credentials=hidden)"
}

object ProductCredentials {
    private val credential = Regex("^[a-zA-Z0-9]{32}$")
    fun valid(value: String) = credential.matches(value.trim())

    /** Parse only the developer-center product protocol, never an arbitrary URL/device code. */
    fun parseQr(raw: String): DemoProduct? {
        val parts = raw.trim().split(':')
        if (parts.size != 3 || parts[0] != "gizwitsproduct") return null
        if (!valid(parts[1]) || !valid(parts[2])) return null
        return DemoProduct(parts[1].trim(), parts[2].trim())
    }

    fun upsert(products: List<DemoProduct>, product: DemoProduct): List<DemoProduct> =
        products.filterNot { it.productKey == product.productKey } + product
}
