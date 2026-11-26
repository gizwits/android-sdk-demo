package com.gizwits.smart.app.feature.control.ota

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.setup.DemoEvents
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.core.data.model.CheckUpdateResultInfo
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType
import com.gizwits.smart.sdk.ota.OTAProgressData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class OTAManageViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val device = checkNotNull(GizSDKManager.getRegisteredDevice(checkNotNull(savedStateHandle["deviceId"])))
    val otaType = if (savedStateHandle.get<String>("type") == "MCU") GizOTAFirmwareType.MCU else GizOTAFirmwareType.Module
    val status = device.bleCapability.subscribeStatus()
    val otaIsRunning = MutableStateFlow(false)
    val deviceUpdateInfo = MutableStateFlow(CheckUpdateResultInfo(softVersion = "", downloadUrl = "", hardVersion = ""))
    val deviceOTAFlow = MutableStateFlow<OTAProgressData?>(null)
    var message by mutableStateOf("连接蓝牙后检查更新"); private set
    var checking by mutableStateOf(false); private set
    private var upgradeJob: Job? = null
    private var attempt = 0
    init { viewModelScope.launch { status.collect { if (it == CapacityStatus.ONLINE_CONTROLLABLE && deviceUpdateInfo.value.softVersion.isBlank()) checkUpdate() } } }
    fun checkUpdate() {
        if (checking || otaIsRunning.value) return
        if (status.value != CapacityStatus.ONLINE_CONTROLLABLE) { message = "蓝牙当前不可控制，请先连接。"; return }
        checking = true
        viewModelScope.launch {
            try {
                device.bleCapability.checkUpdate(otaType).onSuccess {
                    deviceUpdateInfo.value = it
                    message = if (it.softVersion.isBlank() || it.softVersion == it.currentSoftVersion) "当前没有可用更新" else "发现可用更新"
                }.onFailure { message = "检查更新失败，请重试。" }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "检查更新失败，请重试。" }
            finally { checking = false }
        }
    }
    val startUpgrade: () -> Unit = {
        if (!otaIsRunning.value && status.value == CapacityStatus.ONLINE_CONTROLLABLE && deviceUpdateInfo.value.softVersion.isNotBlank()) {
            otaIsRunning.value = true
            val run = ++attempt
            upgradeJob = viewModelScope.launch {
                try {
                    val result = device.bleCapability.startUpgrade(otaType) { progress ->
                        if (run == attempt) deviceOTAFlow.value = progress
                    }
                    if (run == attempt) {
                        message = if (result.isSuccess) "固件升级已完成" else "固件升级未完成，请检查蓝牙连接后重试。"
                        DemoEvents.record("蓝牙固件升级", message, result.isFailure)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "升级失败，请检查设备状态。" }
                finally { if (run == attempt) otaIsRunning.value = false }
            }
        }
    }
    val cancelUpgrade: () -> Unit = {
        if (otaIsRunning.value) viewModelScope.launch {
            val result = device.bleCapability.cancelUpgrade()
            if (result.isSuccess) {
                ++attempt; upgradeJob?.cancel(); otaIsRunning.value = false
                message = "已取消升级，请确认设备状态。"
            } else message = "取消失败，仍在等待升级结果。"
        }
    }
}
