package com.gizwits.smart.app.feature.control

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.R
import com.gizwits.smart.app.feature.control.data.ControlDataMode
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.lan.lanCapability
import com.gizwits.smart.sdk.mqtt.mqttCapability
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlRoute(onBackClick: () -> Unit, navigateToControlData: (String, ControlDataMode) -> Unit,
                 navigateToControlManage: (String) -> Unit, viewModel: ControlViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    Scaffold(topBar = { SoftTopBar(title = state.title, onBack = onBackClick, actions = {
        IconButton(onClick = { navigateToControlManage(state.device.id) }) { Icon(painterResource(R.drawable.ic_setting_24), "设备信息") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SoftHeading("选择当前通道", "先读取状态，再发送控制。设备回报与接口结果分开记录。")
            listOf(Triple(ControlDataMode.Bluetooth, "蓝牙", state.device.bleCapability),
                Triple(ControlDataMode.Lan, "局域网", state.device.lanCapability),
                Triple(ControlDataMode.Mqtt, "云端", state.device.mqttCapability)).forEach { (mode, label, capability) ->
                val status by capability.subscribeStatus().collectAsState()
                SoftCard {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    if (mode == ControlDataMode.Bluetooth &&
                        state.device.bleCapability.getModuleProfile()?.protocolVersion == com.gizwits.smart.sdk.common.GizProtocolVersionEnum.V5 &&
                        state.device.bleCapability.getModuleProfile()?.authenticationMethod == com.gizwits.smart.sdk.common.AuthenticationMethodEnum.ONE_PRODUCT_KEY &&
                        com.gizwits.smart.sdk.GizSDKManager.getConfiguration().getProductWithProductKey(state.device.productKey)?.productSecret.isNullOrBlank()) {
                        SoftMessage("此设备使用一型一密蓝牙鉴权，请在设置 → 产品管理中补充产品 PS。PK：${state.device.productKey}")
                    }
                    Text(when(status) { CapacityStatus.ONLINE_CONTROLLABLE -> "可控制"; CapacityStatus.ONLINE -> "已发现 · 等待连接"; CapacityStatus.OFFLINE -> "离线" })
                    SoftAction(if (status == CapacityStatus.ONLINE_CONTROLLABLE) "进入数据点控制" else "查看最后状态", { navigateToControlData(state.device.id, mode) }, secondary = status != CapacityStatus.ONLINE_CONTROLLABLE)
                }
            }
            SoftAction(if (retrying) "正在检查连接…" else "重新检查连接", {
                retrying = true
                scope.launch {
                    try { listOf(state.device.bleCapability, state.device.lanCapability, state.device.mqttCapability).forEach { capability ->
                        try { capability.connect() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { /* Channel state remains visible for retry. */ }
                    } }
                    finally { retrying = false }
                }
            }, !retrying, true)
        }
    }
}
