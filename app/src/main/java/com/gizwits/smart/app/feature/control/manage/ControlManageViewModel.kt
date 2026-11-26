package com.gizwits.smart.app.feature.control.manage

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.gizwits.smart.app.core.setup.DemoEvents
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.R
import com.gizwits.smart.sdk.GizDevice
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.core.data.model.CheckUpdateResultInfo
import com.gizwits.smart.sdk.core.data.model.DeviceInfoData
import com.gizwits.smart.sdk.lan.lanCapability
import com.gizwits.smart.sdk.mqtt.mqttCapability
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ControlManageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    private val resource: Resources = context.resources

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val device: GizDevice = checkNotNull(GizSDKManager.getRegisteredDevice(deviceId))

    var hasMcuData = false
    var hasModuleData = false
    var hasDeviceInfo = false
    private val deviceUpdateMcuInfo: MutableStateFlow<CheckUpdateResultInfo?> = MutableStateFlow<CheckUpdateResultInfo?>(null)
        .apply {
            viewModelScope.launch {
                while (isActive && !this@ControlManageViewModel.hasMcuData) {
                    with(device.bleCapability) {
                        if (getStatus() == CapacityStatus.ONLINE_CONTROLLABLE) {
                            hasMcuData = true
                            checkUpdate(GizOTAFirmwareType.MCU).onSuccess {
                                this@apply.value = it
                                return@launch
                            }
                        }
                    }
                    delay(1_500)
                }
            }
        }
    private val deviceUpdateModuleInfo: MutableStateFlow<CheckUpdateResultInfo?> = MutableStateFlow<CheckUpdateResultInfo?>(null)
        .apply {
            viewModelScope.launch {
                while (isActive && !hasModuleData) {
                    with(device.bleCapability) {
                        if (getStatus() == CapacityStatus.ONLINE_CONTROLLABLE) {
                            // 查一次就可以了
                            hasModuleData = true
                            checkUpdate(GizOTAFirmwareType.Module).onSuccess {
                                this@apply.value = it
                                return@launch
                            }
                        }
                    }
                    delay(1_500)
                }
            }
        }

    private val deviceInfoState: MutableStateFlow<DeviceInfoData?> = MutableStateFlow<DeviceInfoData?>(null)
        .apply {
            if (device.mac.startsWith("virtual")) {
                // 如果当前设备是虚拟设备，则放弃请求设备信息
                return@apply
            }
            viewModelScope.launch {
                while (isActive && !hasDeviceInfo) {
                    with(device.bleCapability) {
                        if (getStatus() == CapacityStatus.ONLINE_CONTROLLABLE) {
                            getDeviceInfo().onSuccess {
                                this@apply.value = it
                                hasDeviceInfo = true
                                return@launch
                            }
                        }
                    }
                    with(device.lanCapability) {
                        if (getStatus() == CapacityStatus.ONLINE_CONTROLLABLE) {
                            getDeviceInfo().onSuccess {
                                this@apply.value = it
                                return@launch
                            }
                        }
                    }
                    with(device.mqttCapability) {
                        if (getStatus() == CapacityStatus.ONLINE_CONTROLLABLE) {
                            getDeviceInfo().onSuccess {
                                this@apply.value = it
                                return@launch
                            }
                        }
                    }
                    delay(1_500)
                }
            }
        }

    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var alias by mutableStateOf(device.name)
        private set
    var remark by mutableStateOf(device.remark)
        private set

    val hasBleProfile get() = device.bleCapability.getModuleProfile() != null
    fun syncTime() = operation("发送手机时间") { device.bleCapability.syncTime() }

    fun updateInfo(name: String, note: String) = operation("保存设备信息") {
        device.updateDeviceInfo(name.trim(), note.trim()).also {
            if (it.isSuccess) { alias = name.trim(); remark = note.trim() }
        }
    }
    private fun operation(title: String, block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = block()
                message = if (result.isSuccess) "$title 已完成" else "$title 未完成，请检查登录和连接后重试。"
                DemoEvents.record(title, message!!, result.isFailure)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "$title 失败，请重试。" }
            finally { busy = false }
        }
    }
    private val onUnbindDevice: () -> Unit = { operation("解除绑定") { device.unBind() } }
    val needsProductCredentials get() = GizSDKManager.getConfiguration().getProductWithProductKey(device.productKey)?.productSecret.isNullOrBlank()
    private val onBindDevice: () -> Unit = {
        if (needsProductCredentials) message = "绑定需要产品 PS，请在设置 → 产品管理中添加此设备的 PK / PS。PK：${device.productKey}"
        else operation("添加到当前身份") { device.bind(null, null) }
    }
    private val onRegisterDevice: () -> Unit = {
        if (needsProductCredentials) message = "注册设备需要产品 PS，请在设置 → 产品管理中补充。"
        else operation("注册设备") { device.register().map { Unit } }
    }


    val updateState: StateFlow<ControlManageDeviceUpdateState> = combine(
        deviceUpdateModuleInfo,
         deviceUpdateMcuInfo
    ) { moduleInfo, mcuInfo ->
        ControlManageDeviceUpdateState(
            newMcuVersion = mcuInfo?.softVersion,
            newModuleVersion = moduleInfo?.softVersion
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ControlManageDeviceUpdateState(
            newMcuVersion = null,
            newModuleVersion = null
        )
    )

    val uiState: StateFlow<ControlManageUiState> = combine(
        device.subscribeProductName(),
        device.subscribeDeviceId(),
        device.subscribeDevicePasscode(),
        deviceInfoState,
        device.subscribeBoundState()
    ) { productName, deviceId, passcode, deviceInfo, boundState ->
        ControlManageUiState(
            productName = productName ?: resource.getString(R.string.unknown_device),
            productKey = device.productKey,
            mac = device.mac,
            name = device.name,
            deviceId = deviceId,
            id = this.deviceId,
            devicePasscode = passcode,
            deviceInfo = deviceInfo,
            boundState = boundState,
            onUnbindDevice = onUnbindDevice,
            onBindDevice = onBindDevice,
            onRegisterDevice = onRegisterDevice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ControlManageUiState(
            productName = resource.getString(R.string.unknown_device),
            productKey = device.productKey,
            mac = device.mac,
            deviceId = null,
            devicePasscode = null,
            deviceInfo = null,
            boundState = false,
            onUnbindDevice = {},
            onBindDevice = {},
            name = device.name,
            onRegisterDevice = {},
            id= "",
        )
    )

}

data class ControlManageUiState(
    val id: String, // 计算出来的设备标识
    val productName: String,
    val name: String,
    val productKey: String,
    val mac: String,
    val deviceId: String?,
    val devicePasscode: String?,
    val deviceInfo: DeviceInfoData?,
    val boundState: Boolean,
    val onUnbindDevice: () -> Unit,
    val onBindDevice: () -> Unit,
    val onRegisterDevice: () -> Unit
)

data class ControlManageDeviceUpdateState(
    val newMcuVersion: String?,
    val newModuleVersion: String?
)