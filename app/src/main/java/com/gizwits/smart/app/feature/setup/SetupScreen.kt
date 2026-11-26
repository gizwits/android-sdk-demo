package com.gizwits.smart.app.feature.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.R
import com.gizwits.smart.app.core.setup.DemoConfigurationStore
import com.gizwits.smart.app.core.setup.DemoProduct
import com.gizwits.smart.app.core.setup.ProductCredentials
import com.gizwits.smart.app.ui.component.*
import com.gizwits.smart.sdk.core.data.model.UserState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val setupRoute = "setup"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: SetupViewModel, onDevices: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit, onOtherLogin: () -> Unit, onSettings: () -> Unit, canExitSetup: Boolean, onBack: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    var removingProduct by remember { mutableStateOf<DemoProduct?>(null) }
    val user by vm.userState.collectAsState()
    LaunchedEffect(vm.loaded, vm.configuration.server, vm.configuration.products.map { it.productKey }, vm.page) {
        if (vm.page == SetupPage.Product) coroutineScope {
            vm.configuration.products.forEach { product -> launch { vm.loadProductName(product.productKey) } }
        }
    }
    LaunchedEffect(vm.loaded, vm.configuration.server, vm.productKey.trim().lowercase(), vm.page) {
        if (vm.page == SetupPage.ManualProduct || vm.page == SetupPage.ConfirmProduct) vm.loadProductName(vm.productKey)
    }
    val canGoBack = scanning || vm.canGoBack || canExitSetup
    val goBack: () -> Unit = {
        if (!vm.busy) {
            if (scanning) scanning = false
            else if (vm.canGoBack) vm.goBack()
            else if (canExitSetup) onBack()
        }
    }
    BackHandler(enabled = canGoBack || vm.busy, onBack = goBack)
    removingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { removingProduct = null },
            title = { Text("删除产品？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.productName(product.productKey) ?: "已添加产品")
                    Text("PK · ${product.productKey}")
                    Text("仅删除本机产品配置，不会解绑已添加的设备。删除最后一个产品后，将发现所有产品的设备。")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.removeProduct(product.productKey); removingProduct = null }, enabled = !vm.busy) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removingProduct = null }) { Text("取消") } }
        )
    }
    Scaffold(topBar = {
        SoftTopBar(title = if (scanning) "扫描产品二维码" else when (vm.page) {
            SetupPage.Product -> "产品管理"
            SetupPage.ManualProduct -> "手动录入"
            SetupPage.Application -> "应用身份"
            SetupPage.Account -> "登录方式"
            SetupPage.ConfirmProduct -> "确认产品"
            else -> "开始接入"
        }, onBack = if (canGoBack) goBack else null, actions = {
            if (vm.isGuidedSetup && vm.page == SetupPage.Welcome && !scanning) {
                IconButton(onClick = onSettings, enabled = !vm.busy) {
                    Icon(painterResource(R.drawable.ic_setting_24), "设置")
                }
            }
        })
    }, bottomBar = {
        if (vm.busy || vm.error != null) {
            Surface {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                        Text(vm.operationMessage ?: "正在保存，请稍候…")
                    }
                    vm.error?.let { SoftMessage(it, true) }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (scanning) {
                SoftHeading("对准产品二维码", "在开发者中心打开对应产品的二维码，识别后确认 PK 和 PS。")
                QrScanner(onResult = { scanning = false; vm.scanProduct(it) }, onCancel = { scanning = false; vm.show(SetupPage.ManualProduct) })
            } else {
                if (vm.isGuidedSetup && vm.page != SetupPage.Welcome && vm.page != SetupPage.ManualProduct && vm.page != SetupPage.ConfirmProduct) SetupProgress(when(vm.page) {
                    SetupPage.Welcome, SetupPage.Application -> 1
                    SetupPage.Product, SetupPage.ManualProduct, SetupPage.ConfirmProduct -> 2
                    SetupPage.Account -> 3
                    SetupPage.Ready -> 4
                }, enabled = !vm.busy, onSelect = vm::selectGuideStep)
                when (vm.page) {
                    SetupPage.Welcome -> {
                        SetupWelcome(vm.configuration.products.size, user is UserState.Logged,
                            enabled = !vm.busy, onSelect = vm::selectGuideStep)
                    }
                    SetupPage.Application -> {
                        SoftHeading("应用已经准备好。", "默认使用 Super App ID，也可以填写自己的应用配置。")
                        SoftCard(featured = true) {
                            Text("Super App ID", style = MaterialTheme.typography.titleLarge)
                            Text("内置 App ID 与配套配置，无需填写。")
                            SoftAction("跳过，使用默认应用", { vm.saveApplication(true) }, !vm.busy)
                        }
                        SoftCard {
                            Text("使用自己的应用 · 可选")
                            CredentialField("App ID", vm.appId, { vm.appId = it }, enabled = !vm.busy)
                            CredentialField("App Secret", vm.appSecret, { vm.appSecret = it }, secret = true, enabled = !vm.busy)
                            Text("App ID 和 App Secret 各为 32 位字母或数字。", style = MaterialTheme.typography.bodySmall)
                            Text("服务区域")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("中国", "美国", "欧洲").forEachIndexed { i, label ->
                                    FilterChip(selected = vm.server == DemoConfigurationStore.SERVERS[i],
                                        onClick = { vm.server = DemoConfigurationStore.SERVERS[i] }, label = { Text(label) }, enabled = !vm.busy)
                                }
                            }
                            SoftAction("保存并继续", { vm.saveApplication(false) }, !vm.busy && ProductCredentials.valid(vm.appId) && ProductCredentials.valid(vm.appSecret), true)
                        }
                        Text("变更应用配置后需要重新登录，当前连接将重建。", style = MaterialTheme.typography.bodySmall)
                    }
                    SetupPage.Product -> {
                        if (vm.configuration.products.isNotEmpty()) {
                            SoftCard {
                                Text("已添加 ${vm.configuration.products.size} 个产品", style = MaterialTheme.typography.titleLarge)
                                vm.configuration.products.forEach { product ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(vm.productName(product.productKey) ?: "产品名称未获取", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                            TextButton(onClick = { removingProduct = product }, enabled = !vm.busy) {
                                                Text("删除", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Text("PK · ${product.productKey}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (vm.productName(product.productKey) == null) Text(vm.productNameMessage(product.productKey), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        SoftHeading(if (vm.configuration.products.isEmpty()) "添加第一个产品" else "添加产品 · PK", "添加产品可限定设备发现范围，也可以跳过，发现所有产品的设备。")
                        SoftCard(featured = true) {
                            Surface(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).size(56.dp)
                                .softShadow(18.dp, 12.dp, 7.dp, androidx.compose.ui.graphics.Color(0x24916519)),
                                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary) {
                                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_scan_24), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                            }
                            Text("扫描产品二维码", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.titleMedium)
                            Text("自动读取 Product Key 和 Product Secret", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SoftAction("打开相机扫描", { vm.clearError(); scanning = true }, !vm.busy)
                        }
                        SoftAction("手动录入", { vm.show(SetupPage.ManualProduct) }, !vm.busy, true)
                        if (vm.isGuidedSetup) {
                            SoftAction(if (vm.configuration.products.isEmpty()) "跳过，选择登录方式" else "下一步，选择登录方式", vm::continueWithProducts, !vm.busy)
                        }
                    }
                    SetupPage.ManualProduct -> {
                        SoftHeading("手动录入产品", "从开发者中心复制 PK 和 PS，填写后核对保存。")
                        CredentialField("Product Key · PK", vm.productKey, { vm.productKey = it }, enabled = !vm.busy)
                        Text(vm.productName(vm.productKey)?.let { "产品名称：$it" } ?: vm.productNameMessage(vm.productKey), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CredentialField("Product Secret · PS", vm.productSecret, { vm.productSecret = it }, true, !vm.busy)
                        Text("PK 和 PS 各为 32 位字母或数字，可从开发者中心复制。", style = MaterialTheme.typography.bodySmall)
                        SoftAction("核对产品信息", vm::confirmProduct,
                            !vm.busy && ProductCredentials.valid(vm.productKey) && ProductCredentials.valid(vm.productSecret))
                        if (vm.configuration.products.isNotEmpty()) {
                            Text("新增或更新产品后立即生效，保留当前登录身份。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SetupPage.ConfirmProduct -> {
                        SoftHeading("确认产品信息", "确认后保存到当前应用；PS 默认隐藏。")
                        SoftCard {
                            Text(vm.productName(vm.productKey) ?: vm.productNameMessage(vm.productKey), style = MaterialTheme.typography.titleMedium)
                            CredentialField("Product Key · PK", vm.productKey, {}, enabled = false)
                            CredentialField("Product Secret · PS", vm.productSecret, {}, true, false)
                            Text("当前应用：${if (vm.configuration.usesDefaultApp) "Super App ID" else "自定义应用"}")
                        }
                        SoftAction(if (vm.isGuidedSetup) "确认并继续" else "保存产品", vm::saveProduct, !vm.busy)
                        SoftAction("返回修改 / 重新扫描", vm::goBack, !vm.busy, true)
                    }
                    SetupPage.Account -> {
                        SoftHeading("选一种方式继续。", "可以登录已有账号，也可以先匿名体验。")
                        if (user is UserState.Logged) {
                            SoftAction("使用当前身份继续", { vm.show(SetupPage.Ready) }, !vm.busy)
                        }
                        SoftCard {
                            Text("先匿名体验", style = MaterialTheme.typography.titleLarge)
                            Text("无需注册。设备默认添加到当前匿名身份。")
                            SoftAction(if (vm.loginInProgress == true) "正在匿名登录…" else "跳过，匿名体验",
                                { vm.login(true) }, !vm.busy, loading = vm.loginInProgress == true)
                        }
                        SoftCard {
                            Text("使用已有账号")
                            CredentialField("手机号 / 邮箱 / 用户名", vm.account, { vm.account = it }, enabled = !vm.busy)
                            CredentialField("密码", vm.password, { vm.password = it }, true, !vm.busy)
                            SoftAction(if (vm.loginInProgress == false) "正在登录…" else "登录并继续", { vm.login(false) },
                                !vm.busy && vm.account.isNotBlank() && vm.password.isNotBlank(), true, loading = vm.loginInProgress == false)
                            TextButton(onClick = onOtherLogin, enabled = !vm.busy) { Text("更多登录方式 · 手机 / 邮箱验证码", color = MaterialTheme.colorScheme.onSurface) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = onRegister, enabled = !vm.busy) { Text("注册账号", color = MaterialTheme.colorScheme.onSurface) }
                                TextButton(onClick = onForgot, enabled = !vm.busy) { Text("忘记密码", color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                    SetupPage.Ready -> {
                        SoftHeading("准备好了，\n连接第一台设备。", "让设备进入配网模式，保持手机靠近。")
                        SoftCard {
                            Text("应用：${if (vm.configuration.usesDefaultApp) "Super App ID" else "自定义应用"}")
                            Text(if (vm.configuration.products.isEmpty()) "产品：不限产品" else "产品：${vm.configuration.products.size} 个已添加")
                            Text("身份：${if ((user as? UserState.Logged)?.userInfo?.isAnonymous == true) "匿名体验" else if (user is UserState.Logged) "账号已登录" else "等待登录"}")
                        }
                        SoftAction("开始添加设备", { vm.completeOnboarding(onDevices) }, !vm.busy && user is UserState.Logged)
                        if (vm.configuration.products.isEmpty()) SoftAction("添加产品（可选）", { vm.selectGuideStep(1) }, secondary = true)
                        if (user !is UserState.Logged) SoftAction("返回登录", { vm.show(SetupPage.Account) }, secondary = true)
                    }
                }
            }
            vm.notice?.let { SoftMessage(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialField(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            shape = RoundedCornerShape(11.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (secret) {{ IconButton(onClick = { visible = !visible }) {
                Icon(painterResource(if (visible) R.drawable.ic_preview_close_24 else R.drawable.ic_preview_open_24), if (visible) "隐藏" else "显示", Modifier.size(20.dp))
            } }} else null)
    }

}

@Composable
fun SetupProgress(current: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .3f
    Box(Modifier.fillMaxWidth()) {
        Divider(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("应用", "产品", "账号").forEachIndexed { index, title ->
                val done = index + 1 < current
                val active = index + 1 == current
                Column(Modifier.selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) }).heightIn(min = 44.dp).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val fill = when {
                        active -> colors.primary
                        done -> if (dark) androidx.compose.ui.graphics.Color(0xFF304137) else androidx.compose.ui.graphics.Color(0xFFE2EADF)
                        else -> colors.background
                    }
                    Surface(Modifier.size(25.dp), shape = CircleShape, color = fill,
                        border = if (done || active) null else androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (done) "✓" else "${index + 1}", style = MaterialTheme.typography.labelSmall,
                                color = if (active) colors.onPrimary else if (done) (if (dark) androidx.compose.ui.graphics.Color(0xFFADD1B2) else androidx.compose.ui.graphics.Color(0xFF38714B)) else colors.onSurfaceVariant)
                        }
                    }
                    Text(title, style = MaterialTheme.typography.labelSmall, color = if (active) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SetupWelcome(productCount: Int, hasAccount: Boolean, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val ready = hasAccount
    val nextStep = if (productCount == 0) 1 else 2
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            SoftHeading("让设备，\n连接起来。", "从添加产品到控制设备，\n一步步体验 SDK 的完整能力。")
        }
        Image(painterResource(R.drawable.demo_logo), null, Modifier.size(82.dp))
    }
    SoftCard(featured = true) {
        Text(if (ready) "准备就绪" else "只需 3 步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetupProgress(if (ready) 4 else nextStep + 1, enabled = enabled, onSelect = onSelect)
        Text("配置接入信息", style = MaterialTheme.typography.titleLarge)
        Text("配置应用、选择账号，产品可稍后添加。\n点击步骤可随时返回修改。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SoftAction(if (ready) "查看接入配置" else "继续引导", { onSelect(if (ready) 0 else nextStep) }, enabled)
    }
}
