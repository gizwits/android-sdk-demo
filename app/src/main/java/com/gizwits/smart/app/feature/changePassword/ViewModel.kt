package com.gizwits.smart.app.feature.changePassword

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.GizUserManager
import com.gizwits.smart.sdk.core.data.model.UserState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChangePasswordViewModel @Inject constructor() : ViewModel() {

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    val uiState: StateFlow<UserUiState> = userManager.subscribeUserState()
        .map {
            UserUiState(
                userState = it
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserUiState(
                userState = UserState.NotLoggedIn
            )
        )

}

data class UserUiState(
    val userState: UserState
) {
    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    var userName: String by mutableStateOf("")
    var code: String by mutableStateOf("")
    var type: String by mutableStateOf("账号密码登录")

    var userPassword: String by mutableStateOf("")
    var newUserPassword: String by mutableStateOf("")

    var isPasswordVisible: Boolean by mutableStateOf(false)
    var isNewPasswordVisible: Boolean by mutableStateOf(false)

    var isEnabled: Boolean by mutableStateOf(true)
    suspend fun changePassword():Boolean {
        userManager.changePasswordWithOldPassword(userPassword, newUserPassword).onSuccess {
            return true
        }.onFailure {
            return false
        }
        return false;
    }
}