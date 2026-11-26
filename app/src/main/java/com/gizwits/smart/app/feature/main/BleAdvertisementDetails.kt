package com.gizwits.smart.app.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gizwits.smart.sdk.GizDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads the SDK's public snapshot API without connecting to the device. */
@Composable
internal fun BleAdvertisementDetails(device: GizDevice) {
    val updates = remember(device) { device.subscribeBleAdvertisement() }
    val advertisement by updates.collectAsState(initial = device.bleAdvertisement)
    var expanded by remember(device) { mutableStateOf(false) }
    val snapshot = advertisement ?: return
    val clipboard = LocalClipboardManager.current

    TextButton(onClick = { expanded = true }) {
        Text("查看蓝牙广播 · ${snapshot.rawHex.length / 2} 字节")
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("蓝牙广播数据") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MAC：${device.mac}")
                    Text("来源：${snapshot.source}")
                    Text("接收时间：${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(snapshot.receivedAt))}")
                    Text("完整扫描记录 · ${snapshot.rawHex.length / 2} 字节")
                    SelectionContainer {
                        Text(snapshot.rawHex, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("内容变化时自动刷新，离线后保留最后一次数据。",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(snapshot.rawHex)) }) {
                        Text("复制 Hex")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(snapshot.toJsonObject().toString(2)))
                }) { Text("复制 JSON") }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text("关闭") }
            }
        )
    }
}
