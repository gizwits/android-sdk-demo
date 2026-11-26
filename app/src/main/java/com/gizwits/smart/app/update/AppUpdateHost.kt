package com.gizwits.smart.app.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.DialogProperties

@Composable
fun appUpdateViewModel(): AppUpdateViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)

@Composable
fun AppUpdateHost(model: AppUpdateViewModel = appUpdateViewModel()) {
    val context = LocalContext.current
    fun install() {
        val file = model.apk ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.appupdates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            model.installationHandled()
        } catch (_: Exception) { model.installError("无法打开系统安装器，请重试") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else model.installError("需要允许此应用安装更新；可再次点击更新重试")
    }
    LaunchedEffect(Unit) { model.check() }
    LaunchedEffect(model.installRequested) {
        if (model.installRequested) {
            if (context.packageManager.canRequestPackageInstalls()) install()
            else {
                model.installationHandled()
                try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                catch (_: Exception) { model.installError("无法打开安装权限设置，请在系统设置中允许安装未知应用") }
            }
        }
    }
    model.release?.let { release ->
        AlertDialog(onDismissRequest = model::dismiss,
            properties = DialogProperties(dismissOnBackPress = !release.required && !model.busy, dismissOnClickOutside = false),
            title = { Text("发现新版本 ${release.version}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(release.notes.ifBlank { "修复问题并改善使用体验。" })
                Text("安装包 ${(release.size / 1048576.0).let { "%.1f".format(it) }} MB")
                if (release.required) Text("当前版本已不再支持，请更新后继续使用。")
                model.progress?.let { LinearProgressIndicator(progress = it / 100f); Text("正在下载 $it%") }
                model.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !model.busy, onClick = { model.clearMessage(); model.download() }) { Text(if (model.busy) "正在下载…" else "立即更新") } },
            dismissButton = { if (!release.required) TextButton(enabled = !model.busy, onClick = model::dismiss) { Text("稍后再说") } })
    }
    if (model.release == null) model.message?.let { text ->
        AlertDialog(onDismissRequest = model::clearMessage, title = { Text("检查更新") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("知道了") } })
    }
}
