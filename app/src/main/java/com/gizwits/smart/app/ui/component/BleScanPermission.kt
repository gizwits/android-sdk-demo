package com.gizwits.smart.app.ui.component

import android.Manifest
import com.gizwits.smart.sdk.exception.GizConnectException
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

internal enum class BleScanRequirement { Permission, BluetoothUnavailable, BluetoothOff, LocationOff }

internal fun bleScanErrorMessage(error: GizConnectException): String =
    if (error.errorCode == GizConnectException.GIZ_SDK_BLE_NO_PERMISSION.errorCode)
        "蓝牙扫描权限不足，请允许扫描所需权限后重新扫描。"
    else "系统未能启动蓝牙扫描，请稍后重试，或检查蓝牙与权限设置。"

internal fun bleScanRequirement(
    sdkVersion: Int,
    permissionsGranted: Boolean,
    bluetoothAvailable: Boolean,
    bluetoothEnabled: Boolean,
    locationEnabled: Boolean
): BleScanRequirement? = when {
    !bluetoothAvailable -> BleScanRequirement.BluetoothUnavailable
    !permissionsGranted -> BleScanRequirement.Permission
    !bluetoothEnabled -> BleScanRequirement.BluetoothOff
    sdkVersion < 31 && !locationEnabled -> BleScanRequirement.LocationOff
    else -> null
}

/** Every scan entry point rechecks permissions and system switches, including after Settings. */
@SuppressLint("MissingPermission")
@Composable
fun rememberBleScanRequest(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val ready by rememberUpdatedState(onReady)
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var requirement by remember { mutableStateOf<BleScanRequirement?>(null) }
    var denied by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    fun check() {
        val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val location = context.getSystemService(LocationManager::class.java)
        requirement = bleScanRequirement(
            Build.VERSION.SDK_INT, granted, adapter != null,
            granted && adapter?.isEnabled == true,
            Build.VERSION.SDK_INT >= 31 || (location != null && LocationManagerCompat.isLocationEnabled(location))
        )
        if (requirement == null) { denied = false; ready() }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Re-read all permissions: an empty/cancelled result must not count as authorization.
        denied = permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        check()
    }
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { check() }
    fun settings(intent: Intent) {
        try { openSettings.launch(intent) }
        catch (_: android.content.ActivityNotFoundException) { settingsError = true }
    }
    requirement?.let { issue ->
        val nearby = Build.VERSION.SDK_INT >= 31
        AlertDialog(
            onDismissRequest = { requirement = null },
            title = { Text(when (issue) {
                BleScanRequirement.Permission -> if (nearby) "需要附近设备权限" else "需要位置权限"
                BleScanRequirement.BluetoothUnavailable -> "此设备不支持蓝牙"
                BleScanRequirement.BluetoothOff -> "请开启蓝牙"
                BleScanRequirement.LocationOff -> "请开启系统定位"
            }) },
            text = { Text(if (settingsError) "无法打开系统设置，请手动开启相应权限或开关后重新扫描。" else when (issue) {
                BleScanRequirement.Permission -> (if (nearby) "扫描和连接附近的蓝牙设备需要附近设备权限。" else "Android 11 及以下系统需要位置权限和系统定位开关才能发现蓝牙设备。") +
                    if (denied) "权限尚未开启，请在应用设置的权限管理中允许后返回。" else "请授权后继续扫描。"
                BleScanRequirement.BluetoothUnavailable -> "可以使用局域网或 AirLink 添加设备。"
                BleScanRequirement.BluetoothOff -> "蓝牙开关已关闭，无法发现附近设备。请在系统设置中开启蓝牙，返回后将重新检查并扫描。"
                BleScanRequirement.LocationOff -> "位置权限已授权，但系统定位开关未开启。当前 Android 版本需要开启定位才能扫描蓝牙设备，开启后返回即可继续。"
            }) },
            confirmButton = {
                TextButton(onClick = {
                    settingsError = false
                    when (issue) {
                        BleScanRequirement.Permission -> if (denied) settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            else requestPermission.launch(permissions)
                        BleScanRequirement.BluetoothOff -> settings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        BleScanRequirement.LocationOff -> settings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        BleScanRequirement.BluetoothUnavailable -> requirement = null
                    }
                }) { Text(if (issue == BleScanRequirement.Permission && !denied) "允许权限" else if (issue == BleScanRequirement.BluetoothUnavailable) "知道了" else "前往设置") }
            },
            dismissButton = { TextButton(onClick = { requirement = null }) { Text("取消") } }
        )
    }
    return { settingsError = false; check() }
}
