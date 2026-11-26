package com.gizwits.smart.app.feature.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.sdk.GizDevice
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.GizUserManager
import com.gizwits.smart.sdk.bluetooth.startBleScan
import com.gizwits.smart.sdk.bluetooth.subscribeBleScanError
import com.gizwits.smart.app.ui.component.bleScanErrorMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import com.gizwits.smart.sdk.bluetooth.subscribeBluetoothDeviceList
import com.gizwits.smart.sdk.common.combineLatest
import com.gizwits.smart.sdk.lan.subscribeLanDeviceList
import com.gizwits.smart.sdk.mqtt.queryBoundDevices
import com.gizwits.smart.sdk.mqtt.subscribeBoundDeviceList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeviceListType {
    Bound, Bluetooth, Lan
}

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    var error: String? by androidx.compose.runtime.mutableStateOf(null)
        private set

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    fun onRefreshList() {
        viewModelScope.launch {
            error = null
            try {
                GizSDKManager.queryBoundDevices()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = "刷新失败，请稍后重试。" }
        }
    }

    private val deviceListType: MutableStateFlow<DeviceListType> =
        MutableStateFlow(DeviceListType.Bound)

    private val deviceListUiState: Flow<DeviceListUiState> =
        combineLatest(
            deviceListType,
            GizSDKManager.subscribeBoundDeviceList(),
            GizSDKManager.subscribeBluetoothDeviceList(),
            GizSDKManager.subscribeLanDeviceList()
        ) { deviceListType, boundDeviceList, bluetoothDeviceList, lanDeviceList ->
            when (deviceListType) {
                DeviceListType.Bound -> DeviceListUiState.Bound(boundDeviceList)
                DeviceListType.Bluetooth -> DeviceListUiState.Bluetooth(bluetoothDeviceList)
                DeviceListType.Lan -> DeviceListUiState.Lan(lanDeviceList)
            }
        }

    val uiState: StateFlow<MainUiState> =
        combineLatest(
            deviceListType,
            deviceListUiState
        ) { listType, listUiState ->
            MainUiState(
                deviceListType = listType,
                onDeviceListTypeChange = {
                    deviceListType.value = it
                    if (it == DeviceListType.Bluetooth) {
                        error = null
                        try { GizSDKManager.startBleScan() } catch (_: Exception) { error = "无法扫描，请检查蓝牙和权限。" }
                    }
                 },
                deviceListUiState = listUiState,
                onRefreshList = { onRefreshList() }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainUiState(
                deviceListType = deviceListType.value,
                onDeviceListTypeChange = { deviceListType.value = it },
                deviceListUiState = DeviceListUiState.Bound(listOf()),
                onRefreshList = {}
            )
        )

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            GizSDKManager.subscribeBleScanError().collect { failure ->
                if (deviceListType.value == DeviceListType.Bluetooth) error = bleScanErrorMessage(failure)
            }
        }
    }

    val scanCodeDialogState: ScanCodeDialogState = ScanCodeDialogState(viewModelScope)

    val scanBleDeviceDialogState: ScanBleDeviceDialogState = ScanBleDeviceDialogState(viewModelScope)

    override fun onCleared() {
        scanBleDeviceDialogState.dispose()
        super.onCleared()
    }

}

abstract class DeviceListUiState(
    val deviceList: List<GizDevice>
) {

    class Bound(deviceList: List<GizDevice>) : DeviceListUiState(deviceList)

    class Bluetooth(deviceList: List<GizDevice>) : DeviceListUiState(deviceList)

    class Lan(deviceList: List<GizDevice>): DeviceListUiState(deviceList)

}

data class MainUiState(
    // 主页设备列表的类型：绑定设备列表、蓝牙设备列表
    val deviceListType: DeviceListType,
    // 修改主页设备列表类型的接口
    val onDeviceListTypeChange: (DeviceListType) -> Unit,
    // 主页设备列表的Ui状态
    val deviceListUiState: DeviceListUiState,

    val onRefreshList: () -> Unit
)



