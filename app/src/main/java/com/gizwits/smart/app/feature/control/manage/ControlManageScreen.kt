package com.gizwits.smart.app.feature.control.manage

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
import com.gizwits.smart.app.feature.setup.CredentialField
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlManageRoute(onBackClick: () -> Unit, navigateToOTAManage: (String, GizOTAFirmwareType) -> Unit,
                       viewModel: ControlManageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val updates by viewModel.updateState.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var unbind by remember { mutableStateOf(false) }
    Scaffold(topBar = { SoftTopBar(title = "设备信息", onBack = onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SoftHeading(state.productName, if (state.boundState) "已绑定到当前身份" else "尚未绑定")
            SoftCard {
                Text("日常信息", style = MaterialTheme.typography.titleLarge)
                Text("别名：${viewModel.alias.ifEmpty { "未设置" }}")
                Text("备注：${viewModel.remark.ifEmpty { "未填写" }}")
                SoftAction("编辑别名与备注", { name = viewModel.alias; note = viewModel.remark; edit = true }, !viewModel.busy, true)
            }
            SoftCard {
                Text("技术信息", style = MaterialTheme.typography.titleLarge)
                Text("产品 PK：${state.productKey}", style = MaterialTheme.typography.bodySmall)
                Text("设备 ID：${state.deviceId ?: "尚未注册"}", style = MaterialTheme.typography.bodySmall)
                Text("MAC：${state.mac}", style = MaterialTheme.typography.bodySmall)
                CredentialField("设备口令", state.devicePasscode ?: "", {}, secret = true, enabled = false)
                if (state.deviceId == null) SoftAction("注册设备", state.onRegisterDevice, !viewModel.busy, true)
            }
            if (!state.mac.startsWith("virtual")) SoftCard {
                Text("固件与版本", style = MaterialTheme.typography.titleLarge)
                Text("当前支持蓝牙升级，需保持蓝牙连接。")
                Text("模组：硬件 ${state.deviceInfo?.moduleHardVersion ?: "—"} / 固件 ${state.deviceInfo?.moduleSoftVersion ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("MCU：硬件 ${state.deviceInfo?.mcuHardVersion ?: "—"} / 固件 ${state.deviceInfo?.mcuSoftVersion ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("协议：${state.deviceInfo?.protocolVersion ?: "—"} / 串口 ${state.deviceInfo?.serialPortProtocolVersion ?: "—"}", style = MaterialTheme.typography.bodySmall)
                SoftAction("通信模组 · ${updates.newModuleVersion ?: "检查更新"}", { navigateToOTAManage(state.id, GizOTAFirmwareType.Module) }, !viewModel.busy && state.id.isNotBlank(), true)
                SoftAction("MCU · ${updates.newMcuVersion ?: "检查更新"}", { navigateToOTAManage(state.id, GizOTAFirmwareType.MCU) }, !viewModel.busy && state.id.isNotBlank(), true)
            }
            if (viewModel.hasBleProfile) SoftCard {
                Text("设备时间", style = MaterialTheme.typography.titleLarge)
                Text("使用手机时间校时。旧版设备需支持蓝牙校时扩展。")
                SoftAction("同步手机时间", viewModel::syncTime, !viewModel.busy, true)
            }
            if (!state.boundState && viewModel.needsProductCredentials) SoftMessage("绑定需要产品 PS，请在设置 → 产品管理中补充上方 PK 对应的凭据。")
            viewModel.message?.let { SoftMessage(it) }
            if (state.boundState) OutlinedButton(onClick = { unbind = true }, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) { Text("解除绑定", color = MaterialTheme.colorScheme.error) }
            else SoftAction("添加到当前身份", state.onBindDevice, !viewModel.busy)
        }
    }
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text("编辑设备信息") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("设备别名") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("备注") })
        }
    }, confirmButton = { TextButton(onClick = { edit = false; viewModel.updateInfo(name, note) }, enabled = name.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = { edit = false }) { Text("取消") } })
    if (unbind) AlertDialog(onDismissRequest = { unbind = false }, title = { Text("解除设备绑定？") }, text = { Text("此设备将从当前身份的设备列表中移除。") },
        confirmButton = { TextButton(onClick = { unbind = false; state.onUnbindDevice() }) { Text("解除绑定", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { unbind = false }) { Text("取消") } })
}
