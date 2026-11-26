package com.gizwits.smart.app.feature.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.core.datastore.ThemeMode
import com.gizwits.smart.app.feature.setup.*
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.core.data.model.UserState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingRoute(onBackClick: () -> Unit, navigateToLogin: () -> Unit, navigateToUser: () -> Unit,
                 navigateToDebug: () -> Unit, navigateToFeedback: () -> Unit, setup: SetupViewModel, openSetup: (SetupPage) -> Unit,
                 viewModel: SettingViewModel = hiltViewModel()) {
    val updates = com.gizwits.smart.app.update.appUpdateViewModel()
    val user by setup.userState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Scaffold(topBar = { SoftTopBar("接入设置", onBack = onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("产品管理", style = MaterialTheme.typography.titleMedium)
                        Text("${setup.configuration.products.size} 个产品已添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_application_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                SoftAction("＋  添加产品 · PK", { openSetup(SetupPage.Product) }, !busy)
                if (setup.configuration.products.isNotEmpty()) SoftSettingRow("查看已添加产品", "扫码或手动管理 PK / PS") { openSetup(SetupPage.Product) }
            }
            SoftSectionLabel("外观")
            SoftCard {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.values().forEach { mode ->
                        Surface(Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                            color = if (themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Box(Modifier.heightIn(min = 48.dp).selectable(selected = themeMode == mode, enabled = themeMode != null,
                                role = Role.RadioButton, onClick = {
                                    scope.launch {
                                        try { viewModel.setThemeMode(mode) }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { message = "外观设置未能保存，请重试。" }
                                    }
                                }), contentAlignment = Alignment.Center) {
                                Text(mode.label, style = MaterialTheme.typography.labelMedium,
                                    color = if (themeMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            SoftSectionLabel("当前身份")
            SoftCard {
                val identity = when (val current = user) {
                    is UserState.Logged -> if (current.userInfo.isAnonymous) "匿名体验中" else current.userInfo.userName ?: "账号已登录"
                    is UserState.LoggingIn -> "正在确认登录状态…"
                    else -> "尚未登录"
                }
                SoftSettingRow(identity, "设备默认添加到当前身份") {
                    if (user is UserState.Logged) navigateToUser() else openSetup(SetupPage.Account)
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                SoftSettingRow("登录 / 切换身份") { openSetup(SetupPage.Account) }
            }
            SoftSectionLabel("应用配置")
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("应用身份", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (setup.configuration.usesDefaultApp) "Super App ID" else "自定义应用")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("服务区域", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when (setup.configuration.server) { "usapi.gizwits.com" -> "美国区"; "euapi.gizwits.com" -> "欧洲区"; else -> "中国区" })
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                SoftSettingRow("更换应用", "切换后需重新登录") { openSetup(SetupPage.Application) }
            }
            SoftSectionLabel("支持与维护")
            SoftCard {
                SoftSettingRow(if (updates.busy) "正在检查更新…" else "检查更新", "当前版本 ${updates.currentVersion}") { updates.check(manual = true) }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                SoftSettingRow("操作记录", "查看连接、控制与设备回报", navigateToDebug)
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                SoftSettingRow("问题反馈", "描述问题，可选择附带 SDK 日志", navigateToFeedback)
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                SoftSettingRow(if (busy) "正在清理…" else "清理 SDK 缓存", "管理本地缓存数据") {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try { message = if (GizSDKManager.cleanCache().isSuccess) "SDK 缓存已清理" else "清理失败，请重试。" }
                            finally { busy = false }
                        }
                    }
                }
            }
            message?.let { SoftMessage(it) }
        }
    }
}
