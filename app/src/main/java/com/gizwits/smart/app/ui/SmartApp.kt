package com.gizwits.smart.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gizwits.smart.app.feature.setup.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import com.gizwits.smart.app.R
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.app.core.data.util.NetworkMonitor
import com.gizwits.smart.app.feature.changePassword.changePasswordScreen
import com.gizwits.smart.app.feature.changePassword.navigateToChangePassword
import com.gizwits.smart.app.feature.control.controlScreen
import com.gizwits.smart.app.feature.control.data.controlDataScreen
import com.gizwits.smart.app.feature.control.data.navigateToControlData
import com.gizwits.smart.app.feature.control.manage.controlManageScreen
import com.gizwits.smart.app.feature.control.manage.navigateToControlManage
import com.gizwits.smart.app.feature.control.manage.navigateToOTAScreen
import com.gizwits.smart.app.feature.control.manage.otaManagerScreen
import com.gizwits.smart.app.feature.control.navigateToControl
import com.gizwits.smart.app.feature.debug.debugScreen
import com.gizwits.smart.app.feature.debug.navigateToDebug
import com.gizwits.smart.app.feature.forgot.forgotScreen
import com.gizwits.smart.app.feature.login.loginRoute
import com.gizwits.smart.app.feature.login.loginScreen
import com.gizwits.smart.app.feature.login.navigateToLogin
import com.gizwits.smart.app.feature.main.mainRoute
import com.gizwits.smart.app.feature.main.mainScreen
import com.gizwits.smart.app.feature.main.navigateToMain
import com.gizwits.smart.app.feature.register.navigateToRegister
import com.gizwits.smart.app.feature.forgot.navigateToForgot
import com.gizwits.smart.app.feature.register.registerScreen
import com.gizwits.smart.app.feature.setting.navigateToSetting
import com.gizwits.smart.app.feature.setting.navigateToFeedback
import com.gizwits.smart.app.feature.setting.settingScreen
import com.gizwits.smart.app.feature.user.navigateToUser
import com.gizwits.smart.app.feature.user.userScreen
import com.gizwits.smart.app.ui.component.BottomDialog

@Composable
fun SmartApp(
    networkMonitor: NetworkMonitor,
    appPreferencesRepository: AppPreferencesRepository,
    appState: SmartAppState = rememberSmartAppState(
        networkMonitor = networkMonitor,
        appPreferencesRepository = appPreferencesRepository
    )
) {
    // 判断是否需要显示隐私服务条款与用户协议声明对话框，相关状态存储至应用首选项
    val isAgreePrivacyPolicy: Boolean? by appState.isAgreePrivacyPolicy.collectAsState()
    if (isAgreePrivacyPolicy?.not() == true) {
//        PrivacyPolicyStatementDialog(
//            onDismissRequest = appState.quitApp,
//            onAgreePrivacyPolicy = appState::agreePrivacyPolicy
//        )
    }

    com.gizwits.smart.app.update.AppUpdateHost()
    val setup: SetupViewModel = hiltViewModel()
    Box(modifier = Modifier.fillMaxSize()) {
        if (!setup.loaded) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (setup.error == null) CircularProgressIndicator()
                Text(setup.error ?: "正在读取接入配置…")
                if (setup.error != null) TextButton(onClick = setup::load) { Text("重试") }
            }
        } else key(setup.sessionGeneration) {
            SmartNavHost(navHostController = rememberNavController(), setup = setup,
                startDestination = if (setup.startAtDevices) mainRoute else setupRoute)
        }

        val isNetworkOffline: Boolean by appState.isOffline.collectAsState()
        var isIgnoreNetworkOfflineHint: Boolean by remember { mutableStateOf(false) }
        LaunchedEffect(isNetworkOffline) {
            isIgnoreNetworkOfflineHint = false
        }
        AnimatedVisibility(
            visible = isNetworkOffline && !isIgnoreNetworkOfflineHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = stringResource(id = R.string.network_lost_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    IconButton(
                        onClick = {
                            isIgnoreNetworkOfflineHint = true
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartNavHost(
    navHostController: NavHostController,
    setup: SetupViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = mainRoute
) {
    fun openSetup(page: SetupPage) {
        setup.enter(page)
        navHostController.navigate(setupRoute) { launchSingleTop = true }
    }
    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(setupRoute) {
            SetupScreen(setup,
                onDevices = { setup.openAddDevice = true; navHostController.navigate(mainRoute) { popUpTo(setupRoute) { inclusive = true }; launchSingleTop = true } },
                onRegister = navHostController::navigateToRegister,
                onForgot = navHostController::navigateToForgot,
                onOtherLogin = navHostController::navigateToLogin,
                onSettings = navHostController::navigateToSetting,
                canExitSetup = navHostController.previousBackStackEntry != null,
                onBack = { navHostController.popBackStack() })
        }
        // 应用登录页面
        loginScreen(
            onBackClick = navHostController::popBackStack,
            navigateToRegister = navHostController::navigateToRegister,
            navigateToForgot = navHostController::navigateToForgot,
            navigateToSetting = navHostController::navigateToSetting,
        )
        // 应用注册页面
        registerScreen(
            onBackClick = navHostController::popBackStack,
        )
        forgotScreen(
            onBackClick = navHostController::popBackStack,
        )
        // 应用主页面
        mainScreen(
            navigateToControl = navHostController::navigateToControl,
            navigateToSetting = navHostController::navigateToSetting,
            setup = setup,
            openSetup = ::openSetup
        )
        // 设备控制页面
        controlScreen(
            onBackClick = navHostController::popBackStack,
            navigateToControlData = navHostController::navigateToControlData,
            navigateToControlManage = navHostController::navigateToControlManage
        )
        // 设备控制数据页面
        controlDataScreen(
            onBackClick = navHostController::popBackStack
        )
        // 设备控制管理页面
        controlManageScreen(
            onBackClick = navHostController::popBackStack,
            navigateToOTAManage = navHostController::navigateToOTAScreen
        )
        otaManagerScreen(
            onBackClick = navHostController::popBackStack
        )
        // 应用设置页面
        settingScreen(
            onBackClick = navHostController::popBackStack,
            navigateToLogin = navHostController::navigateToLogin,
            navigateToUser = navHostController::navigateToUser,
            navigateToDebug = navHostController::navigateToDebug,
            navigateToFeedback = navHostController::navigateToFeedback,
            setup = setup,
            openSetup = ::openSetup
        )
        changePasswordScreen(
            onBackClick = navHostController::popBackStack,
        )
        // 用户管理页面
        userScreen(
            onBackClick = navHostController::popBackStack,
            navigateToChangePassword = navHostController::navigateToChangePassword,
        )
        // 应用调试页面
        debugScreen(
            onBackClick = navHostController::popBackStack
        )
    }
}

/**
 * 隐私服务条款与用户协议声明对话框
 * @param onDismissRequest 取消对话框回调
 * @param onAgreePrivacyPolicy 同意隐私条款回调
 */
@Composable
private fun PrivacyPolicyStatementDialog(
    onDismissRequest: () -> Unit,
    onAgreePrivacyPolicy: () -> Unit
) {
    BottomDialog(
        onDismissRequest = onDismissRequest,
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.privacy_policy_statement),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(id = R.string.privacy_policy_statement_description),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.cancel),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onAgreePrivacyPolicy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.agree)
                    )
                }
            }
        }
    }
}
