package com.gizwits.smart.app.feature.control

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.R
import com.gizwits.smart.sdk.GizDevice
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.bluetooth.bleCapability
import com.gizwits.smart.sdk.lan.lanCapability
import com.gizwits.smart.sdk.light.MusicalRhythmParseMode
import com.gizwits.smart.sdk.mqtt.mqttCapability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ControlViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    private val resource: Resources = context.resources

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val device: GizDevice = checkNotNull(GizSDKManager.getRegisteredDevice(deviceId))

    val uiState: StateFlow<ControlUiState> =device
        .subscribeProductName()
        .map{ productName ->
            ControlUiState(
                title = productName ?: resource.getString(R.string.unknown_device),
                device = device
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ControlUiState(
                title = resource.getString(R.string.unknown_device),
                device = device
            )
        )

    init {
        listOf(device.bleCapability, device.lanCapability, device.mqttCapability).forEach { capability ->
            viewModelScope.launch {
                try { capability.connect() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* The screen exposes the resulting channel state. */ }
            }
        }
    }

}

data class ControlUiState(
    val title: String,
    val device: GizDevice
)