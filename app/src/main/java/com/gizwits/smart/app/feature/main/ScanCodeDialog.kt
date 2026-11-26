package com.gizwits.smart.app.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.core.setup.DemoEvents
import com.gizwits.smart.app.core.setup.DemoQrCode
import com.gizwits.smart.app.core.setup.allowsDemoProduct
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.mqtt.queryBoundDevices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ScanCodeDialogState(private val scope: CoroutineScope) {
    var isShow by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var needsLogin by mutableStateOf(false); private set
    fun hide() { if (!busy) isShow = false }
    fun scan(raw: String, loggedIn: Boolean, onProduct: () -> Unit) {
        if (busy) return
        needsLogin = false
        val code = DemoQrCode.parse(raw)
        if (code is DemoQrCode.Product) {
            isShow = false
            onProduct()
            return
        }
        isShow = true
        if (code !is DemoQrCode.Device) {
            message = "未识别到设备或产品二维码，请重新扫描。产品二维码需包含完整的 PK 和 PS。"
            return
        }
        if (!loggedIn) {
            needsLogin = true
            message = "添加设备需要先登录或匿名体验，完成后请重新扫描设备二维码。"
            return
        }
        if (!allowsDemoProduct(GizSDKManager.getConfiguration().productInfos, code.productKey)) {
            message = "该设备的产品尚未配置。请先扫描产品二维码录入 PK / PS，或在设置的产品管理中添加。"
            return
        }
        busy = true; message = "正在添加到当前身份…"
        scope.launch {
            try {
                val result = GizSDKManager.getUserManager().bindDeviceWithPasscode(code.deviceId, code.passcode)
                message = if (result.isSuccess) "设备已添加。返回设备列表即可查看。" else "设备添加失败，请检查登录状态或二维码后重试。"
                DemoEvents.record("扫码添加设备", message, result.isFailure)
                if (result.isSuccess) GizSDKManager.queryBoundDevices()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "设备添加失败，请检查网络后重试。" }
            finally { busy = false }
        }
    }
}

@Composable
fun ScanCodeDialog(state: ScanCodeDialogState, onScanAgain: () -> Unit, onLogin: () -> Unit) {
    if (state.isShow) BottomDialog(onDismissRequest = state::hide, dismissOnBackPress = !state.busy, dismissOnClickOutside = false) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("扫码结果", style = MaterialTheme.typography.titleLarge)
            Text(state.message)
            if (state.busy) CircularProgressIndicator()
            if (state.needsLogin) SoftAction("去登录 / 匿名体验", { state.hide(); onLogin() }, !state.busy)
            SoftAction("重新扫描", { state.hide(); onScanAgain() }, !state.busy, secondary = true)
            SoftAction("返回", state::hide, !state.busy)
        }
    }
}
