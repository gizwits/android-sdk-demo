package com.gizwits.smart.app.core.setup

import com.gizwits.smart.sdk.GizUserManager
import com.gizwits.smart.sdk.core.data.model.UserState
import kotlinx.coroutines.flow.first

/** Login updates memory before the asynchronous credential-store write completes. */
internal suspend fun awaitLoginUser(manager: GizUserManager): UserState.Logged {
    val expectedUser = requireNotNull(manager.getUserTokenStream().value?.userId) {
        "Login completed without an in-memory user identity"
    }
    return manager.subscribeUserState().first {
        it is UserState.Logged && it.userInfo.userId == expectedUser
    } as UserState.Logged
}
