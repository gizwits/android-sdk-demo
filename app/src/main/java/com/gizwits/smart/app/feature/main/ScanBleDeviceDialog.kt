package com.gizwits.smart.app.feature.main

import android.content.Context
import android.util.Log
import com.gizwits.smart.sdk.exception.GizException
import com.gizwits.smart.app.core.setup.retryWifiList
import com.gizwits.smart.app.core.setup.canProvisionBleDevice
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.core.setup.DemoEvents
import com.gizwits.smart.app.core.setup.ProvisioningWifiSelection
import com.gizwits.smart.app.feature.setup.CredentialField
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizDevice
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.bluetooth.*
import com.gizwits.smart.sdk.lan.*
import com.gizwits.smart.sdk.mqtt.queryBoundDevices
import com.gizwits.smart.sdk.common.GizWiFiNetwork
import com.gizwits.smart.sdk.common.GizProtocolVersionEnum
import com.gizwits.smart.sdk.common.GizWiFiActivatorEvent
import com.gizwits.smart.sdk.esptouch.GizESPTouchSmartConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ConfigType { BLE, LAN, AirLink }
enum class DialogUiStep { Idle, SearchDevice, ProvideCredentials, ConfigureDevice, Success, Failure }

class ScanBleDeviceDialogState(private val scope: CoroutineScope) {
    var step by mutableStateOf(DialogUiStep.Idle); private set
    var type by mutableStateOf(ConfigType.BLE); private set
    var selectedDevice by mutableStateOf<GizDevice?>(null); private set
    private val wifiSelection = ProvisioningWifiSelection()
    val wifiSSID get() = wifiSelection.ssid
    var wifiPassword: String
        get() = wifiSelection.password
        set(value) { wifiSelection.password = value }
    val canAutofillSSID get() = !wifiSelection.edited
    fun selectSSID(value: String) { wifiSelection.select(value) }
    fun useCurrentSSID(value: String?) { wifiSelection.useCurrentSSID(value) }
    var event by mutableStateOf("准备连接设备"); private set
    var error by mutableStateOf<String?>(null); private set
    var events by mutableStateOf<List<String>>(emptyList()); private set
    var wifiNetworks by mutableStateOf<List<GizWiFiNetwork>>(emptyList()); private set
    var loadingWifi by mutableStateOf(false); private set
    var wifiListMessage by mutableStateOf<String?>(null); private set
    private var wifiJob: Job? = null
    private var wifiGeneration = 0
    val supportsWifiList get() = type == ConfigType.BLE && selectedDevice?.bleCapability?.getModuleProfile()?.protocolVersion in setOf(GizProtocolVersionEnum.V2, GizProtocolVersionEnum.V5)

    private fun stopWifiQuery() {
        ++wifiGeneration; wifiJob?.cancel(); loadingWifi = false
        wifiNetworks = emptyList(); wifiListMessage = null
    }
    fun cancelWifiQuery() { stopWifiQuery() }
    fun refreshWifi() {
        if (!supportsWifiList || loadingWifi || busy) return
        val device = selectedDevice ?: return
        val generation = ++wifiGeneration
        loadingWifi = true; wifiListMessage = null; wifiNetworks = emptyList()
        val previous = wifiJob
        wifiJob = scope.launch {
            try {
                previous?.join()
                val result = retryWifiList(
                    request = { device.bleCapability.getWiFiList(15_000).map { networks ->
                        networks.filter { it.ssid.isNotEmpty() }.sortedByDescending { it.signalStrength }.distinctBy { it.ssid }
                    } },
                    onAttempt = { if (generation == wifiGeneration) wifiListMessage = "正在获取 Wi-Fi 列表…" }
                )
                if (generation != wifiGeneration || selectedDevice !== device || step != DialogUiStep.ProvideCredentials) return@launch
                result.fold(onSuccess = { networks ->
                    wifiNetworks = networks
                    wifiListMessage = if (networks.isEmpty()) "暂时未获取到附近的 Wi-Fi，可手动输入名称。" else null
                }, onFailure = {
                    wifiListMessage = "暂时未获取到附近的 Wi-Fi，可手动输入名称。"
                })
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == wifiGeneration) wifiListMessage = "暂时未获取到附近的 Wi-Fi，可手动输入名称。"
            } finally { if (generation == wifiGeneration) loadingWifi = false }
        }
    }
    fun selectWifi(network: GizWiFiNetwork) {
        selectSSID(network.ssid)
    }

    private var job: Job? = null
    private var attempt = 0
    private var pendingBinding by mutableStateOf<Pair<String, String>?>(null)
    var needsProductCredentials by mutableStateOf(false); private set
    val requiresAirLinkProduct get() = type == ConfigType.AirLink && GizSDKManager.getConfiguration().productInfos.isEmpty()
    val canRetryBinding get() = pendingBinding != null && !needsProductCredentials
    private suspend fun bindProvisioned(mac: String, productKey: String): Result<Unit> {
        pendingBinding = mac to productKey
        needsProductCredentials = GizSDKManager.getConfiguration().getProductWithProductKey(productKey)?.productSecret.isNullOrBlank()
        if (needsProductCredentials) {
            event = "Wi-Fi 配置成功，尚未绑定。请在设置 → 产品管理中补充此设备的 PK / PS，再到设备信息页绑定。PK：$productKey"
            return Result.success(Unit)
        }
        event = "设备已上线，正在添加到当前身份"
        return GizSDKManager.getUserManager().bindDevice(mac, productKey, null, null)
    }
    val busy get() = step == DialogUiStep.ConfigureDevice
    val deviceList = combine(GizSDKManager.subscribeBluetoothDeviceList(), GizSDKManager.subscribeLanDeviceList()) { ble, lan ->
        (ble + lan).distinctBy { it.id }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    private val scanErrorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        GizSDKManager.subscribeBleScanError().collect { failure ->
            if (step == DialogUiStep.SearchDevice && type == ConfigType.BLE) error = bleScanErrorMessage(failure)
        }
    }

    fun show() {
        if (busy) return
        wifiSelection.reset()
        needsProductCredentials = false; pendingBinding = null
        error = null; step = DialogUiStep.SearchDevice
    }
    fun scan() {
        error = null
        try { GizSDKManager.startBleScan() } catch (_: Exception) { error = "无法扫描蓝牙，请检查蓝牙开关和附近设备权限。" }
    }
    fun choose(type: ConfigType) { stopWifiQuery(); wifiSelection.reset(); this.type = type; error = null; selectedDevice = null; if (type == ConfigType.AirLink) step = DialogUiStep.ProvideCredentials }
    fun checkProvisioningDevice(device: GizDevice): Boolean {
        if (type != ConfigType.BLE || canProvisionBleDevice(device.bleCapability.getModuleProfile()?.configured,
                device.bleCapability.getStatus() != CapacityStatus.OFFLINE)) return true
        stopWifiQuery(); selectedDevice = null; step = DialogUiStep.SearchDevice
        error = "设备当前不可配网，请重新开启设备配网模式后搜索。"
        return false
    }
    fun select(device: GizDevice) { if (!checkProvisioningDevice(device)) return; stopWifiQuery(); wifiSelection.reset(); selectedDevice = device; error = null; step = DialogUiStep.ProvideCredentials }
    fun editNetwork() { if (!busy) { error = null; step = DialogUiStep.ProvideCredentials } }
    fun backToDevices() { if (!busy) { stopWifiQuery(); step = DialogUiStep.SearchDevice } }
    fun start(context: Context) {
        if (busy || loadingWifi) return
        if (requiresAirLinkProduct) { error = "AirLink 需要先添加产品以确认配网结果，请添加产品或改用蓝牙 / SoftAP。"; return }
        if (job?.isCompleted == false) { error = "上一次操作正在结束，请稍后重试。"; return }
        if (wifiSSID.isBlank() || wifiSSID.toByteArray().size > 32) { error = "请输入有效的 Wi-Fi 名称（最多 32 字节）。"; return }
        if (type != ConfigType.AirLink && selectedDevice == null) { error = "请先选择设备。"; return }
        if (selectedDevice?.let { checkProvisioningDevice(it) } == false) return
        pendingBinding = null; needsProductCredentials = false
        step = DialogUiStep.ConfigureDevice; error = null; events = emptyList(); event = "正在连接设备"
        val run = ++attempt
        job = scope.launch {
            var registering = true
            try {
                val callback: (GizWiFiActivatorEvent) -> Unit = { value ->
                    scope.launch(Dispatchers.Main.immediate) {
                        if (run == attempt && busy) {
                            event = describe(value)
                            events = (events + event).distinct().takeLast(7)
                            DemoEvents.record("配网阶段", value.name)
                        }
                    }
                }
                val result = if (type == ConfigType.AirLink) {
                    val registration = GizESPTouchSmartConfig.provideWiFiCredentialsWithAirLink(context.applicationContext,
                        wifiSSID, wifiPassword, 60, GizSDKManager.getConfiguration().productInfos.map { it.productKey }, callback).getOrThrow()
                    requireNotNull(registration) { "Missing registration" }
                    registering = false
                    bindProvisioned(registration.mac, registration.productKey)
                } else {
                    val device = selectedDevice!!
                    val capability = if (type == ConfigType.BLE) device.bleCapability else device.lanCapability
                    // Legacy firmware reports registration via random_code and does not bind the appended UID.
                    capability.provideWiFiCredentials(wifiSSID, wifiPassword, 60, callback, false).fold(
                        onSuccess = {
                            registering = false
                            bindProvisioned(device.mac, device.productKey)
                        },
                        onFailure = { Result.failure(it) }
                    )
                }
                if (run != attempt) return@launch
                step = if (result.isSuccess) DialogUiStep.Success else DialogUiStep.Failure
                if (result.isFailure) error = failureMessage(result.exceptionOrNull()!!, registering)
                DemoEvents.record("设备添加结果", if (result.isSuccess) (if (needsProductCredentials) event else "设备已添加到当前身份") else error.orEmpty(), result.isFailure)
                if (result.isSuccess) { wifiPassword = ""; if (!needsProductCredentials) GizSDKManager.queryBoundDevices() }
            } catch (e: CancellationException) { throw e }
            catch (failure: Exception) {
                if (run == attempt) { step = DialogUiStep.Failure; error = failureMessage(failure, registering) }
            }
        }
    }
    private fun failureMessage(failure: Throwable, registering: Boolean): String {
        val code = (failure as? GizException)?.errorCode
        Log.w("GagentProvision", "stage=${if (registering) "registration" else "bind"} code=${code ?: "none"} type=${failure.javaClass.simpleName}")
        val reason = if (code == 9065) "当前 AppID 未关联设备产品，请在机智云后台关联应用与产品后重试（9065）"
            else if (failure is GizException) failure.message.orEmpty().take(240) else when (failure) {
            is java.net.UnknownHostException -> "无法解析云端地址，请检查手机网络"
            is java.net.SocketTimeoutException -> "云端请求超时"
            is java.io.IOException -> "网络请求失败（${failure.javaClass.simpleName}）"
            else -> "${failure.javaClass.simpleName}，请查看 SDK 日志"
        }
        return "${if (registering) "设备配网未完成" else "设备已上线，但添加到当前身份失败"}：$reason"
    }
    fun retryBinding() {
        if (busy || job?.isCompleted == false) return
        val target = pendingBinding ?: return
        step = DialogUiStep.ConfigureDevice; error = null
        event = "设备已上线，正在添加到当前身份"
        val run = ++attempt
        job = scope.launch {
            try {
                val result = GizSDKManager.getUserManager().bindDevice(target.first, target.second, null, null)
                if (run != attempt) return@launch
                step = if (result.isSuccess) DialogUiStep.Success else DialogUiStep.Failure
                error = result.exceptionOrNull()?.let { failureMessage(it, false) }
                DemoEvents.record("设备添加结果", error ?: "设备已添加到当前身份", result.isFailure)
                if (result.isSuccess) { wifiPassword = ""; if (!needsProductCredentials) GizSDKManager.queryBoundDevices() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (run == attempt) { step = DialogUiStep.Failure; error = failureMessage(failure, false) }
            }
        }
    }
    fun cancel() {
        ++attempt
        if (type == ConfigType.AirLink) GizESPTouchSmartConfig.stopProvideWiFiCredentialsWithAirLink()
        else if (type == ConfigType.BLE) selectedDevice?.bleCapability?.stopProvideWiFiCredentials()
        else selectedDevice?.lanCapability?.stopProvideWiFiCredentials()
        job?.cancel()
        step = DialogUiStep.ProvideCredentials
        error = "已停止等待。若设备已经收到网络信息，它可能仍会连接，请先查看设备状态。"
        DemoEvents.record("配网取消", "停止本次等待")
    }
    fun close() { stopWifiQuery(); if (busy) cancel(); pendingBinding = null; step = DialogUiStep.Idle; selectedDevice = null; wifiPassword = "" }
    fun dispose() { stopWifiQuery(); if (busy) cancel(); job?.cancel(); scanErrorJob.cancel() }
    private fun describe(event: GizWiFiActivatorEvent): String = when (event) {
        GizWiFiActivatorEvent.START_WIFI_CONFIG -> "开始配网"
        GizWiFiActivatorEvent.START_CONNECT_DEVICE -> "正在连接设备"
        GizWiFiActivatorEvent.CONNECT_DEVICE_SUCCESS -> "已连接设备"
        GizWiFiActivatorEvent.CONNECT_DEVICE_FAIL -> "连接设备失败"
        GizWiFiActivatorEvent.START_SEND_WIFI_CONFIG -> "正在发送网络信息"
        GizWiFiActivatorEvent.START_SEND_WIFI_CONFIG_SUCCESS, GizWiFiActivatorEvent.WIFI_CONFIG_RECV_SUCCESS -> "设备已收到网络信息"
        GizWiFiActivatorEvent.EVENT_CONNECTING_ROUTER -> "设备正在连接路由器"
        GizWiFiActivatorEvent.EVENT_CONNECT_ROUTER_FAILED -> "设备连接路由器失败"
        GizWiFiActivatorEvent.EVENT_REGISTERING -> "正在注册设备"
        GizWiFiActivatorEvent.EVENT_CLOUD_CONNECTED -> "设备已连接云端"
        GizWiFiActivatorEvent.START_SEARCH_RANDOM_CODE -> "正在确认设备上线"
        GizWiFiActivatorEvent.WIFI_CONFIG_SUCCESS -> "网络已连接，正在确认添加结果"
        GizWiFiActivatorEvent.WIFI_CONFIG_FAIL -> "配网失败"
        GizWiFiActivatorEvent.WIFI_CONFIG_CANCEL -> "已取消配网"
        else -> "阶段：${event.name}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBleDeviceDialog(state: ScanBleDeviceDialogState) {
    val context = LocalContext.current
    val devices by state.deviceList.collectAsState()
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    if (state.step == DialogUiStep.Idle) return
    val requestBluetooth = rememberBleScanRequest {
        if (state.step == DialogUiStep.SearchDevice && state.type == ConfigType.BLE) state.scan()
    }
    LaunchedEffect(state.step, state.type) {
        if (state.step == DialogUiStep.SearchDevice && state.type == ConfigType.BLE) requestBluetooth()
    }
    val wifiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) { permissionMessage = null; state.start(context) }
        else permissionMessage = "AirLink 需要位置权限读取当前 Wi-Fi 信息，请授权并打开系统定位后重试。"
    }
    fun fillCurrentSSID() {
        if (state.step != DialogUiStep.ProvideCredentials || !state.canAutofillSSID) return
        state.useCurrentSSID(readCurrentWifiSSID(context))
        permissionMessage = if (state.wifiSSID.isEmpty()) "未能读取当前 Wi-Fi 名称，请确认已连接 Wi-Fi 并开启位置权限和系统定位，或手动输入。" else null
    }
    var requestedSSIDPermission by remember { mutableStateOf(false) }
    val ssidPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { fillCurrentSSID() }
    LaunchedEffect(state.step, state.type, state.selectedDevice?.id) {
        if (state.step == DialogUiStep.ProvideCredentials) {
            state.refreshWifi()
            if (state.canAutofillSSID && !requestedSSIDPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestedSSIDPermission = true
                ssidPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else fillCurrentSSID()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val fillSSIDOnResume by rememberUpdatedState(::fillCurrentSSID)
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.step == DialogUiStep.ProvideCredentials) {
                fillSSIDOnResume()
                state.refreshWifi()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (state.step != DialogUiStep.Idle) BottomDialog(onDismissRequest = state::close, dismissOnBackPress = !state.busy, dismissOnClickOutside = false) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.step) {
                DialogUiStep.SearchDevice -> {
                    SoftHeading("添加设备", "开启设备配网模式，保持手机靠近。")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ConfigType.values().forEach { type -> FilterChip(state.type == type, { state.choose(type) }, label = {
                            Text(when(type) { ConfigType.BLE -> "蓝牙"; ConfigType.LAN -> "局域网"; ConfigType.AirLink -> "AirLink" })
                        }) }
                    }
                    if (state.type == ConfigType.BLE) {
                        SoftAction("扫描附近蓝牙设备", requestBluetooth, secondary = true)
                        permissionMessage?.let { SoftMessage(it, true) }
                    }
                    // Subscribe before filtering: SDK status is lazy and starts as OFFLINE.
                    val filtered = devices.filter { device ->
                        key(device.id, state.type) {
                            val status by (if (state.type == ConfigType.BLE) device.bleCapability.subscribeStatus()
                                else device.lanCapability.subscribeStatus()).collectAsState()
                            if (state.type == ConfigType.BLE) {
                                val profile by device.bleCapability.subscribeModuleProfile().collectAsState()
                                canProvisionBleDevice(profile?.configured, status != CapacityStatus.OFFLINE)
                            } else status != CapacityStatus.OFFLINE
                        }
                    }
                    if (filtered.isEmpty()) SoftMessage("暂未发现设备。请确认设备已进入配网模式并靠近手机；局域网发现需要连接同一网络。")
                    filtered.forEach { device ->
                        val name by device.subscribeProductName().collectAsState()
                        SoftCard(Modifier.clickable { state.select(device) }) {
                            Text(name ?: "附近设备", style = MaterialTheme.typography.titleMedium)
                            Text(device.mac, style = MaterialTheme.typography.bodySmall)
                            Text("已发现 · 点击选择", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                DialogUiStep.ProvideCredentials -> {
                    if (state.type == ConfigType.BLE) state.selectedDevice?.let { device ->
                        val profile by device.bleCapability.subscribeModuleProfile().collectAsState()
                        val status by device.bleCapability.subscribeStatus().collectAsState()
                        LaunchedEffect(device.id, profile?.configured, status) {
                            state.checkProvisioningDevice(device)
                        }
                    }
                    SoftHeading("连接到哪个网络？", if (state.type == ConfigType.AirLink) "AirLink：先将手机连接目标 Wi-Fi，设备需支持此配网方式。" else "通过${if (state.type == ConfigType.BLE) "蓝牙" else "局域网"}发送网络信息。")
                    if (state.supportsWifiList) {
                        Text("设备附近的 Wi-Fi", style = MaterialTheme.typography.titleSmall)
                        if (state.loadingWifi) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.wifiListMessage?.let { SoftMessage(it) }
                        if (state.loadingWifi) TextButton(onClick = state::cancelWifiQuery) {
                            Text("取消读取，手动输入")
                        }
                        LazyColumn(Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.wifiNetworks, key = { it.ssid }) { network ->
                                SoftCard(Modifier.clickable { state.selectWifi(network) }) {
                                    Text(network.ssid, style = MaterialTheme.typography.titleSmall)
                                    Text("信号强度 ${network.signalStrength} dBm${if (state.wifiSSID == network.ssid) " · 已选择" else ""}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (state.requiresAirLinkProduct) SoftMessage("AirLink 需要先添加产品；蓝牙 / SoftAP 可跳过产品配置。")
                    CredentialField("Wi-Fi 名称", state.wifiSSID, state::selectSSID)
                    CredentialField("Wi-Fi 密码（开放网络可留空）", state.wifiPassword, { state.wifiPassword = it }, true)
                    SoftMessage("连接成功后，设备将出现在设备列表中。请保持手机与设备靠近。")
                    SoftAction("开始连接", {
                        if (state.type == ConfigType.AirLink && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            wifiPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else state.start(context)
                    }, state.wifiSSID.isNotBlank() && !state.loadingWifi && !state.requiresAirLinkProduct)
                    permissionMessage?.let { SoftMessage(it, true) }
                    SoftAction("重新选择设备", state::backToDevices, secondary = true)
                }
                DialogUiStep.ConfigureDevice -> {
                    SoftHeading("正在连接网络", state.event)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    SoftCard { state.events.forEach { Text("· $it") }; Text("最长等待 60 秒，请保持设备通电。") }
                    SoftAction("取消配网", state::cancel, secondary = true)
                }
                DialogUiStep.Success -> {
                    SoftHeading(if (state.needsProductCredentials) "Wi-Fi 配置成功，尚未绑定" else "设备已添加",
                        if (state.needsProductCredentials) state.event else "已添加到当前身份。返回设备列表即可开始控制。")
                    SoftAction("查看设备", state::close)
                }
                DialogUiStep.Failure -> {
                    SoftHeading(if (state.canRetryBinding) "设备已上线，添加未完成" else "配网未完成",
                        if (state.canRetryBinding) "按下方错误提示处理后，可以直接重试添加。" else "核对 Wi-Fi 名称和密码，确认设备处于配网模式且信号良好。")
                    Text("最近阶段：${state.event}")
                    if (state.canRetryBinding) SoftAction("重试添加", state::retryBinding)
                    else SoftAction("修改网络信息后重试", state::editNetwork)
                    SoftAction("重新选择设备", state::backToDevices, secondary = true)
                }
                else -> Unit
            }
            state.error?.let { SoftMessage(it, true) }
        }
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun readCurrentWifiSSID(context: Context): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    return try { context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid }
    catch (_: SecurityException) { null }
}
