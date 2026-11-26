package com.gizwits.smart.app.feature.forgot

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel(){
    private val userNameForgotState: UserNameForgotState =
        UserNameForgotState(onSuccess = ::onSuccess)

    private val _uiState: MutableStateFlow<ForgotUiState> =
        MutableStateFlow(ForgotUiState.ForgotByUserName(userNameForgotState))

    val uiState: StateFlow<ForgotUiState> = _uiState.asStateFlow()

    private fun onSuccess() {
        viewModelScope.launch {
//            _registerUiState.update {  }
        }
    }
}


@Stable
class UserNameForgotState(
    private val onSuccess: () -> Unit
) {

    private val userManager: GizUserManager = GizSDKManager.getUserManager()

    var userName: String by mutableStateOf("")

    var userNameSuggestion: String? by mutableStateOf(null)

    var userPassword: String by mutableStateOf("")
    var code: String by mutableStateOf("")

    var isPasswordVisible: Boolean by mutableStateOf(false)

    var isEnabled: Boolean by mutableStateOf(true)
    suspend fun getCode(): Boolean {
        try {
            if (isValidEmail(userName)) {
                userManager.sendEmailForgotPasswordVerifyCode(userName, lang = null).onSuccess {
                    return true
                }.onFailure {
                    return false
                }
            } else {
                userManager.sendMobileForgotPasswordVerifyCode("", userName, lang = null).onSuccess {
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

            if (isValidEmail(userName)) {
                userManager.forgotWithEmail(userName,code, userPassword)
                    .onSuccess {
                        onSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
            } else {
                userManager.forgotWithMobile("",userName, code, userPassword)
                    .onSuccess {
                        onSuccess()
                        return true
                    }.onFailure {
                        userNameSuggestion = it.message
                        return false
                    }
            }

        } finally {
            isEnabled = true
        }
        return false
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".toRegex()
        return emailRegex.matches(email)
    }

}

interface ForgotUiState {
    data class ForgotByUserName(
        val state: UserNameForgotState
    ) : ForgotUiState
}
