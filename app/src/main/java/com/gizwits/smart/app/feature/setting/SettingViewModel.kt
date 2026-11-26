package com.gizwits.smart.app.feature.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.app.core.datastore.ThemeMode
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.GizUserManager
import com.gizwits.smart.sdk.core.data.model.UserState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    val themeMode = appPreferencesRepository.themeMode

    suspend fun setThemeMode(mode: ThemeMode) = appPreferencesRepository.setThemeMode(mode)

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    val uiState: StateFlow<SettingUiState> = userManager.subscribeUserState()
        .map {
            SettingUiState(
                userState = it,
                logoutUser = ::logoutUser
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingUiState(
                userState = UserState.NotLoggedIn,
                logoutUser = ::logoutUser
            )
        )

    fun logoutUser() {
        viewModelScope.launch {
            // 重置应用的默认登录信息以及调用SDK注销接口
            appPreferencesRepository.resetDefaultLoginCredentials()
            userManager.logout()
        }
    }

}

data class SettingUiState(
    val userState: UserState,
    val logoutUser: () -> Unit
)
