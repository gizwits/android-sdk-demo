package com.gizwits.smart.app.feature.control.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.setup.DemoEvents
import com.gizwits.smart.app.core.setup.DeviceSnapshots
import com.gizwits.smart.app.core.setup.mergeDeviceValues
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.common.DataPointType
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import com.gizwits.smart.sdk.lan.lanCapability
import com.gizwits.smart.sdk.mqtt.mqttCapability
import com.google.gson.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class ControlDataMode { Bluetooth, Lan, Mqtt }

@HiltViewModel
class ControlDataViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val mode = ControlDataMode.valueOf(checkNotNull(savedStateHandle["mode"]))
    private val device = checkNotNull(GizSDKManager.getRegisteredDevice(checkNotNull(savedStateHandle["deviceId"])))
    private val capability = when(mode) {
        ControlDataMode.Bluetooth -> device.bleCapability
        ControlDataMode.Lan -> device.lanCapability
        ControlDataMode.Mqtt -> device.mqttCapability
    }
    val status = capability.subscribeStatus()
    private val busy = MutableStateFlow(false)
    private val snapshotKey = "${device.id}:${mode.name}"
    private val values = MutableStateFlow(DeviceSnapshots.read(snapshotKey))
    var message by mutableStateOf("等待读取设备状态"); private set
    var lastReport by mutableStateOf<Long?>(null); private set
    val enabled = combine(status, busy) { status, busy -> status == CapacityStatus.ONLINE_CONTROLLABLE && !busy }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            capability.subscribeDp().collect { data ->
                updateValues(data)
                message = "设备已回报"
                DemoEvents.record("${mode.name} · 设备回报", "${device.mac} · 收到状态更新")
            }
        }
        viewModelScope.launch {
            status.collect { if (it == CapacityStatus.ONLINE_CONTROLLABLE) read() }
        }
    }
    private fun updateValues(data: JsonObject) {
        values.value = mergeDeviceValues(values.value, data)
        DeviceSnapshots.write(snapshotKey, values.value)
        lastReport = System.currentTimeMillis()
    }

    fun read() {
        if (status.value != CapacityStatus.ONLINE_CONTROLLABLE || busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val result = capability.getDp(null)
                result.onSuccess { updateValues(it); message = "状态读取成功" }
                    .onFailure { message = "读取失败，请检查通道连接后重试。" }
                DemoEvents.record("${mode.name} · 读取结果", "${device.mac} · $message", result.isFailure)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "读取失败，请重试。" }
            finally { busy.value = false }
        }
    }
    private fun send(point: Pair<String, JsonElement>) {
        if (status.value != CapacityStatus.ONLINE_CONTROLLABLE || busy.value) return
        busy.value = true
        message = "正在发送…"
        DemoEvents.record("${mode.name} · 发送请求", "${device.mac} · ${point.first}")
        viewModelScope.launch {
            try {
                val value: Any = if (point.second.isJsonArray) point.second.asJsonArray.map { it.asInt.toByte() }.toByteArray() else point.second
                val result = sendAndReadDataPoint(
                    write = { capability.sendDp(point.first to value) },
                    read = { capability.getDp(null) },
                    onSent = {
                        message = "请求已发送，正在读取设备状态…"
                        DemoEvents.record("${mode.name} · 接口结果", "${device.mac} · 请求已发送")
                    }
                )
                when {
                    result.write.isFailure -> {
                        message = "发送失败，请检查通道后重试。"
                        DemoEvents.record("${mode.name} · 接口结果", "${device.mac} · $message", true)
                    }
                    result.read?.isSuccess == true -> {
                        updateValues(result.read.getOrThrow())
                        message = "请求已发送，设备状态已刷新"
                        DemoEvents.record("${mode.name} · 读取结果", "${device.mac} · $message")
                    }
                    else -> {
                        message = "请求已发送，但状态读取失败，请点击“读取最新状态”重试。"
                        DemoEvents.record("${mode.name} · 读取结果", "${device.mac} · $message", true)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "发送失败，请重试。"; DemoEvents.record("发送异常", "${device.mac} · 操作未完成", true) }
            finally { busy.value = false }
        }
    }
    val uiState = device.subscribeDataPointConfig().map { config ->
        ControlDataUiState(config?.entities?.flatMap { it.attrs.toList() }?.distinctBy { it.name }?.sortedBy { it.id }?.mapNotNull { attr ->
            when(attr.dataType) {
                DataPointType.BOOLEAN -> BooleanDataPointState(attr, enabled, values, ::send)
                DataPointType.NUMBER -> NumberDataPointState(attr, enabled, values, ::send)
                DataPointType.ENUM -> EnumDataPointState(attr, enabled, values, ::send)
                DataPointType.BINARY -> BinaryDataPointState(attr, enabled, values, ::send)
                else -> null
            }
        } ?: emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ControlDataUiState(emptyList()))
}

data class ControlDataUiState(val dataPointStates: List<DataPointState<*>>)


internal data class DataPointWriteResult(
    val write: Result<Unit>,
    val read: Result<JsonObject>? = null
)

/** A write acknowledgement is not a device state. Read back the actual values. */
internal suspend fun sendAndReadDataPoint(
    write: suspend () -> Result<Unit>,
    read: suspend () -> Result<JsonObject>,
    onSent: () -> Unit
): DataPointWriteResult {
    val writeResult = write()
    if (writeResult.isFailure) return DataPointWriteResult(writeResult)
    onSent()
    val readResult = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
    return DataPointWriteResult(writeResult, readResult)
}
