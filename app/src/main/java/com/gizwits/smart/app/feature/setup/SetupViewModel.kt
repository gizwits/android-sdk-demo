package com.gizwits.smart.app.feature.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.setup.*
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.common.GizConfiguration
import com.gizwits.smart.sdk.common.ProductInfoStruct
import com.gizwits.smart.sdk.common.ServerInfoStruct
import com.gizwits.smart.sdk.core.data.model.UserState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SetupPage { Welcome, Application, Product, ManualProduct, ConfirmProduct, Account, Ready }

// Demo-only opt-in. Change to false and restart the process to verify the SDK default behavior.
private const val ENABLE_BLE_ADVERTISEMENT_DATA = true

@HiltViewModel
class SetupViewModel @Inject constructor(private val store: DemoConfigurationStore) : ViewModel() {
    var openAddDevice by mutableStateOf(false)
    var loaded by mutableStateOf(false); private set
    var configuration by mutableStateOf(DemoConfiguration()); private set
    var page by mutableStateOf(SetupPage.Welcome); private set
    var isGuidedSetup by mutableStateOf(true); private set
    private var pageHistory by mutableStateOf<List<SetupPage>>(emptyList())
    val canGoBack: Boolean get() = pageHistory.isNotEmpty()
    var sessionGeneration by mutableStateOf(0); private set
    var startAtDevices by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var loginInProgress by mutableStateOf<Boolean?>(null); private set
    var operationMessage by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var appId by mutableStateOf("")
    var appSecret by mutableStateOf("")
    var server by mutableStateOf("api.gizwits.com")
    var productKey by mutableStateOf("")
    var productSecret by mutableStateOf("")
    private var productNames by mutableStateOf<Map<String, String>>(emptyMap())
    private var productNameMessages by mutableStateOf<Map<String, String>>(emptyMap())
    var account by mutableStateOf("")
    var password by mutableStateOf("")
    private var pendingProduct: DemoProduct? = null
    private val user = MutableStateFlow<UserState>(UserState.NotLoggedIn)
    val userState = user.asStateFlow()

    private var loading = false
    init { load() }
    fun load() {
        if (loading || loaded) return
        loading = true; error = null
        viewModelScope.launch {
            try {
                configuration = withContext(Dispatchers.IO) { store.load() }
                initialize(configuration)
                val completed = configuration.hasCompletedOnboarding(
                    GizSDKManager.getUserManager().getAuthorizeData() != null)
                val defaultApplicationChanged = configuration.needsDefaultSessionRefresh
                val needsCompletionMigration = configuration.onboardingCompleted == null
                configuration = configuration.withOnboardingCompleted(completed)
                if (defaultApplicationChanged) {
                    // Clear the prior app's local session before persisting the new default identity.
                    GizSDKManager.deregister()
                    withContext(Dispatchers.IO) { store.save(configuration) }
                    initialize(configuration)
                    notice = "默认应用已更新，请重新登录或选择匿名体验。"
                } else if (needsCompletionMigration) {
                    withContext(Dispatchers.IO) { store.save(configuration) }
                }
                startAtDevices = completed
                loaded = true
                var previousUserId: String? = null
                GizSDKManager.getUserManager().subscribeUserState().collect { state ->
                    val currentUserId = (state as? UserState.Logged)?.userInfo?.userId
                    if (state !is UserState.LoggingIn && currentUserId != previousUserId) {
                        DeviceSnapshots.clear()
                        DemoEvents.clear()
                        previousUserId = currentUserId
                    }
                    user.value = state
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "无法读取接入配置，请重试。已保存的信息没有被覆盖。" }
            finally { loading = false }
        }
    }

    private fun initialize(config: DemoConfiguration) {
        GizSDKManager.setDebug(false)
        GizSDKManager.initSDK(GizConfiguration(config.appId, config.appSecret,
            config.products.map { ProductInfoStruct(it.productKey, it.productSecret) }, ServerInfoStruct(config.server),
            enableBleAdvertisementData = ENABLE_BLE_ADVERTISEMENT_DATA))
    }

    private fun metadataKey(pk: String) = "${configuration.server}:${pk.trim().lowercase()}"
    fun productName(pk: String): String? = productNames[metadataKey(pk)]
    fun productNameMessage(pk: String): String = productNameMessages[metadataKey(pk)]
        ?: "输入完整 PK 后自动获取产品名称"

    suspend fun loadProductName(pk: String) {
        val key = pk.trim().lowercase()
        if (!loaded || !ProductCredentials.valid(key)) return
        val server = configuration.server
        val cacheKey = metadataKey(key)
        if (productNames.containsKey(cacheKey)) return
        productNameMessages = productNameMessages + (cacheKey to "正在获取产品名称…")
        val name = try {
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                withContext(Dispatchers.IO) { ProductMetadata.name(GizSDKManager.getProductDataPointConfig(key)) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        if (configuration.server != server) return
        if (name != null) {
            productNames = productNames + (cacheKey to name)
            productNameMessages = productNameMessages - cacheKey
        } else {
            productNameMessages = productNameMessages + (cacheKey to "未能获取产品名称，可继续使用 PK")
        }
    }

    fun show(target: SetupPage) {
        if (busy) return
        moveTo(target)
        if (target == SetupPage.Application) {
            appId = configuration.customAppId; appSecret = configuration.customAppSecret; server = configuration.server
        }
        if (target == SetupPage.Product || target == SetupPage.ManualProduct) { productKey = ""; productSecret = ""; pendingProduct = null }
    }

    /** Start a separate setup visit from devices/settings. */
    fun enter(target: SetupPage) {
        if (busy) return
        isGuidedSetup = target == SetupPage.Welcome
        show(target)
        pageHistory = emptyList()
    }

    /** Step changes navigate only; configuration is applied by each page's save action. */
    fun selectGuideStep(index: Int) {
        if (busy || index !in 0..2) return
        val steps = listOf(SetupPage.Application, SetupPage.Product, SetupPage.Account)
        val target = steps[index]
        if (page == target) return
        isGuidedSetup = true
        show(target)
        pageHistory = listOf(SetupPage.Welcome) + steps.take(index)
    }

    private fun moveTo(target: SetupPage) {
        if (target != page) pageHistory = pageHistory + page
        error = null; notice = null; page = target
    }

    fun goBack() {
        if (busy || pageHistory.isEmpty()) return
        page = pageHistory.last()
        pageHistory = pageHistory.dropLast(1)
        if (page == SetupPage.Application) {
            appId = configuration.customAppId; appSecret = configuration.customAppSecret; server = configuration.server
        }
        error = null; notice = null
    }

    private fun perform(failure: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = failure; DemoEvents.record("接入操作未完成", failure, true) }
            finally { busy = false; loginInProgress = null; operationMessage = null }
        }
    }

    /** Apply only after a user confirms. Clearing old sessions avoids mixing app identities. */
    private suspend fun applyConfiguration(next: DemoConfiguration) {
        withContext(Dispatchers.IO) { store.save(next) }
        GizSDKManager.deregister()
        user.value = UserState.NotLoggedIn
        initialize(next)
        configuration = next
        startAtDevices = false
        DemoEvents.clear()
        DeviceSnapshots.clear()
        sessionGeneration += 1
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        perform("接入进度未能保存，请重试。") {
            val next = configuration.withOnboardingCompleted(true)
            withContext(Dispatchers.IO) { store.save(next) }
            configuration = next
            onComplete()
        }
    }

    fun saveApplication(useDefault: Boolean) {
        if (!useDefault && (!ProductCredentials.valid(appId) || !ProductCredentials.valid(appSecret))) {
            error = "App ID 和 App Secret 需为 32 位字母或数字。"; return
        }
        perform("应用配置未能保存，请重试。") {
            val next = DemoConfiguration(if (useDefault) "" else appId.trim(), if (useDefault) "" else appSecret.trim(), server, configuration.products,
                onboardingCompleted = configuration.onboardingCompleted)
            val changed = next.appId != configuration.appId || next.appSecret != configuration.appSecret || next.server != configuration.server
            moveTo(SetupPage.Product)
            if (changed) applyConfiguration(next)
            notice = if (changed) "应用已更新，稍后请重新登录。" else "已使用${if (useDefault) "默认 Super App ID" else "当前应用"}。"
        }
    }

    fun scanProduct(raw: String) {
        val product = ProductCredentials.parseQr(raw)
        if (product == null) { error = "未识别到完整的产品 PK 和 PS。请扫描开发者中心的产品二维码，或手动输入。"; return }
        productKey = product.productKey; productSecret = product.productSecret
        confirmProduct()
    }
    fun clearError() { error = null }
    fun confirmProduct() {
        if (!ProductCredentials.valid(productKey) || !ProductCredentials.valid(productSecret)) {
            error = "PK 和 PS 需各为 32 位字母或数字。"; return
        }
        error = null
        pendingProduct = DemoProduct(productKey.trim(), productSecret.trim())
        val existing = configuration.products.firstOrNull { it.productKey == productKey.trim() }
        moveTo(SetupPage.ConfirmProduct)
        notice = when {
            existing == null -> "确认后添加到当前应用。"
            existing.productSecret == productSecret.trim() -> "这个产品已添加，可以直接继续。"
            else -> "这个 PK 已存在，确认后将更新 PS。"
        }
    }
    fun saveProduct() {
        val product = pendingProduct ?: return
        perform("产品未能保存，请重试。") {
            val existing = configuration.products.firstOrNull { it.productKey == product.productKey }
            if (existing?.productSecret != product.productSecret) {
                applyProducts(ProductCredentials.upsert(configuration.products, product))
            }
            if (isGuidedSetup) {
                moveTo(if (user.value is UserState.Logged) SetupPage.Ready else SetupPage.Account)
                pageHistory = pageHistory.filter { it != SetupPage.ConfirmProduct && it != SetupPage.ManualProduct }
            } else {
                page = SetupPage.Product
                pageHistory = emptyList()
                notice = "产品已保存。"
            }
            productSecret = ""; pendingProduct = null
        }
    }

    private suspend fun applyProducts(products: List<DemoProduct>) {
        val next = configuration.withProducts(products)
        withContext(Dispatchers.IO) { store.save(next) }
        // Product filters can be updated in place. Only app/region changes invalidate identity.
        initialize(next)
        configuration = next
    }

    fun removeProduct(productKey: String) {
        perform("产品未能删除，请重试。") {
            applyProducts(configuration.products.filterNot { it.productKey == productKey })
            notice = if (configuration.products.isEmpty()) "产品已删除，现在发现所有产品的设备。" else "产品已删除。"
        }
    }

    fun continueWithProducts() {
        if (busy) return
        moveTo(if (user.value is UserState.Logged) SetupPage.Ready else SetupPage.Account)
        error = null; notice = null
    }
    fun login(anonymous: Boolean) {
        if (busy) return
        if (!anonymous && (account.isBlank() || password.isBlank())) { error = "请填写账号和密码。"; return }
        perform("登录未完成，请检查网络和应用配置后重试。") {
            loginInProgress = anonymous
            operationMessage = if (anonymous) "正在匿名登录…" else "正在登录…"
            val authenticated = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                val manager = GizSDKManager.getUserManager()
                if (manager.getUserTokenStream().value != null) {
                    GizSDKManager.deregister()
                    initialize(configuration)
                    user.value = UserState.NotLoggedIn
                    startAtDevices = false
                    DemoEvents.clear(); DeviceSnapshots.clear()
                    sessionGeneration += 1
                }
                (if (anonymous) manager.loginByAnonymous() else manager.loginWithAccount(account.trim(), password)).getOrThrow()
                true
            } ?: false
            if (!authenticated) { error = "登录请求超时，请检查网络后重试。"; return@perform }
            operationMessage = "登录验证已完成，正在同步用户信息…"
            val logged = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                awaitLoginUser(GizSDKManager.getUserManager())
            }
            if (logged == null) {
                error = "登录验证已完成，但用户信息同步超时。请检查网络，稍后重试或使用当前身份继续。"
                return@perform
            }
            user.value = logged
            password = ""
            moveTo(SetupPage.Ready)
            DemoEvents.record("登录成功", if (anonymous) "当前使用匿名身份" else "当前使用账号登录")
        }
    }
}
