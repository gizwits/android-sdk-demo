package com.gizwits.smart.app.feature.user

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
class UserViewModel @Inject constructor() : ViewModel() {

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
    suspend fun destoryAccount() {
        val data = GizSDKManager.getUserManager().destroyAccount();
        println(data)
    }
    suspend fun logout() {
        val data = GizSDKManager.getUserManager().logout();
        println(data)
    }

}