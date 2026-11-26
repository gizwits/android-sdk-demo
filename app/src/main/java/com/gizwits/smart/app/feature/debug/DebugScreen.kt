package com.gizwits.smart.app.feature.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.R
import com.gizwits.smart.app.core.setup.DemoEvents
import com.gizwits.smart.app.ui.component.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugRoute(onBackClick: () -> Unit) {
    val events by DemoEvents.events.collectAsState()
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    Scaffold(topBar = { SoftTopBar(title = "操作记录", onBack = onBackClick, actions = { TextButton(onClick = DemoEvents::clear) { Text("清空", color = MaterialTheme.colorScheme.onSurface) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SoftMessage("分别记录请求、接口结果与设备回报。仅保留本次会话的最近 100 条，不记录密码与产品密钥。") }
            if (events.isEmpty()) item { SoftCard { SoftHeading("还没有操作记录", "添加设备或发送控制后，可在这里核对结果。") } }
            items(events) { event ->
                SoftCard {
                    Text(time.format(Date(event.time)), style = MaterialTheme.typography.labelSmall)
                    Text(event.title, style = MaterialTheme.typography.titleMedium, color = if (event.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
