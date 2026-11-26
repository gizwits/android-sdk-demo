package com.gizwits.smart.app.feature.login

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.sdk.GizSDKManager
import com.gizwits.smart.sdk.GizUserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    val isDefault: Boolean = checkNotNull(savedStateHandle["isDefault"])

    private val userNameLoginState: UserNameLoginState =
        UserNameLoginState(onLoginSuccess = ::onLoginSuccess)

    private val _loginUiState: MutableStateFlow<LoginUiState> =
        MutableStateFlow(LoginUiState.LoginByUserName(userNameLoginState))

    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private fun onLoginSuccess() {
        viewModelScope.launch {
            _loginUiState.update { LoginUiState.Logined }
        }
    }

}

@Stable
class UserNameLoginState(
    private val onLoginSuccess: () -> Unit
) {

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    var userName: String by mutableStateOf("")
    var code: String by mutableStateOf("")
    var type: String by mutableStateOf("账号密码登录")

    var userNameSuggestion: String? by mutableStateOf(null)

    var userPassword: String by mutableStateOf("")

    var isPasswordVisible: Boolean by mutableStateOf(false)

    var isAgreePrivacyPolicy: Boolean by mutableStateOf(false)

    var isEnabled: Boolean by mutableStateOf(true)

    suspend fun login(): Boolean {
        try {
            isEnabled = false
            userNameSuggestion = null

            if (type == "账号密码登录") {
                userManager.loginWithAccount(userName, userPassword)
                    .onSuccess {
                        onLoginSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
            } else {
                if (isValidEmail(userName)) {
                    userManager.loginWithEmail(userName, code).onSuccess {
                        onLoginSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
                } else {
                    userManager.loginWithMobile(phoneCode = "", userName, code).onSuccess {
                        onLoginSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
                }
            }


        } finally {
            isEnabled = true
        }
        return false
    }
    suspend fun getCode(): Boolean {
        try {
            if (isValidEmail(userName)) {
                userManager.sendEmailLoginVerifyCode(userName, lang = null).onSuccess {
                    return true
                }.onFailure {
                    return false
                }
            } else {
                userManager.sendMobileLoginVerifyCode("", userName, lang = null).onSuccess {
                    return true
                }.onFailure {
                    return false
                }
            }
        } finally {

        }

        return false
    }
    fun isValidEmail(email: String): Boolean {
        val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".toRegex()
        return emailRegex.matches(email)
    }
    suspend fun loginByAnonymous(): Boolean {
        try {
            isEnabled = false
            userManager.loginByAnonymous()
                .onSuccess {
                    onLoginSuccess()
                    return true
                }.onFailure {
                    userNameSuggestion = it.message
                    return false
                }
        } finally {
            isEnabled = true
        }
        return false
    }

}

interface LoginUiState {

    data class LoginByUserName(
        val loginState: UserNameLoginState
    ) : LoginUiState

    object Logined : LoginUiState

}


