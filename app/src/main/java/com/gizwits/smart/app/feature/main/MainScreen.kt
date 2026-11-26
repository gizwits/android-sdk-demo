package com.gizwits.smart.app.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.R
import com.gizwits.smart.app.feature.setup.*
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizDevice
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.core.data.model.UserState
import com.gizwits.smart.sdk.lan.lanCapability
import com.gizwits.smart.sdk.mqtt.mqttCapability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(navigateToControl: (String) -> Unit, navigateToSetting: () -> Unit,
              setup: SetupViewModel, openSetup: (SetupPage) -> Unit, viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val user by setup.userState.collectAsState()
    var tab by remember { mutableStateOf(if (setup.startAtDevices ||
        user is UserState.Logged) 1 else 0) }
    var scanDevice by remember { mutableStateOf(false) }
    val requestBluetooth = rememberBleScanRequest {
        state.onDeviceListTypeChange(DeviceListType.Bluetooth)
    }
    fun addDevice() {
        when {
            user !is UserState.Logged -> openSetup(SetupPage.Account)
            else -> viewModel.scanBleDeviceDialogState.show()
        }
    }
    LaunchedEffect(Unit) {
        if (setup.openAddDevice) {
            setup.openAddDevice = false
            tab = 1
            viewModel.scanBleDeviceDialogState.show()
        }
    }
    ScanBleDeviceDialog(viewModel.scanBleDeviceDialogState)
    ScanCodeDialog(
        state = viewModel.scanCodeDialogState,
        onScanAgain = { scanDevice = true },
        onLogin = { openSetup(SetupPage.Account) }
    )
    if (scanDevice) {
        BottomDialog(onDismissRequest = { scanDevice = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SoftHeading("扫一扫", "扫描设备二维码添加设备，或扫描产品二维码录入 PK / PS。")
                QrScanner(
                    onResult = { raw ->
                        scanDevice = false
                        viewModel.scanCodeDialogState.scan(raw, loggedIn = user is UserState.Logged) {
                            openSetup(SetupPage.Product)
                            setup.scanProduct(raw)
                        }
                    },
                    onCancel = { scanDevice = false },
                    cancelLabel = "取消"
                )
            }
        }
    }
    Scaffold(topBar = {
        SoftTopBar(title = if (tab == 0) "开始" else "我的设备", actions = {
            if (tab == 1) {
                IconButton(onClick = { scanDevice = true }, enabled = !setup.busy) {
                    Icon(painterResource(R.drawable.ic_scan_24), "扫一扫")
                }
            }
        })
    }, bottomBar = {
        Surface(shape = RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp),
            color = MaterialTheme.colorScheme.surface, modifier = Modifier.softShadow(21.dp, 15.dp, (-4).dp)) {
          Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 64.dp).selectableGroup()) {
            listOf(R.drawable.ic_application_24 to "开始", R.drawable.ic_device_24 to "设备", R.drawable.ic_setting_24 to "设置").forEachIndexed { i, item ->
                Column(Modifier.weight(1f).selectable(selected = tab == i, role = Role.Tab,
                    onClick = { if (i == 2) navigateToSetting() else tab = i }).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(48.dp, 27.dp).background(if (tab == i) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(item.first), null, Modifier.size(19.dp),
                            tint = if (tab == i) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(item.second, style = MaterialTheme.typography.labelSmall,
                        color = if (tab == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
          }
        }
    }) { padding ->
        if (tab == 0) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupWelcome(setup.configuration.products.size, user is UserState.Logged, enabled = !setup.busy) { index ->
                    openSetup(SetupPage.Welcome)
                    setup.selectGuideStep(index)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (setup.configuration.products.isEmpty()) item { SoftMessage("当前发现所有产品的设备，可在设置中添加产品以限定范围。") }
                    viewModel.error?.let { message -> item { SoftMessage(message, true) } }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("设备列表", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { if (state.deviceListType == DeviceListType.Bluetooth) requestBluetooth() else viewModel.onRefreshList() }) { Text("刷新", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DeviceListType.values().forEach { type ->
                                FilterChip(selected = state.deviceListType == type, onClick = {
                                    if (type == DeviceListType.Bluetooth) requestBluetooth() else state.onDeviceListTypeChange(type)
                                }, label = { Text(when(type) { DeviceListType.Bound -> "已添加"; DeviceListType.Bluetooth -> "蓝牙附近"; DeviceListType.Lan -> "局域网" }) })
                            }
                        }
                    }
                    if (state.deviceListUiState.deviceList.isEmpty()) item {
                        SoftCard {
                            Text(if (state.deviceListType == DeviceListType.Bound) "还没有设备" else "暂未发现设备", style = MaterialTheme.typography.titleMedium)
                            Text(if (state.deviceListType == DeviceListType.Bound) "从添加第一台设备开始。也可扫描设备二维码体验虚拟设备。" else "检查设备是否通电、手机权限与网络，然后重新发现。")
                        }
                    }
                    items(state.deviceListUiState.deviceList, key = { it.id }) { device -> DeviceCard(device, navigateToControl) }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    SoftAction("添加设备", ::addDevice)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: GizDevice, onClick: (String) -> Unit) {
    val name by device.subscribeProductName().collectAsState()
    val ble by device.bleCapability.subscribeStatus().collectAsState()
    val lan by device.lanCapability.subscribeStatus().collectAsState()
    val mqtt by device.mqttCapability.subscribeStatus().collectAsState()
    val controllable = listOf(ble, lan, mqtt).any { it == CapacityStatus.ONLINE_CONTROLLABLE }
    val discovered = listOf(ble, lan, mqtt).any { it == CapacityStatus.ONLINE }
    SoftCard(Modifier.clickable { onClick(device.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_device_24), null, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name.ifBlank { name ?: "未知设备" }, style = MaterialTheme.typography.titleMedium)
                Text(device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (controllable) "可控制" else if (discovered) "已发现" else "离线", style = MaterialTheme.typography.labelMedium)
        }
        Text(listOf("蓝牙" to ble, "局域网" to lan, "云端" to mqtt).joinToString("  ·  ") { "${it.first} ${if (it.second == CapacityStatus.ONLINE_CONTROLLABLE) "可控" else if (it.second == CapacityStatus.ONLINE) "已发现" else "离线"}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BleAdvertisementDetails(device)
    }
}
