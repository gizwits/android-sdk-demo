package com.gizwits.smart.app.feature.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.GizSDKManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(onBackClick: () -> Unit) {
    var contact by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var uploadLog by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { SoftTopBar("问题反馈", onBack = onBackClick) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SoftHeading("描述遇到的问题", "请描述操作步骤、遇到的现象或建议，便于我们定位问题。")
            SoftCard {
                Text("联系方式（可选）", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(contact, { contact = it.take(100); message = null },
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting, singleLine = true,
                    placeholder = { Text("手机号、邮箱或其他联系方式") }, shape = RoundedCornerShape(11.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                Text("问题描述", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(content, { content = it.take(200); message = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), enabled = !submitting, minLines = 5,
                    placeholder = { Text("请描述操作步骤、遇到的现象或建议") }, shape = RoundedCornerShape(11.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                Text("${content.length}/200", Modifier.align(Alignment.End), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(uploadLog, { uploadLog = it; message = null }, enabled = !submitting)
                    Text("附带 SDK 日志", style = MaterialTheme.typography.bodyMedium)
                }
                Text("SDK 日志用于设备与网络问题诊断，仅在勾选后上传。请勿填写密码、产品密钥或令牌。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SoftAction(if (submitting) "正在提交…" else "提交反馈", {
                submitting = true
                message = null
                failed = false
                scope.launch {
                    try {
                        val result = GizSDKManager.feedback(uploadLog, contact.trim(), content.trim())
                        failed = result.isFailure
                        message = if (failed) "反馈提交失败，请重试。" else "反馈请求已提交。"
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true; message = "反馈提交失败，请重试。" }
                    finally { submitting = false }
                }
            }, enabled = !submitting && content.isNotBlank())
            message?.let { SoftMessage(it, error = failed) }
        }
    }
}
