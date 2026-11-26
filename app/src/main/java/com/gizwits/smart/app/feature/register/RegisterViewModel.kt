package com.gizwits.smart.app.feature.register

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.app.feature.login.LoginUiState
import com.gizwits.smart.app.feature.login.UserNameLoginState
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
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel(){
    private val userNameRegisterState: UserNameRegisterState =
        UserNameRegisterState(onRegisterSuccess = ::onRegisterSuccess)

    private val _registerUiState: MutableStateFlow<RegisterUiState> =
        MutableStateFlow(RegisterUiState.RegisterByUserName(userNameRegisterState))

    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    private fun onRegisterSuccess() {
        viewModelScope.launch {
//            _registerUiState.update {  }
        }
    }
}


@Stable
class UserNameRegisterState(
    private val onRegisterSuccess: () -> Unit
) {

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    var userName: String by mutableStateOf("")

    var type: String by mutableStateOf("账号注册")
    var code: String by mutableStateOf("")

    var userNameSuggestion: String? by mutableStateOf(null)

    var userPassword: String by mutableStateOf("")

    var isPasswordVisible: Boolean by mutableStateOf(false)

    var isEnabled: Boolean by mutableStateOf(true)

    fun isValidEmail(email: String): Boolean {
        val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".toRegex()
        return emailRegex.matches(email)
    }
    suspend fun getCode(): Boolean {
        try {
            if (isValidEmail(userName)) {
                userManager.sendEmailRegisterVerifyCode(userName, lang = null).onSuccess {
                    return true
                }.onFailure {
                    return false
                }
            } else {
                userManager.sendMobileRegisterVerifyCode("", userName, lang = null).onSuccess {
                    return true
                }.onFailure {
                    return false
                }
            }
        } finally {

        }

        return false
    }
    suspend fun registerWithAccount(): Boolean {
        try {
            isEnabled = false
            userNameSuggestion = null
            if (type == "账号注册") {
                userManager.registerWithAccount(userName, userPassword)
                    .onSuccess {
                        onRegisterSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
            } else {
                if (isValidEmail(userName)) {
                    userManager.registerWithEmail(userName, code, userPassword)
                        .onSuccess {
                            onRegisterSuccess()
                            return true
                        }.onFailure {
                            userNameSuggestion = it.message
                            return false
                        }
                } else {
                    userManager.registerWithMobile("", userName, code, userPassword)
                        .onSuccess {
                            onRegisterSuccess()
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

}

interface RegisterUiState {
    data class RegisterByUserName(
        val registerState: UserNameRegisterState
    ) : RegisterUiState
}
