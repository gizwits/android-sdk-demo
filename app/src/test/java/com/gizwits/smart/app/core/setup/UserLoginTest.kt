package com.gizwits.smart.app.core.setup

import com.gizwits.smart.sdk.GizUserManager
import com.gizwits.smart.sdk.core.data.model.UserInfo
import com.gizwits.smart.sdk.core.data.model.UserState
import com.gizwits.smart.sdk.core.data.model.UserToken
import java.lang.reflect.Proxy
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class UserLoginTest {
    private fun logged(uid: String) = UserState.Logged(UserInfo(uid, true,
        null, null, null, null, null, null, null, null, null))

    private fun manager(token: UserToken?, persisted: UserToken?, vararg states: UserState): GizUserManager =
        Proxy.newProxyInstance(GizUserManager::class.java.classLoader, arrayOf(GizUserManager::class.java)) { _, method, _ ->
            when (method.name) {
                "getUserTokenStream" -> MutableStateFlow(token)
                "getAuthorizeData" -> persisted
                "subscribeUserState" -> flowOf(*states)
                else -> error("Unexpected call: ${method.name}")
            }
        } as GizUserManager

    @Test fun loginCompletesBeforeCredentialsReachDisk() = runBlocking {
        val current = UserToken("test-token", "new-user", Date())
        val user = withTimeout(1_000) {
            awaitLoginUser(manager(current, null, UserState.LoggingIn, logged("new-user")))
        }
        assertEquals("new-user", user.userInfo.userId)
    }

    @Test fun ignoresOldPersistedIdentityAndOldUserState() = runBlocking {
        val current = UserToken("test-new", "new-user", Date())
        val old = UserToken("test-old", "old-user", Date())
        val user = withTimeout(1_000) {
            awaitLoginUser(manager(current, old, logged("old-user"), UserState.LoggingIn, logged("new-user")))
        }
        assertEquals("new-user", user.userInfo.userId)
    }

    @Test fun missingLoginIdentityFailsImmediatelyInsteadOfWaitingForNullUser() = runBlocking {
        try {
            withTimeout(1_000) { awaitLoginUser(manager(null, null, UserState.NotLoggedIn)) }
            fail("Missing identity must fail")
        } catch (_: IllegalArgumentException) { }
    }
}
