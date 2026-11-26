package com.gizwits.smart.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.app.core.data.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun rememberSmartAppState(
    networkMonitor: NetworkMonitor,
    appPreferencesRepository: AppPreferencesRepository,
    navHostController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): SmartAppState {
    val activity: Activity = LocalContext.current as Activity
    return remember(
        networkMonitor,
        appPreferencesRepository,
        navHostController
    ) {
        SmartAppState(
            navHostController = navHostController,
            coroutineScope = coroutineScope,
            networkMonitor =networkMonitor,
            appPreferencesRepository = appPreferencesRepository,
            quitApp = activity::finishAndRemoveTask
        )
    }
}

@Stable
class SmartAppState(
    val navHostController: NavHostController,
    val coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
    private val appPreferencesRepository: AppPreferencesRepository,
    val quitApp: () -> Unit
) {

    /**
     * 当前用户是否已同意隐私服务条款与用户协议的状态流，为null则代表配置未加载，默认为不显示
     */
    val isAgreePrivacyPolicy: StateFlow<Boolean?> = appPreferencesRepository
        .isAgreePrivacyPolicy
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * 当前应用是否联网状态
     */
    val isOffline = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * 同意隐私服务条款和用户协议
     */
    fun agreePrivacyPolicy() {
        coroutineScope.launch { appPreferencesRepository.agreePrivacyPolicy() }
    }

}