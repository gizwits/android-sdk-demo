package com.gizwits.smart.app.feature.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.core.data.model.UserState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoute(
    onBackClick: () -> Unit,
    navigateToChangePassword: () -> Unit,
    viewModel: UserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = (uiState.userState as? UserState.Logged)?.userInfo
    val anonymous = profile?.isAnonymous == true
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = busy) {}

    Scaffold(topBar = { SoftTopBar("当前身份", onBack = { if (!busy) onBackClick() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SoftHeading(if (anonymous) "匿名身份" else "账号资料",
                if (anonymous) "无需注册，设备默认添加到当前匿名身份。" else "查看当前登录身份与账号信息。")
            if (profile == null) {
                SoftMessage("当前尚未登录，请返回设置选择登录方式。")
            } else {
                SoftCard(featured = true) {
                    Text(if (anonymous) "匿名体验中" else profile.userName ?: "已登录账号",
                        style = MaterialTheme.typography.titleMedium)
                    Text("UID · ${profile.userId.take(6)}…${profile.userId.takeLast(4)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("设备将绑定到此身份", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (anonymous) {
                    Text("匿名身份也可以绑定设备。退出后，其他账号不会自动继承这些设备。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SoftSectionLabel("账号资料")
                    SoftCard {
                        ProfileValue("昵称", profile.name)
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileValue("邮箱", profile.email)
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileValue("手机号", profile.phone)
                    }
                    SoftSectionLabel("账号安全")
                    SoftCard {
                        SoftSettingRow("修改密码", "使用当前密码设置新密码") {
                            if (!busy) navigateToChangePassword()
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(onClick = { confirmation = "destroy" }, enabled = !busy) {
                            Text("删除账号", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                SoftAction(if (busy) "正在处理…" else "退出当前身份", { confirmation = "logout" }, !busy, true)
            }
            message?.let { SoftMessage(it, true) }
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(if (action == "destroy") "删除账号？" else "退出当前身份？") },
            text = { Text(if (action == "destroy") "删除后无法恢复，请确认不再使用此账号。"
                else "退出后可以重新登录。已绑定设备保留在当前身份下。") },
            dismissButton = { TextButton(onClick = { confirmation = null }, enabled = !busy) { Text("取消") } },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            val manager = GizSDKManager.getUserManager()
                            if (action == "destroy") manager.destroyAccount().getOrThrow()
                            else manager.logout().getOrThrow()
                            confirmation = null
                            onBackClick()
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { message = if (action == "destroy") "账号未能删除，请重试。" else "退出未完成，请重试。"; confirmation = null }
                        finally { busy = false }
                    }
                }) { Text(if (action == "destroy") "删除账号" else "退出", color = MaterialTheme.colorScheme.error) }
            })
    }
}

@Composable
private fun ProfileValue(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "未设置", style = MaterialTheme.typography.bodyMedium)
    }
}
