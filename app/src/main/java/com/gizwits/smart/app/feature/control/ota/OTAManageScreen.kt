package com.gizwits.smart.app.feature.control.ota

import androidx.activity.compose.BackHandler
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
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.ota.OTAEvent
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTAManageRoute(onBackClick: () -> Unit, viewModel: OTAManageViewModel = hiltViewModel()) {
    val version by viewModel.deviceUpdateInfo.collectAsState()
    val progress by viewModel.deviceOTAFlow.collectAsState()
    val running by viewModel.otaIsRunning.collectAsState()
    val status by viewModel.status.collectAsState()
    BackHandler(enabled = running) { /* Keep the owning screen until the upgrade is completed or cancelled. */ }
    Scaffold(topBar = { SoftTopBar(title = "固件升级", onBack = { if (!running) onBackClick() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SoftMessage("当前支持蓝牙升级。升级期间保持设备通电并靠近手机。")
            SoftCard {
                Text(if (viewModel.otaType == GizOTAFirmwareType.MCU) "MCU 固件" else "通信模组", style = MaterialTheme.typography.titleLarge)
                Text("当前版本：${version.currentSoftVersion.ifBlank { "待获取" }}")
                Text("目标版本：${version.softVersion.ifBlank { "待检查" }}")
                if (running) {
                    val current = progress
                    if (current == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        Text(when(current.event) {
                            OTAEvent.GizOTAEventCheckingDeviceVersion -> "检查设备版本"
                            OTAEvent.GizOTAEventDownloading -> "下载固件"
                            OTAEvent.GizOTAEventTransferring -> "正在传输固件"
                            OTAEvent.GizOTAEventVerification -> "校验固件"
                            OTAEvent.GizOTAEventFinish -> "设备返回完成"
                            else -> "正在处理"
                        })
                        LinearProgressIndicator(progress = (current.percentage.toFloat() / 100).coerceIn(0f,1f), modifier = Modifier.fillMaxWidth())
                        Text("${current.percentage}%")
                    }
                }
            }
            SoftMessage(viewModel.message)
            if (running) SoftAction("取消升级", viewModel.cancelUpgrade, secondary = true)
            else {
                SoftAction(if (viewModel.checking) "正在检查…" else "检查更新", viewModel::checkUpdate, !viewModel.checking && status == CapacityStatus.ONLINE_CONTROLLABLE, true)
                SoftAction("开始升级", viewModel.startUpgrade, !viewModel.checking && status == CapacityStatus.ONLINE_CONTROLLABLE && version.softVersion.isNotBlank() && version.softVersion != version.currentSoftVersion)
            }
        }
    }
}
